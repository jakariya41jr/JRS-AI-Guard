package com.example.data.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AiInsightEntity
import com.example.data.local.JrsGuardDatabase
import com.example.data.telemetry.SystemIntegrityAuditor
import com.example.security.AiScheduleFrequency
import com.example.security.CryptoVault
import com.example.security.RootBridge
import com.example.service.NotificationHelper
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AiDigestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val scheduleLabel = inputData.getString(KEY_SCHEDULE_LABEL) ?: "Scheduled Digest"
        val vault = CryptoVault(applicationContext)
        val config = vault.configFlow.first()
        if (!config.hasGeminiKey) {
            return Result.success()
        }

        val db = JrsGuardDatabase.getInstance(applicationContext)
        val dao = db.dao()
        val rootBridge = RootBridge()
        val auditor = SystemIntegrityAuditor(applicationContext, rootBridge)

        val apps = dao.getAllAppTrust()
        val events = dao.getRecentEvents(30)
        val network = dao.getRecentNetworkSessions(20)
        val snapshots = dao.getRecentSnapshots(10)
        val audit = auditor.runFullIntegrityAudit(apps)

        val gemini = GeminiClient(vault)
        val summaryPayload = gemini.buildPrivacyFilteredSummary(
            mode = audit.rootStatus.mode,
            integrityScore = audit.overallScore,
            events = events,
            apps = apps,
            network = network,
            snapshots = snapshots
        )

        val response = gemini.generateScheduledDigest(
            scheduleLabel = scheduleLabel,
            privacySummary = summaryPayload,
            model = config.geminiModel
        )

        if (response.success) {
            val now = System.currentTimeMillis()
            dao.insertAiInsight(
                AiInsightEntity(
                    createdAtMs = now,
                    scheduleType = scheduleLabel,
                    title = "JRS AI Guard • $scheduleLabel",
                    summaryText = response.text,
                    eventsAnalyzed = events.size,
                    quotaSavedNote = "Privacy-filtered compact summary (~${summaryPayload.length} chars)"
                )
            )
            vault.recordDigestRun(now)
            NotificationHelper.postDigestNotification(
                applicationContext,
                "JRS AI Guard $scheduleLabel",
                response.text.take(180)
            )
            return Result.success()
        }

        return Result.retry()
    }

    companion object {
        const val KEY_SCHEDULE_LABEL = "schedule_label"
        private const val PERIODIC_WORK_NAME = "jrs_ai_guard_periodic_digest"
        private const val EVENT_WORK_NAME = "jrs_ai_guard_event_digest"

        fun schedulePeriodicDigest(context: Context, frequency: AiScheduleFrequency) {
            val wm = WorkManager.getInstance(context)
            if (frequency == AiScheduleFrequency.OFF || frequency.intervalHours <= 0L) {
                wm.cancelUniqueWork(PERIODIC_WORK_NAME)
                return
            }

            val req = PeriodicWorkRequestBuilder<AiDigestWorker>(
                frequency.intervalHours,
                TimeUnit.HOURS
            )
                .setInputData(workDataOf(KEY_SCHEDULE_LABEL to frequency.label))
                .build()

            wm.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun triggerImmediateDigest(context: Context, reason: String) {
            val req = OneTimeWorkRequestBuilder<AiDigestWorker>()
                .setInputData(workDataOf(KEY_SCHEDULE_LABEL to reason))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                EVENT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}
