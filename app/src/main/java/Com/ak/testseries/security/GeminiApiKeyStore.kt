package Com.ak.testseries.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores each signed-in user's own Gemini API key encrypted with an Android Keystore AES key.
 * Plaintext keys are never written to SharedPreferences, Firebase, logs, or backup files.
 */
object GeminiApiKeyStore {
    private const val PREFS_NAME = "gemini_personal_credentials_v1"
    private const val KEY_ALIAS = "akts_gemini_user_key_aes_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    private fun requireUid(uid: String): String {
        require(uid.isNotBlank()) { "A signed-in account is required before saving a Gemini key." }
        return sha256(uid)
    }

    private fun storageKey(uid: String) = "encrypted_key_${requireUid(uid)}"
    private fun timestampKey(uid: String) = "updated_at_${requireUid(uid)}"

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun saveApiKey(context: Context, uid: String, apiKey: String) {
        val cleanKey = apiKey.trim()
        require(cleanKey.isNotEmpty()) { "Gemini API key cannot be empty." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(cleanKey.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        val committed = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(storageKey(uid), encoded)
            .putLong(timestampKey(uid), System.currentTimeMillis())
            .commit()
        check(committed) { "Could not persist the encrypted Gemini API key on this device." }
    }

    fun getApiKey(context: Context, uid: String): String? {
        if (uid.isBlank()) return null
        val encoded = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(storageKey(uid), null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= IV_BYTES) return null
            val iv = combined.copyOfRange(0, IV_BYTES)
            val ciphertext = combined.copyOfRange(IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun hasApiKey(context: Context, uid: String): Boolean = getApiKey(context, uid) != null

    fun lastUpdatedAt(context: Context, uid: String): Long {
        if (uid.isBlank()) return 0L
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(timestampKey(uid), 0L)
    }

    fun deleteApiKey(context: Context, uid: String) {
        if (uid.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(storageKey(uid))
            .remove(timestampKey(uid))
            .apply()
    }

    /**
     * Verifies the supplied credential against the Gemini Models endpoint.
     * The key is sent in the documented x-goog-api-key header, never in the URL.
     */
    suspend fun verifyApiKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Paste your Gemini API key first."))

        var connection: HttpURLConnection? = null
        try {
            connection = (URL("https://generativelanguage.googleapis.com/v1beta/models").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("x-goog-api-key", cleanKey)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
            }
            val code = connection.responseCode
            if (code in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val models = JSONObject(body).optJSONArray("models")
                if (models != null && models.length() > 0) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Google accepted the request but returned no available Gemini models for this key."))
                }
            } else {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val detail = try {
                    JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
                } catch (_: Exception) {
                    ""
                }
                val message = when (code) {
                    400, 401 -> "API key was rejected. Check that you copied the complete key."
                    403 -> "Google denied this key. Check Gemini API access, project permissions, and key restrictions."
                    429 -> "This Google project has reached its current request quota. Try again later or check AI Studio usage limits."
                    else -> "Gemini connection failed (HTTP $code). ${detail.ifBlank { "Check the key and network, then retry." }}"
                }
                Result.failure(IllegalStateException(message))
            }
        } catch (error: Exception) {
            Result.failure(IllegalStateException("Could not reach Google Gemini. Check your internet connection and try again.", error))
        } finally {
            connection?.disconnect()
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
