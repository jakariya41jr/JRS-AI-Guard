package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "jrs_secure_vault")

enum class AiScheduleFrequency(val label: String, val intervalHours: Long) {
    OFF("Manual Only", 0L),
    DAILY("Daily Digest", 24L),
    WEEKLY("Weekly Audit", 168L),
    EVENT_TRIGGERED("Event-Triggered + Daily", 24L)
}

data class VaultConfig(
    val hasGeminiKey: Boolean = false,
    val maskedGeminiKey: String = "",
    val geminiModel: String = "gemini-2.5-flash",
    val scheduleFrequency: AiScheduleFrequency = AiScheduleFrequency.DAILY,
    val eventTriggeredEnabled: Boolean = true,
    val watchdogEnabled: Boolean = true,
    val screenOffSensorAlert: Boolean = true,
    val rootRealtimeWatch: Boolean = true,
    val githubOwner: String = "Jakariya41jr",
    val githubRepo: String = "JRS-AI-Guard",
    val apiCallsConservedByCache: Int = 0,
    val lastDigestTimestampMs: Long = 0L
)

class CryptoVault(private val context: Context) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "jrs_ai_guard_aes_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        private val KEY_GEMINI_ENC = stringPreferencesKey("enc_gemini_api_key")
        private val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
        private val KEY_SCHEDULE_FREQ = stringPreferencesKey("ai_schedule_freq")
        private val KEY_EVENT_TRIGGER = booleanPreferencesKey("ai_event_trigger")
        private val KEY_WATCHDOG_ENABLED = booleanPreferencesKey("watchdog_enabled")
        private val KEY_SCREEN_OFF_ALERT = booleanPreferencesKey("screen_off_sensor_alert")
        private val KEY_ROOT_WATCH = booleanPreferencesKey("root_realtime_watch")
        private val KEY_GH_OWNER = stringPreferencesKey("github_owner")
        private val KEY_GH_REPO = stringPreferencesKey("github_repo")
        private val KEY_CACHE_SAVED = intPreferencesKey("api_calls_conserved")
        private val KEY_LAST_DIGEST = longPreferencesKey("last_digest_ms")
    }

    val configFlow: Flow<VaultConfig> = context.vaultDataStore.data.map { prefs ->
        val decrypted = prefs[KEY_GEMINI_ENC]?.let { decryptString(it) }.orEmpty()
        VaultConfig(
            hasGeminiKey = decrypted.isNotBlank(),
            maskedGeminiKey = maskKey(decrypted),
            geminiModel = prefs[KEY_GEMINI_MODEL] ?: "gemini-2.5-flash",
            scheduleFrequency = runCatching {
                AiScheduleFrequency.valueOf(prefs[KEY_SCHEDULE_FREQ] ?: AiScheduleFrequency.DAILY.name)
            }.getOrDefault(AiScheduleFrequency.DAILY),
            eventTriggeredEnabled = prefs[KEY_EVENT_TRIGGER] ?: true,
            watchdogEnabled = prefs[KEY_WATCHDOG_ENABLED] ?: true,
            screenOffSensorAlert = prefs[KEY_SCREEN_OFF_ALERT] ?: true,
            rootRealtimeWatch = prefs[KEY_ROOT_WATCH] ?: true,
            githubOwner = prefs[KEY_GH_OWNER]?.takeIf { it.isNotBlank() } ?: "Jakariya41jr",
            githubRepo = prefs[KEY_GH_REPO]?.takeIf { it.isNotBlank() } ?: "JRS-AI-Guard",
            apiCallsConservedByCache = prefs[KEY_CACHE_SAVED] ?: 0,
            lastDigestTimestampMs = prefs[KEY_LAST_DIGEST] ?: 0L
        )
    }

    suspend fun saveGeminiApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        context.vaultDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(KEY_GEMINI_ENC)
            } else {
                prefs[KEY_GEMINI_ENC] = encryptString(trimmed)
            }
        }
    }

    suspend fun getGeminiApiKey(): String? {
        val prefs = context.vaultDataStore.data.first()
        val enc = prefs[KEY_GEMINI_ENC] ?: return null
        return decryptString(enc)?.takeIf { it.isNotBlank() }
    }

    suspend fun updateSchedule(frequency: AiScheduleFrequency, eventTriggered: Boolean, model: String) {
        context.vaultDataStore.edit { prefs ->
            prefs[KEY_SCHEDULE_FREQ] = frequency.name
            prefs[KEY_EVENT_TRIGGER] = eventTriggered
            if (model.isNotBlank()) prefs[KEY_GEMINI_MODEL] = model.trim()
        }
    }

    suspend fun updateGeminiModel(model: String) {
        val cleaned = model.trim()
        if (cleaned.isEmpty()) return
        context.vaultDataStore.edit { prefs ->
            prefs[KEY_GEMINI_MODEL] = cleaned
        }
    }

    suspend fun updateWatchdogSettings(
        watchdogEnabled: Boolean,
        screenOffAlert: Boolean,
        rootWatch: Boolean
    ) {
        context.vaultDataStore.edit { prefs ->
            prefs[KEY_WATCHDOG_ENABLED] = watchdogEnabled
            prefs[KEY_SCREEN_OFF_ALERT] = screenOffAlert
            prefs[KEY_ROOT_WATCH] = rootWatch
        }
    }

    suspend fun updateGitHubRepo(owner: String, repo: String) {
        context.vaultDataStore.edit { prefs ->
            prefs[KEY_GH_OWNER] = owner.trim().ifEmpty { "Jakariya41jr" }
            prefs[KEY_GH_REPO] = repo.trim().ifEmpty { "JRS-AI-Guard" }
        }
    }

    suspend fun incrementConservedApiCalls() {
        context.vaultDataStore.edit { prefs ->
            prefs[KEY_CACHE_SAVED] = (prefs[KEY_CACHE_SAVED] ?: 0) + 1
        }
    }

    suspend fun recordDigestRun(timestampMs: Long) {
        context.vaultDataStore.edit { prefs ->
            prefs[KEY_LAST_DIGEST] = timestampMs
        }
    }

    fun encryptString(plainText: String): String {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            "$ivB64:$dataB64"
        }.getOrElse {
            Base64.encodeToString(plainText.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decryptString(cipherBlob: String): String? {
        return runCatching {
            val parts = cipherBlob.split(":")
            if (parts.size != 2) {
                return String(Base64.decode(cipherBlob, Base64.NO_WRAP), StandardCharsets.UTF_8)
            }
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(data), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun maskKey(key: String): String {
        if (key.isBlank()) return ""
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••••••••${key.takeLast(4)}"
    }
}
