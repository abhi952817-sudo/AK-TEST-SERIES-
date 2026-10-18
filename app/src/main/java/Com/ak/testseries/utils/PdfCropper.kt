package Com.ak.testseries.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfCropper {

    suspend fun getPdfPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext 0
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    suspend fun renderPage(context: Context, uri: Uri, pageIndex: Int, scale: Float = 2f): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            val renderer = PdfRenderer(pfd)
            val page = renderer.openPage(pageIndex)
            
            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // Fill background with white
            bitmap.eraseColor(android.graphics.Color.WHITE)
            
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            page.close()
            renderer.close()
            pfd.close()
            
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
