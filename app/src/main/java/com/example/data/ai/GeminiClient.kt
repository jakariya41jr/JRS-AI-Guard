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
import java.util.concurrent.TimeUnit

data class GeminiResponseResult(
    val success: Boolean,
    val text: String,
    val isRefusal: Boolean = false
)

class GeminiClient(private val cryptoVault: CryptoVault) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    companion object {
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

        // Deterministic creator guard
        if (CREATOR_PATTERNS.any { lower.contains(it) }) {
            return@withContext GeminiResponseResult(
                success = true,
                text = "JRS AI Guard (${BuildConfig.APPLICATION_ID}) and this in-app AI security assistant were created and developed by Md Jakariya Hasan."
            )
        }

        // Local fast-refusal guard to conserve free Gemini API quota on obviously off-topic prompts
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
                text = "No Gemini API key is configured in your encrypted local vault yet. Open the AI Digests & Schedule tab to save your own Gemini API key securely, or review your local telemetry above."
            )
        }

        val promptWithContext = """
            [Privacy-Filtered Device Telemetry Snapshot]
            $privacySummary

            [User Question]
            $userMessage
        """.trimIndent()

        callGeminiGenerateContent(apiKey, model, promptWithContext)
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

        callGeminiGenerateContent(apiKey, model, prompt)
    }

    suspend fun testApiKey(rawKey: String, model: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val res = callGeminiGenerateContent(
            apiKey = rawKey.trim(),
            model = model.ifBlank { "gemini-2.5-flash" },
            userPrompt = "Reply in one short sentence confirming JRS AI Guard created by Md Jakariya Hasan is connected."
        )
        res.success to res.text
    }

    private fun callGeminiGenerateContent(
        apiKey: String,
        model: String,
        userPrompt: String
    ): GeminiResponseResult {
        val safeModel = model.ifBlank { "gemini-2.5-flash" }.replace(Regex("[^a-zA-Z0-9._-]"), "")
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
                    return GeminiResponseResult(false, "Gemini API error: $errMsg")
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
                    GeminiResponseResult(false, "Gemini returned an empty response.")
                } else {
                    GeminiResponseResult(true, text)
                }
            }
        }.getOrElse { ex ->
            GeminiResponseResult(false, "Network request failed: ${ex.message ?: "Unknown error"}")
        }
    }
}
