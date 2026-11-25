package com.whatsapp.logger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.whatsapp.logger.data.prefs.AuthTokenRepository
import com.whatsapp.logger.data.prefs.BackendConfigRepository
import com.whatsapp.logger.di.AppContainer

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Check if we have valid credentials to restart sync
            val container = AppContainer.getInstance(context.applicationContext as android.app.Application)
            val authTokenRepository: AuthTokenRepository = container.authTokenRepository
            val backendConfigRepository: BackendConfigRepository = container.backendConfigRepository

            val hasBackendUrl = backendConfigRepository.hasBackendUrl()
            val hasToken = authTokenRepository.hasValidToken()

            if (hasBackendUrl && hasToken) {
                // Start the foreground service for sync
                val serviceIntent = Intent(context, SyncForegroundService::class.java)
                serviceIntent.action = SyncForegroundService.ACTION_START
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d("BootReceiver", "Started sync service on boot")
            }
        }
    }
}