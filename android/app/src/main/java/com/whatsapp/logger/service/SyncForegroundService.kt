package com.whatsapp.logger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.whatsapp.logger.MainActivity
import com.whatsapp.logger.R
import com.whatsapp.logger.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SyncForegroundService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "sync_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START_SYNC"
        const val ACTION_STOP = "ACTION_STOP_SYNC"
    }

    private lateinit var syncRepository: com.whatsapp.logger.data.repository.SyncRepository
    private lateinit var authTokenRepository: com.whatsapp.logger.data.prefs.AuthTokenRepository

    private val syncJob = SupervisorJob()
    private val syncScope = CoroutineScope(Dispatchers.IO + syncJob)

    override fun onCreate() {
        super.onCreate()
        val container = AppContainer.getInstance(application)
        syncRepository = container.syncRepository
        authTokenRepository = container.authTokenRepository
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                val token = authTokenRepository.getToken()
                if (token != null) {
                    // Get last synced timestamp from preferences or database
                    val since = 0L // TODO: Get from preferences
                    startForeground(NOTIFICATION_ID, createNotification("Syncing messages..."))
                    syncScope.launch {
                        syncRepository.startGlobalSync(since)
                    }
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                syncRepository.stopSync()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        syncJob.cancel()
        syncRepository.stopSync()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Message Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background message synchronization"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WhatsApp Logger")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}