package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.EventCategory
import com.example.data.local.TimelineEventEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonBadge
import com.example.ui.components.severityColor
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.TimeFormatter

@Composable
fun TimelineScreen(
    events: List<TimelineEventEntity>,
    selectedCategory: EventCategory?,
    onSelectCategory: (EventCategory?) -> Unit,
    onQuickActions: (String, String) -> Unit
) {
    var onlyHighSeverity by remember { mutableStateOf(false) }

    val filtered = remember(events, selectedCategory, onlyHighSeverity) {
        events.filter { ev ->
            val catMatch = selectedCategory == null || ev.category == selectedCategory.name
            val sevMatch = !onlyHighSeverity || ev.severity == "HIGH" || ev.severity == "CRITICAL"
            catMatch && sevMatch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Watchdog Timeline & Leases",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Exact start/end timestamps & active durations across all monitors",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                FilterChip(
                    selected = onlyHighSeverity,
                    onClick = { onlyHighSeverity = !onlyHighSeverity },
                    label = { Text("Alerts Only") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AlertCrimson.copy(alpha = 0.2f),
                        selectedLabelColor = AlertCrimson
                    ),
                    modifier = Modifier.testTag("timeline_alerts_only_chip")
                )
            }

            Spacer(Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text("All (${events.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan
                        ),
                        modifier = Modifier.testTag("filter_all")
                    )
                }
                items(EventCategory.entries) { cat ->
                    val count = events.count { it.category == cat.name }
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onSelectCategory(cat) },
                        label = { Text("${cat.displayName} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan
                        ),
                        modifier = Modifier.testTag("filter_${cat.name.lowercase()}")
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            GlassCard(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "No timeline entries match the current filter. Enable Watchdog Service or refresh telemetry to capture live permission, sensor, root, and resource events.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { ev ->
                    val sevColor = severityColor(ev.severity)
                    GlassCard(
                        accentBorder = sevColor.copy(alpha = 0.35f),
                        modifier = Modifier.testTag("timeline_card_${ev.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ev.appName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = ev.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (ev.isScreenOff) {
                                    NeonBadge(text = "SCREEN OFF", color = AlertCrimson)
                                }
                                NeonBadge(text = ev.permissionOrResource, color = sevColor)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = ev.actionTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = NeonCyan
                        )
                        Text(
                            text = ev.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(Modifier.height(10.dp))

                        // Timestamp & Duration Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Schedule,
                                    contentDescription = "Timestamp",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${TimeFormatter.formatShortTime(ev.startTimeMs)} → ${TimeFormatter.formatShortTime(ev.endTimeMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.AccessTime,
                                    contentDescription = "Active duration",
                                    tint = CyberEmerald,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = TimeFormatter.formatDuration(ev.durationMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyberEmerald
                                )
                            }
                        }

                        if (ev.cpuPercent > 0f || ev.ramMb > 0f || ev.batteryPercentDrain > 0f) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (ev.cpuPercent > 0f) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Memory, contentDescription = null, tint = AmberPulse, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("CPU ${ev.cpuPercent.toInt()}% • ${ev.ramMb.toInt()}MB", style = MaterialTheme.typography.labelSmall, color = AmberPulse)
                                    }
                                }
                                if (ev.batteryPercentDrain > 0f) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.BatteryAlert, contentDescription = null, tint = AlertCrimson, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            String.format("~%.1f%% battery", ev.batteryPercentDrain),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AlertCrimson
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { onQuickActions(ev.packageName, ev.appName) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("quick_action_btn_${ev.id}")
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Quick Actions (Stop / Freeze / Revoke / Trust)", color = NeonCyan)
                        }
                    }
                }
            }
        }
    }
}
