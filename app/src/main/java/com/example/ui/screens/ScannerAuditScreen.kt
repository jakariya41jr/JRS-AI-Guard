package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.export.ExportFormat
import com.example.data.local.AppTrustEntity
import com.example.data.local.ScanCacheEntity
import com.example.data.local.SecuritySnapshotEntity
import com.example.data.telemetry.IntegrityAuditReport
import com.example.data.telemetry.MalwareScanUiResult
import com.example.security.VaultConfig
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonBadge
import com.example.ui.components.severityColor
import com.example.ui.components.trustColor
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.GlassStroke
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.TimeFormatter

@Composable
fun ScannerAuditScreen(
    apps: List<AppTrustEntity>,
    scanHistory: List<ScanCacheEntity>,
    lastScan: MalwareScanUiResult?,
    isScanning: Boolean,
    integrityReport: IntegrityAuditReport?,
    snapshots: List<SecuritySnapshotEntity>,
    vaultConfig: VaultConfig,
    onScanPickedFile: (Uri) -> Unit,
    onScanInstalledApp: (String, String) -> Unit,
    onOpenQuickActions: (String, String) -> Unit,
    onCaptureSnapshot: () -> Unit,
    onShareReport: (ExportFormat) -> Unit,
    onSaveReportFile: (ExportFormat) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("APK Scanner", "App Auditor", "Integrity & Export")

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onScanPickedFile(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = ObsidianDeep,
            contentColor = NeonCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = NeonCyan
                )
            }
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("scanner_tab_$idx")
                )
            }
        }

        when (selectedTab) {
            0 -> MalwareScannerTab(
                apps = apps,
                scanHistory = scanHistory,
                lastScan = lastScan,
                isScanning = isScanning,
                conservedCalls = vaultConfig.apiCallsConservedByCache,
                onPickFile = { filePicker.launch(arrayOf("*/*")) },
                onScanInstalledApp = onScanInstalledApp
            )
            1 -> AppTrustAuditorTab(
                apps = apps,
                onOpenQuickActions = onOpenQuickActions
            )
            2 -> IntegrityAndSnapshotsTab(
                integrityReport = integrityReport,
                snapshots = snapshots,
                onCaptureSnapshot = onCaptureSnapshot,
                onShareReport = onShareReport,
                onSaveReportFile = onSaveReportFile
            )
        }
    }
}

