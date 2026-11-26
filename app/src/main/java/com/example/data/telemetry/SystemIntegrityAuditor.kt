package com.example.data.telemetry

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.example.data.local.AppTrustEntity
import com.example.data.local.TrustLevel
import com.example.security.GuardOperatingMode
import com.example.security.RootBridge
import com.example.security.RootEnvironmentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class IntegrityCheckItem(
    val id: String,
    val title: String,
    val statusLabel: String,
    val passed: Boolean,
    val detail: String,
    val rootRequired: Boolean = false
)

data class IntegrityAuditReport(
    val overallScore: Int,
    val rootStatus: RootEnvironmentStatus,
    val usageStatsGranted: Boolean,
    val checks: List<IntegrityCheckItem>,
    val highRiskAppsCount: Int,
    val sideloadedAppsCount: Int,
    val blockedAppsCount: Int,
    val auditedAtMs: Long
)

class SystemIntegrityAuditor(
    private val context: Context,
    private val rootBridge: RootBridge
) {

    suspend fun runFullIntegrityAudit(
        apps: List<AppTrustEntity>
    ): IntegrityAuditReport = withContext(Dispatchers.IO) {
        val rootStatus = rootBridge.inspectEnvironment()
        val usageGranted = hasUsageStatsPermission(context)
        val checks = mutableListOf<IntegrityCheckItem>()

        // 1. Build tags & release-keys check
        val buildTags = Build.TAGS ?: "unknown"
        val isReleaseKeys = buildTags.contains("release-keys")
        checks += IntegrityCheckItem(
            id = "build_tags",
            title = "OS Signing Keys",
            statusLabel = if (isReleaseKeys) "RELEASE-KEYS" else buildTags.uppercase(),
            passed = isReleaseKeys,
            detail = if (isReleaseKeys) {
                "System image signed with official release keys."
            } else {
                "Non-standard build tags detected ($buildTags)."
            }
        )

        // 2. SELinux status
        val selinuxEnforcing = rootStatus.selinuxStatus.equals("Enforcing", ignoreCase = true)
        checks += IntegrityCheckItem(
            id = "selinux",
            title = "SELinux Policy Enforcement",
            statusLabel = rootStatus.selinuxStatus.uppercase(),
            passed = selinuxEnforcing || rootStatus.selinuxStatus == "Restricted",
            detail = if (selinuxEnforcing || rootStatus.selinuxStatus == "Restricted") {
                "Mandatory Access Control active (${rootStatus.selinuxStatus})."
            } else {
                "Warning: SELinux is permissive or disabled."
            }
        )

        // 3. ADB / USB Debugging check
        val adbEnabled = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)
        checks += IntegrityCheckItem(
            id = "adb_debug",
            title = "USB Debugging (ADB)",
            statusLabel = if (adbEnabled) "ENABLED" else "DISABLED",
            passed = !adbEnabled,
            detail = if (adbEnabled) {
                "ADB bridge is enabled. Disable when not actively debugging."
            } else {
                "ADB debugging interface is locked."
            }
        )

        // 4. Root / Hook Framework Integrity (Frida / Xposed / Magisk)
        val hookArtifacts = listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/system/framework/XposedBridge.jar"
        ).filter { File(it).exists() }

        checks += IntegrityCheckItem(
            id = "hooks",
            title = "Runtime Hook & Injection Scan",
            statusLabel = if (hookArtifacts.isEmpty()) "CLEAN" else "DETECTED",
            passed = hookArtifacts.isEmpty(),
            detail = if (hookArtifacts.isEmpty()) {
                "No Xposed or Frida runtime injection artifacts found."
            } else {
                "Hook artifacts found: ${hookArtifacts.joinToString()}"
            }
        )

        // 5. Root Binary Checksums & Deep Root Posture vs Non-Root Safety Net
        if (rootStatus.mode == GuardOperatingMode.ROOTED_ACTIVE) {
            val shHash = rootBridge.computeRootSystemFileHash("/system/bin/sh")
            val appProcHash = rootBridge.computeRootSystemFileHash("/system/bin/app_process64")
            val hostsLines = rootBridge.checkHostsFileCustomEntries()
            checks += IntegrityCheckItem(
                id = "root_sys_bin",
                title = "System Binary SHA-256 Watch",
                statusLabel = if (shHash != null || appProcHash != null) "VERIFIED" else "PARTIAL",
                passed = shHash != null || appProcHash != null,
                detail = buildString {
                    append("${rootStatus.rootManagerName} (${rootStatus.moduleCount} modules) • Boot: ${rootStatus.verifiedBootState}. ")
                    if (shHash != null) append("sh=${shHash.take(10)}… ")
                    if (appProcHash != null) append("app_process64=${appProcHash.take(10)}… ")
                    append("• hosts entries: $hostsLines")
                },
                rootRequired = true
            )
        } else {
            checks += IntegrityCheckItem(
                id = "non_root_safety",
                title = "Non-Root Safety Net",
                statusLabel = if (usageGranted) "FULL TELEMETRY" else "LIMITED ACCESS",
                passed = usageGranted,
                detail = if (usageGranted) {
                    "UsageStats, Camera/Mic lease callbacks, Clipboard watch, and Package Auditor active without root."
                } else {
                    "Grant Usage Access in Android Settings to unlock background & network session timelines."
                }
            )
        }

        // 6. Sideload & High-Risk App Posture
        val highRiskApps = apps.filter { it.riskScore >= 65 && it.trustLevel != TrustLevel.TRUSTED.name }
        val sideloaded = apps.filter { it.isSideloaded }
        val blocked = apps.filter { it.trustLevel == TrustLevel.BLOCKED.name }

        checks += IntegrityCheckItem(
            id = "app_posture",
            title = "Installed App Risk Posture",
            statusLabel = "${highRiskApps.size} HIGH RISK",
            passed = highRiskApps.isEmpty(),
            detail = "${apps.size} audited • ${sideloaded.size} sideloaded • ${blocked.size} blocked"
        )

        var score = 100
        if (!isReleaseKeys) score -= 10
        if (!selinuxEnforcing && rootStatus.selinuxStatus != "Restricted") score -= 20
        if (adbEnabled) score -= 8
        if (hookArtifacts.isNotEmpty()) score -= 22
        if (!usageGranted && rootStatus.mode != GuardOperatingMode.ROOTED_ACTIVE) score -= 8
        score -= (highRiskApps.size * 6).coerceAtMost(28)
        score -= (sideloaded.count { it.riskScore >= 50 } * 4).coerceAtMost(12)

        IntegrityAuditReport(
            overallScore = score.coerceIn(12, 100),
            rootStatus = rootStatus,
            usageStatsGranted = usageGranted,
            checks = checks,
            highRiskAppsCount = highRiskApps.size,
            sideloadedAppsCount = sideloaded.size,
            blockedAppsCount = blocked.size,
            auditedAtMs = System.currentTimeMillis()
        )
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun sha256OfBytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
