package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GuardRepository
import com.example.data.export.ExportFormat
import com.example.data.github.GitHubReleaseInfo
import com.example.data.local.AiInsightEntity
import com.example.data.local.AppTrustEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.EventCategory
import com.example.data.local.NetworkSessionEntity
import com.example.data.local.ScanCacheEntity
import com.example.data.local.SecuritySnapshotEntity
import com.example.data.local.SeverityLevel
import com.example.data.local.TimelineEventEntity
import com.example.data.local.TrustLevel
import com.example.data.telemetry.IntegrityAuditReport
import com.example.data.telemetry.MalwareScanUiResult
import com.example.security.AiScheduleFrequency
import com.example.security.GuardOperatingMode
import com.example.security.VaultConfig
import com.example.service.WatchdogForegroundService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QuickActionTarget(
    val packageName: String,
    val appName: String,
    val uid: Int = -1,
    val currentTrust: TrustLevel = TrustLevel.WATCHLIST,
    val permissions: List<String> = emptyList()
)

sealed interface UiToastEvent {
    data class Message(val text: String) : UiToastEvent
    data class ShareIntent(val intent: Intent) : UiToastEvent
    data class ExportReady(val format: ExportFormat, val content: String) : UiToastEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = GuardRepository(application.applicationContext)

    val vaultConfig: StateFlow<VaultConfig> = repo.vaultConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultConfig())

    val timelineEvents: StateFlow<List<TimelineEventEntity>> = repo.timelineEventsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val networkSessions: StateFlow<List<NetworkSessionEntity>> = repo.networkSessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appTrustList: StateFlow<List<AppTrustEntity>> = repo.appTrustListFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scanHistory: StateFlow<List<ScanCacheEntity>> = repo.scanHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val snapshots: StateFlow<List<SecuritySnapshotEntity>> = repo.snapshotsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiInsights: StateFlow<List<AiInsightEntity>> = repo.aiInsightsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatMessages: StateFlow<List<ChatMessageEntity>> = repo.chatMessagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _integrityReport = MutableStateFlow<IntegrityAuditReport?>(null)
    val integrityReport: StateFlow<IntegrityAuditReport?> = _integrityReport.asStateFlow()

    private val _isScanningTelemetry = MutableStateFlow(false)
    val isScanningTelemetry: StateFlow<Boolean> = _isScanningTelemetry.asStateFlow()

    private val _isMalwareScanning = MutableStateFlow(false)
    val isMalwareScanning: StateFlow<Boolean> = _isMalwareScanning.asStateFlow()

    private val _lastMalwareScan = MutableStateFlow<MalwareScanUiResult?>(null)
    val lastMalwareScan: StateFlow<MalwareScanUiResult?> = _lastMalwareScan.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<EventCategory?>(null)
    val selectedCategoryFilter: StateFlow<EventCategory?> = _selectedCategoryFilter.asStateFlow()

    private val _quickActionTarget = MutableStateFlow<QuickActionTarget?>(null)
    val quickActionTarget: StateFlow<QuickActionTarget?> = _quickActionTarget.asStateFlow()

    private val _isAiBusy = MutableStateFlow(false)
    val isAiBusy: StateFlow<Boolean> = _isAiBusy.asStateFlow()

    private val _githubRelease = MutableStateFlow<GitHubReleaseInfo?>(null)
    val githubRelease: StateFlow<GitHubReleaseInfo?> = _githubRelease.asStateFlow()

    private val _isCheckingRelease = MutableStateFlow(false)
    val isCheckingRelease: StateFlow<Boolean> = _isCheckingRelease.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiToastEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        refreshAll()
        viewModelScope.launch {
            val cfg = repo.vaultConfigFlow.stateIn(viewModelScope).value
            if (cfg.watchdogEnabled) {
                WatchdogForegroundService.start(getApplication())
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isScanningTelemetry.value = true
            runCatching {
                val report = repo.refreshTelemetryAndIntegrity()
                _integrityReport.value = report
                repo.checkOverlayWatchStatus()
            }.onFailure { err ->
                _uiEvents.emit(UiToastEvent.Message("Telemetry refresh notice: ${err.message ?: "Partial read"}"))
            }
            _isScanningTelemetry.value = false
        }
    }

    fun selectCategoryFilter(category: EventCategory?) {
        _selectedCategoryFilter.value = category
    }

    fun openQuickActionsForPackage(packageName: String, fallbackName: String) {
        viewModelScope.launch {
            val existing = appTrustList.value.firstOrNull { it.packageName == packageName }
            if (existing != null) {
                _quickActionTarget.value = QuickActionTarget(
                    packageName = existing.packageName,
                    appName = existing.appName,
                    uid = existing.uid,
                    currentTrust = runCatching { TrustLevel.valueOf(existing.trustLevel) }
                        .getOrDefault(TrustLevel.WATCHLIST),
                    permissions = existing.permissionSnapshot.split(",").filter { it.isNotBlank() }
                )
            } else {
                _quickActionTarget.value = QuickActionTarget(
                    packageName = packageName,
                    appName = fallbackName,
                    uid = -1,
                    currentTrust = TrustLevel.WATCHLIST,
                    permissions = emptyList()
                )
            }
        }
    }

    fun dismissQuickActions() {
        _quickActionTarget.value = null
    }

    fun performForceStop(packageName: String) {
        viewModelScope.launch {
            val isRoot = _integrityReport.value?.rootStatus?.mode == GuardOperatingMode.ROOTED_ACTIVE
            val res = repo.forceStopApp(packageName, isRoot)
            _uiEvents.emit(UiToastEvent.Message(res.message))
        }
    }

    fun performFreeze(packageName: String, freeze: Boolean) {
        viewModelScope.launch {
            val isRoot = _integrityReport.value?.rootStatus?.mode == GuardOperatingMode.ROOTED_ACTIVE
            val res = repo.freezeApp(packageName, freeze, isRoot)
            _uiEvents.emit(UiToastEvent.Message(res.message))
        }
    }

    fun performRevokePermission(packageName: String, shortPerm: String) {
        viewModelScope.launch {
            val fullPerm = if (shortPerm.contains(".")) shortPerm else "android.permission.$shortPerm"
            val isRoot = _integrityReport.value?.rootStatus?.mode == GuardOperatingMode.ROOTED_ACTIVE
            val res = repo.revokeAppPermission(packageName, fullPerm, isRoot)
            _uiEvents.emit(UiToastEvent.Message(res.message))
        }
    }

    fun updateAppTrust(packageName: String, level: TrustLevel) {
        viewModelScope.launch {
            repo.updateTrustLevel(packageName, level)
            _quickActionTarget.value = _quickActionTarget.value?.let {
                if (it.packageName == packageName) it.copy(currentTrust = level) else it
            }
            _uiEvents.emit(UiToastEvent.Message("$packageName moved to ${level.label}"))
        }
    }

    fun toggleNetworkBlock(uid: Int, packageName: String, block: Boolean) {
        viewModelScope.launch {
            val isRoot = _integrityReport.value?.rootStatus?.mode == GuardOperatingMode.ROOTED_ACTIVE
            val res = repo.toggleNetworkBlock(uid, packageName, block, isRoot)
            _uiEvents.emit(UiToastEvent.Message(res.message))
        }
    }

    fun scanInstalledAppApk(packageName: String, appName: String) {
        viewModelScope.launch {
            _isMalwareScanning.value = true
            runCatching {
                val res = repo.scanInstalledApp(packageName, appName)
                _lastMalwareScan.value = res
                val cacheTag = if (res.fromCache) " (SHA-256 Cache Hit — Quota Saved)" else ""
                _uiEvents.emit(UiToastEvent.Message("Scan complete: ${res.entity.verdict}$cacheTag"))
            }.onFailure {
                _uiEvents.emit(UiToastEvent.Message("Scan failed: ${it.message ?: "Unable to read APK"}"))
            }
            _isMalwareScanning.value = false
        }
    }

    fun scanPickedFileUri(uri: Uri) {
        viewModelScope.launch {
            _isMalwareScanning.value = true
            runCatching {
                val res = repo.scanPickedFile(uri)
                _lastMalwareScan.value = res
                val cacheTag = if (res.fromCache) " (SHA-256 Cache Hit — Quota Saved)" else ""
                _uiEvents.emit(UiToastEvent.Message("File scan: ${res.entity.verdict}$cacheTag"))
            }.onFailure {
                _uiEvents.emit(UiToastEvent.Message("File scan error: ${it.message ?: "Read failed"}"))
            }
            _isMalwareScanning.value = false
        }
    }

    fun captureSnapshotNow(reason: String = "Manual Security Snapshot") {
        viewModelScope.launch {
            val mode = _integrityReport.value?.rootStatus?.mode ?: GuardOperatingMode.NON_ROOT_SAFETY_NET
            repo.captureSnapshot(reason, SeverityLevel.MEDIUM, mode)
            _uiEvents.emit(UiToastEvent.Message("Encrypted point-in-time snapshot saved"))
        }
    }

    fun shareExportReport(format: ExportFormat) {
        viewModelScope.launch {
            runCatching {
                val intent = repo.buildShareIntent(format)
                _uiEvents.emit(UiToastEvent.ShareIntent(intent))
            }.onFailure {
                _uiEvents.emit(UiToastEvent.Message("Export failed: ${it.message}"))
            }
        }
    }

    fun prepareExportFile(format: ExportFormat) {
        viewModelScope.launch {
            val content = repo.buildExportText(format)
            _uiEvents.emit(UiToastEvent.ExportReady(format, content))
        }
    }

    fun saveGeminiKey(key: String) {
        viewModelScope.launch {
            repo.saveGeminiKey(key)
            _uiEvents.emit(UiToastEvent.Message(
                if (key.isBlank()) "Gemini API key cleared from encrypted vault"
                else "Gemini API key encrypted & saved in Android Keystore vault"
            ))
        }
    }

    fun testGeminiKey(rawKey: String, model: String) {
        viewModelScope.launch {
            _isAiBusy.value = true
            val (ok, msg) = repo.testGeminiKey(rawKey, model)
            _isAiBusy.value = false
            _uiEvents.emit(UiToastEvent.Message(if (ok) "Connected: $msg" else msg))
        }
    }

    fun updateAiSchedule(freq: AiScheduleFrequency, eventTriggered: Boolean, model: String) {
        viewModelScope.launch {
            repo.updateAiSchedule(freq, eventTriggered, model)
            _uiEvents.emit(UiToastEvent.Message("AI Schedule updated: ${freq.label}"))
        }
    }

    fun runAiDigestNow() {
        viewModelScope.launch {
            _isAiBusy.value = true
            val mode = _integrityReport.value?.rootStatus?.mode ?: GuardOperatingMode.NON_ROOT_SAFETY_NET
            val score = _integrityReport.value?.overallScore ?: 80
            val (ok, msg) = repo.runAiDigestNow(mode, score)
            _isAiBusy.value = false
            _uiEvents.emit(UiToastEvent.Message(if (ok) "AI Security Digest generated" else msg))
        }
    }

    fun sendChatMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _isAiBusy.value = true
            val mode = _integrityReport.value?.rootStatus?.mode ?: GuardOperatingMode.NON_ROOT_SAFETY_NET
            val score = _integrityReport.value?.overallScore ?: 80
            repo.sendAiChatMessage(trimmed, mode, score)
            _isAiBusy.value = false
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repo.clearChatHistory()
        }
    }

    fun toggleWatchdog(enabled: Boolean, screenOffAlert: Boolean, rootWatch: Boolean) {
        viewModelScope.launch {
            repo.setWatchdogEnabled(enabled, screenOffAlert, rootWatch)
            _uiEvents.emit(
                UiToastEvent.Message(
                    if (enabled) "Watchdog background monitor activated"
                    else "Watchdog background monitor paused"
                )
            )
        }
    }

    fun checkGitHubRelease(owner: String, repoName: String) {
        viewModelScope.launch {
            _isCheckingRelease.value = true
            val result = repo.checkGitHubRelease(owner, repoName)
            _isCheckingRelease.value = false
            result.onSuccess {
                _githubRelease.value = it
                val msg = if (it.isUpdateAvailable) {
                    "New release ${it.tagName} available on GitHub!"
                } else {
                    "Up to date (${it.tagName})"
                }
                _uiEvents.emit(UiToastEvent.Message(msg))
            }.onFailure {
                _uiEvents.emit(UiToastEvent.Message(it.message ?: "Unable to reach GitHub Releases"))
            }
        }
    }

    fun downloadReleaseApk(info: GitHubReleaseInfo) {
        val url = info.apkDownloadUrl
        val name = info.apkAssetName ?: "JRS-AI-Guard-${info.tagName}.apk"
        viewModelScope.launch {
            if (url != null && repo.githubUpdater.enqueueApkDownload(url, name)) {
                _uiEvents.emit(UiToastEvent.Message("Downloading $name via Android DownloadManager"))
            } else {
                repo.githubUpdater.openReleasePage(info.htmlUrl)
            }
        }
    }

    fun openUsageAccessSettings() {
        val ctx = getApplication<Application>()
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(intent) }
    }

    fun openOfficialWebsite() {
        val ctx = getApplication<Application>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://Jakariya41jr.xyz")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(intent) }
    }
}
