package com.whatsapp.logger.data.prefs

import android.content.SharedPreferences
import com.whatsapp.logger.data.prefs.BackendConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackendConfigRepositoryImpl(
    private val encryptedPrefs: SharedPreferences
) : BackendConfigRepository {

    private companion object {
        const val KEY_BACKEND_URL = "backend_url"
    }

    override fun getBackendUrl(): String? {
        return encryptedPrefs.getString(KEY_BACKEND_URL, null)
    }

    override suspend fun setBackendUrl(url: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putString(KEY_BACKEND_URL, url).apply()
    }

    override fun hasBackendUrl(): Boolean {
        return encryptedPrefs.contains(KEY_BACKEND_URL)
    }

    override suspend fun clearBackendUrl() = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().remove(KEY_BACKEND_URL).apply()
    }
}