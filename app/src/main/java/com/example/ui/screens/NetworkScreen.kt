package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.NetworkSessionEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonBadge
import com.example.ui.theme.LocalJrsPalette
import com.example.util.TimeFormatter

@Composable
fun NetworkScreen(
    sessions: List<NetworkSessionEntity>,
    isRootActive: Boolean,
    onToggleBlock: (Int, String, Boolean) -> Unit,
    onQuickActions: (String, String) -> Unit
) {
    val palette = LocalJrsPalette.current
    val totalUp = sessions.sumOf { it.uploadBytes }
    val totalDown = sessions.sumOf { it.downloadBytes }
    val spikeCount = sessions.count { it.isSpike }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(accentBorder = palette.cyan.copy(alpha = 0.4f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Network Session & Data Spike Timeline",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                        Text(
                            text = if (isRootActive) "Root netpolicy/iptables firewall enabled" else "Non-Root TrafficStats & /proc/net socket view",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                    NeonBadge(
                        text = "$spikeCount SPIKES",
                        color = if (spikeCount > 0) palette.crimson else palette.emerald
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Upload", tint = palette.amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Total Up: ${TimeFormatter.formatBytes(totalUp)}", style = MaterialTheme.typography.labelMedium, color = palette.amber)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Download", tint = palette.cyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Total Down: ${TimeFormatter.formatBytes(totalDown)}", style = MaterialTheme.typography.labelMedium, color = palette.cyan)
                    }
                }

                Spacer(Modifier.height(12.dp))

                NetworkSpikeChart(
                    sessions = sessions.take(12),
                    cyanColor = palette.cyan,
                    blueColor = palette.blue,
                    crimsonColor = palette.crimson,
                    amberColor = palette.amber,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )
            }
        }

        if (sessions.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        text = "No per-app traffic sessions recorded yet. Grant Usage Access or trigger a telemetry refresh to inspect upload/download spikes and remote endpoints.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                val accent = when {
                    session.isBlocked -> palette.crimson
                    session.isSpike -> palette.amber
                    else -> palette.blue
                }
                GlassCard(
                    accentBorder = accent.copy(alpha = 0.4f),
                    onClick = { onQuickActions(session.packageName, session.appName) },
                    modifier = Modifier.testTag("network_card_${session.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.appName,
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = session.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (session.isSpike) NeonBadge(text = "SPIKE", color = palette.crimson)
                            if (session.isBlocked) NeonBadge(text = "BLOCKED", color = palette.crimson)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "↑ ${TimeFormatter.formatBytes(session.uploadBytes)}  •  ↓ ${TimeFormatter.formatBytes(session.downloadBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.cyan
                        )
                        Text(
                            text = TimeFormatter.formatDuration(session.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.emerald
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Router, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${session.protocol} • ${session.endpoint} • ${TimeFormatter.formatShortTime(session.endTimeMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onToggleBlock(session.uid, session.packageName, !session.isBlocked) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("net_block_btn_${session.id}"),
                        border = BorderStroke(
                            1.dp,
                            if (session.isBlocked) palette.emerald else palette.crimson.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(
                            imageVector = if (session.isBlocked) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                            contentDescription = null,
                            tint = if (session.isBlocked) palette.emerald else palette.crimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (session.isBlocked) {
                                "Unblock Network Access"
                            } else if (isRootActive) {
                                "Block via Root Firewall"
                            } else {
                                "Flag & Block in Watchlist"
                            },
                            color = if (session.isBlocked) palette.emerald else palette.crimson
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkSpikeChart(
    sessions: List<NetworkSessionEntity>,
    cyanColor: Color,
    blueColor: Color,
    crimsonColor: Color,
    amberColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (sessions.isEmpty()) return@Canvas
        val maxTotal = sessions.maxOf { (it.uploadBytes + it.downloadBytes).coerceAtLeast(1L) }.toFloat()
        val count = sessions.size.coerceAtLeast(1)
        val slotWidth = size.width / count
        val barWidth = (slotWidth * 0.58f).coerceAtLeast(8f)

        sessions.forEachIndexed { idx, s ->
            val total = (s.uploadBytes + s.downloadBytes).toFloat()
            val ratio = (total / maxTotal).coerceIn(0.08f, 1f)
            val barH = size.height * ratio
            val x = idx * slotWidth + (slotWidth - barWidth) / 2f
            val y = size.height - barH

            val barBrush = if (s.isSpike) {
                Brush.verticalGradient(listOf(crimsonColor, amberColor))
            } else {
                Brush.verticalGradient(listOf(cyanColor, blueColor))
            }

            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
    }
}
