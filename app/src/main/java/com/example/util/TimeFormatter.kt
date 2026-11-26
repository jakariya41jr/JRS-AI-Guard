package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormatter {

    fun formatTimestamp(timeMs: Long): String {
        if (timeMs <= 0L) return "--:--"
        val fmt = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        return fmt.format(Date(timeMs))
    }

    fun formatShortTime(timeMs: Long): String {
        if (timeMs <= 0L) return "--:--"
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        return fmt.format(Date(timeMs))
    }

    fun formatDateTime(timeMs: Long): String {
        if (timeMs <= 0L) return "Unknown"
        val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return fmt.format(Date(timeMs))
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "< 1s"
        val totalSeconds = durationMs / 1000L
        if (totalSeconds < 1L) return "${durationMs}ms"
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0L -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0L -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }
}
