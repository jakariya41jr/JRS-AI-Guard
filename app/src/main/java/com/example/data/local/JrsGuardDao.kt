package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JrsGuardDao {

    @Query("SELECT * FROM timeline_events ORDER BY startTimeMs DESC LIMIT 300")
    fun observeTimelineEvents(): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events ORDER BY startTimeMs DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 100): List<TimelineEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineEvent(event: TimelineEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineEvents(events: List<TimelineEventEntity>)

    @Query("DELETE FROM timeline_events WHERE startTimeMs < :olderThanMs")
    suspend fun pruneOldTimelineEvents(olderThanMs: Long)

    @Query("SELECT * FROM network_sessions ORDER BY endTimeMs DESC LIMIT 200")
    fun observeNetworkSessions(): Flow<List<NetworkSessionEntity>>

    @Query("SELECT * FROM network_sessions ORDER BY endTimeMs DESC LIMIT :limit")
    suspend fun getRecentNetworkSessions(limit: Int = 100): List<NetworkSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetworkSessions(sessions: List<NetworkSessionEntity>)

    @Query("UPDATE network_sessions SET isBlocked = :blocked WHERE packageName = :packageName")
    suspend fun updateNetworkBlockByPackage(packageName: String, blocked: Boolean)

    @Query("SELECT * FROM app_trust ORDER BY riskScore DESC, appName ASC")
    fun observeAppTrustList(): Flow<List<AppTrustEntity>>

    @Query("SELECT * FROM app_trust")
    suspend fun getAllAppTrust(): List<AppTrustEntity>

    @Query("SELECT * FROM app_trust WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppTrust(packageName: String): AppTrustEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppTrust(app: AppTrustEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppTrustList(apps: List<AppTrustEntity>)

    @Query("UPDATE app_trust SET trustLevel = :trustLevel WHERE packageName = :packageName")
    suspend fun updateTrustLevel(packageName: String, trustLevel: String)

    @Query("UPDATE app_trust SET isFrozen = :frozen WHERE packageName = :packageName")
    suspend fun updateFrozenState(packageName: String, frozen: Boolean)

    @Query("SELECT * FROM scan_cache ORDER BY scannedAtMs DESC")
    fun observeScanHistory(): Flow<List<ScanCacheEntity>>

    @Query("SELECT * FROM scan_cache WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getCachedScan(sha256: String): ScanCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScanResult(scan: ScanCacheEntity)

    @Query("SELECT * FROM security_snapshots ORDER BY capturedAtMs DESC")
    fun observeSnapshots(): Flow<List<SecuritySnapshotEntity>>

    @Query("SELECT * FROM security_snapshots ORDER BY capturedAtMs DESC LIMIT :limit")
    suspend fun getRecentSnapshots(limit: Int = 50): List<SecuritySnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: SecuritySnapshotEntity): Long

    @Query("SELECT * FROM ai_insights ORDER BY createdAtMs DESC")
    fun observeAiInsights(): Flow<List<AiInsightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiInsight(insight: AiInsightEntity): Long

    @Query("SELECT * FROM chat_messages ORDER BY timestampMs ASC")
    fun observeChatMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatMessages()
}
