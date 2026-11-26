package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.local.AppTrustEntity
import com.example.data.local.EventCategory
import com.example.data.local.NetworkSessionEntity
import com.example.data.local.TimelineEventEntity
import com.example.data.telemetry.IntegrityAuditReport
import com.example.security.GuardOperatingMode
import com.example.security.VaultConfig
import com.example.ui.components.GlassCard
import com.example.ui.components.IntegrityGauge
import com.example.ui.components.JrsShieldLogo
import com.example.ui.components.NeonBadge
import com.example.ui.components.OfficialWebsiteButton
import com.example.ui.components.severityColor
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassStrokeBright
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeural
import com.example.util.TimeFormatter

@Composable
fun DashboardScreen(
    integrityReport: IntegrityAuditReport?,
    vaultConfig: VaultConfig,
    events: List<TimelineEventEntity>,
    apps: List<AppTrustEntity>,
    networkSessions: List<NetworkSessionEntity>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onToggleWatchdog: (Boolean) -> Unit,
    onGrantUsageAccess: () -> Unit,
    onCaptureSnapshot: () -> Unit,
    onQuickActions: (String, String) -> Unit,
    onOpenCategory: (EventCategory) -> Unit,
    onOpenOfficialWebsite: () -> Unit
) {
    val mode = integrityReport?.rootStatus?.mode ?: GuardOperatingMode.NON_ROOT_SAFETY_NET
    val score = integrityReport?.overallScore ?: 85
    val modeColor = when (mode) {
        GuardOperatingMode.ROOTED_ACTIVE -> CyberEmerald
        GuardOperatingMode.ROOT_BINARY_LOCKED -> AmberPulse
        GuardOperatingMode.NON_ROOT_SAFETY_NET -> NeonCyan
    }

    val sensorCount = events.count { it.category == EventCategory.SENSOR_LEASE.name }
    val rootWatchCount = events.count { it.category == EventCategory.ROOT_WATCH.name }
    val clipCount = events.count { it.category == EventCategory.CLIPBOARD_OVERLAY.name }
    val galleryCount = events.count { it.category == EventCategory.GALLERY_DATA.name }
    val bgSpikeCount = events.count { it.category == EventCategory.BACKGROUND_RESOURCE.name }
    val netSpikeCount = networkSessions.count { it.isSpike }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Branded Hero Banner Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianDeep)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_jrs_hero_banner),
                    contentDescription = "JRS AI Guard defense grid banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.38f)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianVoid.copy(alpha = 0.5f),
                                    ObsidianVoid.copy(alpha = 0.92f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            JrsShieldLogo(size = 48.dp)
                            Column {
                                Text(
                                    text = "JRS AI Guard",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${BuildConfig.APPLICATION_ID} • Md Jakariya Hasan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("dashboard_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh telemetry",
                                tint = NeonCyan
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IntegrityGauge(score = score)
                        Spacer(Modifier.width(14.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            NeonBadge(text = mode.badge, color = modeColor)
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "SELinux: ${integrityReport?.rootStatus?.selinuxStatus ?: "Checking"} • ${apps.size} apps audited",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    if (isRefreshing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = NeonCyan,
                            trackColor = Color.White.copy(alpha = 0.08f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onCaptureSnapshot,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("capture_snapshot_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = ObsidianVoid
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Snapshot", style = MaterialTheme.typography.labelLarge)
                        }

                        OfficialWebsiteButton(
                            onClick = onOpenOfficialWebsite,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Live Watchdog Service Switch
        item {
            GlassCard(
                accentBorder = if (vaultConfig.watchdogEnabled) CyberEmerald.copy(alpha = 0.5f) else GlassStrokeBright
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Real-Time Hardware & Clipboard Watchdog",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tracks camera, mic leases, screen-off sensor alerts & clipboard access with exact duration.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = vaultConfig.watchdogEnabled,
                        onCheckedChange = onToggleWatchdog,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianVoid,
                            checkedTrackColor = CyberEmerald
                        ),
                        modifier = Modifier.testTag("watchdog_service_switch")
                    )
                }
            }
        }

        // Usage Access Setup Banner when Non-Root telemetry needs permission
        item {
            AnimatedVisibility(visible = integrityReport?.usageStatsGranted == false) {
                GlassCard(accentBorder = AmberPulse.copy(alpha = 0.55f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = "Usage access needed",
                            tint = AmberPulse,
                            modifier = Modifier.size(26.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Unlock Full Background & Network Timelines",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Grant Usage Access so JRS AI Guard can log per-app background runtimes and data spikes without root.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onGrantUsageAccess,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("grant_usage_access_button"),
                        border = BorderStroke(1.dp, AmberPulse)
                    ) {
                        Text("Open Usage Access Settings", color = AmberPulse)
                    }
                }
            }
        }

        // 2x3 Watchdog Modules Grid
        item {
            Text(
                text = "Watchdog Telemetry Modules",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ModuleTile(
                        title = "Sensor Leases",
                        value = "$sensorCount",
                        subtitle = "Cam • Mic • Screen-Off",
                        icon = Icons.Rounded.CameraAlt,
                        accent = NeonCyan,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_sensor_leases"),
                        onClick = { onOpenCategory(EventCategory.SENSOR_LEASE) }
                    )
                    ModuleTile(
                        title = "Root Watch",
                        value = "$rootWatchCount",
                        subtitle = if (mode == GuardOperatingMode.ROOTED_ACTIVE) "su Active" else "Non-Root Safe",
                        icon = Icons.Rounded.Terminal,
                        accent = CyberEmerald,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_root_watch"),
                        onClick = { onOpenCategory(EventCategory.ROOT_WATCH) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ModuleTile(
                        title = "Clipboard & Overlay",
                        value = "$clipCount",
                        subtitle = "Read & Capture Guard",
                        icon = Icons.Rounded.ContentPaste,
                        accent = AmberPulse,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_clipboard_overlay"),
                        onClick = { onOpenCategory(EventCategory.CLIPBOARD_OVERLAY) }
                    )
                    ModuleTile(
                        title = "Gallery & Exfil",
                        value = "$galleryCount",
                        subtitle = "Media + Outbound",
                        icon = Icons.Rounded.PhotoLibrary,
                        accent = VioletNeural,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_gallery_data"),
                        onClick = { onOpenCategory(EventCategory.GALLERY_DATA) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ModuleTile(
                        title = "Background & CPU",
                        value = "$bgSpikeCount",
                        subtitle = "RAM • Battery • Runtime",
                        icon = Icons.Rounded.Memory,
                        accent = ElectricBlue,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_background_cpu"),
                        onClick = { onOpenCategory(EventCategory.BACKGROUND_RESOURCE) }
                    )
                    ModuleTile(
                        title = "Net Data Spikes",
                        value = "$netSpikeCount",
                        subtitle = "${networkSessions.size} sessions",
                        icon = Icons.Rounded.Shield,
                        accent = if (netSpikeCount > 0) AlertCrimson else CyberEmerald,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_net_spikes"),
                        onClick = { onOpenCategory(EventCategory.PERMISSION) }
                    )
                }
            }
        }

        // Recent High-Priority Events with One-Tap Quick Actions
        item {
            Text(
                text = "Recent Security Events",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }

        val topEvents = events.take(8)
        if (topEvents.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        text = "No anomalies logged yet. Keep Watchdog enabled or tap Refresh to audit installed apps, permissions, and network traffic.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(topEvents, key = { it.id }) { ev ->
                val sevColor = severityColor(ev.severity)
                GlassCard(
                    accentBorder = sevColor.copy(alpha = 0.35f),
                    onClick = { onQuickActions(ev.packageName, ev.appName) },
                    modifier = Modifier.testTag("dashboard_event_${ev.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ev.appName,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        NeonBadge(text = ev.severity, color = sevColor)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = ev.actionTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonCyan
                    )
                    Text(
                        text = ev.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "At ${TimeFormatter.formatShortTime(ev.startTimeMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = "Active: ${TimeFormatter.formatDuration(ev.durationMs)} • Tap for Quick Actions",
                            style = MaterialTheme.typography.labelSmall,
                            color = sevColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier.heightIn(min = 104.dp),
        accentBorder = accent.copy(alpha = 0.32f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
