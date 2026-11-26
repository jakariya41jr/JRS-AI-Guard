package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val CHANNEL_WATCHDOG = "jrs_watchdog_service"
    const val CHANNEL_CRITICAL = "jrs_critical_alerts"
    const val CHANNEL_DIGEST = "jrs_ai_digests"

    const val WATCHDOG_NOTIFICATION_ID = 1001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val watchdogChannel = NotificationChannel(
                CHANNEL_WATCHDOG,
                context.getString(R.string.channel_watchdog_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_watchdog_desc)
            }

            val criticalChannel = NotificationChannel(
                CHANNEL_CRITICAL,
                context.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_alerts_desc)
                enableVibration(true)
            }

            val digestChannel = NotificationChannel(
                CHANNEL_DIGEST,
                context.getString(R.string.channel_digest_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_digest_desc)
            }

            manager.createNotificationChannels(listOf(watchdogChannel, criticalChannel, digestChannel))
        }
    }

    fun buildWatchdogNotification(context: Context, subtitle: String) =
        NotificationCompat.Builder(context, CHANNEL_WATCHDOG)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("JRS AI Guard Watchdog Active")
            .setContentText(subtitle)
            .setOngoing(true)
            .setContentIntent(launchAppPendingIntent(context))
            .build()

    fun postCriticalAlert(context: Context, title: String, message: String, alertId: Int) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_CRITICAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launchAppPendingIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(alertId, notification)
    }

    fun postDigestNotification(context: Context, title: String, summary: String) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .setContentIntent(launchAppPendingIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(2040, notification)
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun launchAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
