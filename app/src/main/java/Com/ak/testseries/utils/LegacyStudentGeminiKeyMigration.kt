package Com.ak.testseries.utils

import android.content.Context
import android.security.keystore.KeyStore
import android.util.Base64
import java.security.KeyStore as JavaKeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * One-time migration/removal helper for the retired Gemini-only Student key store.
 * It does not save new keys and is not an active provider store.
 */
object LegacyStudentGeminiKeyMigration {
    private const val LEGACY_PREFS = "student_gemini_keys_v1"
    private const val LEGACY_KEY_PREFIX = "student_key_"
    private const val LEGACY_ALIAS_PREFIX = "akts_gemini_"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    @Synchronized
    fun migrate(context: Context, uid: String): Boolean {
        if (uid.isBlank()) return false
        val legacyKey = readLegacyKey(context, uid)
        if (legacyKey.isNullOrBlank()) {
            clear(context, uid)
            return false
        }
        return try {
            StudentAIKeyStore.save(context, uid, StudentAIKeyStore.Provider.GEMINI, legacyKey)
            clear(context, uid)
            true
        } catch (_: Exception) {
            // Keep the legacy ciphertext if the canonical store could not persist it.
            false
        }
    }

    @Synchronized
    fun clear(context: Context, uid: String) {
        if (uid.isBlank()) return
        val key = preferenceKey(uid)
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .remove(key + "_saved_at")
            .commit()
        try {
            val keyStore = JavaKeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(alias(uid))
        } catch (_: Exception) {
            // Best-effort removal of an obsolete device-bound alias.
        }
    }

    private fun readLegacyKey(context: Context, uid: String): String? {
        val encoded = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(preferenceKey(uid), null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= IV_LENGTH) return null
            val keyStore = JavaKeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val secretKey = keyStore.getKey(alias(uid), null) as? SecretKey ?: return null
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val ciphertext = payload.copyOfRange(IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun preferenceKey(uid: String): String =
        LEGACY_KEY_PREFIX + digest(uid).take(32)

    private fun alias(uid: String): String =
        LEGACY_ALIAS_PREFIX + digest(uid).take(32)

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
