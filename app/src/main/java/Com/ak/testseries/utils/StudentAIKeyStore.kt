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
 * Device-local encrypted storage for Student-owned AI provider keys.
 * The AES key is non-exportable and held by AndroidKeyStore.
 * Exclude student_ai_keys_v1.xml from Android backup and device transfer.
 * Call clearAll(context, uid) on sign-out/account switch.
 */
object StudentAIKeyStore {
    enum class Provider(val id: String) {
        GEMINI("gemini"),
        OPENAI("openai"),
        GROQ("groq"),
        ANTHROPIC("anthropic"),
        CEREBRAS("cerebras")
    }

    private const val PREFS_NAME = "student_ai_keys_v1"
    private const val KEY_PREFIX = "student_ai_key_"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    @Synchronized
    fun save(context: Context, uid: String, provider: Provider, apiKey: String) {
        require(uid.isNotBlank()) { "Authenticated user ID is required." }
        val normalized = apiKey.trim()
        require(normalized.isNotBlank()) { "API key cannot be empty." }
        require(normalized.length <= 4096) { "API key is unexpectedly long." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(uid, provider))
        val encryptedPayload = cipher.iv + cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(encryptedPayload, Base64.NO_WRAP)

        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = preferences.edit()
            .putString(preferenceKey(uid, provider), encoded)
            .putLong(savedAtKey(uid, provider), System.currentTimeMillis())
            .commit()
        check(saved && preferences.getString(preferenceKey(uid, provider), null) == encoded) {
            "Encrypted AI key could not be persisted."
        }
    }

    @Synchronized
    fun read(context: Context, uid: String, provider: Provider): String? {
        if (uid.isBlank()) return null
        val encoded = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(preferenceKey(uid, provider), null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= IV_LENGTH) {
                clear(context, uid, provider)
                return null
            }
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val ciphertext = payload.copyOfRange(IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val existingKey = getExistingSecretKey(uid, provider) ?: run {
                clear(context, uid, provider)
                return null
            }
            cipher.init(
                Cipher.DECRYPT_MODE,
                existingKey,
                GCMParameterSpec(TAG_LENGTH_BITS, iv)
            )
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            clear(context, uid, provider)
            null
        }
    }

    fun isConfigured(context: Context, uid: String, provider: Provider): Boolean =
        !read(context, uid, provider).isNullOrBlank()

    fun savedAtMillis(context: Context, uid: String, provider: Provider): Long {
        if (uid.isBlank()) return 0L
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(savedAtKey(uid, provider), 0L)
    }

    @Synchronized
    fun savePreferredModel(context: Context, uid: String, provider: Provider, model: String) {
        require(uid.isNotBlank()) { "Authenticated user ID is required." }
        require(model.isNotBlank() && model.length <= 160) { "A valid model ID is required." }
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(preferredProviderKey(uid), provider.id)
            .putString(preferredModelKey(uid, provider), model.trim())
            .commit()
        check(saved) { "Student AI preferences could not be saved." }
    }

    fun preferredProvider(context: Context, uid: String): Provider? {
        if (uid.isBlank()) return null
        val id = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(preferredProviderKey(uid), null) ?: return null
        return Provider.values().firstOrNull { it.id == id }
    }

    fun preferredModel(context: Context, uid: String, provider: Provider): String? {
        if (uid.isBlank()) return null
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(preferredModelKey(uid, provider), null)
    }

    @Synchronized
    fun clear(context: Context, uid: String, provider: Provider) {
        if (uid.isBlank()) return
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = preferences.edit()
            .remove(preferenceKey(uid, provider))
            .remove(savedAtKey(uid, provider))
            .remove(preferredModelKey(uid, provider))
        if (preferences.getString(preferredProviderKey(uid), null) == provider.id) {
            editor.remove(preferredProviderKey(uid))
        }
        editor.commit()
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.deleteEntry(alias(uid, provider))
        } catch (_: Exception) {
            // Ciphertext has already been removed; stale keystore cleanup is best effort.
        }
    }

    fun clearAll(context: Context, uid: String) {
        if (uid.isBlank()) return
        Provider.values().forEach { provider -> clear(context, uid, provider) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(preferredProviderKey(uid))
            .commit()
    }

    private fun getExistingSecretKey(uid: String, provider: Provider): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.getKey(alias(uid, provider), null) as? SecretKey
    }

    private fun getOrCreateSecretKey(uid: String, provider: Provider): SecretKey {
        getExistingSecretKey(uid, provider)?.let { return it }
        val keyAlias = alias(uid, provider)

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

    private fun preferenceKey(uid: String, provider: Provider): String =
        KEY_PREFIX + digest(uid + "|" + provider.id).take(40)

    private fun savedAtKey(uid: String, provider: Provider): String =
        preferenceKey(uid, provider) + "_saved_at"

    private fun preferredProviderKey(uid: String): String =
        "student_ai_active_provider_" + digest(uid).take(32)

    private fun preferredModelKey(uid: String, provider: Provider): String =
        "student_ai_model_" + digest(uid + "|" + provider.id).take(40)

    private fun alias(uid: String, provider: Provider): String =
        "akts_student_ai_" + digest(uid + "|" + provider.id).take(40)

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
