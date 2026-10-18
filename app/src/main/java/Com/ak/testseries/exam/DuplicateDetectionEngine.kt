package Com.ak.testseries.exam

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max

/**
 * Duplicate Question Detection Engine using Normalized Hashing
 * Prevents saving identical or near-identical option duplicates.
 */
object DuplicateDetectionEngine {

    data class DuplicateResult(
        val exactDuplicate: Boolean,
        val nearDuplicate: Boolean,
        val semanticDuplicate: Boolean,
        val exactMatchHash: String? = null
    )

    fun generateNormalizedHash(questionText: String, options: List<String>): String {
        val normalizedQuestion = normalizeText(questionText)
        val sortedNormalizedOptions = options.map { normalizeText(it) }.sorted().joinToString("|")
        val combined = "$normalizedQuestion##$sortedNormalizedOptions"
        return sha256(combined)
    }

    fun normalizeText(input: String): String {
        return input.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\u0900-\\u097F]"), "") // Keeps alphanumeric + Hindi Devnagari characters
            .trim()
    }

    fun checkDuplicate(
        questionText: String, 
        options: List<String>, 
        existingRecords: Map<String, String> // Map of hash to normalized text
    ): DuplicateResult {
        val exactHash = generateNormalizedHash(questionText, options)
        
        if (existingRecords.containsKey(exactHash)) {
            return DuplicateResult(exactDuplicate = true, nearDuplicate = true, semanticDuplicate = false, exactMatchHash = exactHash)
        }
        
        // Lightweight Near Duplicate check (Jaccard similarity on tokens)
        val newQTokens = normalizeText(questionText).chunked(4).toSet() // Simple n-grams
        
        for ((_, existingNormText) in existingRecords) {
            val existingQTokens = existingNormText.chunked(4).toSet()
            val intersection = newQTokens.intersect(existingQTokens).size
            val union = newQTokens.union(existingQTokens).size
            val similarity = if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
            
            if (similarity > 0.85) { // 85% character n-gram match
                return DuplicateResult(exactDuplicate = false, nearDuplicate = true, semanticDuplicate = false)
            }
        }
        
        return DuplicateResult(exactDuplicate = false, nearDuplicate = false, semanticDuplicate = false)
    }

    private fun sha256(base: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(base.toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString()
        } catch (ex: Exception) {
            base.hashCode().toString()
        }
    }
}