@Composable
private fun MalwareScannerTab(
    apps: List<AppTrustEntity>,
    scanHistory: List<ScanCacheEntity>,
    lastScan: MalwareScanUiResult?,
    isScanning: Boolean,
    conservedCalls: Int,
    onPickFile: () -> Unit,
    onScanInstalledApp: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(accentBorder = NeonCyan.copy(alpha = 0.4f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Free API Malware & APK Scanner",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Computes SHA-256 hash locally, checks 24h cache first, and queries MalwareBazaar free threat intel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    NeonBadge(
                        text = "$conservedCalls QUOTA SAVED",
                        color = CyberEmerald
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onPickFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("pick_file_scan_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = ObsidianVoid
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Select APK or File to Scan")
                }

                if (isScanning) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = NeonCyan,
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                }
            }
        }

        if (lastScan != null) {
            item {
                val res = lastScan.entity
                val color = when (res.verdict) {
                    "MALICIOUS" -> AlertCrimson
                    "SUSPICIOUS" -> AmberPulse
                    else -> CyberEmerald
                }
                GlassCard(accentBorder = color.copy(alpha = 0.6f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Latest Scan Result",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (lastScan.fromCache) {
                                NeonBadge(text = "CACHE HIT", color = NeonCyan)
                            }
                            NeonBadge(text = res.verdict, color = color)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(res.targetName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("SHA-256: ${res.sha256}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${res.sourceApi} • ${res.detectionRatio}",
                        style = MaterialTheme.typography.bodySmall,
                        color = color
                    )
                    if (res.threatFamily.isNotBlank()) {
                        Text(
                            text = "Threat Family: ${res.threatFamily}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AlertCrimson
                        )
                    }
                    Text(
                        text = "Static Heuristic (${res.heuristicScore}/100): ${res.heuristicFlags}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        item {
            Text(
                text = "Scan Installed App APKs",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }

        items(apps.take(15), key = { it.packageName }) { app ->
            GlassCard(
                onClick = { onScanInstalledApp(app.packageName, app.appName) },
                modifier = Modifier.testTag("scan_app_${app.packageName}")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            text = "${app.packageName} • Risk ${app.riskScore}/100",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.BugReport, contentDescription = "Scan APK", tint = NeonCyan)
                }
            }
        }

        if (scanHistory.isNotEmpty()) {
            item {
                Text(
                    text = "Cached SHA-256 Scan History (${scanHistory.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            items(scanHistory.take(15), key = { it.sha256 }) { item ->
                val color = if (item.verdict == "CLEAN") CyberEmerald else AlertCrimson
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            item.targetName,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        NeonBadge(text = item.verdict, color = color)
                    }
                    Text(
                        text = "SHA-256: ${item.sha256.take(24)}… • ${TimeFormatter.formatDateTime(item.scannedAtMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTrustAuditorTab(
    apps: List<AppTrustEntity>,
    onOpenQuickActions: (String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Filter installed & sideloaded apps…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = NeonCyan) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = GlassStroke,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("app_auditor_search")
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                val tColor = trustColor(app.trustLevel)
                val rColor = when {
                    app.riskScore >= 65 -> AlertCrimson
                    app.riskScore >= 40 -> AmberPulse
                    else -> CyberEmerald
                }
                GlassCard(
                    accentBorder = rColor.copy(alpha = 0.35f),
                    onClick = { onOpenQuickActions(app.packageName, app.appName) },
                    modifier = Modifier.testTag("audit_app_${app.packageName}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (app.isSideloaded) {
                                NeonBadge(text = "SIDELOAD", color = AmberPulse)
                            }
                            NeonBadge(text = app.trustLevel, color = tColor)
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Risk Score: ${app.riskScore}/100 • ${app.riskReasons}",
                        style = MaterialTheme.typography.bodySmall,
                        color = rColor
                    )
                    Text(
                        text = "Installer: ${app.installerSource} • Updated: ${TimeFormatter.formatDateTime(app.lastUpdateTimeMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    if (app.newlyAddedPermissions.isNotBlank()) {
                        Text(
                            text = "New Perms on Update: ${app.newlyAddedPermissions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AlertCrimson
                        )
                    }
                    if (app.newlyAddedServices.isNotBlank()) {
                        Text(
                            text = "New Services on Update: ${app.newlyAddedServices}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberPulse
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegrityAndSnapshotsTab(
    integrityReport: IntegrityAuditReport?,
    snapshots: List<SecuritySnapshotEntity>,
    onCaptureSnapshot: () -> Unit,
    onShareReport: (ExportFormat) -> Unit,
    onSaveReportFile: (ExportFormat) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(accentBorder = CyberEmerald.copy(alpha = 0.45f)) {
                Text(
                    text = "System Integrity & Dual-Mode Safety Net",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Score: ${integrityReport?.overallScore ?: "--"}/100 • Mode: ${integrityReport?.rootStatus?.mode?.title ?: "Detecting"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonCyan
                )
                Spacer(Modifier.height(10.dp))
                integrityReport?.checks?.forEach { check ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (check.passed) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                            contentDescription = check.title,
                            tint = if (check.passed) CyberEmerald else AmberPulse,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(check.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(check.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        NeonBadge(
                            text = check.statusLabel,
                            color = if (check.passed) CyberEmerald else AmberPulse
                        )
                    }
                }
            }
        }

        // Snapshot & Export Controls
        item {
            GlassCard(accentBorder = NeonCyan.copy(alpha = 0.4f)) {
                Text(
                    text = "Suspicious Activity Snapshots & Export",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Point-in-time system/network/permission snapshots are AES-256-GCM encrypted in local storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onCaptureSnapshot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("integrity_capture_snapshot"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianVoid)
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Capture Encrypted Snapshot Now")
                }

                Spacer(Modifier.height(10.dp))
                Text("Export / Share Audit Reports", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportFormat.entries.forEach { fmt ->
                        OutlinedButton(
                            onClick = { onSaveReportFile(fmt) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("export_save_${fmt.extension}"),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(fmt.label, color = NeonCyan)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportFormat.entries.forEach { fmt ->
                        OutlinedButton(
                            onClick = { onShareReport(fmt) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("export_share_${fmt.extension}"),
                            border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, tint = CyberEmerald, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share ${fmt.label}", color = CyberEmerald, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Encrypted Security Snapshots (${snapshots.size})",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }

        items(snapshots, key = { it.id }) { snap ->
            val sevColor = severityColor(snap.severity)
            GlassCard(accentBorder = sevColor.copy(alpha = 0.4f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(snap.triggerReason, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
                    NeonBadge(text = snap.severity, color = sevColor)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${TimeFormatter.formatDateTime(snap.capturedAtMs)} • ${snap.operatingMode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
                Text(snap.resourceSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(snap.permissionsSummary, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Text(snap.networkSummary, style = MaterialTheme.typography.labelSmall, color = CyberEmerald)
            }
        }
    }
}
