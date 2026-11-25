package com.whatsapp.logger.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.whatsapp.logger.data.prefs.AuthTokenRepository
import com.whatsapp.logger.data.prefs.BackendConfigRepository
import com.whatsapp.logger.data.repository.SyncRepository
import com.whatsapp.logger.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val container = AppContainer.getInstance(applicationContext as android.app.Application)
    private val authTokenRepository: AuthTokenRepository = container.authTokenRepository
    private val backendConfigRepository: BackendConfigRepository = container.backendConfigRepository
    private val syncRepository: SyncRepository = container.syncRepository

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val hasBackendUrl = backendConfigRepository.hasBackendUrl()
        val hasToken = authTokenRepository.hasValidToken()

        if (!hasBackendUrl || !hasToken) {
            return@withContext Result.success()
        }

        try {
            if (syncRepository.performFullResync().isSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}