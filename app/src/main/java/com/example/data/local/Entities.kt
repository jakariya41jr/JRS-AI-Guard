package com.example.data.local

enum class EventCategory(val displayName: String) {
    PERMISSION("Permissions"),
    BACKGROUND_RESOURCE("Background & CPU"),
    ROOT_WATCH("Root Watch"),
    GALLERY_DATA("Gallery & Data"),
    SENSOR_LEASE("Sensor Leases"),
    CLIPBOARD_OVERLAY("Clipboard & Screen"),
    INSTALL_AUDIT("Install & Updates")
}

enum class SeverityLevel(val label: String, val weight: Int) {
    INFO("INFO", 1),
    MEDIUM("ELEVATED", 2),
    HIGH("HIGH", 3),
    CRITICAL("CRITICAL", 4)
}

enum class TrustLevel(val label: String) {
    TRUSTED("Trusted"),
    WATCHLIST("Watchlist"),
    BLOCKED("Blocked")
}

data class TimelineEventEntity(
    val id: Long = 0,
    val category: String,
    val packageName: String,
    val appName: String,
    val actionTitle: String,
    val detail: String,
    val permissionOrResource: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val severity: String = SeverityLevel.INFO.name,
    val isScreenOff: Boolean = false,
    val cpuPercent: Float = 0f,
    val ramMb: Float = 0f,
    val batteryPercentDrain: Float = 0f,
    val dataBytes: Long = 0L,
    val encryptedExtra: String = ""
)

data class NetworkSessionEntity(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val uid: Int,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val endpoint: String,
    val protocol: String,
    val isSpike: Boolean = false,
    val isBlocked: Boolean = false
)

data class AppTrustEntity(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val trustLevel: String = TrustLevel.TRUSTED.name,
    val riskScore: Int = 0,
    val riskReasons: String = "",
    val isSideloaded: Boolean = false,
    val installerSource: String = "Unknown",
    val firstInstallTimeMs: Long = 0L,
    val lastUpdateTimeMs: Long = 0L,
    val permissionSnapshot: String = "",
    val serviceSnapshot: String = "",
    val newlyAddedPermissions: String = "",
    val newlyAddedServices: String = "",
    val isFrozen: Boolean = false,
    val isSystemApp: Boolean = false
)

data class ScanCacheEntity(
    val sha256: String,
    val targetName: String,
    val packageName: String = "",
    val fileSizeBytes: Long = 0L,
    val verdict: String, // CLEAN, SUSPICIOUS, MALICIOUS, UNKNOWN
    val threatFamily: String = "",
    val detectionRatio: String = "0/1",
    val sourceApi: String = "Local Static + MalwareBazaar",
    val heuristicScore: Int = 0,
    val heuristicFlags: String = "",
    val scannedAtMs: Long,
    val expiresAtMs: Long
)

data class SecuritySnapshotEntity(
    val id: Long = 0,
    val capturedAtMs: Long,
    val triggerReason: String,
    val severity: String,
    val operatingMode: String,
    val activeProcessesSummary: String,
    val permissionsSummary: String,
    val networkSummary: String,
    val resourceSummary: String,
    val encryptedPayload: String
)

data class AiInsightEntity(
    val id: Long = 0,
    val createdAtMs: Long,
    val scheduleType: String,
    val title: String,
    val summaryText: String,
    val eventsAnalyzed: Int,
    val quotaSavedNote: String
)

data class ChatMessageEntity(
    val id: Long = 0,
    val timestampMs: Long,
    val isUser: Boolean,
    val messageText: String,
    val isRefusal: Boolean = false
)
