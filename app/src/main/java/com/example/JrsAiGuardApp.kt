package com.example

import android.app.Application
import com.example.data.ai.AiDigestWorker
import com.example.security.CryptoVault
import com.example.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class JrsAiGuardApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        appScope.launch {
            val vault = CryptoVault(this@JrsAiGuardApp)
            val cfg = vault.configFlow.first()
            AiDigestWorker.schedulePeriodicDigest(this@JrsAiGuardApp, cfg.scheduleFrequency)
        }
    }
}
