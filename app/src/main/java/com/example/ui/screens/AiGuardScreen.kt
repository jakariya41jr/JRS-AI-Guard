package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.github.GitHubReleaseInfo
import com.example.data.local.AiInsightEntity
import com.example.data.local.ChatMessageEntity
import com.example.security.AiScheduleFrequency
import com.example.security.VaultConfig
import com.example.ui.components.GlassCard
import com.example.ui.components.JrsShieldLogo
import com.example.ui.components.NeonBadge
import com.example.ui.components.OfficialWebsiteButton
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.GlassStroke
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeural
import com.example.util.TimeFormatter

@Composable
fun AiGuardScreen(
    vaultConfig: VaultConfig,
    chatMessages: List<ChatMessageEntity>,
    aiInsights: List<AiInsightEntity>,
    isAiBusy: Boolean,
    githubRelease: GitHubReleaseInfo?,
    isCheckingRelease: Boolean,
    onSendChat: (String) -> Unit,
    onClearChat: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onTestGeminiKey: (String, String) -> Unit,
    onUpdateSchedule: (AiScheduleFrequency, Boolean, String) -> Unit,
    onRunDigestNow: () -> Unit,
    onCheckGitHubRelease: (String, String) -> Unit,
    onDownloadReleaseApk: (GitHubReleaseInfo) -> Unit,
    onOpenOfficialWebsite: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("AI Chat", "Vault & Schedule", "Releases & About")

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
                    modifier = Modifier.testTag("ai_tab_$idx")
                )
            }
        }

        when (selectedTab) {
            0 -> RestrictedAiChatTab(
                messages = chatMessages,
                hasKey = vaultConfig.hasGeminiKey,
                isBusy = isAiBusy,
                onSend = onSendChat,
                onClear = onClearChat,
                onJumpToVault = { selectedTab = 1 }
            )
            1 -> GeminiVaultAndScheduleTab(
                vaultConfig = vaultConfig,
                insights = aiInsights,
                isBusy = isAiBusy,
                onSaveKey = onSaveGeminiKey,
                onTestKey = onTestGeminiKey,
                onUpdateSchedule = onUpdateSchedule,
                onRunDigestNow = onRunDigestNow
            )
            2 -> ReleasesAndAboutTab(
                vaultConfig = vaultConfig,
                githubRelease = githubRelease,
                isChecking = isCheckingRelease,
                onCheckRelease = onCheckGitHubRelease,
                onDownloadApk = onDownloadReleaseApk,
                onOpenOfficialWebsite = onOpenOfficialWebsite
            )
        }
    }
}

