package com.whatsapp.logger.data.prefs

import android.content.SharedPreferences
import com.whatsapp.logger.data.prefs.PinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom

class PinRepositoryImpl(
    private val encryptedPrefs: SharedPreferences
) : PinRepository {

    private companion object {
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val HASH_ITERATIONS = 100000
        const val HASH_LENGTH = 32
        const val SALT_LENGTH = 16
    }

    override fun getPinHash(): String? {
        return encryptedPrefs.getString(KEY_PIN_HASH, null)
    }

    override suspend fun setPin(pin: String) = withContext(Dispatchers.IO) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        encryptedPrefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, bytesToHex(salt))
            .apply()
    }

    override fun hasPin(): Boolean {
        return encryptedPrefs.contains(KEY_PIN_HASH)
    }

    override suspend fun clearPin() = withContext(Dispatchers.IO) {
        encryptedPrefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .apply()
    }

    override fun verifyPin(pin: String): Boolean {
        val storedHash = getPinHash()
        val saltHex = encryptedPrefs.getString(KEY_PIN_SALT, null)
        if (storedHash == null || saltHex == null) return false
        val salt = hexToBytes(saltHex)
        val computedHash = hashPin(pin, salt)
        return constantTimeEquals(storedHash, computedHash)
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val input = (pin + bytesToHex(salt)).toByteArray()
        var hash = MessageDigest.getInstance("SHA-256").digest(input)
        repeat(HASH_ITERATIONS) {
            hash = MessageDigest.getInstance("SHA-256").digest(hash)
        }
        return bytesToHex(hash.copyOf(HASH_LENGTH))
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}