package com.whatsapp.logger.di

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.whatsapp.logger.data.api.ApiService
import com.whatsapp.logger.data.api.AuthApiService
import com.whatsapp.logger.data.local.AppDatabase
import com.whatsapp.logger.data.local.ChatDao
import com.whatsapp.logger.data.local.MessageDao
import com.whatsapp.logger.data.prefs.BackendConfigRepository
import com.whatsapp.logger.data.prefs.BackendConfigRepositoryImpl
import com.whatsapp.logger.data.prefs.SettingsRepository
import com.whatsapp.logger.data.prefs.SettingsRepositoryImpl
import com.whatsapp.logger.data.prefs.AuthTokenRepository
import com.whatsapp.logger.data.prefs.AuthTokenRepositoryImpl
import com.whatsapp.logger.data.prefs.PinRepository
import com.whatsapp.logger.data.prefs.PinRepositoryImpl
import com.whatsapp.logger.data.repository.ChatRepository
import com.whatsapp.logger.data.repository.ChatRepositoryImpl
import com.whatsapp.logger.data.repository.MessageRepository
import com.whatsapp.logger.data.repository.MessageRepositoryImpl
import com.whatsapp.logger.data.repository.SyncRepository
import com.whatsapp.logger.data.repository.SyncRepositoryImpl
import com.whatsapp.logger.viewmodel.AuthViewModel
import com.whatsapp.logger.viewmodel.ChatListViewModel
import com.whatsapp.logger.viewmodel.ChatViewModel
import com.whatsapp.logger.viewmodel.LockViewModel
import com.whatsapp.logger.viewmodel.LogsViewModel
import com.whatsapp.logger.data.ActivityLog
import com.whatsapp.logger.data.repository.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.whatsapp.logger.viewmodel.SettingsViewModel
import com.whatsapp.logger.viewmodel.SetupViewModel
import com.whatsapp.logger.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.sse.EventSource
import okhttp3.sse.EventSources
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class AppContainer private constructor(val application: Application) {

    // Core
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // Never log URLs in release builds: they carry the auth token as a query parameter
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor(backendHostInterceptor)
            .addInterceptor(logging)
            .build()
    }

    // SSE streams stay open indefinitely, so the read timeout must be disabled
    private val sseClient: OkHttpClient by lazy {
        okHttpClient.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    }

    // The backend URL is chosen by the user at runtime, so every request is
    // pointed at the currently saved URL instead of a fixed Retrofit base URL.
    private val backendHostInterceptor = Interceptor { chain ->
        val original = chain.request()
        val base = backendConfigRepository.getBackendUrl()?.toHttpUrlOrNull()
        val request = if (base != null) {
            val newUrl = original.url.newBuilder()
                .scheme(base.scheme)
                .host(base.host)
                .port(base.port)
                .build()
            original.newBuilder().url(newUrl).build()
        } else {
            original
        }
        chain.proceed(request)
    }

    private val json: Json by lazy { Json { ignoreUnknownKeys = true } }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(application)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            application,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Repositories (Preferences)
    val backendConfigRepository: BackendConfigRepository by lazy { BackendConfigRepositoryImpl(encryptedPrefs) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(encryptedPrefs) }
    val authTokenRepository: AuthTokenRepository by lazy { AuthTokenRepositoryImpl(encryptedPrefs) }
    val pinRepository: PinRepository by lazy { PinRepositoryImpl(encryptedPrefs) }

    // Database
    private val appDatabase: AppDatabase by lazy {
        Room.databaseBuilder(application, AppDatabase::class.java, "whatsapp_logger.db")
            .addMigrations(com.whatsapp.logger.data.local.MIGRATION_1_2, com.whatsapp.logger.data.local.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    val chatDao: ChatDao by lazy { appDatabase.chatDao() }
    val messageDao: MessageDao by lazy { appDatabase.messageDao() }

    // Network
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://placeholder.invalid/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val apiService: ApiService by lazy { retrofit.create(ApiService::class.java) }
    val authApiService: AuthApiService by lazy { retrofit.create(AuthApiService::class.java) }

    private val eventSourceFactory: EventSource.Factory by lazy { EventSources.createFactory(sseClient) }

    // Domain Repositories
    val chatRepository: ChatRepository by lazy {
        ChatRepositoryImpl(chatDao, apiService, authTokenRepository, backendConfigRepository)
    }
    val messageRepository: MessageRepository by lazy {
        MessageRepositoryImpl(messageDao)
    }
    val syncRepository: SyncRepository by lazy {
        SyncRepositoryImpl(eventSourceFactory, apiService, messageDao, chatDao, authTokenRepository, backendConfigRepository, json, application)
    }

    // Mirrors sync state changes into the in-app activity log
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        appScope.launch {
            var lastCount = -1
            syncRepository.syncState.collect { state ->
                when (state) {
                    is SyncState.Connecting -> ActivityLog.info("Connecting to sync stream...")
                    is SyncState.Syncing -> {
                        if (state.messagesReceived == 0) {
                            ActivityLog.info("Connected, waiting for data")
                        } else if (state.messagesReceived != lastCount) {
                            ActivityLog.info("Received messages: ${state.messagesReceived} synced this session")
                        }
                        lastCount = state.messagesReceived
                    }
                    is SyncState.Connected -> ActivityLog.info("Connected, up to date")
                    is SyncState.Error -> ActivityLog.error(
                        "Sync error: ${state.message}" +
                            if (state.retryIn > 0) " (retrying in ${state.retryIn / 1000}s)" else " (not retrying)"
                    )
                    is SyncState.Idle -> ActivityLog.info("Sync idle")
                    is SyncState.Stopped -> ActivityLog.warn("Sync stopped")
                }
            }
        }
    }

    // ViewModel Factory
    val viewModelFactory: ViewModelProvider.Factory by lazy {
        ViewModelFactory(this)
    }

    inner class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when (modelClass) {
                AuthViewModel::class.java -> AuthViewModel(
                    container.authApiService,
                    container.authTokenRepository,
                    container.backendConfigRepository,
                    container.pinRepository,
                    container.syncRepository
                ) as T
                SettingsViewModel::class.java -> SettingsViewModel(
                    container.settingsRepository,
                    container.pinRepository,
                    container.backendConfigRepository,
                    container.authTokenRepository,
                    container.syncRepository,
                    container.application,
                    container.apiService
                ) as T
                ChatListViewModel::class.java -> ChatListViewModel(container.chatRepository, container.syncRepository, container.messageRepository) as T
                LogsViewModel::class.java -> LogsViewModel(container.syncRepository, container.chatRepository, container.apiService, container.authTokenRepository) as T
                ChatViewModel::class.java -> ChatViewModel(
                    container.messageRepository,
                    container.chatRepository
                ) as T
                LockViewModel::class.java -> LockViewModel(
                    container.pinRepository,
                    container.settingsRepository
                ) as T
                SetupViewModel::class.java -> SetupViewModel(
                    container.authApiService,
                    container.backendConfigRepository,
                    container.authTokenRepository
                ) as T
                else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
            }
        }
    }

    companion object {
        @Volatile private var INSTANCE: AppContainer? = null

        fun getInstance(application: Application): AppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppContainer(application).also { INSTANCE = it }
            }
        }
    }
}