@Composable
private fun RestrictedAiChatTab(
    messages: List<ChatMessageEntity>,
    hasKey: Boolean,
    isBusy: Boolean,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    onJumpToVault: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickPrompts = listOf(
        "Summarize my recent security alerts",
        "Explain Root vs Non-Root mode",
        "Check high-risk permissions & sideloads",
        "Who created JRS AI Guard?"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "JRS AI Guard Assistant",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Strictly scoped to ${BuildConfig.APPLICATION_ID} security & logs",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
            }
            if (!hasKey) {
                OutlinedButton(
                    onClick = onJumpToVault,
                    modifier = Modifier.testTag("chat_configure_key_btn"),
                    border = BorderStroke(1.dp, AmberPulse)
                ) {
                    Text("Set Key", color = AmberPulse, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                IconButton(onClick = onClear, modifier = Modifier.testTag("clear_chat_button")) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear chat", tint = TextSecondary)
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickPrompts) { prompt ->
                AssistChip(
                    onClick = { onSend(prompt) },
                    label = { Text(prompt, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("quick_prompt_${prompt.hashCode()}")
                )
            }
        }

        if (isBusy) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = NeonCyan
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    GlassCard(accentBorder = VioletNeural.copy(alpha = 0.45f)) {
                        Text(
                            text = "Ask me anything about JRS AI Guard (${BuildConfig.APPLICATION_ID}): permission durations, sensor screen-off alerts, root su watch, malware scans, snapshots, or privacy hardening. Off-topic requests are declined.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val bubbleBg = when {
                        msg.isUser -> NeonCyan.copy(alpha = 0.16f)
                        msg.isRefusal -> AlertCrimson.copy(alpha = 0.14f)
                        else -> ObsidianCardElevated
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.86f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(bubbleBg)
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (msg.isUser) "You" else "JRS AI Guard",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (msg.isUser) NeonCyan else if (msg.isRefusal) AlertCrimson else CyberEmerald
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = msg.messageText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask JRS AI Guard about logs, risks…") },
                singleLine = false,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = GlassStroke,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_chat_input")
            )
            IconButton(
                onClick = {
                    val toSend = input
                    input = ""
                    onSend(toSend)
                },
                enabled = input.isNotBlank() && !isBusy,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NeonCyan.copy(alpha = if (input.isNotBlank()) 0.22f else 0.08f))
                    .testTag("ai_chat_send_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send AI message", tint = NeonCyan)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeminiVaultAndScheduleTab(
    vaultConfig: VaultConfig,
    insights: List<AiInsightEntity>,
    isBusy: Boolean,
    onSaveKey: (String) -> Unit,
    onTestKey: (String, String) -> Unit,
    onUpdateSchedule: (AiScheduleFrequency, Boolean, String) -> Unit,
    onRunDigestNow: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var modelInput by remember(vaultConfig.geminiModel) { mutableStateOf(vaultConfig.geminiModel) }
    var selectedFreq by remember(vaultConfig.scheduleFrequency) { mutableStateOf(vaultConfig.scheduleFrequency) }
    var eventTriggered by remember(vaultConfig.eventTriggeredEnabled) { mutableStateOf(vaultConfig.eventTriggeredEnabled) }

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
                    Text("Encrypted Gemini API Key Vault", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    NeonBadge(
                        text = if (vaultConfig.hasGeminiKey) "ENCRYPTED" else "NOT SET",
                        color = if (vaultConfig.hasGeminiKey) CyberEmerald else AmberPulse
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (vaultConfig.hasGeminiKey) {
                        "Saved locally with Android Keystore AES-256-GCM (${vaultConfig.maskedGeminiKey})."
                    } else {
                        "Enter your own Gemini API key. It is encrypted locally via Android Keystore and never transmitted except directly to Google Gemini."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIza…") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle key visibility",
                                tint = TextSecondary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_key_input")
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("Gemini Model ID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_model_input")
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onSaveKey(apiKeyInput)
                            apiKeyInput = ""
                        },
                        enabled = apiKeyInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_gemini_key_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianVoid)
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Encrypted")
                    }
                    OutlinedButton(
                        onClick = { onTestKey(apiKeyInput, modelInput) },
                        enabled = apiKeyInput.isNotBlank() && !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("test_gemini_key_button")
                    ) {
                        Text("Test Key", color = CyberEmerald)
                    }
                }
            }
        }

        item {
            GlassCard(accentBorder = VioletNeural.copy(alpha = 0.4f)) {
                Text("Automated AI Digest Schedule", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    text = "Uses compact privacy-filtered telemetry summaries to conserve free Gemini API quota.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiScheduleFrequency.entries.forEach { freq ->
                        FilterChip(
                            selected = selectedFreq == freq,
                            onClick = { selectedFreq = freq },
                            label = { Text(freq.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VioletNeural.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary
                            ),
                            modifier = Modifier.testTag("schedule_chip_${freq.name.lowercase()}")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Event-Triggered Critical Analysis", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Auto-analyze screen-off camera/mic alerts", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = eventTriggered,
                        onCheckedChange = { eventTriggered = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianVoid, checkedTrackColor = NeonCyan)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onUpdateSchedule(selectedFreq, eventTriggered, modelInput) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("apply_ai_schedule_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = VioletNeural, contentColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Schedule")
                    }
                    OutlinedButton(
                        onClick = onRunDigestNow,
                        enabled = vaultConfig.hasGeminiKey && !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("run_ai_digest_now_button")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Run Now", color = NeonCyan)
                    }
                }
            }
        }

        if (insights.isNotEmpty()) {
            item {
                Text("Saved AI Security Digests (${insights.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            items(insights, key = { it.id }) { insight ->
                GlassCard(accentBorder = CyberEmerald.copy(alpha = 0.35f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(insight.title, style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                        Text(TimeFormatter.formatDateTime(insight.createdAtMs), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(insight.summaryText, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${insight.eventsAnalyzed} events • ${insight.quotaSavedNote}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleasesAndAboutTab(
    vaultConfig: VaultConfig,
    githubRelease: GitHubReleaseInfo?,
    isChecking: Boolean,
    onCheckRelease: (String, String) -> Unit,
    onDownloadApk: (GitHubReleaseInfo) -> Unit,
    onOpenOfficialWebsite: () -> Unit
) {
    var owner by remember(vaultConfig.githubOwner) { mutableStateOf(vaultConfig.githubOwner) }
    var repo by remember(vaultConfig.githubRepo) { mutableStateOf(vaultConfig.githubRepo) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(accentBorder = NeonCyan.copy(alpha = 0.45f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    JrsShieldLogo(size = 56.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "JRS AI Guard",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonCyan
                        )
                        Text(
                            text = "Developed by Md Jakariya Hasan",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                OfficialWebsiteButton(
                    onClick = onOpenOfficialWebsite,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            GlassCard(accentBorder = CyberEmerald.copy(alpha = 0.4f)) {
                Text(
                    text = "GitHub Releases APK Updater",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Checks GitHub Releases API for signed APK builds and downloads directly via Android DownloadManager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = owner,
                        onValueChange = { owner = it },
                        label = { Text("GitHub Owner") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("github_owner_input")
                    )
                    OutlinedTextField(
                        value = repo,
                        onValueChange = { repo = it },
                        label = { Text("Repository") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("github_repo_input")
                    )
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onCheckRelease(owner, repo) },
                    enabled = !isChecking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("check_github_release_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = ObsidianVoid)
                ) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Check Latest GitHub Release")
                }

                if (isChecking) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CyberEmerald)
                }

                if (githubRelease != null) {
                    Spacer(Modifier.height(12.dp))
                    NeonBadge(
                        text = if (githubRelease.isUpdateAvailable) "UPDATE AVAILABLE: ${githubRelease.tagName}" else "LATEST: ${githubRelease.tagName}",
                        color = if (githubRelease.isUpdateAvailable) AmberPulse else CyberEmerald
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(githubRelease.releaseName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(githubRelease.body, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 5)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onDownloadApk(githubRelease) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("download_release_apk_button"),
                        border = BorderStroke(1.dp, NeonCyan)
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (githubRelease.apkAssetName != null) {
                                "Download ${githubRelease.apkAssetName} (${TimeFormatter.formatBytes(githubRelease.apkSizeBytes)})"
                            } else {
                                "Open GitHub Release Page"
                            },
                            color = NeonCyan
                        )
                    }
                }
            }
        }
    }
}
