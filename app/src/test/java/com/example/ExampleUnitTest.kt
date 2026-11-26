package com.example

import com.example.data.ai.GeminiClient
import com.example.util.TimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun package_identity_matches_jrs_ai_guard() {
        assertEquals("com.JRS.AI.Guard", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun duration_and_byte_formatting_is_accurate() {
        assertEquals("14s", TimeFormatter.formatDuration(14_000L))
        assertEquals("2m 10s", TimeFormatter.formatDuration(130_000L))
        assertTrue(TimeFormatter.formatBytes(2_048L).contains("KB"))
    }

    @Test
    fun selectPreferredModel_prioritizes_newest_flash_and_falls_back() {
        val models = listOf(
            "models/gemini-1.5-pro",
            "models/gemini-2.0-flash",
            "models/gemini-2.5-flash",
            "models/gemini-2.5-pro",
            "models/text-embedding-004"
        )
        assertEquals("gemini-2.5-flash", GeminiClient.selectPreferredModel(models))

        val proOnly = listOf("models/gemini-1.5-pro", "models/gemini-2.5-pro")
        assertEquals("gemini-2.5-pro", GeminiClient.selectPreferredModel(proOnly))
    }
}
