package Com.ak.testseries.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

object R2Uploader {

    // ==========================================
    // PLACEHOLDERS: Replace with your actual keys
    // ==========================================
    private const val R2_ACCESS_KEY = "YOUR_R2_ACCESS_KEY"
    private const val R2_SECRET_KEY = "YOUR_R2_SECRET_KEY"
    private const val CLOUDFLARE_ACCOUNT_ID = "YOUR_CLOUDFLARE_ACCOUNT_ID"
    private const val BUCKET_NAME = "YOUR_BUCKET_NAME"
    private const val R2_PUBLIC_DOMAIN_URL = "https://YOUR_PUBLIC_R2_DOMAIN.com"
    // ==========================================

        private val firestore: FirebaseFirestore? by lazy {
        try {
            Com.ak.testseries.data.remote.FirestoreHelper.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    // 1. Initialize S3 Client pointing to Cloudflare R2
    private fun getS3Client(): AmazonS3Client {
        val credentials = BasicAWSCredentials(R2_ACCESS_KEY, R2_SECRET_KEY)
        val s3Client = AmazonS3Client(credentials)
        // R2 Endpoint format: https://<ACCOUNT_ID>.r2.cloudflarestorage.com
        s3Client.setEndpoint("https://${CLOUDFLARE_ACCOUNT_ID}.r2.cloudflarestorage.com")
        // R2 uses auto region
        s3Client.setRegion(Region.getRegion("auto")) 
        return s3Client
    }

    /**
     * Uploads an Image to Cloudflare R2, compresses it to WEBP, and saves URL to Firestore.
     */
    suspend fun uploadImageAndSaveToFirestore(
        context: Context, 
        uri: Uri, 
        collectionName: String, 
        documentId: String, 
        fieldName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val baos = ByteArrayOutputStream()
            // Compress to WEBP 75%
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 75, baos)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 75, baos)
            }
            val data = baos.toByteArray()
            val byteArrayInputStream = ByteArrayInputStream(data)

            val fileName = "images/${UUID.randomUUID()}.webp"
            
            // Setup Object Metadata
            val metadata = ObjectMetadata()
            metadata.contentLength = data.size.toLong()
            metadata.contentType = "image/webp"

            // Upload to R2
            val s3Client = getS3Client()
            val putObjectRequest = PutObjectRequest(BUCKET_NAME, fileName, byteArrayInputStream, metadata)
            s3Client.putObject(putObjectRequest)

            // Generate Public URL
            val publicUrl = "$R2_PUBLIC_DOMAIN_URL/$fileName"

            // Save to Firestore
            firestore?.collection(collectionName)?.document(documentId)
                ?.update(fieldName, publicUrl)
                ?.await()

            return@withContext publicUrl
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Uploads a PDF to Cloudflare R2 and saves URL to Firestore.
     */
    suspend fun uploadPdfAndSaveToFirestore(
        context: Context, 
        uri: Uri, 
        collectionName: String, 
        documentId: String, 
        fieldName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            
            // Read file fully into memory (Note: for very large PDFs, consider saving to a temp file first)
            val bytes = inputStream?.readBytes() ?: return@withContext null
            inputStream.close()
            
            val byteArrayInputStream = ByteArrayInputStream(bytes)

            val fileName = "pdfs/${UUID.randomUUID()}.pdf"

            val metadata = ObjectMetadata()
            metadata.contentLength = bytes.size.toLong()
            metadata.contentType = "application/pdf"

            // Upload to R2
            val s3Client = getS3Client()
            val putObjectRequest = PutObjectRequest(BUCKET_NAME, fileName, byteArrayInputStream, metadata)
            s3Client.putObject(putObjectRequest)

            // Generate Public URL
            val publicUrl = "$R2_PUBLIC_DOMAIN_URL/$fileName"

            // Save to Firestore
            firestore?.collection(collectionName)?.document(documentId)
                ?.update(fieldName, publicUrl)
                ?.await()

            return@withContext publicUrl
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
