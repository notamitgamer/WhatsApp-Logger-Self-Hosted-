package com.whatsapp.logger.data.prefs

interface BackendConfigRepository {
    fun getBackendUrl(): String?
    suspend fun setBackendUrl(url: String)
    fun hasBackendUrl(): Boolean
    suspend fun clearBackendUrl()
}