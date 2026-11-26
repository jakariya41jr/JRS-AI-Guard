package com.example.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

enum class GuardOperatingMode(val title: String, val badge: String) {
    ROOTED_ACTIVE("Rooted Deep Watchdog", "ROOT SU ACTIVE"),
    ROOT_BINARY_LOCKED("Root Binary Detected (SU Denied)", "ROOT LOCKED"),
    NON_ROOT_SAFETY_NET("Non-Root Safety Net", "NON-ROOT GUARD")
}

data class RootEnvironmentStatus(
    val mode: GuardOperatingMode,
    val suBinaryFound: Boolean,
    val suPath: String?,
    val suGrantVerified: Boolean,
    val magiskOrKernelSuDetected: Boolean,
    val selinuxStatus: String,
    val uidOutput: String
)

data class RootActionResult(
    val success: Boolean,
    val executedViaRoot: Boolean,
    val message: String
)

class RootBridge {

    companion object {
        private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
        private val PERMISSION_REGEX = Regex("^[a-zA-Z0-9_.]+$")
        private val SU_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su"
        )
    }

    suspend fun inspectEnvironment(): RootEnvironmentStatus = withContext(Dispatchers.IO) {
        val foundSu = SU_PATHS.firstOrNull { File(it).exists() }
        val magiskFound = File("/data/adb/magisk").exists() ||
            File("/data/adb/ksu").exists() ||
            File("/sbin/.magisk").exists() ||
            File("/debug_ramdisk/.magisk").exists()

        val idCheck = runSuCommandInternal("id", timeoutSeconds = 2)
        val suVerified = idCheck.first && idCheck.second.contains("uid=0")

        val selinux = readSysFile("/sys/fs/selinux/enforce")?.let {
            if (it.trim() == "1") "Enforcing" else "Permissive"
        } ?: runCommandDirect(listOf("getenforce")).second.trim().ifEmpty { "Restricted" }

        val mode = when {
            suVerified -> GuardOperatingMode.ROOTED_ACTIVE
            foundSu != null || magiskFound -> GuardOperatingMode.ROOT_BINARY_LOCKED
            else -> GuardOperatingMode.NON_ROOT_SAFETY_NET
        }

        RootEnvironmentStatus(
            mode = mode,
            suBinaryFound = foundSu != null,
            suPath = foundSu,
            suGrantVerified = suVerified,
            magiskOrKernelSuDetected = magiskFound,
            selinuxStatus = selinux,
            uidOutput = idCheck.second.trim().ifEmpty { "uid=${android.os.Process.myUid()}" }
        )
    }

    suspend fun forceStopPackage(context: Context, packageName: String, isRootGranted: Boolean): RootActionResult {
        if (!isValidPackage(packageName)) {
            return RootActionResult(false, false, "Invalid package identifier rejected")
        }
        if (isRootGranted) {
            val res = runSuCommand("am force-stop $packageName")
            if (res.first) {
                return RootActionResult(true, true, "Force-stopped $packageName via root shell")
            }
        }
        openAppDetailsSettings(context, packageName)
        return RootActionResult(
            success = true,
            executedViaRoot = false,
            message = "Non-root mode: Opened System App Info for $packageName to complete Force Stop"
        )
    }

    suspend fun freezeOrUnfreezePackage(
        context: Context,
        packageName: String,
        freeze: Boolean,
        isRootGranted: Boolean
    ): RootActionResult {
        if (!isValidPackage(packageName)) {
            return RootActionResult(false, false, "Invalid package identifier rejected")
        }
        if (isRootGranted) {
            val subCmd = if (freeze) "disable-user --user 0" else "enable"
            val res = runSuCommand("pm $subCmd $packageName")
            if (res.first) {
                val verb = if (freeze) "Frozen" else "Unfrozen"
                return RootActionResult(true, true, "$verb $packageName via root pm")
            }
        }
        openAppDetailsSettings(context, packageName)
        return RootActionResult(
            success = true,
            executedViaRoot = false,
            message = "Non-root mode: Opened App Info for $packageName (Freeze requires root; marked in local Watchlist/Blocked)"
        )
    }

    suspend fun revokePermission(
        context: Context,
        packageName: String,
        permissionName: String,
        isRootGranted: Boolean
    ): RootActionResult {
        if (!isValidPackage(packageName) || !PERMISSION_REGEX.matches(permissionName)) {
            return RootActionResult(false, false, "Invalid package or permission name rejected")
        }
        if (isRootGranted) {
            val res = runSuCommand("pm revoke $packageName $permissionName")
            if (res.first) {
                return RootActionResult(
                    true,
                    true,
                    "Revoked ${permissionName.substringAfterLast('.')} from $packageName via root"
                )
            }
        }
        openAppDetailsSettings(context, packageName)
        return RootActionResult(
            success = true,
            executedViaRoot = false,
            message = "Non-root mode: Opened App Permissions settings for $packageName"
        )
    }

    suspend fun setNetworkBlocked(
        uid: Int,
        packageName: String,
        block: Boolean,
        isRootGranted: Boolean
    ): RootActionResult {
        if (!isValidPackage(packageName) || uid <= 1000) {
            return RootActionResult(false, false, "Refused network rule on core system UID")
        }
        if (isRootGranted) {
            val policyCmd = if (block) {
                "cmd netpolicy add restrict-background-blacklist $uid"
            } else {
                "cmd netpolicy remove restrict-background-blacklist $uid"
            }
            val res = runSuCommand(policyCmd)
            if (res.first) {
                val state = if (block) "Blocked" else "Allowed"
                return RootActionResult(true, true, "$state background network policy for $packageName (UID $uid)")
            }
        }
        return RootActionResult(
            success = true,
            executedViaRoot = false,
            message = if (block) {
                "Added $packageName to JRS Guard Blocked Watchlist (kernel firewall requires root)"
            } else {
                "Removed $packageName from JRS Guard network blocklist"
            }
        )
    }

    suspend fun readRootActiveConnections(): List<String> = withContext(Dispatchers.IO) {
        val res = runSuCommandInternal("ss -tunap 2>/dev/null || netstat -tupn 2>/dev/null", 3)
        if (!res.first) return@withContext emptyList()
        res.second.lines().filter { it.isNotBlank() }.take(40)
    }

    suspend fun readRootSuAccessLogs(): List<String> = withContext(Dispatchers.IO) {
        val res = runSuCommandInternal(
            "logcat -d -t 40 -s Magisk:I su:I KernelSU:I 2>/dev/null",
            3
        )
        if (!res.first) return@withContext emptyList()
        res.second.lines().filter { it.isNotBlank() }.take(25)
    }

    suspend fun computeRootSystemFileHash(path: String): String? = withContext(Dispatchers.IO) {
        if (!path.startsWith("/system/") && !path.startsWith("/vendor/")) return@withContext null
        val safePath = path.replace(Regex("[^a-zA-Z0-9_./-]"), "")
        val res = runSuCommandInternal("sha256sum $safePath 2>/dev/null", 3)
        if (res.first) res.second.trim().substringBefore(" ").takeIf { it.length == 64 } else null
    }

    private suspend fun runSuCommand(command: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runSuCommandInternal(command, timeoutSeconds = 4)
    }

    private fun runSuCommandInternal(command: String, timeoutSeconds: Long): Pair<Boolean, String> {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@runCatching false to "Timed out"
            }
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            (process.exitValue() == 0) to output
        }.getOrElse { false to (it.message ?: "su unavailable") }
    }

    private fun runCommandDirect(cmd: List<String>): Pair<Boolean, String> {
        return runCatching {
            val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            process.waitFor(2, TimeUnit.SECONDS)
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            (process.exitValue() == 0) to output
        }.getOrElse { false to "" }
    }

    private fun readSysFile(path: String): String? {
        return runCatching { File(path).takeIf { it.canRead() }?.readText() }.getOrNull()
    }

    private fun isValidPackage(pkg: String): Boolean = PACKAGE_REGEX.matches(pkg)

    fun openAppDetailsSettings(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
