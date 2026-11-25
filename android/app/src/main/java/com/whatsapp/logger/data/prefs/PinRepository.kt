package com.whatsapp.logger.data.prefs

interface PinRepository {
    fun getPinHash(): String?
    suspend fun setPin(pin: String)
    fun hasPin(): Boolean
    suspend fun clearPin()
    fun verifyPin(pin: String): Boolean
}