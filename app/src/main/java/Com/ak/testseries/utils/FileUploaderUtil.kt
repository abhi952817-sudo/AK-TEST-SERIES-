package Com.ak.testseries.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Uploads app media to Firebase Storage.
 *
 * A local content/cache URI is never returned as a successful cloud URL:
 * such a URI is not accessible to other users or devices. Upload failures
 * therefore return null and callers must keep the media pending/retryable.
 */
object FileUploaderUtil {

    private val storage by lazy {
        try {
            FirebaseStorage.getInstance()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Compresses a chart/diagram and uploads it to chart_images/.
     * Returns a Firebase download URL only after the upload is confirmed.
     */
    suspend fun uploadImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        var originalBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext null
            originalBitmap = inputStream.use { BitmapFactory.decodeStream(it) }
                ?: return@withContext null

            val maxDimension = 1280
            val width = originalBitmap.width
            val height = originalBitmap.height
            scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                val targetWidth: Int
                val targetHeight: Int
                if (width > height) {
                    targetWidth = maxDimension
                    targetHeight = (maxDimension / ratio).toInt()
                } else {
                    targetHeight = maxDimension
                    targetWidth = (maxDimension * ratio).toInt()
                }
                Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            } else {
                originalBitmap
            }

            val baos = ByteArrayOutputStream()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 75, baos)
            } else {
                @Suppress("DEPRECATION")
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 75, baos)
            }
            val compressedBytes = baos.toByteArray()
            if (compressedBytes.isEmpty()) return@withContext null

            val firebaseRef = storage?.reference?.child("chart_images/${UUID.randomUUID()}.webp")
                ?: return@withContext null

            firebaseRef.putBytes(compressedBytes).await()
            firebaseRef.downloadUrl.await().toString()
        } catch (_: Exception) {
            null
        } finally {
            if (scaledBitmap != null && scaledBitmap !== originalBitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }
            if (originalBitmap != null && !originalBitmap.isRecycled) {
                originalBitmap.recycle()
            }
        }
    }

    /**
     * Uploads a PDF to pdfs/. Returns null when cloud upload is unavailable
     * or fails; the original device URI is not a shareable cloud reference.
     */
    suspend fun uploadPdf(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val ref = storage?.reference?.child("pdfs/${UUID.randomUUID()}.pdf")
                ?: return@withContext null
            ref.putFile(uri).await()
            ref.downloadUrl.await().toString()
        } catch (_: Exception) {
            null
        }
    }
}
