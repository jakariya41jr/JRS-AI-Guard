package com.example.data.ai

import com.example.data.local.AppTrustEntity
import com.example.data.local.NetworkSessionEntity
import com.example.data.local.SecuritySnapshotEntity
import com.example.data.local.TimelineEventEntity
import com.example.security.CryptoVault
import com.example.security.GuardOperatingMode
import com.example.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"

        private val STRICT_SYSTEM_INSTRUCTION = """
            You are the official in-app security assistant for "JRS AI Guard".
            LANGUAGE RULE (CRITICAL): Always reply in clear, natural, polite Bengali (বাংলা) by default — even if the user writes in Bengali, Banglish, or English — unless the user explicitly asks you to reply in English. Keep technical identifiers (like CAMERA, RECORD_AUDIO, app names, Root/Non-Root) accurate and easy to understand alongside Bengali explanations.
            BRAND PRIVACY RULE: Never output any raw Android package identifier string in your responses. Refer to the app simply as "JRS AI Guard".
            CREATOR RULE: If the user asks who created, built, owns, or developed you or JRS AI Guard, clearly state in Bengali that "Md Jakariya Hasan" (মোঃ জাকারিয়া হাসান) created and developed JRS AI Guard and this AI security assistant.
            STRICT SCOPE RULE: You are ONLY allowed to discuss JRS AI Guard, its security modules, permission timelines, background resource usage, Rooted vs Non-Rooted monitoring, gallery/data collection tracking, malware/APK SHA-256 scanning, system integrity, risk scores, Trust Levels (Trusted/Watchlist/Blocked), sensor lease timers (camera/mic/GPS/wake-locks), clipboard & screen-recording alerts, network sessions/firewalls, install/update history, snapshots/exports, scheduled AI digests, GitHub Releases updates, and Android device security/privacy directly related to this app.
            OFF-TOPIC REFUSAL RULE: If the user asks about unrelated topics (cooking, movies, sports, politics, math homework, general chitchat, etc.), politely refuse in Bengali and state that you only assist with JRS AI Guard device security and privacy monitoring.
            QUOTA & PRIVACY RULE: Keep answers concise, structured with short bullet points when helpful, and grounded in the privacy-filtered telemetry summary provided. Never ask for or output raw API keys or personal media content.
        """.trimIndent()

        private val CREATOR_PATTERNS = listOf(
            "who created", "who made", "who built", "who developed", "who is the developer",
            "creator", "author", "developer of", "who owns",
            "কে বানিয়েছে", "কে বানিয়েছে", "কে তৈরি", "তৈরি করেছে", "বানিয়েছে কে",
            "ডেভেলপার কে", "নির্মাতা কে", "মালিক কে", "কার তৈরি", "কে ডেভেলপ"
        )

        private val GREETING_PATTERNS = listOf(
            "hi", "hello", "hey", "salam", "assalamu", "হ্যালো", "হাই", "সালাম", "কেমন আছো", "কেমন আছেন"
        )

        private val ASCII_WORD_HINTS = listOf(
            "jrs", "guard", "app", "apps", "apk", "su", "root", "gps", "mic", "cpu", "ram",
            "sha", "hash", "api", "log", "data", "scan", "risk", "help", "dark", "light",
            "theme", "audit", "check", "status", "alert", "block", "trust", "freeze",
            "revoke", "force", "update", "install", "screen", "spike", "wake", "feature",
            "summary", "explain", "kemon", "korbo", "bolun", "bolo", "obostha", "amar", "phone"
        )

        private val PHRASE_HINTS = listOf(
            // English security keywords
            "permission", "camera", "microphone", "location", "magisk", "kernelsu", "apatch",
            "non-root", "battery", "network", "firewall", "malware", "virus", "clipboard",
            "overlay", "snapshot", "export", "watchlist", "sideload", "integrity", "selinux",
            "gemini", "digest", "schedule", "github", "release", "timeline", "gallery",
            "exfil", "sensor", "security", "privacy", "monitor", "watchdog", "threat",
            // Bengali security keywords
            "নিরাপত্তা", "সিকিউরিটি", "প্রাইভেসি", "অ্যাপ", "ক্যামেরা", "মাইক", "মাইক্রোফোন",
            "লোকেশন", "অবস্থান", "রুট", "নন-রুট", "ম্যালওয়্যার", "ম্যালওয়্যার", "ভাইরাস",
            "স্ক্যান", "অনুমতি", "পারমিশন", "নেটওয়ার্ক", "নেটওয়ার্ক", "ডেটা", "ডাটা", "ব্যাটারি",
            "সিপিইউ", "র‍্যাম", "স্ন্যাপশট", "রিপোর্ট", "এক্সপোর্ট", "ক্লিপবোর্ড", "স্ক্রিন", "ওভারলে",
            "গ্যালারি", "মিডিয়া", "সেন্সর", "ওয়াক", "অ্যালার্ট", "সতর্কতা", "টাইমলাইন", "ইতিহাস",
            "আপডেট", "ইন্সটল", "সাইডলোড", "রিস্ক", "ঝুঁকি", "ব্লক", "ফ্রিজ", "রিভোক", "ফায়ারওয়াল",
            "ফায়ারওয়াল", "জেআরএস", "গার্ড", "সাহায্য", "চেক", "অডিট", "ডিভাইস", "ফোন", "সিস্টেম",
            "জেমিনি", "এপিআই", "শিডিউল", "গিটহাব", "থিম", "কেমন", "সারাংশ", "বিস্তারিত"
        )

        private val ASCII_WORD_REGEX = Regex(
            "\\b(${ASCII_WORD_HINTS.joinToString("|") { Regex.escape(it) }})\\b",
            RegexOption.IGNORE_CASE
        )

        fun isLikelyOnTopic(userMessage: String): Boolean {
            val lower = userMessage.lowercase().trim()
            if (lower.isEmpty()) return false
            if (CREATOR_PATTERNS.any { lower.contains(it) }) return true
            if (GREETING_PATTERNS.any { lower == it || lower.startsWith("$it ") }) return true
            if (ASCII_WORD_REGEX.containsMatchIn(lower)) return true
            if (PHRASE_HINTS.any { lower.contains(it) }) return true
            return userMessage.trim().length <= 6
        }

        fun isCreatorQuestion(userMessage: String): Boolean {
            val lower = userMessage.lowercase()
            return CREATOR_PATTERNS.any { lower.contains(it) }
        }

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
        val lower = userMessage.lowercase().trim()

        if (isCreatorQuestion(userMessage)) {
            return@withContext GeminiResponseResult(
                success = true,
                text = "JRS AI Guard এবং এই ইন-অ্যাপ AI নিরাপত্তা সহকারী তৈরি ও ডেভেলপ করেছেন Md Jakariya Hasan।"
            )
        }

        if (GREETING_PATTERNS.any { lower == it }) {
            return@withContext GeminiResponseResult(
                success = true,
                text = "হ্যালো! আমি JRS AI Guard নিরাপত্তা সহকারী। আপনার ডিভাইসের পারমিশন টাইমলাইন, রুট/নন-রুট নিরাপত্তা, ক্যামেরা/মাইক অ্যালার্ট, ম্যালওয়্যার স্ক্যান বা নেটওয়ার্ক স্পাইক নিয়ে বাংলায় বা ইংরেজিতে যেকোনো প্রশ্ন করতে পারেন।"
            )
        }

        if (!isLikelyOnTopic(userMessage)) {
            return@withContext GeminiResponseResult(
                success = true,
                isRefusal = true,
                text = "আমি শুধুমাত্র JRS AI Guard-এর নিরাপত্তা সহকারী। আমি শুধু আপনার ডিভাইসের পারমিশন লগ, সেন্সর অ্যালার্ট, রুট/নন-রুট মনিটরিং, ম্যালওয়্যার স্ক্যান, স্ন্যাপশট এবং প্রাইভেসি সুরক্ষা বিষয়েই সাহায্য করতে পারি।"
            )
        }

        val apiKey = cryptoVault.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext GeminiResponseResult(
                success = false,
                text = "আপনার এনক্রিপ্টেড ভল্টে এখনো কোনো Gemini API Key সেট করা নেই। Vault & Schedule ট্যাবে গিয়ে আপনার Gemini API Key সেভ করুন।"
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
                text = "স্বয়ংক্রিয় AI নিরাপত্তা সারাংশ পেতে Encrypted Vault-এ আপনার Gemini API Key সেভ করুন।"
            )
        }
        val prompt = """
            Generate a concise $scheduleLabel security briefing in natural Bengali (বাংলা) with max 5 short bullet points for JRS AI Guard based strictly on this privacy-filtered telemetry snapshot. Highlight top risks, sensor/root alerts, network spikes, and recommended Quick Actions.
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
                statusMessage = "প্রথমে একটি Gemini API Key লিখুন।"
            )
        }

        val testPrompt = "সংক্ষেপে এক লাইনে বাংলায় নিশ্চিত করুন যে JRS AI Guard সফলভাবে সংযুক্ত হয়েছে।"
        val cleanedRequested = requestedModel.trim().removePrefix("models/")

        if (customModelTyped && cleanedRequested.isNotEmpty()) {
            val directRes = callGeminiGenerateContent(apiKey, cleanedRequested, testPrompt)
            if (directRes.success) {
                return@withContext KeyValidationResult(
                    success = true,
                    resolvedModel = cleanedRequested,
                    autoDetected = false,
                    fellBackFromCustom = false,
                    statusMessage = "Test Key সফল হয়েছে ('$cleanedRequested'): ${directRes.text}"
                )
            }

            if (!isModelNotFoundError(directRes)) {
                return@withContext KeyValidationResult(
                    success = false,
                    resolvedModel = cleanedRequested,
                    statusMessage = "'$cleanedRequested' টেস্ট ব্যর্থ হয়েছে: ${directRes.text}"
                )
            }

            val listed = fetchGenerateContentModels(apiKey)
            val available = listed.getOrNull().orEmpty()
            val fallback = selectPreferredModel(available.filterNot { it.equals(cleanedRequested, ignoreCase = true) })
                ?: selectPreferredModel(available)

            if (fallback == null) {
                return@withContext KeyValidationResult(
                    success = false,
                    resolvedModel = cleanedRequested,
                    statusMessage = "কাস্টম মডেল '$cleanedRequested' পাওয়া যায়নি (404), এবং ListModels-এ কোনো বিকল্প মডেল মেলেনি।"
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
                    statusMessage = "কাস্টম '$cleanedRequested' (404) কাজ না করায় স্বয়ংক্রিয়ভাবে '$fallback' নির্বাচন করা হয়েছে (${available.size}টি মডেল পাওয়া গেছে)।"
                )
            } else {
                KeyValidationResult(
                    success = false,
                    resolvedModel = fallback,
                    availableModelsCount = available.size,
                    statusMessage = "কাস্টম '$cleanedRequested' (404) ও ফ্লব্যাক '$fallback' ব্যর্থ: ${fallbackRes.text}"
                )
            }
        }

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
                    statusMessage = "'$fallbackCandidate' দিয়ে সংযুক্ত হয়েছে: ${fallbackRes.text}"
                )
            } else {
                val reason = listed.exceptionOrNull()?.message ?: fallbackRes.text
                KeyValidationResult(
                    success = false,
                    resolvedModel = fallbackCandidate,
                    statusMessage = "মডেল শনাক্তকরণ ব্যর্থ: $reason"
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
                statusMessage = "স্বয়ংক্রিয়ভাবে '$detectedModel' শনাক্ত হয়েছে (${available.size}টি মডেল উপলব্ধ)। Test Key সফল।"
            )
        } else {
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
                        statusMessage = "স্বয়ংক্রিয়ভাবে '$secondBest' নির্বাচিত হয়েছে (${available.size}টি মডেল উপলব্ধ)। Test Key সফল।"
                    )
                }
            }

            KeyValidationResult(
                success = false,
                resolvedModel = detectedModel,
                availableModelsCount = available.size,
                autoDetected = true,
                statusMessage = "'$detectedModel' পাওয়া গেছে, কিন্তু Test Key ব্যর্থ হয়েছে: ${verifyRes.text}"
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
                    statusMessage = "এনক্রিপ্টেড Key সেভ হয়েছে ও '$best' নির্বাচিত হয়েছে (${available.size}টি মডেল)।"
                )
            } else {
                KeyValidationResult(
                    success = false,
                    resolvedModel = currentModel.ifBlank { DEFAULT_MODEL },
                    statusMessage = "এনক্রিপ্টেড Key লোকালি সেভ হয়েছে। মডেল স্বয়ংক্রিয়ভাবে শনাক্ত করা যায়নি (${listed.exceptionOrNull()?.message ?: "offline"})।"
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

    private suspend fun callGeminiGenerateContent(
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
                put("maxOutputTokens", 512)
            })
        }

        val retryableCodes = setOf(429, 500, 502, 503, 504)
        var lastResult = GeminiResponseResult(false, "নেটওয়ার্ক সংযোগ ব্যর্থ হয়েছে।")

        for (attempt in 0..2) {
            if (attempt > 0) {
                delay(650L * attempt)
            }
            val attemptRes = runCatching {
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
                        return@use GeminiResponseResult(
                            success = false,
                            text = "Gemini API ত্রুটি (${resp.code}): $errMsg",
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
                        GeminiResponseResult(false, "Gemini থেকে কোনো উত্তর পাওয়া যায়নি।", httpCode = resp.code)
                    } else {
                        GeminiResponseResult(true, text, httpCode = resp.code)
                    }
                }
            }.getOrElse { ex ->
                GeminiResponseResult(
                    false,
                    "নেটওয়ার্ক সংযোগ সমস্যা হয়েছে: ${ex.message ?: "ইন্টারনেট সংযোগ পরীক্ষা করুন"}"
                )
            }

            if (attemptRes.success) return attemptRes
            lastResult = attemptRes

            val shouldRetry = attemptRes.httpCode in retryableCodes || attemptRes.httpCode == 0
            if (!shouldRetry) return attemptRes
        }
        return lastResult
    }
}
