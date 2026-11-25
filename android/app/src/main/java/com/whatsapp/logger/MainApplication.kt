package com.whatsapp.logger

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.whatsapp.logger.data.repository.SyncState
import com.whatsapp.logger.di.AppContainer
import com.whatsapp.logger.worker.SyncWorker
import java.util.concurrent.TimeUnit

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val container = AppContainer.getInstance(this)
        scheduleBackgroundSync()
        reconnectWhenOnline(container)
    }

    /** Every ~15 minutes (when online) fetch whatever arrived while the app was closed. */
    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("periodic_sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** When the phone gets internet back, restart the live stream if it had dropped. */
    private fun reconnectWhenOnline(container: AppContainer) {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val state = container.syncRepository.syncState.value
                    val signedIn = container.backendConfigRepository.hasBackendUrl() && container.authTokenRepository.hasValidToken()
                    if (signedIn && (state is SyncState.Error || state is SyncState.Stopped)) {
                        container.syncRepository.startGlobalSync(0)
                    }
                }
            })
        } catch (_: Exception) {
            // Callback limit or missing permission: background worker still covers us
        }
    }
}
