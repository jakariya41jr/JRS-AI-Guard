package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.data.ai.AiDigestWorker
import com.example.data.ai.GeminiClient
import com.example.data.export.ExportFormat
import com.example.data.export.ReportExporter
import com.example.data.github.GitHubReleaseInfo
import com.example.data.github.GitHubReleaseUpdater
import com.example.data.local.AiInsightEntity
import com.example.data.local.AppTrustEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.EventCategory
import com.example.data.local.JrsGuardDatabase
import com.example.data.local.NetworkSessionEntity
import com.example.data.local.ScanCacheEntity
import com.example.data.local.SecuritySnapshotEntity
import com.example.data.local.SeverityLevel
import com.example.data.local.TimelineEventEntity
import com.example.data.local.TrustLevel
import com.example.data.telemetry.ApkMalwareScanner
import com.example.data.telemetry.IntegrityAuditReport
import com.example.data.telemetry.MalwareScanUiResult
import com.example.data.telemetry.SystemIntegrityAuditor
import com.example.data.telemetry.SystemTelemetryCollector
import com.example.security.AiScheduleFrequency
import com.example.security.AppThemeMode
import com.example.security.CryptoVault
import com.example.security.GuardOperatingMode
import com.example.security.RootActionResult
import com.example.security.RootBridge
import com.example.security.RootEnvironmentStatus
import com.example.security.VaultConfig
import com.example.service.WatchdogForegroundService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class GuardRepository(private val context: Context) {

    private val db = JrsGuardDatabase.getInstance(context)
    private val dao = db.dao()
    val cryptoVault = CryptoVault(context)
    val rootBridge = RootBridge()
    val integrityAuditor = SystemIntegrityAuditor(context, rootBridge)
    private val telemetryCollector = SystemTelemetryCollector(context, dao, rootBridge, integrityAuditor)
    private val malwareScanner = ApkMalwareScanner(context, dao, cryptoVault)
    private val reportExporter = ReportExporter(context, cryptoVault)
    private val geminiClient = GeminiClient(cryptoVault)
    val githubUpdater = GitHubReleaseUpdater(context)

    val vaultConfigFlow: Flow<VaultConfig> = cryptoVault.configFlow
    val timelineEventsFlow: Flow<List<TimelineEventEntity>> = dao.observeTimelineEvents()
    val networkSessionsFlow: Flow<List<NetworkSessionEntity>> = dao.observeNetworkSessions()
    val appTrustListFlow: Flow<List<AppTrustEntity>> = dao.observeAppTrustList()
    val scanHistoryFlow: Flow<List<ScanCacheEntity>> = dao.observeScanHistory()
    val snapshotsFlow: Flow<List<SecuritySnapshotEntity>> = dao.observeSnapshots()
    val aiInsightsFlow: Flow<List<AiInsightEntity>> = dao.observeAiInsights()
    val chatMessagesFlow: Flow<List<ChatMessageEntity>> = dao.observeChatMessages()

    suspend fun refreshTelemetryAndIntegrity(): IntegrityAuditReport {
        val env = rootBridge.inspectEnvironment()
        telemetryCollector.refreshAllTelemetry(env.mode)
        val apps = dao.getAllAppTrust()
        return integrityAuditor.runFullIntegrityAudit(apps)
    }

    suspend fun verifyRootAccess(): RootEnvironmentStatus {
        return rootBridge.requestAndVerifyRootGrant()
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        cryptoVault.updateThemeMode(mode)
    }

    suspend fun runIntegrityCheckOnly(): IntegrityAuditReport {
        val apps = dao.getAllAppTrust()
        return integrityAuditor.runFullIntegrityAudit(apps)
    }

    suspend fun scanInstalledApp(packageName: String, appName: String): MalwareScanUiResult {
        val res = malwareScanner.scanInstalledAppApk(packageName, appName)
        if (res.entity.verdict == "MALICIOUS" || res.entity.verdict == "SUSPICIOUS") {
            dao.insertTimelineEvent(
                TimelineEventEntity(
                    category = EventCategory.INSTALL_AUDIT.name,
                    packageName = packageName,
                    appName = appName,
                    actionTitle = "APK Scan: ${res.entity.verdict}",
                    detail = "${res.entity.sourceApi} • ${res.entity.heuristicFlags}",
                    permissionOrResource = "SHA-256 ${res.entity.sha256.take(12)}…",
                    startTimeMs = System.currentTimeMillis(),
                    endTimeMs = System.currentTimeMillis(),
                    durationMs = 1000L,
                    severity = if (res.entity.verdict == "MALICIOUS") SeverityLevel.CRITICAL.name else SeverityLevel.HIGH.name
                )
            )
        }
        return res
    }

    suspend fun scanPickedFile(uri: Uri): MalwareScanUiResult {
        return malwareScanner.scanDocumentUri(uri)
    }

    suspend fun captureSnapshot(reason: String, severity: SeverityLevel, mode: GuardOperatingMode) {
        val snap = reportExporter.capturePointInTimeSnapshot(
            triggerReason = reason,
            severity = severity.name,
            operatingMode = mode.badge,
            recentEvents = dao.getRecentEvents(25),
            recentNetwork = dao.getRecentNetworkSessions(15),
            apps = dao.getAllAppTrust()
        )
        dao.insertSnapshot(snap)
    }

    suspend fun buildExportText(format: ExportFormat): String {
        return reportExporter.buildExportContent(
            format = format,
            events = dao.getRecentEvents(150),
            networkSessions = dao.getRecentNetworkSessions(80),
            snapshots = dao.getRecentSnapshots(40),
            apps = dao.getAllAppTrust()
        )
    }

    suspend fun buildShareIntent(format: ExportFormat): Intent {
        val content = buildExportText(format)
        return reportExporter.writeAndShareReport(format, content)
    }

    suspend fun updateTrustLevel(packageName: String, level: TrustLevel) {
        dao.updateTrustLevel(packageName, level.name)
        dao.updateNetworkBlockByPackage(packageName, level == TrustLevel.BLOCKED)
    }

    suspend fun forceStopApp(packageName: String, isRoot: Boolean): RootActionResult {
        return rootBridge.forceStopPackage(context, packageName, isRoot)
    }

    suspend fun freezeApp(packageName: String, freeze: Boolean, isRoot: Boolean): RootActionResult {
        val res = rootBridge.freezeOrUnfreezePackage(context, packageName, freeze, isRoot)
        if (res.executedViaRoot && res.success) {
            dao.updateFrozenState(packageName, freeze)
        } else if (freeze) {
            dao.updateTrustLevel(packageName, TrustLevel.BLOCKED.name)
        }
        return res
    }

    suspend fun revokeAppPermission(
        packageName: String,
        permissionName: String,
        isRoot: Boolean
    ): RootActionResult {
        return rootBridge.revokePermission(context, packageName, permissionName, isRoot)
    }

    suspend fun toggleNetworkBlock(
        uid: Int,
        packageName: String,
        block: Boolean,
        isRoot: Boolean
    ): RootActionResult {
        val res = rootBridge.setNetworkBlocked(uid, packageName, block, isRoot)
        dao.updateNetworkBlockByPackage(packageName, block)
        if (block) {
            dao.updateTrustLevel(packageName, TrustLevel.BLOCKED.name)
        }
        return res
    }

    suspend fun checkOverlayWatchStatus(): Boolean {
        val granted = Settings.canDrawOverlays(context)
        val now = System.currentTimeMillis()
        val apps = dao.getAllAppTrust()
        val overlayApps = apps.filter { it.riskReasons.contains("Overlay", ignoreCase = true) }
        if (overlayApps.isNotEmpty()) {
            val top = overlayApps.first()
            dao.insertTimelineEvent(
                TimelineEventEntity(
                    category = EventCategory.CLIPBOARD_OVERLAY.name,
                    packageName = top.packageName,
                    appName = top.appName,
                    actionTitle = "Screen Overlay Capability Audit",
                    detail = "${overlayApps.size} installed apps declare SYSTEM_ALERT_WINDOW / Screen Overlay permissions.",
                    permissionOrResource = "OVERLAY / CAPTURE",
                    startTimeMs = now,
                    endTimeMs = now,
                    durationMs = 1000L,
                    severity = SeverityLevel.MEDIUM.name
                )
            )
        }
        return granted
    }

    suspend fun saveGeminiKey(
        rawKey: String,
        requestedModel: String,
        customModelTyped: Boolean
    ): com.example.data.ai.KeyValidationResult? {
        cryptoVault.saveGeminiApiKey(rawKey)
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) return null
        val result = if (customModelTyped && requestedModel.isNotBlank()) {
            geminiClient.detectAndValidateKey(trimmed, requestedModel, customModelTyped = true)
        } else {
            geminiClient.detectBestAvailableModelOnly(trimmed, requestedModel)
        }
        if (result.resolvedModel.isNotBlank()) {
            cryptoVault.updateGeminiModel(result.resolvedModel)
        }
        return result
    }

    suspend fun testGeminiKey(
        rawKey: String,
        model: String,
        customModelTyped: Boolean
    ): com.example.data.ai.KeyValidationResult {
        val effectiveKey = rawKey.trim().ifEmpty { cryptoVault.getGeminiApiKey().orEmpty() }
        val result = geminiClient.detectAndValidateKey(effectiveKey, model, customModelTyped)
        if (result.success && result.resolvedModel.isNotBlank()) {
            cryptoVault.updateGeminiModel(result.resolvedModel)
        }
        return result
    }

    suspend fun updateAiSchedule(frequency: AiScheduleFrequency, eventTriggered: Boolean, model: String) {
        cryptoVault.updateSchedule(frequency, eventTriggered, model)
        AiDigestWorker.schedulePeriodicDigest(context, frequency)
    }

    suspend fun runAiDigestNow(mode: GuardOperatingMode, integrityScore: Int): Pair<Boolean, String> {
        val cfg = cryptoVault.configFlow.first()
        val events = dao.getRecentEvents(30)
        val apps = dao.getAllAppTrust()
        val network = dao.getRecentNetworkSessions(20)
        val snapshots = dao.getRecentSnapshots(10)
        val summary = geminiClient.buildPrivacyFilteredSummary(
            mode = mode,
            integrityScore = integrityScore,
            events = events,
            apps = apps,
            network = network,
            snapshots = snapshots
        )
        val res = geminiClient.generateScheduledDigest("On-Demand Audit", summary, cfg.geminiModel)
        if (res.success) {
            val now = System.currentTimeMillis()
            dao.insertAiInsight(
                AiInsightEntity(
                    createdAtMs = now,
                    scheduleType = "On-Demand Audit",
                    title = "JRS AI Guard • Security Digest",
                    summaryText = res.text,
                    eventsAnalyzed = events.size,
                    quotaSavedNote = "Privacy-filtered compact summary (~${summary.length} chars)"
                )
            )
            cryptoVault.recordDigestRun(now)
        }
        return res.success to res.text
    }

    suspend fun sendAiChatMessage(userText: String, mode: GuardOperatingMode, integrityScore: Int) {
        val now = System.currentTimeMillis()
        dao.insertChatMessage(ChatMessageEntity(timestampMs = now, isUser = true, messageText = userText))

        val cfg = cryptoVault.configFlow.first()
        val summary = geminiClient.buildPrivacyFilteredSummary(
            mode = mode,
            integrityScore = integrityScore,
            events = dao.getRecentEvents(20),
            apps = dao.getAllAppTrust(),
            network = dao.getRecentNetworkSessions(15),
            snapshots = dao.getRecentSnapshots(5)
        )
        val reply = geminiClient.sendRestrictedChatMessage(userText, summary, cfg.geminiModel)
        dao.insertChatMessage(
            ChatMessageEntity(
                timestampMs = System.currentTimeMillis(),
                isUser = false,
                messageText = reply.text,
                isRefusal = reply.isRefusal
            )
        )
    }

    suspend fun clearChatHistory() {
        dao.clearChatMessages()
    }

    suspend fun setWatchdogEnabled(enabled: Boolean, screenOffAlert: Boolean, rootWatch: Boolean) {
        cryptoVault.updateWatchdogSettings(enabled, screenOffAlert, rootWatch)
        if (enabled) {
            WatchdogForegroundService.start(context)
        } else {
            WatchdogForegroundService.stop(context)
        }
    }

    suspend fun checkGitHubRelease(owner: String, repo: String): Result<GitHubReleaseInfo> {
        cryptoVault.updateGitHubRepo(owner, repo)
        return githubUpdater.checkLatestRelease(owner, repo)
    }
}
