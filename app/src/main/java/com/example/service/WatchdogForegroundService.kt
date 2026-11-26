package com.example.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import com.example.data.ai.AiDigestWorker
import com.example.data.export.ReportExporter
import com.example.data.local.EventCategory
import com.example.data.local.JrsGuardDatabase
import com.example.data.local.SeverityLevel
import com.example.data.local.TimelineEventEntity
import com.example.security.CryptoVault
import com.example.security.RootBridge
import com.example.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WatchdogForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var cameraManager: CameraManager
    private lateinit var audioManager: AudioManager
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var powerManager: PowerManager

    private val activeCameraStarts = mutableMapOf<String, Long>()
    private var activeMicStartMs: Long? = null
    private var lastClipChangedMs: Long = 0L

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            val now = System.currentTimeMillis()
            activeCameraStarts[cameraId] = now
            val screenOff = !powerManager.isInteractive
            if (screenOff) {
                logCriticalScreenOffEvent(
                    resource = "Camera #$cameraId",
                    title = "CRITICAL: Camera Activated While Screen Off",
                    detail = "Hardware camera #$cameraId opened at ${TimeFormatter.formatTimestamp(now)} while device display was off.",
                    now = now
                )
            }
        }

        override fun onCameraAvailable(cameraId: String) {
            val start = activeCameraStarts.remove(cameraId) ?: return
            val end = System.currentTimeMillis()
            val duration = (end - start).coerceAtLeast(1000L)
            val screenOff = !powerManager.isInteractive
            scope.launch {
                val dao = JrsGuardDatabase.getInstance(applicationContext).dao()
                dao.insertTimelineEvent(
                    TimelineEventEntity(
                        category = EventCategory.SENSOR_LEASE.name,
                        packageName = "hardware.camera",
                        appName = "Camera Sensor #$cameraId",
                        actionTitle = if (screenOff) "Screen-Off Camera Lease" else "Camera Hardware Lease",
                        detail = "Active from ${TimeFormatter.formatTimestamp(start)} to ${TimeFormatter.formatTimestamp(end)}",
                        permissionOrResource = "CAMERA",
                        startTimeMs = start,
                        endTimeMs = end,
                        durationMs = duration,
                        severity = if (screenOff) SeverityLevel.CRITICAL.name else SeverityLevel.MEDIUM.name,
                        isScreenOff = screenOff
                    )
                )
            }
        }
    }

    private val audioCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            val now = System.currentTimeMillis()
            if (configs.isNotEmpty() && activeMicStartMs == null) {
                activeMicStartMs = now
                val screenOff = !powerManager.isInteractive
                if (screenOff) {
                    logCriticalScreenOffEvent(
                        resource = "Microphone",
                        title = "CRITICAL: Microphone Recording While Screen Off",
                        detail = "Active audio capture session detected at ${TimeFormatter.formatTimestamp(now)} with display locked/off.",
                        now = now
                    )
                }
            } else if (configs.isEmpty() && activeMicStartMs != null) {
                val start = activeMicStartMs ?: return
                activeMicStartMs = null
                val duration = (now - start).coerceAtLeast(1000L)
                val screenOff = !powerManager.isInteractive
                scope.launch {
                    val dao = JrsGuardDatabase.getInstance(applicationContext).dao()
                    dao.insertTimelineEvent(
                        TimelineEventEntity(
                            category = EventCategory.SENSOR_LEASE.name,
                            packageName = "hardware.microphone",
                            appName = "Microphone Audio Capture",
                            actionTitle = "Microphone Hardware Lease",
                            detail = "Recorded from ${TimeFormatter.formatTimestamp(start)} to ${TimeFormatter.formatTimestamp(now)}",
                            permissionOrResource = "MICROPHONE",
                            startTimeMs = start,
                            endTimeMs = now,
                            durationMs = duration,
                            severity = if (screenOff) SeverityLevel.CRITICAL.name else SeverityLevel.MEDIUM.name,
                            isScreenOff = screenOff
                        )
                    )
                }
            }
        }
    }

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val now = System.currentTimeMillis()
        val delta = if (lastClipChangedMs > 0L) (now - lastClipChangedMs).coerceAtMost(30_000L) else 1500L
        lastClipChangedMs = now
        val canDrawOverlays = Settings.canDrawOverlays(applicationContext)
        scope.launch {
            val dao = JrsGuardDatabase.getInstance(applicationContext).dao()
            dao.insertTimelineEvent(
                TimelineEventEntity(
                    category = EventCategory.CLIPBOARD_OVERLAY.name,
                    packageName = "system.clipboard",
                    appName = "Clipboard Guard",
                    actionTitle = "Clipboard Buffer Accessed / Updated",
                    detail = "Clipboard activity logged at ${TimeFormatter.formatTimestamp(now)} (Content redacted for privacy). Overlay status: ${if (canDrawOverlays) "Overlay Ready" else "Protected"}.",
                    permissionOrResource = "CLIPBOARD",
                    startTimeMs = now,
                    endTimeMs = now + 1000L,
                    durationMs = delta.coerceAtLeast(1000L),
                    severity = SeverityLevel.MEDIUM.name
                )
            )
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                val now = System.currentTimeMillis()
                if (activeCameraStarts.isNotEmpty() || activeMicStartMs != null) {
                    logCriticalScreenOffEvent(
                        resource = if (activeCameraStarts.isNotEmpty()) "Camera" else "Microphone",
                        title = "CRITICAL: Sensor Held Active After Screen Off",
                        detail = "Active sensor lease persisted when display turned off at ${TimeFormatter.formatTimestamp(now)}.",
                        now = now
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        val notification = NotificationHelper.buildWatchdogNotification(
            this,
            "Monitoring camera, mic, clipboard, and package events"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.WATCHDOG_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.WATCHDOG_NOTIFICATION_ID, notification)
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        runCatching { cameraManager.registerAvailabilityCallback(cameraCallback, mainHandler) }
        runCatching { audioManager.registerAudioRecordingCallback(audioCallback, mainHandler) }
        runCatching { clipboardManager.addPrimaryClipChangedListener(clipboardListener) }
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { cameraManager.unregisterAvailabilityCallback(cameraCallback) }
        runCatching { audioManager.unregisterAudioRecordingCallback(audioCallback) }
        runCatching { clipboardManager.removePrimaryClipChangedListener(clipboardListener) }
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun logCriticalScreenOffEvent(
        resource: String,
        title: String,
        detail: String,
        now: Long
    ) {
        NotificationHelper.postCriticalAlert(applicationContext, title, detail, (now % 10000).toInt() + 3000)
        scope.launch {
            val db = JrsGuardDatabase.getInstance(applicationContext)
            val dao = db.dao()
            val vault = CryptoVault(applicationContext)
            val exporter = ReportExporter(applicationContext, vault)
            val rootStatus = RootBridge().inspectEnvironment()

            dao.insertTimelineEvent(
                TimelineEventEntity(
                    category = EventCategory.SENSOR_LEASE.name,
                    packageName = "hardware.${resource.lowercase()}",
                    appName = "$resource Screen-Off Alert",
                    actionTitle = title,
                    detail = detail,
                    permissionOrResource = resource.uppercase(),
                    startTimeMs = now,
                    endTimeMs = now,
                    durationMs = 1000L,
                    severity = SeverityLevel.CRITICAL.name,
                    isScreenOff = true
                )
            )

            val snapshot = exporter.capturePointInTimeSnapshot(
                triggerReason = title,
                severity = SeverityLevel.CRITICAL.name,
                operatingMode = rootStatus.mode.badge,
                recentEvents = dao.getRecentEvents(15),
                recentNetwork = dao.getRecentNetworkSessions(10),
                apps = dao.getAllAppTrust()
            )
            dao.insertSnapshot(snapshot)

            val config = vault.configFlow.first()
            if (config.eventTriggeredEnabled && config.hasGeminiKey) {
                AiDigestWorker.triggerImmediateDigest(applicationContext, "Event-Triggered Alert: $resource")
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, WatchdogForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchdogForegroundService::class.java))
        }
    }
}
