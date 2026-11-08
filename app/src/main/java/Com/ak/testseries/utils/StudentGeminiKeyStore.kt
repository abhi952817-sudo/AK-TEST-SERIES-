package Com.ak.testseries.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * UID-scoped local Gemini key storage.
 * Plaintext API keys are never written to preferences, files, logs, analytics or Firestore.
 * The AES-256 key is non-exportable and held by AndroidKeyStore.
 */
object StudentGeminiKeyStore {
    private const val PREFS_NAME = "student_gemini_keys_v1"
    private const val KEY_PREFIX = "student_key_"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    fun save(context: Context, uid: String, apiKey: String) {
        require(uid.isNotBlank()) { "Authenticated user ID is required." }
        val normalized = apiKey.trim()
        require(normalized.isNotBlank()) { "Gemini API key cannot be empty." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(uid))
        val encrypted = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(preferenceKey(uid), encoded).commit()
        if (context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(preferenceKey(uid), null) != encoded) {
            throw IllegalStateException("Encrypted Gemini key could not be persisted.")
        }
    }

    fun read(context: Context, uid: String): String? {
        if (uid.isBlank()) return null
        val encoded = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(preferenceKey(uid), null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= IV_LENGTH) return null
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val encrypted = payload.copyOfRange(IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(uid), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            // Fail closed. Do not expose ciphertext or key material in logs.
            null
        }
    }

    fun isConfigured(context: Context, uid: String): Boolean = !read(context, uid).isNullOrBlank()

    fun clear(context: Context, uid: String) {
        if (uid.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(preferenceKey(uid)).commit()
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.deleteEntry(alias(uid))
        } catch (_: Exception) {
            // Ciphertext has already been removed; stale keystore cleanup is best effort.
        }
    }

    private fun getOrCreateSecretKey(uid: String): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val keyAlias = alias(uid)
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun preferenceKey(uid: String): String = KEY_PREFIX + digest(uid).take(32)

    private fun alias(uid: String): String = "akts_gemini_" + digest(uid).take(32)

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
