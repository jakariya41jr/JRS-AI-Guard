package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.SeverityLevel
import com.example.data.local.TrustLevel
import com.example.ui.QuickActionTarget
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassStroke
import com.example.ui.theme.GlassStrokeBright
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentBorder: Color = GlassStroke,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val baseModifier = modifier
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(ObsidianCardElevated, ObsidianCard)
            )
        )
        .border(width = 1.dp, color = accentBorder, shape = shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Column(
        modifier = baseModifier.padding(16.dp),
        content = content
    )
}

@Composable
fun NeonBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun JrsShieldLogo(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(NeonCyan.copy(alpha = 0.22f), ObsidianDeep)
                )
            )
            .border(1.dp, GlassStrokeBright, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.68f)) {
            val w = this.size.width
            val h = this.size.height
            val shieldPath = Path().apply {
                moveTo(w * 0.5f, h * 0.06f)
                lineTo(w * 0.9f, h * 0.22f)
                lineTo(w * 0.85f, h * 0.62f)
                quadraticTo(w * 0.72f, h * 0.86f, w * 0.5f, h * 0.95f)
                quadraticTo(w * 0.28f, h * 0.86f, w * 0.15f, h * 0.62f)
                lineTo(w * 0.1f, h * 0.22f)
                close()
            }
            drawPath(
                path = shieldPath,
                brush = Brush.linearGradient(listOf(NeonCyan, CyberEmerald)),
                style = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
            )
            // Neural AI Sentinel Eye
            drawCircle(
                color = NeonCyan,
                radius = w * 0.13f,
                center = Offset(w * 0.5f, h * 0.47f),
                style = Stroke(width = w * 0.065f)
            )
            drawCircle(
                color = CyberEmerald,
                radius = w * 0.055f,
                center = Offset(w * 0.5f, h * 0.47f)
            )
        }
    }
}

@Composable
fun IntegrityGauge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val ringColor = when {
        score >= 80 -> CyberEmerald
        score >= 55 -> AmberPulse
        else -> AlertCrimson
    }
    Box(
        modifier = modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            drawArc(
                color = Color.White.copy(alpha = 0.09f),
                startAngle = -220f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(NeonCyan, ringColor)),
                startAngle = -220f,
                sweepAngle = 260f * (score.coerceIn(0, 100) / 100f),
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "POSTURE",
                style = MaterialTheme.typography.labelSmall,
                color = ringColor
            )
        }
    }
}

@Composable
fun OfficialWebsiteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("official_website_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonCyan.copy(alpha = 0.16f),
            contentColor = NeonCyan
        ),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.55f))
    ) {
        Icon(
            imageVector = Icons.Rounded.Language,
            contentDescription = "Official Developer Website",
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Official Website",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}

fun severityColor(severity: String): Color = when (severity) {
    SeverityLevel.CRITICAL.name -> AlertCrimson
    SeverityLevel.HIGH.name -> Color(0xFFFF6D00)
    SeverityLevel.MEDIUM.name -> AmberPulse
    else -> NeonCyan
}

fun trustColor(level: String): Color = when (level) {
    TrustLevel.BLOCKED.name -> AlertCrimson
    TrustLevel.WATCHLIST.name -> AmberPulse
    else -> CyberEmerald
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickActionControlSheet(
    target: QuickActionTarget,
    isRootActive: Boolean,
    onDismiss: () -> Unit,
    onForceStop: (String) -> Unit,
    onFreeze: (String, Boolean) -> Unit,
    onRevokePerm: (String, String) -> Unit,
    onUpdateTrust: (String, TrustLevel) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianDeep,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = target.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_quick_actions")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close controls", tint = TextSecondary)
                }
            }

            NeonBadge(
                text = if (isRootActive) "ROOT QUICK EXECUTION READY" else "NON-ROOT SAFETY GUIDANCE",
                color = if (isRootActive) CyberEmerald else ElectricBlue
            )

            Text(
                text = "App Trust Classification",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrustLevel.entries.forEach { level ->
                    val selected = target.currentTrust == level
                    val color = trustColor(level.name)
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateTrust(target.packageName, level) },
                        label = { Text(level.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (level) {
                                    TrustLevel.TRUSTED -> Icons.Rounded.CheckCircle
                                    TrustLevel.WATCHLIST -> Icons.Rounded.Visibility
                                    TrustLevel.BLOCKED -> Icons.Rounded.Block
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.2f),
                            selectedLabelColor = color,
                            selectedLeadingIconColor = color
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("trust_chip_${level.name.lowercase()}")
                    )
                }
            }

            Text(
                text = "One-Tap Containment",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onForceStop(target.packageName) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("action_force_stop"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPulse.copy(alpha = 0.18f),
                        contentColor = AmberPulse
                    ),
                    border = BorderStroke(1.dp, AmberPulse.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Force Stop")
                }

                Button(
                    onClick = { onFreeze(target.packageName, true) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("action_freeze_app"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlertCrimson.copy(alpha = 0.18f),
                        contentColor = AlertCrimson
                    ),
                    border = BorderStroke(1.dp, AlertCrimson.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isRootActive) "Freeze App" else "Disable / Block")
                }
            }

            if (target.permissions.isNotEmpty()) {
                Text(
                    text = "Revoke Sensitive Permission",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    target.permissions.take(6).forEach { perm ->
                        OutlinedButton(
                            onClick = { onRevokePerm(target.packageName, perm) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("revoke_${perm.lowercase()}"),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Revoke $perm", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { onRevokePerm(target.packageName, "CAMERA") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("open_app_permissions"),
                    border = BorderStroke(1.dp, GlassStrokeBright)
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Manage / Revoke App Permissions", color = NeonCyan)
                }
            }

            Text(
                text = if (isRootActive) {
                    "Root commands run directly via verified su bridge."
                } else {
                    "Non-root safety net opens Android system controls when root privileges are absent."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
