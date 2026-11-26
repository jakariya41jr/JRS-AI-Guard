package com.example.data.export

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.local.AppTrustEntity
import com.example.data.local.NetworkSessionEntity
import com.example.data.local.SecuritySnapshotEntity
import com.example.data.local.TimelineEventEntity
import com.example.security.CryptoVault
import com.example.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    JSON("json", "application/json", "JSON"),
    CSV("csv", "text/csv", "CSV"),
    TEXT("txt", "text/plain", "Text")
}

class ReportExporter(
    private val context: Context,
    private val cryptoVault: CryptoVault
) {

    suspend fun capturePointInTimeSnapshot(
        triggerReason: String,
        severity: String,
        operatingMode: String,
        recentEvents: List<TimelineEventEntity>,
        recentNetwork: List<NetworkSessionEntity>,
        apps: List<AppTrustEntity>
    ): SecuritySnapshotEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val usedRamMb = ((memInfo.totalMem - memInfo.availMem) / (1024 * 1024)).coerceAtLeast(0L)
        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).coerceAtLeast(1L)

        val highRiskApps = apps.filter { it.riskScore >= 60 }
        val activeProcessSummary = "Running services/apps tracked: ${recentEvents.map { it.packageName }.distinct().size} | High-Risk Apps: ${highRiskApps.size}"
        val permSummary = recentEvents.take(5).joinToString("; ") {
            "${it.appName}: ${it.permissionOrResource} (${TimeFormatter.formatDuration(it.durationMs)})"
        }.ifEmpty { "No recent permission bursts" }
        val netSummary = recentNetwork.take(5).joinToString("; ") {
            "${it.appName} ↑${TimeFormatter.formatBytes(it.uploadBytes)} ↓${TimeFormatter.formatBytes(it.downloadBytes)}"
        }.ifEmpty { "Baseline network state" }
        val resourceSummary = "RAM Used: ${usedRamMb}MB / ${totalRamMb}MB | LowMemory: ${memInfo.lowMemory}"

        val rawPayloadJson = JSONObject().apply {
            put("app", "JRS AI Guard")
            put("package", BuildConfig.APPLICATION_ID)
            put("developer", "Md Jakariya Hasan")
            put("capturedAt", TimeFormatter.formatDateTime(now))
            put("triggerReason", triggerReason)
            put("operatingMode", operatingMode)
            put("ramUsedMb", usedRamMb)
            put("ramTotalMb", totalRamMb)
            put("highRiskPackages", JSONArray(highRiskApps.map { it.packageName }))
            put("recentEventCount", recentEvents.size)
        }.toString()

        SecuritySnapshotEntity(
            capturedAtMs = now,
            triggerReason = triggerReason,
            severity = severity,
            operatingMode = operatingMode,
            activeProcessesSummary = activeProcessSummary,
            permissionsSummary = permSummary,
            networkSummary = netSummary,
            resourceSummary = resourceSummary,
            encryptedPayload = cryptoVault.encryptString(rawPayloadJson)
        )
    }

    fun buildExportContent(
        format: ExportFormat,
        events: List<TimelineEventEntity>,
        networkSessions: List<NetworkSessionEntity>,
        snapshots: List<SecuritySnapshotEntity>,
        apps: List<AppTrustEntity>
    ): String {
        return when (format) {
            ExportFormat.JSON -> buildJson(events, networkSessions, snapshots, apps)
            ExportFormat.CSV -> buildCsv(events, networkSessions, snapshots)
            ExportFormat.TEXT -> buildText(events, networkSessions, snapshots, apps)
        }
    }

    suspend fun writeAndShareReport(
        format: ExportFormat,
        content: String
    ): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "jrs_ai_guard_report_${System.currentTimeMillis()}.${format.extension}")
        file.writeText(content)
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
        Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "JRS AI Guard Security Audit Report (${format.label})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildJson(
        events: List<TimelineEventEntity>,
        networkSessions: List<NetworkSessionEntity>,
        snapshots: List<SecuritySnapshotEntity>,
        apps: List<AppTrustEntity>
    ): String {
        val root = JSONObject()
        root.put("application", "JRS AI Guard")
        root.put("packageName", BuildConfig.APPLICATION_ID)
        root.put("developer", "Md Jakariya Hasan")
        root.put("exportedAt", TimeFormatter.formatDateTime(System.currentTimeMillis()))

        val eventsArr = JSONArray()
        events.forEach { ev ->
            eventsArr.put(JSONObject().apply {
                put("category", ev.category)
                put("appName", ev.appName)
                put("packageName", ev.packageName)
                put("actionTitle", ev.actionTitle)
                put("resource", ev.permissionOrResource)
                put("startTime", TimeFormatter.formatTimestamp(ev.startTimeMs))
                put("endTime", TimeFormatter.formatTimestamp(ev.endTimeMs))
                put("duration", TimeFormatter.formatDuration(ev.durationMs))
                put("severity", ev.severity)
                put("screenOff", ev.isScreenOff)
                put("detail", ev.detail)
            })
        }
        root.put("timelineEvents", eventsArr)

        val netArr = JSONArray()
        networkSessions.forEach { ns ->
            netArr.put(JSONObject().apply {
                put("appName", ns.appName)
                put("packageName", ns.packageName)
                put("uploadBytes", ns.uploadBytes)
                put("downloadBytes", ns.downloadBytes)
                put("duration", TimeFormatter.formatDuration(ns.durationMs))
                put("endpoint", ns.endpoint)
                put("isSpike", ns.isSpike)
                put("isBlocked", ns.isBlocked)
            })
        }
        root.put("networkSessions", netArr)

        val snapArr = JSONArray()
        snapshots.forEach { sn ->
            snapArr.put(JSONObject().apply {
                put("capturedAt", TimeFormatter.formatDateTime(sn.capturedAtMs))
                put("triggerReason", sn.triggerReason)
                put("severity", sn.severity)
                put("operatingMode", sn.operatingMode)
                put("processes", sn.activeProcessesSummary)
                put("permissions", sn.permissionsSummary)
                put("network", sn.networkSummary)
                put("resources", sn.resourceSummary)
            })
        }
        root.put("snapshots", snapArr)

        val trustArr = JSONArray()
        apps.filter { it.riskScore >= 40 || it.trustLevel != "TRUSTED" }.forEach { app ->
            trustArr.put(JSONObject().apply {
                put("appName", app.appName)
                put("packageName", app.packageName)
                put("trustLevel", app.trustLevel)
                put("riskScore", app.riskScore)
                put("sideloaded", app.isSideloaded)
                put("installer", app.installerSource)
            })
        }
        root.put("flaggedApps", trustArr)

        return root.toString(2)
    }

    private fun buildCsv(
        events: List<TimelineEventEntity>,
        networkSessions: List<NetworkSessionEntity>,
        snapshots: List<SecuritySnapshotEntity>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("RecordType,Timestamp,Duration,AppName,PackageName,CategoryOrEndpoint,Severity,Detail")
        fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""
        events.forEach { ev ->
            sb.appendLine(
                listOf(
                    "EVENT",
                    esc(TimeFormatter.formatDateTime(ev.startTimeMs)),
                    esc(TimeFormatter.formatDuration(ev.durationMs)),
                    esc(ev.appName),
                    esc(ev.packageName),
                    esc("${ev.category}:${ev.permissionOrResource}"),
                    esc(ev.severity),
                    esc(ev.detail)
                ).joinToString(",")
            )
        }
        networkSessions.forEach { ns ->
            sb.appendLine(
                listOf(
                    "NETWORK",
                    esc(TimeFormatter.formatDateTime(ns.endTimeMs)),
                    esc(TimeFormatter.formatDuration(ns.durationMs)),
                    esc(ns.appName),
                    esc(ns.packageName),
                    esc("${ns.protocol} ${ns.endpoint}"),
                    esc(if (ns.isSpike) "SPIKE" else "NORMAL"),
                    esc("Up=${TimeFormatter.formatBytes(ns.uploadBytes)} Down=${TimeFormatter.formatBytes(ns.downloadBytes)}")
                ).joinToString(",")
            )
        }
        snapshots.forEach { sn ->
            sb.appendLine(
                listOf(
                    "SNAPSHOT",
                    esc(TimeFormatter.formatDateTime(sn.capturedAtMs)),
                    "\"Point-in-time\"",
                    "\"System\"",
                    esc(BuildConfig.APPLICATION_ID),
                    esc(sn.operatingMode),
                    esc(sn.severity),
                    esc("${sn.triggerReason} | ${sn.resourceSummary}")
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun buildText(
        events: List<TimelineEventEntity>,
        networkSessions: List<NetworkSessionEntity>,
        snapshots: List<SecuritySnapshotEntity>,
        apps: List<AppTrustEntity>
    ): String {
        return buildString {
            appendLine("====================================================")
            appendLine("JRS AI GUARD — SECURITY & WATCHDOG AUDIT REPORT")
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Developer: Md Jakariya Hasan")
            appendLine("Exported: ${TimeFormatter.formatDateTime(System.currentTimeMillis())}")
            appendLine("====================================================")
            appendLine()
            appendLine("--- SNAPSHOTS (${snapshots.size}) ---")
            snapshots.take(15).forEach { sn ->
                appendLine("[${sn.severity}] ${TimeFormatter.formatDateTime(sn.capturedAtMs)} | ${sn.triggerReason}")
                appendLine("  Mode: ${sn.operatingMode} | ${sn.resourceSummary}")
                appendLine("  Perms: ${sn.permissionsSummary}")
                appendLine("  Net: ${sn.networkSummary}")
            }
            appendLine()
            appendLine("--- TIMELINE EVENTS (${events.size}) ---")
            events.take(80).forEach { ev ->
                appendLine("[${ev.severity}] ${TimeFormatter.formatShortTime(ev.startTimeMs)} (${TimeFormatter.formatDuration(ev.durationMs)}) | ${ev.appName} [${ev.category}]")
                appendLine("  ${ev.actionTitle}: ${ev.detail}")
            }
            appendLine()
            appendLine("--- NETWORK SESSIONS (${networkSessions.size}) ---")
            networkSessions.take(40).forEach { ns ->
                appendLine("${ns.appName} (${ns.packageName}) | ↑${TimeFormatter.formatBytes(ns.uploadBytes)} ↓${TimeFormatter.formatBytes(ns.downloadBytes)} | ${TimeFormatter.formatDuration(ns.durationMs)} | ${ns.endpoint}")
            }
            appendLine()
            appendLine("--- HIGH RISK / FLAGGED APPS ---")
            apps.filter { it.riskScore >= 50 || it.trustLevel != "TRUSTED" }.forEach { app ->
                appendLine("${app.appName} (${app.packageName}) | Risk: ${app.riskScore}/100 | Trust: ${app.trustLevel} | Sideloaded: ${app.isSideloaded}")
            }
        }
    }
}
