package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class JrsGuardDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val tableInvalidations = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val daoImpl: JrsGuardDao by lazy { SqliteJrsGuardDao() }

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TIMELINE_EVENTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL,
                packageName TEXT NOT NULL,
                appName TEXT NOT NULL,
                actionTitle TEXT NOT NULL,
                detail TEXT NOT NULL,
                permissionOrResource TEXT NOT NULL,
                startTimeMs INTEGER NOT NULL,
                endTimeMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                severity TEXT NOT NULL,
                isScreenOff INTEGER NOT NULL,
                cpuPercent REAL NOT NULL,
                ramMb REAL NOT NULL,
                batteryPercentDrain REAL NOT NULL,
                dataBytes INTEGER NOT NULL,
                encryptedExtra TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_timeline_start ON $TABLE_TIMELINE_EVENTS(startTimeMs DESC)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NETWORK_SESSIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                packageName TEXT NOT NULL,
                appName TEXT NOT NULL,
                uid INTEGER NOT NULL,
                uploadBytes INTEGER NOT NULL,
                downloadBytes INTEGER NOT NULL,
                startTimeMs INTEGER NOT NULL,
                endTimeMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                endpoint TEXT NOT NULL,
                protocol TEXT NOT NULL,
                isSpike INTEGER NOT NULL,
                isBlocked INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_network_end ON $TABLE_NETWORK_SESSIONS(endTimeMs DESC)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_APP_TRUST (
                packageName TEXT PRIMARY KEY NOT NULL,
                appName TEXT NOT NULL,
                uid INTEGER NOT NULL,
                trustLevel TEXT NOT NULL,
                riskScore INTEGER NOT NULL,
                riskReasons TEXT NOT NULL,
                isSideloaded INTEGER NOT NULL,
                installerSource TEXT NOT NULL,
                firstInstallTimeMs INTEGER NOT NULL,
                lastUpdateTimeMs INTEGER NOT NULL,
                permissionSnapshot TEXT NOT NULL,
                serviceSnapshot TEXT NOT NULL,
                newlyAddedPermissions TEXT NOT NULL,
                newlyAddedServices TEXT NOT NULL,
                isFrozen INTEGER NOT NULL,
                isSystemApp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SCAN_CACHE (
                sha256 TEXT PRIMARY KEY NOT NULL,
                targetName TEXT NOT NULL,
                packageName TEXT NOT NULL,
                fileSizeBytes INTEGER NOT NULL,
                verdict TEXT NOT NULL,
                threatFamily TEXT NOT NULL,
                detectionRatio TEXT NOT NULL,
                sourceApi TEXT NOT NULL,
                heuristicScore INTEGER NOT NULL,
                heuristicFlags TEXT NOT NULL,
                scannedAtMs INTEGER NOT NULL,
                expiresAtMs INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SECURITY_SNAPSHOTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                capturedAtMs INTEGER NOT NULL,
                triggerReason TEXT NOT NULL,
                severity TEXT NOT NULL,
                operatingMode TEXT NOT NULL,
                activeProcessesSummary TEXT NOT NULL,
                permissionsSummary TEXT NOT NULL,
                networkSummary TEXT NOT NULL,
                resourceSummary TEXT NOT NULL,
                encryptedPayload TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_AI_INSIGHTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                createdAtMs INTEGER NOT NULL,
                scheduleType TEXT NOT NULL,
                title TEXT NOT NULL,
                summaryText TEXT NOT NULL,
                eventsAnalyzed INTEGER NOT NULL,
                quotaSavedNote TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CHAT_MESSAGES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestampMs INTEGER NOT NULL,
                isUser INTEGER NOT NULL,
                messageText TEXT NOT NULL,
                isRefusal INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    fun dao(): JrsGuardDao = daoImpl

    private fun notifyTableChanged(tableName: String) {
        tableInvalidations.tryEmit(tableName)
    }

    private fun <T> observeTable(tableName: String, query: () -> List<T>): Flow<List<T>> =
        flow {
            emit(query())
            tableInvalidations
                .filter { it == tableName }
                .collect { emit(query()) }
        }.flowOn(Dispatchers.IO)

    private inner class SqliteJrsGuardDao : JrsGuardDao {

        override fun observeTimelineEvents(): Flow<List<TimelineEventEntity>> =
            observeTable(TABLE_TIMELINE_EVENTS) { queryRecentEvents(300) }

        override suspend fun getRecentEvents(limit: Int): List<TimelineEventEntity> =
            withContext(Dispatchers.IO) { queryRecentEvents(limit) }

        override suspend fun insertTimelineEvent(event: TimelineEventEntity): Long =
            withContext(Dispatchers.IO) {
                val id = writableDatabase.insertWithOnConflict(
                    TABLE_TIMELINE_EVENTS,
                    null,
                    event.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                notifyTableChanged(TABLE_TIMELINE_EVENTS)
                id
            }

        override suspend fun insertTimelineEvents(events: List<TimelineEventEntity>) {
            if (events.isEmpty()) return
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                db.beginTransaction()
                try {
                    for (event in events) {
                        db.insertWithOnConflict(
                            TABLE_TIMELINE_EVENTS,
                            null,
                            event.toContentValues(),
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                notifyTableChanged(TABLE_TIMELINE_EVENTS)
            }
        }

        override suspend fun pruneOldTimelineEvents(olderThanMs: Long) {
            withContext(Dispatchers.IO) {
                writableDatabase.delete(
                    TABLE_TIMELINE_EVENTS,
                    "startTimeMs < ?",
                    arrayOf(olderThanMs.toString())
                )
                notifyTableChanged(TABLE_TIMELINE_EVENTS)
            }
        }

        override fun observeNetworkSessions(): Flow<List<NetworkSessionEntity>> =
            observeTable(TABLE_NETWORK_SESSIONS) { queryRecentNetworkSessions(200) }

        override suspend fun getRecentNetworkSessions(limit: Int): List<NetworkSessionEntity> =
            withContext(Dispatchers.IO) { queryRecentNetworkSessions(limit) }

        override suspend fun insertNetworkSessions(sessions: List<NetworkSessionEntity>) {
            if (sessions.isEmpty()) return
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                db.beginTransaction()
                try {
                    for (session in sessions) {
                        db.insertWithOnConflict(
                            TABLE_NETWORK_SESSIONS,
                            null,
                            session.toContentValues(),
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                notifyTableChanged(TABLE_NETWORK_SESSIONS)
            }
        }

        override suspend fun updateNetworkBlockByPackage(packageName: String, blocked: Boolean) {
            withContext(Dispatchers.IO) {
                val values = ContentValues().apply {
                    put("isBlocked", if (blocked) 1 else 0)
                }
                writableDatabase.update(
                    TABLE_NETWORK_SESSIONS,
                    values,
                    "packageName = ?",
                    arrayOf(packageName)
                )
                notifyTableChanged(TABLE_NETWORK_SESSIONS)
            }
        }

        override fun observeAppTrustList(): Flow<List<AppTrustEntity>> =
            observeTable(TABLE_APP_TRUST) {
                queryAppTrust("SELECT * FROM $TABLE_APP_TRUST ORDER BY riskScore DESC, appName ASC", emptyArray())
            }

        override suspend fun getAllAppTrust(): List<AppTrustEntity> =
            withContext(Dispatchers.IO) {
                queryAppTrust("SELECT * FROM $TABLE_APP_TRUST", emptyArray())
            }

        override suspend fun getAppTrust(packageName: String): AppTrustEntity? =
            withContext(Dispatchers.IO) {
                queryAppTrust(
                    "SELECT * FROM $TABLE_APP_TRUST WHERE packageName = ? LIMIT 1",
                    arrayOf(packageName)
                ).firstOrNull()
            }

        override suspend fun upsertAppTrust(app: AppTrustEntity) {
            withContext(Dispatchers.IO) {
                writableDatabase.insertWithOnConflict(
                    TABLE_APP_TRUST,
                    null,
                    app.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                notifyTableChanged(TABLE_APP_TRUST)
            }
        }

        override suspend fun upsertAppTrustList(apps: List<AppTrustEntity>) {
            if (apps.isEmpty()) return
            withContext(Dispatchers.IO) {
                val db = writableDatabase
                db.beginTransaction()
                try {
                    for (app in apps) {
                        db.insertWithOnConflict(
                            TABLE_APP_TRUST,
                            null,
                            app.toContentValues(),
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                notifyTableChanged(TABLE_APP_TRUST)
            }
        }

        override suspend fun updateTrustLevel(packageName: String, trustLevel: String) {
            withContext(Dispatchers.IO) {
                val values = ContentValues().apply {
                    put("trustLevel", trustLevel)
                }
                writableDatabase.update(
                    TABLE_APP_TRUST,
                    values,
                    "packageName = ?",
                    arrayOf(packageName)
                )
                notifyTableChanged(TABLE_APP_TRUST)
            }
        }

        override suspend fun updateFrozenState(packageName: String, frozen: Boolean) {
            withContext(Dispatchers.IO) {
                val values = ContentValues().apply {
                    put("isFrozen", if (frozen) 1 else 0)
                }
                writableDatabase.update(
                    TABLE_APP_TRUST,
                    values,
                    "packageName = ?",
                    arrayOf(packageName)
                )
                notifyTableChanged(TABLE_APP_TRUST)
            }
        }

        override fun observeScanHistory(): Flow<List<ScanCacheEntity>> =
            observeTable(TABLE_SCAN_CACHE) {
                queryScanCache("SELECT * FROM $TABLE_SCAN_CACHE ORDER BY scannedAtMs DESC", emptyArray())
            }

        override suspend fun getCachedScan(sha256: String): ScanCacheEntity? =
            withContext(Dispatchers.IO) {
                queryScanCache(
                    "SELECT * FROM $TABLE_SCAN_CACHE WHERE sha256 = ? LIMIT 1",
                    arrayOf(sha256)
                ).firstOrNull()
            }

        override suspend fun upsertScanResult(scan: ScanCacheEntity) {
            withContext(Dispatchers.IO) {
                writableDatabase.insertWithOnConflict(
                    TABLE_SCAN_CACHE,
                    null,
                    scan.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                notifyTableChanged(TABLE_SCAN_CACHE)
            }
        }

        override fun observeSnapshots(): Flow<List<SecuritySnapshotEntity>> =
            observeTable(TABLE_SECURITY_SNAPSHOTS) { querySnapshots(null) }

        override suspend fun getRecentSnapshots(limit: Int): List<SecuritySnapshotEntity> =
            withContext(Dispatchers.IO) { querySnapshots(limit) }

        override suspend fun insertSnapshot(snapshot: SecuritySnapshotEntity): Long =
            withContext(Dispatchers.IO) {
                val id = writableDatabase.insertWithOnConflict(
                    TABLE_SECURITY_SNAPSHOTS,
                    null,
                    snapshot.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                notifyTableChanged(TABLE_SECURITY_SNAPSHOTS)
                id
            }

        override fun observeAiInsights(): Flow<List<AiInsightEntity>> =
            observeTable(TABLE_AI_INSIGHTS) { queryAiInsights() }

        override suspend fun insertAiInsight(insight: AiInsightEntity): Long =
            withContext(Dispatchers.IO) {
                val id = writableDatabase.insertWithOnConflict(
                    TABLE_AI_INSIGHTS,
                    null,
                    insight.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                notifyTableChanged(TABLE_AI_INSIGHTS)
                id
            }

        override fun observeChatMessages(): Flow<List<ChatMessageEntity>> =
            observeTable(TABLE_CHAT_MESSAGES) { queryChatMessages() }

        override suspend fun insertChatMessage(message: ChatMessageEntity): Long =
            withContext(Dispatchers.IO) {
                val id = writableDatabase.insertWithOnConflict(
                    TABLE_CHAT_MESSAGES,
                    null,
                    message.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                notifyTableChanged(TABLE_CHAT_MESSAGES)
                id
            }

        override suspend fun clearChatMessages() {
            withContext(Dispatchers.IO) {
                writableDatabase.delete(TABLE_CHAT_MESSAGES, null, null)
                notifyTableChanged(TABLE_CHAT_MESSAGES)
            }
        }

        private fun queryRecentEvents(limit: Int): List<TimelineEventEntity> {
            val result = mutableListOf<TimelineEventEntity>()
            readableDatabase.rawQuery(
                "SELECT * FROM $TABLE_TIMELINE_EVENTS ORDER BY startTimeMs DESC LIMIT ?",
                arrayOf(limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(cursor.toTimelineEvent())
                }
            }
            return result
        }

        private fun queryRecentNetworkSessions(limit: Int): List<NetworkSessionEntity> {
            val result = mutableListOf<NetworkSessionEntity>()
            readableDatabase.rawQuery(
                "SELECT * FROM $TABLE_NETWORK_SESSIONS ORDER BY endTimeMs DESC LIMIT ?",
                arrayOf(limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(cursor.toNetworkSession())
                }
            }
            return result
        }

        private fun queryAppTrust(sql: String, args: Array<String>): List<AppTrustEntity> {
            val result = mutableListOf<AppTrustEntity>()
            readableDatabase.rawQuery(sql, args).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(cursor.toAppTrust())
                }
            }
            return result
        }

        private fun queryScanCache(sql: String, args: Array<String>): List<ScanCacheEntity> {
            val result = mutableListOf<ScanCacheEntity>()
            readableDatabase.rawQuery(sql, args).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(cursor.toScanCache())
                }
            }
            return result
        }

        private fun querySnapshots(limit: Int?): List<SecuritySnapshotEntity> {
            val result = mutableListOf<SecuritySnapshotEntity>()
            val sql = if (limit != null) {
                "SELECT * FROM $TABLE_SECURITY_SNAPSHOTS ORDER BY capturedAtMs DESC LIMIT ?"
            } else {
                "SELECT * FROM $TABLE_SECURITY_SNAPSHOTS ORDER BY capturedAtMs DESC"
            }
            val args = if (limit != null) arrayOf(limit.toString()) else emptyArray()
            readableDatabase.rawQuery(sql, args).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(cursor.toSecuritySnapshot())
                }
            }
            return result
        }

        private fun queryAiInsights(): List<AiInsightEntity> {
            val result = mutableListOf<AiInsightEntity>()
            readableDatabase.rawQuery(
                "SELECT * FROM $TABLE_AI_INSIGHTS ORDER BY createdAtMs DESC",
                emptyArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(cursor.toAiInsight())
                }
            }
            return result
        }

        private fun queryChatMessages(): List<ChatMessageEntity> {
            val result = mutableListOf<ChatMessageEntity>()
            readableDatabase.rawQuery(
                "SELECT * FROM $TABLE_CHAT_MESSAGES ORDER BY timestampMs ASC",
                emptyArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result.add(cursor.toChatMessage())
                }
            }
            return result
        }
    }

    companion object {
        private const val DATABASE_NAME = "jrs_ai_guard_db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_TIMELINE_EVENTS = "timeline_events"
        private const val TABLE_NETWORK_SESSIONS = "network_sessions"
        private const val TABLE_APP_TRUST = "app_trust"
        private const val TABLE_SCAN_CACHE = "scan_cache"
        private const val TABLE_SECURITY_SNAPSHOTS = "security_snapshots"
        private const val TABLE_AI_INSIGHTS = "ai_insights"
        private const val TABLE_CHAT_MESSAGES = "chat_messages"

        @Volatile
        private var INSTANCE: JrsGuardDatabase? = null

        fun getInstance(context: Context): JrsGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JrsGuardDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

private fun TimelineEventEntity.toContentValues(): ContentValues = ContentValues().apply {
    if (id != 0L) put("id", id)
    put("category", category)
    put("packageName", packageName)
    put("appName", appName)
    put("actionTitle", actionTitle)
    put("detail", detail)
    put("permissionOrResource", permissionOrResource)
    put("startTimeMs", startTimeMs)
    put("endTimeMs", endTimeMs)
    put("durationMs", durationMs)
    put("severity", severity)
    put("isScreenOff", if (isScreenOff) 1 else 0)
    put("cpuPercent", cpuPercent)
    put("ramMb", ramMb)
    put("batteryPercentDrain", batteryPercentDrain)
    put("dataBytes", dataBytes)
    put("encryptedExtra", encryptedExtra)
}

private fun Cursor.toTimelineEvent(): TimelineEventEntity = TimelineEventEntity(
    id = getLong(getColumnIndexOrThrow("id")),
    category = getString(getColumnIndexOrThrow("category")),
    packageName = getString(getColumnIndexOrThrow("packageName")),
    appName = getString(getColumnIndexOrThrow("appName")),
    actionTitle = getString(getColumnIndexOrThrow("actionTitle")),
    detail = getString(getColumnIndexOrThrow("detail")),
    permissionOrResource = getString(getColumnIndexOrThrow("permissionOrResource")),
    startTimeMs = getLong(getColumnIndexOrThrow("startTimeMs")),
    endTimeMs = getLong(getColumnIndexOrThrow("endTimeMs")),
    durationMs = getLong(getColumnIndexOrThrow("durationMs")),
    severity = getString(getColumnIndexOrThrow("severity")),
    isScreenOff = getInt(getColumnIndexOrThrow("isScreenOff")) != 0,
    cpuPercent = getFloat(getColumnIndexOrThrow("cpuPercent")),
    ramMb = getFloat(getColumnIndexOrThrow("ramMb")),
    batteryPercentDrain = getFloat(getColumnIndexOrThrow("batteryPercentDrain")),
    dataBytes = getLong(getColumnIndexOrThrow("dataBytes")),
    encryptedExtra = getString(getColumnIndexOrThrow("encryptedExtra"))
)

private fun NetworkSessionEntity.toContentValues(): ContentValues = ContentValues().apply {
    if (id != 0L) put("id", id)
    put("packageName", packageName)
    put("appName", appName)
    put("uid", uid)
    put("uploadBytes", uploadBytes)
    put("downloadBytes", downloadBytes)
    put("startTimeMs", startTimeMs)
    put("endTimeMs", endTimeMs)
    put("durationMs", durationMs)
    put("endpoint", endpoint)
    put("protocol", protocol)
    put("isSpike", if (isSpike) 1 else 0)
    put("isBlocked", if (isBlocked) 1 else 0)
}

private fun Cursor.toNetworkSession(): NetworkSessionEntity = NetworkSessionEntity(
    id = getLong(getColumnIndexOrThrow("id")),
    packageName = getString(getColumnIndexOrThrow("packageName")),
    appName = getString(getColumnIndexOrThrow("appName")),
    uid = getInt(getColumnIndexOrThrow("uid")),
    uploadBytes = getLong(getColumnIndexOrThrow("uploadBytes")),
    downloadBytes = getLong(getColumnIndexOrThrow("downloadBytes")),
    startTimeMs = getLong(getColumnIndexOrThrow("startTimeMs")),
    endTimeMs = getLong(getColumnIndexOrThrow("endTimeMs")),
    durationMs = getLong(getColumnIndexOrThrow("durationMs")),
    endpoint = getString(getColumnIndexOrThrow("endpoint")),
    protocol = getString(getColumnIndexOrThrow("protocol")),
    isSpike = getInt(getColumnIndexOrThrow("isSpike")) != 0,
    isBlocked = getInt(getColumnIndexOrThrow("isBlocked")) != 0
)

private fun AppTrustEntity.toContentValues(): ContentValues = ContentValues().apply {
    put("packageName", packageName)
    put("appName", appName)
    put("uid", uid)
    put("trustLevel", trustLevel)
    put("riskScore", riskScore)
    put("riskReasons", riskReasons)
    put("isSideloaded", if (isSideloaded) 1 else 0)
    put("installerSource", installerSource)
    put("firstInstallTimeMs", firstInstallTimeMs)
    put("lastUpdateTimeMs", lastUpdateTimeMs)
    put("permissionSnapshot", permissionSnapshot)
    put("serviceSnapshot", serviceSnapshot)
    put("newlyAddedPermissions", newlyAddedPermissions)
    put("newlyAddedServices", newlyAddedServices)
    put("isFrozen", if (isFrozen) 1 else 0)
    put("isSystemApp", if (isSystemApp) 1 else 0)
}

private fun Cursor.toAppTrust(): AppTrustEntity = AppTrustEntity(
    packageName = getString(getColumnIndexOrThrow("packageName")),
    appName = getString(getColumnIndexOrThrow("appName")),
    uid = getInt(getColumnIndexOrThrow("uid")),
    trustLevel = getString(getColumnIndexOrThrow("trustLevel")),
    riskScore = getInt(getColumnIndexOrThrow("riskScore")),
    riskReasons = getString(getColumnIndexOrThrow("riskReasons")),
    isSideloaded = getInt(getColumnIndexOrThrow("isSideloaded")) != 0,
    installerSource = getString(getColumnIndexOrThrow("installerSource")),
    firstInstallTimeMs = getLong(getColumnIndexOrThrow("firstInstallTimeMs")),
    lastUpdateTimeMs = getLong(getColumnIndexOrThrow("lastUpdateTimeMs")),
    permissionSnapshot = getString(getColumnIndexOrThrow("permissionSnapshot")),
    serviceSnapshot = getString(getColumnIndexOrThrow("serviceSnapshot")),
    newlyAddedPermissions = getString(getColumnIndexOrThrow("newlyAddedPermissions")),
    newlyAddedServices = getString(getColumnIndexOrThrow("newlyAddedServices")),
    isFrozen = getInt(getColumnIndexOrThrow("isFrozen")) != 0,
    isSystemApp = getInt(getColumnIndexOrThrow("isSystemApp")) != 0
)

private fun ScanCacheEntity.toContentValues(): ContentValues = ContentValues().apply {
    put("sha256", sha256)
    put("targetName", targetName)
    put("packageName", packageName)
    put("fileSizeBytes", fileSizeBytes)
    put("verdict", verdict)
    put("threatFamily", threatFamily)
    put("detectionRatio", detectionRatio)
    put("sourceApi", sourceApi)
    put("heuristicScore", heuristicScore)
    put("heuristicFlags", heuristicFlags)
    put("scannedAtMs", scannedAtMs)
    put("expiresAtMs", expiresAtMs)
}

private fun Cursor.toScanCache(): ScanCacheEntity = ScanCacheEntity(
    sha256 = getString(getColumnIndexOrThrow("sha256")),
    targetName = getString(getColumnIndexOrThrow("targetName")),
    packageName = getString(getColumnIndexOrThrow("packageName")),
    fileSizeBytes = getLong(getColumnIndexOrThrow("fileSizeBytes")),
    verdict = getString(getColumnIndexOrThrow("verdict")),
    threatFamily = getString(getColumnIndexOrThrow("threatFamily")),
    detectionRatio = getString(getColumnIndexOrThrow("detectionRatio")),
    sourceApi = getString(getColumnIndexOrThrow("sourceApi")),
    heuristicScore = getInt(getColumnIndexOrThrow("heuristicScore")),
    heuristicFlags = getString(getColumnIndexOrThrow("heuristicFlags")),
    scannedAtMs = getLong(getColumnIndexOrThrow("scannedAtMs")),
    expiresAtMs = getLong(getColumnIndexOrThrow("expiresAtMs"))
)

private fun SecuritySnapshotEntity.toContentValues(): ContentValues = ContentValues().apply {
    if (id != 0L) put("id", id)
    put("capturedAtMs", capturedAtMs)
    put("triggerReason", triggerReason)
    put("severity", severity)
    put("operatingMode", operatingMode)
    put("activeProcessesSummary", activeProcessesSummary)
    put("permissionsSummary", permissionsSummary)
    put("networkSummary", networkSummary)
    put("resourceSummary", resourceSummary)
    put("encryptedPayload", encryptedPayload)
}

private fun Cursor.toSecuritySnapshot(): SecuritySnapshotEntity = SecuritySnapshotEntity(
    id = getLong(getColumnIndexOrThrow("id")),
    capturedAtMs = getLong(getColumnIndexOrThrow("capturedAtMs")),
    triggerReason = getString(getColumnIndexOrThrow("triggerReason")),
    severity = getString(getColumnIndexOrThrow("severity")),
    operatingMode = getString(getColumnIndexOrThrow("operatingMode")),
    activeProcessesSummary = getString(getColumnIndexOrThrow("activeProcessesSummary")),
    permissionsSummary = getString(getColumnIndexOrThrow("permissionsSummary")),
    networkSummary = getString(getColumnIndexOrThrow("networkSummary")),
    resourceSummary = getString(getColumnIndexOrThrow("resourceSummary")),
    encryptedPayload = getString(getColumnIndexOrThrow("encryptedPayload"))
)

private fun AiInsightEntity.toContentValues(): ContentValues = ContentValues().apply {
    if (id != 0L) put("id", id)
    put("createdAtMs", createdAtMs)
    put("scheduleType", scheduleType)
    put("title", title)
    put("summaryText", summaryText)
    put("eventsAnalyzed", eventsAnalyzed)
    put("quotaSavedNote", quotaSavedNote)
}

private fun Cursor.toAiInsight(): AiInsightEntity = AiInsightEntity(
    id = getLong(getColumnIndexOrThrow("id")),
    createdAtMs = getLong(getColumnIndexOrThrow("createdAtMs")),
    scheduleType = getString(getColumnIndexOrThrow("scheduleType")),
    title = getString(getColumnIndexOrThrow("title")),
    summaryText = getString(getColumnIndexOrThrow("summaryText")),
    eventsAnalyzed = getInt(getColumnIndexOrThrow("eventsAnalyzed")),
    quotaSavedNote = getString(getColumnIndexOrThrow("quotaSavedNote"))
)

private fun ChatMessageEntity.toContentValues(): ContentValues = ContentValues().apply {
    if (id != 0L) put("id", id)
    put("timestampMs", timestampMs)
    put("isUser", if (isUser) 1 else 0)
    put("messageText", messageText)
    put("isRefusal", if (isRefusal) 1 else 0)
}

private fun Cursor.toChatMessage(): ChatMessageEntity = ChatMessageEntity(
    id = getLong(getColumnIndexOrThrow("id")),
    timestampMs = getLong(getColumnIndexOrThrow("timestampMs")),
    isUser = getInt(getColumnIndexOrThrow("isUser")) != 0,
    messageText = getString(getColumnIndexOrThrow("messageText")),
    isRefusal = getInt(getColumnIndexOrThrow("isRefusal")) != 0
)
