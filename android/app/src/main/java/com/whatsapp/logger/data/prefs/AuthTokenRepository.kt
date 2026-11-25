package com.whatsapp.logger.data.prefs

interface AuthTokenRepository {
    fun getToken(): String?
    suspend fun setToken(token: String)
    fun hasValidToken(): Boolean
    suspend fun clearToken()
    suspend fun setAuthExpiry(expiry: Long)
    fun getAuthExpiry(): Long
}