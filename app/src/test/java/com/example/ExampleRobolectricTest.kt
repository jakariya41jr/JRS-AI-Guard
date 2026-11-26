package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.TimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read JRS AI Guard app name and package id`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("JRS AI Guard", appName)
        assertEquals("com.JRS.AI.Guard", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `format duration and bytes accurately`() {
        assertEquals("14s", TimeFormatter.formatDuration(14_000L))
        assertEquals("2m 10s", TimeFormatter.formatDuration(130_000L))
        assertTrue(TimeFormatter.formatBytes(1_536L).contains("KB"))
    }
}
