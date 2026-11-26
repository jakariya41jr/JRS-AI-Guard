package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.EventCategory
import com.example.data.local.JrsGuardDatabase
import com.example.data.local.SeverityLevel
import com.example.data.local.TimelineEventEntity
import com.example.data.telemetry.SystemIntegrityAuditor
import com.example.data.telemetry.SystemTelemetryCollector
import com.example.security.RootBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageAuditReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val db = JrsGuardDatabase.getInstance(context)
                val dao = db.dao()
                val rootBridge = RootBridge()
                val auditor = SystemIntegrityAuditor(context, rootBridge)
                val collector = SystemTelemetryCollector(context, dao, rootBridge, auditor)

                when (action) {
                    Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                        collector.auditInstalledPackages(now)
                    }
                    Intent.ACTION_PACKAGE_REMOVED -> {
                        dao.insertTimelineEvent(
                            TimelineEventEntity(
                                category = EventCategory.INSTALL_AUDIT.name,
                                packageName = pkg,
                                appName = pkg,
                                actionTitle = "Package Uninstalled",
                                detail = "Package removed from device",
                                permissionOrResource = "Uninstall",
                                startTimeMs = now,
                                endTimeMs = now,
                                durationMs = 0L,
                                severity = SeverityLevel.INFO.name
                            )
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
