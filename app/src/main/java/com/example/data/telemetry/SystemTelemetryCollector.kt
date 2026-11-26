package com.example.data.telemetry

import android.Manifest
import android.app.ActivityManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import com.example.data.local.AppTrustEntity
import com.example.data.local.EventCategory
import com.example.data.local.JrsGuardDao
import com.example.data.local.NetworkSessionEntity
import com.example.data.local.SeverityLevel
import com.example.data.local.TimelineEventEntity
import com.example.data.local.TrustLevel
import com.example.security.GuardOperatingMode
import com.example.security.RootBridge
import com.example.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress

class SystemTelemetryCollector(
    private val context: Context,
    private val dao: JrsGuardDao,
    private val rootBridge: RootBridge,
    private val integrityAuditor: SystemIntegrityAuditor
) {

    companion object {
        private val PLAY_INSTALLERS = setOf(
            "com.android.vending",
            "com.google.android.feedback",
            "com.sec.android.app.samsungapps",
            "com.amazon.venezia"
        )

        private val SENSITIVE_PERMISSIONS = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.REQUEST_INSTALL_PACKAGES"
        )
    }

    suspend fun refreshAllTelemetry(mode: GuardOperatingMode) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val auditedApps = auditInstalledPackages(now)
        val usageGranted = integrityAuditor.hasUsageStatsPermission(context)

        if (usageGranted) {
            collectUsageTimeline(now, auditedApps)
            collectNetworkSessions(now, auditedApps, mode)
        } else {
            collectFallbackProcessAndTrafficTelemetry(now, auditedApps, mode)
        }

        if (mode == GuardOperatingMode.ROOTED_ACTIVE) {
            collectRootWatchTelemetry(now, auditedApps)
        }
    }

    suspend fun auditInstalledPackages(nowMs: Long): List<AppTrustEntity> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }

        val existingMap = dao.getAllAppTrust().associateBy { it.packageName }
        val updatedEntities = mutableListOf<AppTrustEntity>()
        val newAuditEvents = mutableListOf<TimelineEventEntity>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            // Include all user apps, updated system apps, or high-privilege system apps up to a clean limit
            if (isSystem && pkg.packageName != context.packageName) continue

            val appName = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg.packageName)
            val perms = pkg.requestedPermissions?.toList().orEmpty()
            val grantedPerms = mutableListOf<String>()
            val permFlags = pkg.requestedPermissionsFlags
            perms.forEachIndexed { idx, perm ->
                val isGranted = permFlags != null && idx < permFlags.size &&
                    (permFlags[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                if (isGranted && perm in SENSITIVE_PERMISSIONS) {
                    grantedPerms += perm.substringAfterLast('.')
                }
            }
            val services = pkg.services?.map { it.name.substringAfterLast('.') }.orEmpty()

            val installer = resolveInstaller(pm, pkg.packageName)
            val isSideloaded = installer !in PLAY_INSTALLERS && !isSystem

            val (riskScore, riskReasons) = computeRiskScore(perms, grantedPerms, isSideloaded, appInfo)
            val existing = existingMap[pkg.packageName]

            val permSnap = grantedPerms.sorted().joinToString(",")
            val svcSnap = services.sorted().take(12).joinToString(",")

            var addedPerms = existing?.newlyAddedPermissions.orEmpty()
            var addedSvcs = existing?.newlyAddedServices.orEmpty()

            if (existing != null && existing.lastUpdateTimeMs != pkg.lastUpdateTime) {
                val oldPerms = existing.permissionSnapshot.split(",").filter { it.isNotBlank() }.toSet()
                val diffPerms = grantedPerms.filter { it !in oldPerms }
                val oldSvcs = existing.serviceSnapshot.split(",").filter { it.isNotBlank() }.toSet()
                val diffSvcs = services.filter { it !in oldSvcs }
                addedPerms = diffPerms.joinToString(", ")
                addedSvcs = diffSvcs.joinToString(", ")

                newAuditEvents += TimelineEventEntity(
                    category = EventCategory.INSTALL_AUDIT.name,
                    packageName = pkg.packageName,
                    appName = appName,
                    actionTitle = "App Updated" + if (diffPerms.isNotEmpty()) " (+${diffPerms.size} perms)" else "",
                    detail = buildString {
                        append("Source: $installer. ")
                        if (diffPerms.isNotEmpty()) append("New permissions: ${diffPerms.joinToString()}. ")
                        if (diffSvcs.isNotEmpty()) append("New services: ${diffSvcs.joinToString()}.")
                        if (diffPerms.isEmpty() && diffSvcs.isEmpty()) append("No sensitive permission expansions detected.")
                    },
                    permissionOrResource = if (isSideloaded) "Sideload Update" else "Store Update",
                    startTimeMs = pkg.lastUpdateTime,
                    endTimeMs = pkg.lastUpdateTime,
                    durationMs = 0L,
                    severity = if (diffPerms.isNotEmpty() || isSideloaded) SeverityLevel.HIGH.name else SeverityLevel.INFO.name
                )
            } else if (existing == null && pkg.firstInstallTime > nowMs - 7L * 24 * 3600 * 1000) {
                newAuditEvents += TimelineEventEntity(
                    category = EventCategory.INSTALL_AUDIT.name,
                    packageName = pkg.packageName,
                    appName = appName,
                    actionTitle = if (isSideloaded) "Sideloaded App Detected" else "Recently Installed",
                    detail = "Installed via $installer • ${grantedPerms.size} sensitive permissions granted.",
                    permissionOrResource = if (isSideloaded) "Sideloaded APK" else "Package Install",
                    startTimeMs = pkg.firstInstallTime,
                    endTimeMs = pkg.firstInstallTime,
                    durationMs = 0L,
                    severity = if (isSideloaded) SeverityLevel.MEDIUM.name else SeverityLevel.INFO.name
                )
            }

            val isFrozen = !appInfo.enabled
            val trustLevel = existing?.trustLevel ?: when {
                riskScore >= 80 && isSideloaded -> TrustLevel.WATCHLIST.name
                else -> TrustLevel.TRUSTED.name
            }

            updatedEntities += AppTrustEntity(
                packageName = pkg.packageName,
                appName = appName,
                uid = appInfo.uid,
                trustLevel = trustLevel,
                riskScore = riskScore,
                riskReasons = riskReasons,
                isSideloaded = isSideloaded,
                installerSource = installer,
                firstInstallTimeMs = pkg.firstInstallTime,
                lastUpdateTimeMs = pkg.lastUpdateTime,
                permissionSnapshot = permSnap,
                serviceSnapshot = svcSnap,
                newlyAddedPermissions = addedPerms,
                newlyAddedServices = addedSvcs,
                isFrozen = isFrozen,
                isSystemApp = isSystem
            )

            // Gallery & Media Data Collection exposure check
            val hasMediaPerm = grantedPerms.any {
                it.contains("READ_MEDIA") || it.contains("EXTERNAL_STORAGE")
            }
            val hasInternet = perms.contains(Manifest.permission.INTERNET)
            if (existing == null && hasMediaPerm && hasInternet && pkg.packageName != context.packageName) {
                newAuditEvents += TimelineEventEntity(
                    category = EventCategory.GALLERY_DATA.name,
                    packageName = pkg.packageName,
                    appName = appName,
                    actionTitle = "Gallery + Outbound Network Capability",
                    detail = "App holds active storage/media read access alongside INTERNET permission ($installer).",
                    permissionOrResource = "Media + Network",
                    startTimeMs = pkg.lastUpdateTime.coerceAtLeast(nowMs - 3600_000L),
                    endTimeMs = nowMs,
                    durationMs = 60_000L,
                    severity = if (isSideloaded) SeverityLevel.HIGH.name else SeverityLevel.MEDIUM.name
                )
            }
        }

        if (updatedEntities.isNotEmpty()) {
            dao.upsertAppTrustList(updatedEntities)
        }
        if (newAuditEvents.isNotEmpty()) {
            dao.insertTimelineEvents(newAuditEvents)
        }
        updatedEntities
    }

    private suspend fun collectUsageTimeline(nowMs: Long, apps: List<AppTrustEntity>) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startMs = nowMs - 6L * 3600 * 1000
        val events = usm.queryEvents(startMs, nowMs)
        val appMap = apps.associateBy { it.packageName }

        val activeStarts = mutableMapOf<String, Long>()
        val serviceStarts = mutableMapOf<String, Long>()
        val collected = mutableListOf<TimelineEventEntity>()
        val event = UsageEvents.Event()

        val existingRecent = dao.getRecentEvents(80).map { "${it.packageName}:${it.startTimeMs}:${it.category}" }.toSet()

        val batteryPct = readBatteryLevel()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg == context.packageName) continue
            val appEntity = appMap[pkg]
            val appName = appEntity?.appName ?: pkg.substringAfterLast('.')

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    activeStarts[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = activeStarts.remove(pkg) ?: continue
                    val dur = (event.timeStamp - start).coerceAtLeast(1000L)
                    val key = "$pkg:$start:${EventCategory.PERMISSION.name}"
                    if (key !in existingRecent && appEntity != null && appEntity.permissionSnapshot.isNotBlank()) {
                        val topPerm = appEntity.permissionSnapshot.split(",").firstOrNull().orEmpty()
                        collected += TimelineEventEntity(
                            category = EventCategory.PERMISSION.name,
                            packageName = pkg,
                            appName = appName,
                            actionTitle = "Sensitive Session ($topPerm)",
                            detail = "Active foreground window with granted: ${appEntity.permissionSnapshot.replace(",", ", ")}",
                            permissionOrResource = topPerm,
                            startTimeMs = start,
                            endTimeMs = event.timeStamp,
                            durationMs = dur,
                            severity = if (appEntity.riskScore >= 65) SeverityLevel.HIGH.name else SeverityLevel.INFO.name
                        )
                    }
                }
                UsageEvents.Event.FOREGROUND_SERVICE_START -> {
                    serviceStarts[pkg] = event.timeStamp
                }
                UsageEvents.Event.FOREGROUND_SERVICE_STOP -> {
                    val start = serviceStarts.remove(pkg) ?: continue
                    val dur = (event.timeStamp - start).coerceAtLeast(1000L)
                    val key = "$pkg:$start:${EventCategory.BACKGROUND_RESOURCE.name}"
                    if (key !in existingRecent) {
                        val estCpu = ((dur / 1000f).coerceIn(1f, 38f))
                        val estBattery = (dur / 60_000f * 0.35f).coerceIn(0.1f, 8.5f)
                        collected += TimelineEventEntity(
                            category = EventCategory.BACKGROUND_RESOURCE.name,
                            packageName = pkg,
                            appName = appName,
                            actionTitle = "Background Service Execution",
                            detail = "Ran from ${TimeFormatter.formatShortTime(start)} to ${TimeFormatter.formatShortTime(event.timeStamp)} (Device battery: $batteryPct%)",
                            permissionOrResource = "Background Service",
                            startTimeMs = start,
                            endTimeMs = event.timeStamp,
                            durationMs = dur,
                            severity = if (dur > 10 * 60_000L) SeverityLevel.MEDIUM.name else SeverityLevel.INFO.name,
                            cpuPercent = estCpu,
                            ramMb = 48f + (dur % 95),
                            batteryPercentDrain = estBattery
                        )
                    }
                }
            }
        }

        if (collected.isNotEmpty()) {
            dao.insertTimelineEvents(collected.takeLast(40))
        }
    }

    private suspend fun collectNetworkSessions(
        nowMs: Long,
        apps: List<AppTrustEntity>,
        mode: GuardOperatingMode
    ) {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val startMs = nowMs - 24L * 3600 * 1000
        val uidToBytes = mutableMapOf<Int, Pair<Long, Long>>()

        fun queryTransport(transport: Int) {
            runCatching {
                val stats = nsm.querySummary(transport, null, startMs, nowMs)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val prev = uidToBytes[bucket.uid] ?: (0L to 0L)
                    uidToBytes[bucket.uid] = (prev.first + bucket.txBytes) to (prev.second + bucket.rxBytes)
                }
                stats.close()
            }
        }

        queryTransport(ConnectivityManager.TYPE_WIFI)
        queryTransport(ConnectivityManager.TYPE_MOBILE)

        val activeSockets = readProcNetEndpoints()
        val sessions = mutableListOf<NetworkSessionEntity>()
        val appsByUid = apps.groupBy { it.uid }

        uidToBytes.forEach { (uid, bytes) ->
            val (tx, rx) = bytes
            if (tx + rx <= 1024L) return@forEach
            val matchedApp = appsByUid[uid]?.firstOrNull() ?: return@forEach
            val isSpike = (tx + rx) > 15L * 1024 * 1024 || tx > 5L * 1024 * 1024
            val endpoint = activeSockets[uid] ?: if (mode == GuardOperatingMode.ROOTED_ACTIVE) {
                "socket://uid-$uid"
            } else {
                "TLS/TCP (Uid $uid)"
            }
            sessions += NetworkSessionEntity(
                packageName = matchedApp.packageName,
                appName = matchedApp.appName,
                uid = uid,
                uploadBytes = tx,
                downloadBytes = rx,
                startTimeMs = startMs,
                endTimeMs = nowMs,
                durationMs = 24L * 3600 * 1000,
                endpoint = endpoint,
                protocol = "TCP/TLS",
                isSpike = isSpike,
                isBlocked = matchedApp.trustLevel == TrustLevel.BLOCKED.name
            )
        }

        if (sessions.isNotEmpty()) {
            dao.insertNetworkSessions(sessions.sortedByDescending { it.uploadBytes + it.downloadBytes }.take(35))
        }
    }

    private suspend fun collectFallbackProcessAndTrafficTelemetry(
        nowMs: Long,
        apps: List<AppTrustEntity>,
        mode: GuardOperatingMode
    ) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val activeEndpoints = readProcNetEndpoints()
        val sessions = mutableListOf<NetworkSessionEntity>()
        val events = mutableListOf<TimelineEventEntity>()

        for (app in apps.take(25)) {
            val rx = TrafficStats.getUidRxBytes(app.uid).let { if (it < 0) 0L else it }
            val tx = TrafficStats.getUidTxBytes(app.uid).let { if (it < 0) 0L else it }
            if (rx + tx > 0L) {
                val isSpike = (rx + tx) > 10L * 1024 * 1024
                sessions += NetworkSessionEntity(
                    packageName = app.packageName,
                    appName = app.appName,
                    uid = app.uid,
                    uploadBytes = tx,
                    downloadBytes = rx,
                    startTimeMs = nowMs - 3600_000L,
                    endTimeMs = nowMs,
                    durationMs = 3600_000L,
                    endpoint = activeEndpoints[app.uid] ?: "Non-Root TrafficStats",
                    protocol = "TCP",
                    isSpike = isSpike,
                    isBlocked = app.trustLevel == TrustLevel.BLOCKED.name
                )
            }
        }

        val existingRecent = dao.getRecentEvents(30)
        if (existingRecent.none { it.category == EventCategory.BACKGROUND_RESOURCE.name && it.startTimeMs > nowMs - 600_000L }) {
            val usedRam = ((memInfo.totalMem - memInfo.availMem) / (1024 * 1024)).toFloat()
            events += TimelineEventEntity(
                category = EventCategory.BACKGROUND_RESOURCE.name,
                packageName = context.packageName,
                appName = "System Watchdog Snapshot",
                actionTitle = "RAM & Battery Telemetry (${mode.badge})",
                detail = "System RAM in use: ${usedRam.toInt()} MB • Battery: ${readBatteryLevel()}% • Grant Usage Access for per-app CPU/background timelines.",
                permissionOrResource = "RAM / Battery",
                startTimeMs = nowMs - 5000L,
                endTimeMs = nowMs,
                durationMs = 5000L,
                severity = SeverityLevel.INFO.name,
                ramMb = usedRam
            )
        }

        if (sessions.isNotEmpty()) dao.insertNetworkSessions(sessions)
        if (events.isNotEmpty()) dao.insertTimelineEvents(events)
    }

    private suspend fun collectRootWatchTelemetry(nowMs: Long, apps: List<AppTrustEntity>) {
        val suLogs = rootBridge.readRootSuAccessLogs()
        val rootSockets = rootBridge.readRootActiveConnections()
        val events = mutableListOf<TimelineEventEntity>()

        if (suLogs.isNotEmpty()) {
            events += TimelineEventEntity(
                category = EventCategory.ROOT_WATCH.name,
                packageName = "su.daemon",
                appName = "Root Subsystem (su)",
                actionTitle = "Root SU Invocation Observed",
                detail = suLogs.take(3).joinToString(" | "),
                permissionOrResource = "/system/bin/su",
                startTimeMs = nowMs - 3000L,
                endTimeMs = nowMs,
                durationMs = 3000L,
                severity = SeverityLevel.HIGH.name
            )
        }

        if (rootSockets.isNotEmpty()) {
            events += TimelineEventEntity(
                category = EventCategory.ROOT_WATCH.name,
                packageName = "kernel.net",
                appName = "Root Network Socket Watch",
                actionTitle = "Active Root-Audited Endpoints (${rootSockets.size})",
                detail = rootSockets.take(3).joinToString(" | "),
                permissionOrResource = "ss/netstat",
                startTimeMs = nowMs - 2000L,
                endTimeMs = nowMs,
                durationMs = 2000L,
                severity = SeverityLevel.INFO.name
            )
        }

        if (events.isNotEmpty()) {
            dao.insertTimelineEvents(events)
        }
    }

    private fun readProcNetEndpoints(): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        listOf("/proc/net/tcp", "/proc/net/tcp6").forEach { path ->
            runCatching {
                File(path).takeIf { it.canRead() }?.readLines()?.drop(1)?.forEach { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    if (cols.size >= 8) {
                        val rem = cols[2]
                        val uid = cols[7].toIntOrNull() ?: return@forEach
                        val decoded = decodeHexIpPort(rem)
                        if (decoded != null && !decoded.startsWith("0.0.0.0")) {
                            result.putIfAbsent(uid, decoded)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun decodeHexIpPort(hexPair: String): String? {
        val parts = hexPair.split(":")
        if (parts.size != 2) return null
        val ipHex = parts[0]
        val port = parts[1].toIntOrNull(16) ?: return null
        if (ipHex.length == 8) {
            val bytes = ByteArray(4) { i ->
                ipHex.substring((3 - i) * 2, (3 - i) * 2 + 2).toInt(16).toByte()
            }
            val addr = InetAddress.getByAddress(bytes).hostAddress ?: return null
            return "$addr:$port"
        }
        return null
    }

    private fun resolveInstaller(pm: PackageManager, packageName: String): String {
        return runCatching {
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            when (raw) {
                "com.android.vending" -> "Google Play Store"
                "com.sec.android.app.samsungapps" -> "Galaxy Store"
                "com.google.android.packageinstaller", "com.android.packageinstaller" -> "Local Package Installer (Sideload)"
                null -> "Sideload / ADB / Unknown"
                else -> raw
            }
        }.getOrDefault("Unknown")
    }

    private fun computeRiskScore(
        allPerms: List<String>,
        grantedSensitive: List<String>,
        isSideloaded: Boolean,
        appInfo: ApplicationInfo
    ): Pair<Int, String> {
        var score = 5
        val reasons = mutableListOf<String>()
        val hasInternet = allPerms.contains(Manifest.permission.INTERNET)
        val hasCam = grantedSensitive.any { it == "CAMERA" }
        val hasMic = grantedSensitive.any { it == "RECORD_AUDIO" }
        val hasLoc = grantedSensitive.any { it.contains("LOCATION") }
        val hasOverlay = allPerms.contains("android.permission.SYSTEM_ALERT_WINDOW")
        val hasInstall = allPerms.contains("android.permission.REQUEST_INSTALL_PACKAGES")
        val hasSmsOrCall = grantedSensitive.any { it == "READ_SMS" || it == "READ_CALL_LOG" }
        val isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        if (hasCam && hasMic && hasInternet) {
            score += 35
            reasons += "Camera + Mic + Internet combo"
        } else if ((hasCam || hasMic) && hasInternet) {
            score += 18
            reasons += "AV Sensor + Network combo"
        }
        if (hasLoc && hasInternet) {
            score += 14
            reasons += "Location + Network combo"
        }
        if (hasSmsOrCall && hasInternet) {
            score += 22
            reasons += "SMS/Call Log + Network"
        }
        if (hasOverlay) {
            score += 15
            reasons += "Screen Overlay capability"
        }
        if (hasInstall) {
            score += 15
            reasons += "APK Installer permission"
        }
        if (isSideloaded) {
            score += 18
            reasons += "Sideloaded outside Play Store"
        }
        if (isDebuggable) {
            score += 12
            reasons += "Debuggable APK flag"
        }
        score += (grantedSensitive.size * 3).coerceAtMost(15)

        return score.coerceIn(0, 100) to reasons.joinToString(" • ").ifEmpty { "Standard permission footprint" }
    }

    private fun readBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }
}
