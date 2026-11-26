package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.SeverityLevel
import com.example.data.local.TrustLevel
import com.example.ui.QuickActionTarget
import com.example.ui.theme.LocalJrsPalette
import kotlinx.coroutines.delay

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentBorder: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalJrsPalette.current
    val shape = RoundedCornerShape(18.dp)
    val strokeColor = accentBorder ?: palette.glassStroke
    val baseModifier = modifier
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(palette.cardTop, palette.cardBottom)
            )
        )
        .border(width = 1.dp, color = strokeColor, shape = shape)
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
    val palette = LocalJrsPalette.current
    val bgAlpha = if (palette.isDark) 0.14f else 0.12f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = bgAlpha),
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
fun RootVibeBadge(
    text: String,
    isRootActive: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalJrsPalette.current
    val infiniteTransition = rememberInfiniteTransition(label = "root_vibe_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val badgeColor = if (isRootActive) palette.emerald else palette.cyan

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = badgeColor.copy(alpha = if (isRootActive) 0.16f else 0.13f),
        border = BorderStroke(
            width = if (isRootActive) 1.25.dp else 1.dp,
            color = badgeColor.copy(alpha = if (isRootActive) pulse else 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isRootActive) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(palette.emerald.copy(alpha = pulse))
                )
            }
            Text(
                text = text,
                color = badgeColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RootGrantCelebrationOverlay(
    visible: Boolean,
    rootManagerName: String,
    uidOutput: String,
    onDismiss: () -> Unit
) {
    val palette = LocalJrsPalette.current
    val haptic = LocalHapticFeedback.current
    val pulseAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.75f) }

    LaunchedEffect(visible) {
        if (visible) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            pulseAnim.snapTo(0f)
            scaleAnim.snapTo(0.78f)
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(420, easing = FastOutSlowInEasing)
            )
            pulseAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(1300, easing = LinearEasing)
            )
            delay(1100)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.88f),
        exit = fadeOut(tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(onClick = onDismiss)
                .testTag("root_grant_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(280.dp)) {
                val p = pulseAnim.value
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f
                drawCircle(
                    color = palette.emerald.copy(alpha = (1f - p) * 0.48f),
                    radius = maxR * (0.35f + p * 0.65f),
                    center = center,
                    style = Stroke(width = 4.dp.toPx())
                )
                drawCircle(
                    color = palette.cyan.copy(alpha = (1f - p) * 0.35f),
                    radius = maxR * (0.2f + p * 0.52f),
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = palette.cardTop.copy(alpha = 0.98f),
                border = BorderStroke(1.5.dp, palette.emerald),
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .scale(scaleAnim.value)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(palette.emerald.copy(alpha = 0.18f))
                            .border(1.5.dp, palette.emerald, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = "Root Access Granted",
                            tint = palette.emerald,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    NeonBadge(text = "ROOT VIBE ACTIVE", color = palette.emerald)
                    Text(
                        text = "Root Access Granted",
                        style = MaterialTheme.typography.headlineSmall,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "$rootManagerName • $uidOutput\nDeep kernel firewall, AppOps attribution & binary integrity watch unlocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun JrsShieldLogo(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val palette = LocalJrsPalette.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(palette.cyan.copy(alpha = 0.22f), palette.surface)
                )
            )
            .border(1.dp, palette.glassStrokeBright, RoundedCornerShape(14.dp)),
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
                brush = Brush.linearGradient(listOf(palette.cyan, palette.emerald)),
                style = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
            )
            drawCircle(
                color = palette.cyan,
                radius = w * 0.13f,
                center = Offset(w * 0.5f, h * 0.47f),
                style = Stroke(width = w * 0.065f)
            )
            drawCircle(
                color = palette.emerald,
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
    val palette = LocalJrsPalette.current
    val ringColor = when {
        score >= 80 -> palette.emerald
        score >= 55 -> palette.amber
        else -> palette.crimson
    }
    Box(
        modifier = modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            drawArc(
                color = palette.trackMuted,
                startAngle = -220f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(palette.cyan, ringColor)),
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
                color = palette.textPrimary,
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
    val palette = LocalJrsPalette.current
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("official_website_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.cyan.copy(alpha = 0.14f),
            contentColor = palette.cyan
        ),
        border = BorderStroke(1.dp, palette.cyan.copy(alpha = 0.55f))
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

@Composable
fun severityColor(severity: String): Color {
    val palette = LocalJrsPalette.current
    return when (severity) {
        SeverityLevel.CRITICAL.name -> palette.crimson
        SeverityLevel.HIGH.name -> Color(0xFFEA580C)
        SeverityLevel.MEDIUM.name -> palette.amber
        else -> palette.cyan
    }
}

@Composable
fun trustColor(level: String): Color {
    val palette = LocalJrsPalette.current
    return when (level) {
        TrustLevel.BLOCKED.name -> palette.crimson
        TrustLevel.WATCHLIST.name -> palette.amber
        else -> palette.emerald
    }
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
    val palette = LocalJrsPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        contentColor = palette.textPrimary
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
                        color = palette.textPrimary
                    )
                    Text(
                        text = target.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_quick_actions")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close controls", tint = palette.textSecondary)
                }
            }

            NeonBadge(
                text = if (isRootActive) "ROOT QUICK EXECUTION READY" else "NON-ROOT SAFETY GUIDANCE",
                color = if (isRootActive) palette.emerald else palette.blue
            )

            Text(
                text = "App Trust Classification",
                style = MaterialTheme.typography.labelLarge,
                color = palette.textSecondary
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
                color = palette.textSecondary
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
                        containerColor = palette.amber.copy(alpha = 0.16f),
                        contentColor = palette.amber
                    ),
                    border = BorderStroke(1.dp, palette.amber.copy(alpha = 0.5f))
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
                        containerColor = palette.crimson.copy(alpha = 0.16f),
                        contentColor = palette.crimson
                    ),
                    border = BorderStroke(1.dp, palette.crimson.copy(alpha = 0.5f))
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
                    color = palette.textSecondary
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
                            border = BorderStroke(1.dp, palette.cyan.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = palette.cyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Revoke $perm", color = palette.textPrimary, style = MaterialTheme.typography.labelMedium)
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
                    border = BorderStroke(1.dp, palette.glassStrokeBright)
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = palette.cyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Manage / Revoke App Permissions", color = palette.cyan)
                }
            }

            Text(
                text = if (isRootActive) {
                    "Root commands run directly via verified su bridge."
                } else {
                    "Non-root safety net opens Android system controls when root privileges are absent."
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted
            )
        }
    }
}
