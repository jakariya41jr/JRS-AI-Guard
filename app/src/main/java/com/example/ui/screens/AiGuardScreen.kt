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
import androidx.compose.material.icons.rounded.Palette
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
import com.example.data.ai.KeyValidationResult
import com.example.data.github.GitHubReleaseInfo
import com.example.data.local.AiInsightEntity
import com.example.data.local.ChatMessageEntity
import com.example.security.AiScheduleFrequency
import com.example.security.AppThemeMode
import com.example.security.VaultConfig
import com.example.ui.components.GlassCard
import com.example.ui.components.JrsShieldLogo
import com.example.ui.components.NeonBadge
import com.example.ui.components.OfficialWebsiteButton
import com.example.ui.theme.LocalJrsPalette
import com.example.util.TimeFormatter

@Composable
fun AiGuardScreen(
    vaultConfig: VaultConfig,
    chatMessages: List<ChatMessageEntity>,
    aiInsights: List<AiInsightEntity>,
    isAiBusy: Boolean,
    keyValidationResult: KeyValidationResult?,
    githubRelease: GitHubReleaseInfo?,
    isCheckingRelease: Boolean,
    onSendChat: (String) -> Unit,
    onClearChat: () -> Unit,
    onSaveGeminiKey: (String, String, Boolean) -> Unit,
    onTestGeminiKey: (String, String, Boolean) -> Unit,
    onUpdateSchedule: (AiScheduleFrequency, Boolean, String) -> Unit,
    onRunDigestNow: () -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onCheckGitHubRelease: (String, String) -> Unit,
    onDownloadReleaseApk: (GitHubReleaseInfo) -> Unit,
    onOpenOfficialWebsite: () -> Unit
) {
    val palette = LocalJrsPalette.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("AI Chat", "Vault & Schedule", "Releases & About")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = palette.navBar,
            contentColor = palette.cyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = palette.cyan
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
                keyValidationResult = keyValidationResult,
                onSaveKey = onSaveGeminiKey,
                onTestKey = onTestGeminiKey,
                onUpdateSchedule = onUpdateSchedule,
                onRunDigestNow = onRunDigestNow
            )
            2 -> ReleasesAndAboutTab(
                vaultConfig = vaultConfig,
                githubRelease = githubRelease,
                isChecking = isCheckingRelease,
                onSetThemeMode = onSetThemeMode,
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
    val palette = LocalJrsPalette.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickPrompts = listOf(
        "আমার ডিভাইসের নিরাপত্তা সারাংশ দেখাও",
        "রুট ও নন-রুট মোড কীভাবে কাজ করে?",
        "ক্যামেরা ও মাইক্রোফোন রিস্ক চেক করো",
        "JRS AI Guard কে তৈরি করেছেন?"
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
                    color = palette.textPrimary
                )
                Text(
                    text = "বাংলা ও English সাপোর্টেড নিরাপত্তা সহকারী",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.cyan
                )
            }
            if (!hasKey) {
                OutlinedButton(
                    onClick = onJumpToVault,
                    modifier = Modifier.testTag("chat_configure_key_btn"),
                    border = BorderStroke(1.dp, palette.amber)
                ) {
                    Text("Set Key", color = palette.amber, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                IconButton(onClick = onClear, modifier = Modifier.testTag("clear_chat_button")) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear chat", tint = palette.textSecondary)
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
                color = palette.cyan,
                trackColor = palette.trackMuted
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
                    GlassCard(accentBorder = palette.violet.copy(alpha = 0.45f)) {
                        Text(
                            text = "JRS AI Guard-এ স্বাগতম! আপনার ফোনের পারমিশন টাইমলাইন, ক্যামেরা/মাইক অ্যালার্ট, রুট বা নন-রুট নিরাপত্তা, ম্যালওয়্যার স্ক্যান, স্ন্যাপশট এবং নেটওয়ার্ক সুরক্ষা নিয়ে বাংলায় বা ইংরেজিতে যেকোনো প্রশ্ন করুন।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val bubbleBg = when {
                        msg.isUser -> palette.cyan.copy(alpha = 0.16f)
                        msg.isRefusal -> palette.crimson.copy(alpha = 0.14f)
                        else -> palette.bubbleElevated
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
                                    text = if (msg.isUser) "আপনি" else "JRS AI Guard",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (msg.isUser) palette.cyan else if (msg.isRefusal) palette.crimson else palette.emerald
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = msg.messageText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textPrimary
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
                placeholder = { Text("বাংলায় বা ইংরেজিতে নিরাপত্তা প্রশ্ন লিখুন…") },
                singleLine = false,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.cyan,
                    unfocusedBorderColor = palette.glassStroke,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary
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
                    .background(palette.cyan.copy(alpha = if (input.isNotBlank()) 0.22f else 0.08f))
                    .testTag("ai_chat_send_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send AI message", tint = palette.cyan)
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
    keyValidationResult: KeyValidationResult?,
    onSaveKey: (String, String, Boolean) -> Unit,
    onTestKey: (String, String, Boolean) -> Unit,
    onUpdateSchedule: (AiScheduleFrequency, Boolean, String) -> Unit,
    onRunDigestNow: () -> Unit
) {
    val palette = LocalJrsPalette.current
    var apiKeyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var modelInput by remember(vaultConfig.geminiModel) { mutableStateOf(vaultConfig.geminiModel) }
    var isCustomModelEdited by remember(vaultConfig.geminiModel) { mutableStateOf(false) }
    var selectedFreq by remember(vaultConfig.scheduleFrequency) { mutableStateOf(vaultConfig.scheduleFrequency) }
    var eventTriggered by remember(vaultConfig.eventTriggeredEnabled) { mutableStateOf(vaultConfig.eventTriggeredEnabled) }

    LaunchedEffect(keyValidationResult?.resolvedModel) {
        val resolved = keyValidationResult?.resolvedModel
        if (!resolved.isNullOrBlank()) {
            modelInput = resolved
            isCustomModelEdited = false
        }
    }

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
                    Text("Encrypted Gemini API Key Vault", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    NeonBadge(
                        text = if (vaultConfig.hasGeminiKey) "ENCRYPTED" else "NOT SET",
                        color = if (vaultConfig.hasGeminiKey) palette.emerald else palette.amber
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (vaultConfig.hasGeminiKey) {
                        "Saved locally with Android Keystore AES-256-GCM (${vaultConfig.maskedGeminiKey})."
                    } else {
                        "Enter your own Gemini API key. It is encrypted locally via Android Keystore and sent directly to Google Gemini."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text(if (vaultConfig.hasGeminiKey) "Saved in vault (enter new key to replace)" else "AIza…") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle key visibility",
                                tint = palette.textSecondary
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
                    onValueChange = {
                        modelInput = it
                        isCustomModelEdited = it.trim() != vaultConfig.geminiModel
                    },
                    label = { Text("Gemini Model ID (Auto-detected on Test/Save)") },
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
                            onSaveKey(apiKeyInput, modelInput, isCustomModelEdited)
                            apiKeyInput = ""
                        },
                        enabled = apiKeyInput.isNotBlank() && !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_gemini_key_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.cyan, contentColor = palette.onAccentButton)
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Encrypted")
                    }
                    OutlinedButton(
                        onClick = { onTestKey(apiKeyInput, modelInput, isCustomModelEdited) },
                        enabled = (apiKeyInput.isNotBlank() || vaultConfig.hasGeminiKey) && !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("test_gemini_key_button")
                    ) {
                        Text("Test Key", color = palette.emerald)
                    }
                }

                if (isBusy) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = palette.cyan)
                }

                if (keyValidationResult != null) {
                    Spacer(Modifier.height(10.dp))
                    val statusColor = when {
                        keyValidationResult.fellBackFromCustom && keyValidationResult.success -> palette.amber
                        keyValidationResult.success -> palette.emerald
                        else -> palette.crimson
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .padding(10.dp)
                            .testTag("gemini_key_status_banner")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (keyValidationResult.success) "Connection Verified" else "Validation Notice",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = statusColor
                                )
                                if (keyValidationResult.resolvedModel.isNotBlank()) {
                                    NeonBadge(
                                        text = keyValidationResult.resolvedModel,
                                        color = statusColor
                                    )
                                }
                            }
                            Text(
                                text = keyValidationResult.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textPrimary
                            )
                        }
                    }
                }
            }
        }

        item {
            GlassCard(accentBorder = palette.violet.copy(alpha = 0.4f)) {
                Text("Automated AI Digest Schedule", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                Text(
                    text = "Uses compact privacy-filtered telemetry summaries to conserve free Gemini API quota.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
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
                                selectedContainerColor = palette.violet.copy(alpha = 0.22f),
                                selectedLabelColor = palette.textPrimary
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
                        Text("Event-Triggered Critical Analysis", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                        Text("Auto-analyze screen-off camera/mic alerts", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                    }
                    Switch(
                        checked = eventTriggered,
                        onCheckedChange = { eventTriggered = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = palette.onAccentButton, checkedTrackColor = palette.cyan)
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
                        colors = ButtonDefaults.buttonColors(containerColor = palette.violet, contentColor = Color.White)
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
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = palette.cyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Run Now", color = palette.cyan)
                    }
                }
            }
        }

        if (insights.isNotEmpty()) {
            item {
                Text("Saved AI Security Digests (${insights.size})", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
            }
            items(insights, key = { it.id }) { insight ->
                GlassCard(accentBorder = palette.emerald.copy(alpha = 0.35f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(insight.title, style = MaterialTheme.typography.titleSmall, color = palette.cyan)
                        Text(TimeFormatter.formatDateTime(insight.createdAtMs), style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(insight.summaryText, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${insight.eventsAnalyzed} events • ${insight.quotaSavedNote}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.emerald
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
    onSetThemeMode: (AppThemeMode) -> Unit,
    onCheckRelease: (String, String) -> Unit,
    onDownloadApk: (GitHubReleaseInfo) -> Unit,
    onOpenOfficialWebsite: () -> Unit
) {
    val palette = LocalJrsPalette.current
    var owner by remember(vaultConfig.githubOwner) { mutableStateOf(vaultConfig.githubOwner) }
    var repo by remember(vaultConfig.githubRepo) { mutableStateOf(vaultConfig.githubRepo) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(accentBorder = palette.cyan.copy(alpha = 0.45f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    JrsShieldLogo(size = 56.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "JRS AI Guard",
                            style = MaterialTheme.typography.headlineSmall,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} • Security Suite",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.cyan
                        )
                        Text(
                            text = "Developed by Md Jakariya Hasan",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
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
            GlassCard(accentBorder = palette.violet.copy(alpha = 0.4f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Palette, contentDescription = null, tint = palette.violet, modifier = Modifier.size(18.dp))
                    Text("Appearance & Theme", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Switch between Dark Obsidian, Light Frost, or follow Android System theme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = vaultConfig.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.cyan.copy(alpha = 0.2f),
                                selectedLabelColor = palette.cyan
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_chip_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        item {
            GlassCard(accentBorder = palette.emerald.copy(alpha = 0.4f)) {
                Text(
                    text = "GitHub Releases APK Updater",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary
                )
                Text(
                    text = "Checks GitHub Releases API for signed APK builds and downloads directly via Android DownloadManager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
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
                    colors = ButtonDefaults.buttonColors(containerColor = palette.emerald, contentColor = palette.onAccentButton)
                ) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Check Latest GitHub Release")
                }

                if (isChecking) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = palette.emerald)
                }

                if (githubRelease != null) {
                    Spacer(Modifier.height(12.dp))
                    NeonBadge(
                        text = if (githubRelease.isUpdateAvailable) "UPDATE AVAILABLE: ${githubRelease.tagName}" else "LATEST: ${githubRelease.tagName}",
                        color = if (githubRelease.isUpdateAvailable) palette.amber else palette.emerald
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(githubRelease.releaseName, style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                    Text(githubRelease.body, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 5)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onDownloadApk(githubRelease) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("download_release_apk_button"),
                        border = BorderStroke(1.dp, palette.cyan)
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = palette.cyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (githubRelease.apkAssetName != null) {
                                "Download ${githubRelease.apkAssetName} (${TimeFormatter.formatBytes(githubRelease.apkSizeBytes)})"
                            } else {
                                "Open GitHub Release Page"
                            },
                            color = palette.cyan
                        )
                    }
                }
            }
        }
    }
}
