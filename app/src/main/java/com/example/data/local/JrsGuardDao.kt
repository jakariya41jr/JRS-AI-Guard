package com.example.data.local

import kotlinx.coroutines.flow.Flow

interface JrsGuardDao {

    fun observeTimelineEvents(): Flow<List<TimelineEventEntity>>

    suspend fun getRecentEvents(limit: Int = 100): List<TimelineEventEntity>

    suspend fun insertTimelineEvent(event: TimelineEventEntity): Long

    suspend fun insertTimelineEvents(events: List<TimelineEventEntity>)

    suspend fun pruneOldTimelineEvents(olderThanMs: Long)

    fun observeNetworkSessions(): Flow<List<NetworkSessionEntity>>

    suspend fun getRecentNetworkSessions(limit: Int = 100): List<NetworkSessionEntity>

    suspend fun insertNetworkSessions(sessions: List<NetworkSessionEntity>)

    suspend fun updateNetworkBlockByPackage(packageName: String, blocked: Boolean)

    fun observeAppTrustList(): Flow<List<AppTrustEntity>>

    suspend fun getAllAppTrust(): List<AppTrustEntity>

    suspend fun getAppTrust(packageName: String): AppTrustEntity?

    suspend fun upsertAppTrust(app: AppTrustEntity)

    suspend fun upsertAppTrustList(apps: List<AppTrustEntity>)

    suspend fun updateTrustLevel(packageName: String, trustLevel: String)

    suspend fun updateFrozenState(packageName: String, frozen: Boolean)

    fun observeScanHistory(): Flow<List<ScanCacheEntity>>

    suspend fun getCachedScan(sha256: String): ScanCacheEntity?

    suspend fun upsertScanResult(scan: ScanCacheEntity)

    fun observeSnapshots(): Flow<List<SecuritySnapshotEntity>>

    suspend fun getRecentSnapshots(limit: Int = 50): List<SecuritySnapshotEntity>

    suspend fun insertSnapshot(snapshot: SecuritySnapshotEntity): Long

    fun observeAiInsights(): Flow<List<AiInsightEntity>>

    suspend fun insertAiInsight(insight: AiInsightEntity): Long

    fun observeChatMessages(): Flow<List<ChatMessageEntity>>

    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    suspend fun clearChatMessages()
}
