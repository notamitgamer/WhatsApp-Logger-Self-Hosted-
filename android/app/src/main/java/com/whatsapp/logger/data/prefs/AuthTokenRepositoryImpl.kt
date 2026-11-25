package com.whatsapp.logger.data.prefs

import android.content.SharedPreferences
import com.whatsapp.logger.data.prefs.AuthTokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthTokenRepositoryImpl(
    private val encryptedPrefs: SharedPreferences
) : AuthTokenRepository {

    private companion object {
        const val KEY_TOKEN = "auth_token"
        const val KEY_AUTH_EXPIRY = "auth_expiry"
    }

    override fun getToken(): String? {
        return encryptedPrefs.getString(KEY_TOKEN, null)
    }

    override suspend fun setToken(token: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun hasValidToken(): Boolean {
        val token = getToken()
        val expiry = getAuthExpiry()
        return token != null && expiry > System.currentTimeMillis()
    }

    override suspend fun clearToken() = withContext(Dispatchers.IO) {
        encryptedPrefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_AUTH_EXPIRY)
            .apply()
    }

    override suspend fun setAuthExpiry(expiry: Long) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putLong(KEY_AUTH_EXPIRY, expiry).apply()
    }

    override fun getAuthExpiry(): Long {
        return encryptedPrefs.getLong(KEY_AUTH_EXPIRY, 0)
    }
}