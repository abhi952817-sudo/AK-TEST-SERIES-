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
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object FileUploaderUtil {

    private val storage by lazy {
        try {
            FirebaseStorage.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compress bitmap image (charts, figures, diagrams) to max width/height of 1280px
     * and WEBP/JPEG format with 75% quality for optimal size and clarity.
     */
    suspend fun uploadImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) return@withContext null

            // Resize bitmap maintaining aspect ratio (max dimension 1280px for high chart clarity)
            val maxDimension = 1280
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
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

            val fileName = "chart_images/${UUID.randomUUID()}.webp"
            
            // Try uploading to Firebase Storage if available
            val firebaseRef = storage?.reference?.child(fileName)
            if (firebaseRef != null) {
                try {
                    firebaseRef.putBytes(compressedBytes).await()
                    val downloadUrl = firebaseRef.downloadUrl.await()
                    return@withContext downloadUrl.toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Local compressed fallback file if Firebase Storage fails or offline
            val localDir = File(context.cacheDir, "compressed_charts").apply { mkdirs() }
            val localFile = File(localDir, "chart_${UUID.randomUUID()}.webp")
            FileOutputStream(localFile).use { fos ->
                fos.write(compressedBytes)
            }
            return@withContext Uri.fromFile(localFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun uploadPdf(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "pdfs/${UUID.randomUUID()}.pdf"
            val ref = storage?.reference?.child(fileName)
            if (ref != null) {
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await()
                return@withContext downloadUrl.toString()
            }
            return@withContext uri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext uri.toString()
        }
    }
}
