package com.example.data.ai

import com.example.BuildConfig
import com.example.data.local.AppTrustEntity
import com.example.data.local.NetworkSessionEntity
import com.example.data.local.SecuritySnapshotEntity
import com.example.data.local.TimelineEventEntity
import com.example.security.CryptoVault
import com.example.security.GuardOperatingMode
import com.example.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class GeminiResponseResult(
    val success: Boolean,
    val text: String,
    val isRefusal: Boolean = false,
    val httpCode: Int = 0
)

data class KeyValidationResult(
    val success: Boolean,
    val resolvedModel: String,
    val availableModelsCount: Int = 0,
    val autoDetected: Boolean = false,
    val fellBackFromCustom: Boolean = false,
    val statusMessage: String
)

class GeminiClient(private val cryptoVault: CryptoVault) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"

        private val STRICT_SYSTEM_INSTRUCTION = """
            You are the official in-app security assistant for "JRS AI Guard" (Android package: ${BuildConfig.APPLICATION_ID}).
            CREATOR RULE: If the user asks who created, built, owns, or developed you or JRS AI Guard, you MUST clearly state that "Md Jakariya Hasan" created JRS AI Guard and this AI assistant.
            STRICT SCOPE RULE: You are ONLY allowed to discuss JRS AI Guard, its security modules, permission timelines, background resource usage, Rooted vs Non-Rooted monitoring, gallery/data collection tracking, malware/APK SHA-256 scanning, system integrity, risk scores, Trust Levels (Trusted/Watchlist/Blocked), sensor lease timers (camera/mic/GPS/wake-locks), clipboard & screen-recording alerts, network sessions/firewalls, install/update history, snapshots/exports, scheduled AI digests, GitHub Releases updates, and Android security/privacy guidance directly related to this app.
            OFF-TOPIC REFUSAL RULE: If the user asks about anything unrelated to JRS AI Guard or Android device security inside this app (such as general trivia, cooking, math homework, politics, entertainment, unrelated coding, or general chitchat), politely refuse and explain that you are restricted exclusively to JRS AI Guard security assistance.
            QUOTA & PRIVACY RULE: Keep answers concise, actionable, and grounded in the privacy-filtered telemetry summary provided. Never ask for or output raw API keys or personal media content.
        """.trimIndent()

        private val CREATOR_PATTERNS = listOf(
            "who created", "who made", "who built", "who developed", "who is the developer",
            "creator", "author", "developer of", "who owns"
        )

        private val ON_TOPIC_HINTS = listOf(
            "jrs", "guard", "permission", "camera", "mic", "microphone", "gps", "location",
            "root", "su", "magisk", "non-root", "battery", "cpu", "ram", "network", "data",
            "spike", "firewall", "malware", "apk", "scan", "sha", "hash", "virus", "clipboard",
            "screen", "overlay", "snapshot", "export", "csv", "json", "trust", "watchlist",
            "block", "freeze", "force", "revoke", "install", "update", "sideload", "integrity",
            "selinux", "gemini", "api", "digest", "schedule", "github", "release", "risk",
            "alert", "timeline", "gallery", "exfil", "sensor", "wake", "security", "privacy",
            "app", "monitor", "watchdog", "log", "freeze", "threat", "help", "feature", "status"
        )

        fun selectPreferredModel(models: List<String>): String? {
            if (models.isEmpty()) return null
            val cleanCandidates = models
                .map { it.removePrefix("models/").trim() }
                .filter { it.contains("gemini", ignoreCase = true) }
                .filterNot {
                    val low = it.lowercase()
                    low.contains("embedding") ||
                        low.contains("imagen") ||
                        low.contains("veo") ||
                        low.contains("aqa") ||
                        low.contains("tts") ||
                        low.contains("robotics") ||
                        low.contains("native-audio")
                }
                .distinct()

            val pool = cleanCandidates.ifEmpty { models.map { it.removePrefix("models/").trim() }.distinct() }
            return pool.maxWithOrNull(compareBy<String>(
                { model -> if (model.contains("flash", ignoreCase = true)) 1 else 0 },
                { model -> parseGeminiVersionScore(model) },
                { model ->
                    val low = model.lowercase()
                    when {
                        low.endsWith("-flash") -> 4
                        low.contains("flash") && !low.contains("lite") && !low.contains("exp") && !low.contains("preview") && !low.contains("8b") -> 3
                        low.contains("flash-lite") -> 2
                        !low.contains("exp") && !low.contains("preview") -> 1
                        else -> 0
                    }
                },
                { model -> -model.length }
            ))
        }

        private fun parseGeminiVersionScore(modelId: String): Int {
            val match = Regex("""gemini-(\d+)(?:\.(\d+))?""", RegexOption.IGNORE_CASE).find(modelId)
            val major = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val minor = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
            return major * 100 + minor
        }
    }

    fun buildPrivacyFilteredSummary(
        mode: GuardOperatingMode,
        integrityScore: Int,
        events: List<TimelineEventEntity>,
        apps: List<AppTrustEntity>,
        network: List<NetworkSessionEntity>,
        snapshots: List<SecuritySnapshotEntity>
    ): String {
        val highAlerts = events.filter { it.severity == "HIGH" || it.severity == "CRITICAL" }.take(6)
        val flaggedApps = apps.filter { it.riskScore >= 60 || it.trustLevel != "TRUSTED" }.take(6)
        val spikes = network.filter { it.isSpike }.take(4)

        return buildString {
            appendLine("Mode=${mode.badge}, Integrity=$integrityScore/100, TotalApps=${apps.size}, Snapshots=${snapshots.size}")
            if (highAlerts.isNotEmpty()) {
                appendLine("RecentAlerts: " + highAlerts.joinToString("; ") {
                    "${it.appName}[${it.category}:${it.permissionOrResource} ${TimeFormatter.formatDuration(it.durationMs)} screenOff=${it.isScreenOff}]"
                })
            }
            if (flaggedApps.isNotEmpty()) {
                appendLine("FlaggedApps: " + flaggedApps.joinToString("; ") {
                    "${it.appName}(risk=${it.riskScore}, trust=${it.trustLevel}, sideload=${it.isSideloaded})"
                })
            }
            if (spikes.isNotEmpty()) {
                appendLine("NetSpikes: " + spikes.joinToString("; ") {
                    "${it.appName}(up=${TimeFormatter.formatBytes(it.uploadBytes)}, down=${TimeFormatter.formatBytes(it.downloadBytes)})"
                })
            }
        }.trim()
    }

    suspend fun sendRestrictedChatMessage(
        userMessage: String,
        privacySummary: String,
        model: String
    ): GeminiResponseResult = withContext(Dispatchers.IO) {
        val lower = userMessage.lowercase()

        if (CREATOR_PATTERNS.any { lower.contains(it) }) {
            return@withContext GeminiResponseResult(
                success = true,
                text = "JRS AI Guard (${BuildConfig.APPLICATION_ID}) and this in-app AI security assistant were created and developed by Md Jakariya Hasan."
            )
        }

        val looksOnTopic = ON_TOPIC_HINTS.any { lower.contains(it) }
        if (!looksOnTopic && userMessage.length > 10) {
            return@withContext GeminiResponseResult(
                success = true,
                isRefusal = true,
                text = "I am the JRS AI Guard Assistant. I can only discuss JRS AI Guard (${BuildConfig.APPLICATION_ID}), your device's security logs, permissions, root/non-root monitoring, malware scans, snapshots, and privacy protections."
            )
        }

        val apiKey = cryptoVault.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext GeminiResponseResult(
                success = false,
                text = "No Gemini API key is configured in your encrypted local vault yet. Open the Vault & Schedule tab to save your own Gemini API key securely."
            )
        }

        val promptWithContext = """
            [Privacy-Filtered Device Telemetry Snapshot]
            $privacySummary

            [User Question]
            $userMessage
        """.trimIndent()

        callWithAutoFallback(apiKey, model, promptWithContext)
    }

    suspend fun generateScheduledDigest(
        scheduleLabel: String,
        privacySummary: String,
        model: String
    ): GeminiResponseResult = withContext(Dispatchers.IO) {
        val apiKey = cryptoVault.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext GeminiResponseResult(
                success = false,
                text = "Configure your Gemini API key in the Encrypted Vault to enable automated AI digests."
            )
        }
        val prompt = """
            Generate a concise $scheduleLabel security briefing (max 5 bullet points) for JRS AI Guard based strictly on this privacy-filtered telemetry snapshot. Highlight top risks, sensor/root alerts, network spikes, and recommended Quick Actions.
            $privacySummary
        """.trimIndent()

        callWithAutoFallback(apiKey, model, prompt)
    }

    suspend fun detectAndValidateKey(
        rawKey: String,
        requestedModel: String,
        customModelTyped: Boolean
    ): KeyValidationResult = withContext(Dispatchers.IO) {
        val apiKey = rawKey.trim()
        if (apiKey.isEmpty()) {
            return@withContext KeyValidationResult(
                success = false,
                resolvedModel = requestedModel.ifBlank { DEFAULT_MODEL },
                statusMessage = "Enter a Gemini API key first."
            )
        }

        val testPrompt = "Reply in one short sentence confirming JRS AI Guard created by Md Jakariya Hasan is connected."
        val cleanedRequested = requestedModel.trim().removePrefix("models/")

        // If user manually typed a custom model ID, test that model first
        if (customModelTyped && cleanedRequested.isNotEmpty()) {
            val directRes = callGeminiGenerateContent(apiKey, cleanedRequested, testPrompt)
            if (directRes.success) {
                return@withContext KeyValidationResult(
                    success = true,
                    resolvedModel = cleanedRequested,
                    autoDetected = false,
                    fellBackFromCustom = false,
                    statusMessage = "Test Key succeeded with custom model '$cleanedRequested': ${directRes.text}"
                )
            }

            if (!isModelNotFoundError(directRes)) {
                return@withContext KeyValidationResult(
                    success = false,
                    resolvedModel = cleanedRequested,
                    statusMessage = "Test Key failed on '$cleanedRequested': ${directRes.text}"
                )
            }

            // Custom model returned 404 / unsupported -> query ListModels and fall back automatically
            val listed = fetchGenerateContentModels(apiKey)
            val available = listed.getOrNull().orEmpty()
            val fallback = selectPreferredModel(available.filterNot { it.equals(cleanedRequested, ignoreCase = true) })
                ?: selectPreferredModel(available)

            if (fallback == null) {
                return@withContext KeyValidationResult(
                    success = false,
                    resolvedModel = cleanedRequested,
                    statusMessage = "Custom model '$cleanedRequested' returned 404, and ListModels found no generateContent fallback (${listed.exceptionOrNull()?.message ?: "no models"})."
                )
            }

            val fallbackRes = callGeminiGenerateContent(apiKey, fallback, testPrompt)
            return@withContext if (fallbackRes.success) {
                KeyValidationResult(
                    success = true,
                    resolvedModel = fallback,
                    availableModelsCount = available.size,
                    autoDetected = true,
                    fellBackFromCustom = true,
                    statusMessage = "Custom model '$cleanedRequested' returned 404 (not found). Auto-switched to working model '$fallback' (${available.size} models detected)."
                )
            } else {
                KeyValidationResult(
                    success = false,
                    resolvedModel = fallback,
                    availableModelsCount = available.size,
                    statusMessage = "Custom '$cleanedRequested' (404) and fallback '$fallback' failed: ${fallbackRes.text}"
                )
            }
        }

        // Auto-detect via ListModels endpoint and prioritize newest Flash model
        val listed = fetchGenerateContentModels(apiKey)
        val available = listed.getOrNull().orEmpty()
        val detectedModel = selectPreferredModel(available)

        if (detectedModel == null) {
            val fallbackCandidate = cleanedRequested.ifEmpty { DEFAULT_MODEL }
            val fallbackRes = callGeminiGenerateContent(apiKey, fallbackCandidate, testPrompt)
            return@withContext if (fallbackRes.success) {
                KeyValidationResult(
                    success = true,
                    resolvedModel = fallbackCandidate,
                    autoDetected = false,
                    statusMessage = "Connected using '$fallbackCandidate': ${fallbackRes.text}"
                )
            } else {
                val reason = listed.exceptionOrNull()?.message ?: fallbackRes.text
                KeyValidationResult(
                    success = false,
                    resolvedModel = fallbackCandidate,
                    statusMessage = "Model detection failed: $reason"
                )
            }
        }

        val verifyRes = callGeminiGenerateContent(apiKey, detectedModel, testPrompt)
        return@withContext if (verifyRes.success) {
            KeyValidationResult(
                success = true,
                resolvedModel = detectedModel,
                availableModelsCount = available.size,
                autoDetected = true,
                statusMessage = "Auto-detected working model '$detectedModel' (${available.size} generateContent models available). Test Key passed."
            )
        } else {
            // Try next non-failing candidate from ListModels if top pick errored with 404
            val secondBest = if (isModelNotFoundError(verifyRes)) {
                selectPreferredModel(available.filterNot { it.equals(detectedModel, ignoreCase = true) })
            } else null

            if (secondBest != null) {
                val secondRes = callGeminiGenerateContent(apiKey, secondBest, testPrompt)
                if (secondRes.success) {
                    return@withContext KeyValidationResult(
                        success = true,
                        resolvedModel = secondBest,
                        availableModelsCount = available.size,
                        autoDetected = true,
                        statusMessage = "Auto-detected working model '$secondBest' (${available.size} models available). Test Key passed."
                    )
                }
            }

            KeyValidationResult(
                success = false,
                resolvedModel = detectedModel,
                availableModelsCount = available.size,
                autoDetected = true,
                statusMessage = "Detected '$detectedModel', but Test Key failed: ${verifyRes.text}"
            )
        }
    }

    suspend fun detectBestAvailableModelOnly(rawKey: String, currentModel: String): KeyValidationResult =
        withContext(Dispatchers.IO) {
            val apiKey = rawKey.trim()
            val listed = fetchGenerateContentModels(apiKey)
            val available = listed.getOrNull().orEmpty()
            val best = selectPreferredModel(available)
            if (best != null) {
                KeyValidationResult(
                    success = true,
                    resolvedModel = best,
                    availableModelsCount = available.size,
                    autoDetected = true,
                    statusMessage = "Encrypted key saved & auto-selected '$best' (${available.size} available models)."
                )
            } else {
                KeyValidationResult(
                    success = false,
                    resolvedModel = currentModel.ifBlank { DEFAULT_MODEL },
                    statusMessage = "Encrypted key saved locally. Could not auto-detect models (${listed.exceptionOrNull()?.message ?: "offline"})."
                )
            }
        }

    fun fetchGenerateContentModels(apiKey: String): Result<List<String>> {
        val encodedKey = URLEncoder.encode(apiKey.trim(), "UTF-8")
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$encodedKey"
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty().ifEmpty { "HTTP ${resp.code}" }
                    throw IllegalStateException(errMsg)
                }
                val json = JSONObject(body)
                val modelsArr = json.optJSONArray("models") ?: return@use emptyList()
                val supported = mutableListOf<String>()
                for (i in 0 until modelsArr.length()) {
                    val obj = modelsArr.optJSONObject(i) ?: continue
                    val methods = obj.optJSONArray("supportedGenerationMethods") ?: continue
                    var supportsGenerate = false
                    for (m in 0 until methods.length()) {
                        if (methods.optString(m) == "generateContent") {
                            supportsGenerate = true
                            break
                        }
                    }
                    if (supportsGenerate) {
                        val name = obj.optString("name").removePrefix("models/").trim()
                        if (name.isNotEmpty()) supported += name
                    }
                }
                supported
            }
        }
    }

    private suspend fun callWithAutoFallback(
        apiKey: String,
        model: String,
        prompt: String
    ): GeminiResponseResult {
        val primary = model.ifBlank { DEFAULT_MODEL }.removePrefix("models/")
        val firstRes = callGeminiGenerateContent(apiKey, primary, prompt)
        if (firstRes.success || !isModelNotFoundError(firstRes)) {
            return firstRes
        }
        val available = fetchGenerateContentModels(apiKey).getOrNull().orEmpty()
        val fallback = selectPreferredModel(available.filterNot { it.equals(primary, ignoreCase = true) })
            ?: return firstRes
        val retryRes = callGeminiGenerateContent(apiKey, fallback, prompt)
        if (retryRes.success) {
            cryptoVault.updateGeminiModel(fallback)
        }
        return retryRes
    }

    private fun isModelNotFoundError(res: GeminiResponseResult): Boolean {
        if (res.httpCode == 404) return true
        val lower = res.text.lowercase()
        return lower.contains("404") ||
            lower.contains("not found") ||
            lower.contains("not supported for generatecontent")
    }

    private fun callGeminiGenerateContent(
        apiKey: String,
        model: String,
        userPrompt: String
    ): GeminiResponseResult {
        val safeModel = model.ifBlank { DEFAULT_MODEL }
            .removePrefix("models/")
            .replace(Regex("[^a-zA-Z0-9._-]"), "")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$safeModel:generateContent"

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", STRICT_SYSTEM_INSTRUCTION))
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", userPrompt))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 450)
            })
        }

        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty().ifEmpty { "HTTP ${resp.code}" }
                    return GeminiResponseResult(
                        success = false,
                        text = "Gemini API error (${resp.code}): $errMsg",
                        httpCode = resp.code
                    )
                }
                val json = JSONObject(body)
                val candidates = json.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    .orEmpty()
                    .trim()

                if (text.isEmpty()) {
                    GeminiResponseResult(false, "Gemini returned an empty response.", httpCode = resp.code)
                } else {
                    GeminiResponseResult(true, text, httpCode = resp.code)
                }
            }
        }.getOrElse { ex ->
            GeminiResponseResult(false, "Network request failed: ${ex.message ?: "Unknown error"}")
        }
    }
}
