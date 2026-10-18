package Com.ak.testseries.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import Com.ak.testseries.utils.PdfCropper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfCropperScreen(
    uri: Uri,
    initialPage: Int = 0,
    onClose: () -> Unit,
    onCropResult: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var currentPageIndex by remember { mutableStateOf(initialPage) }
    
    var isCropMode by remember { mutableStateOf(false) }
    var cropRect by remember { mutableStateOf(Rect(100f, 100f, 400f, 400f)) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            val count = PdfCropper.getPdfPageCount(context, uri)
            pageCount = count
        }
    }

    LaunchedEffect(currentPageIndex, uri) {
        currentBitmap = null
        withContext(Dispatchers.IO) {
            val bmp = PdfCropper.renderPage(context, uri, currentPageIndex)
            currentBitmap = bmp
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(if (isCropMode) "Crop Image" else "Select PDF Page") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    },
                    actions = {
                        if (isCropMode) {
                            IconButton(onClick = {
                                scope.launch {
                                    val bmp = currentBitmap
                                    if (bmp != null) {
                                        try {
                                            val imgW = bmp.width.toFloat()
                                            val imgH = bmp.height.toFloat()
                                            
                                            val viewW = imageSize.width.toFloat()
                                            val viewH = imageSize.height.toFloat()
                                            
                                            // The image might be smaller than the view if it's scrollable, 
                                            // but since it's FitWidth, width scales perfectly.
                                            if (viewW > 0 && viewH > 0) {
                                                val scaleX = imgW / viewW
                                                val scaleY = imgH / viewH
                                                // If we scale by FillWidth, then scaleX and scaleY are equal!
                                                val scale = scaleX 
                                                
                                                var cropX = (cropRect.left * scale).toInt()
                                                var cropY = (cropRect.top * scale).toInt()
                                                var cropW = (cropRect.width * scale).toInt()
                                                var cropH = (cropRect.height * scale).toInt()
                                                
                                                cropX = cropX.coerceIn(0, bmp.width - 1)
                                                cropY = cropY.coerceIn(0, bmp.height - 1)
                                                cropW = cropW.coerceAtMost(bmp.width - cropX)
                                                cropH = cropH.coerceAtMost(bmp.height - cropY)
                                                
                                                if (cropW > 0 && cropH > 0) {
                                                    val cropped = Bitmap.createBitmap(bmp, cropX, cropY, cropW, cropH)
                                                    onCropResult(cropped)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Check, "Done Crop")
                            }
                        }
                    }
                )
                
                // Page controls
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentPageIndex > 0) { currentPageIndex--; isCropMode = false } },
                        enabled = currentPageIndex > 0
                    ) {
                        Icon(Icons.Default.NavigateBefore, "Prev Page")
                    }
                    Text("Page ${currentPageIndex + 1} of $pageCount", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    IconButton(
                        onClick = { if (currentPageIndex < pageCount - 1) { currentPageIndex++; isCropMode = false } },
                        enabled = currentPageIndex < pageCount - 1
                    ) {
                        Icon(Icons.Default.NavigateNext, "Next Page")
                    }
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentBitmap != null) {
                        // Added vertical scroll here!
                        val scrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .pointerInput(isCropMode) {
                                    if (!isCropMode) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            if (dragAmount.x > 50 && currentPageIndex > 0) {
                                                currentPageIndex--
                                            } else if (dragAmount.x < -50 && currentPageIndex < pageCount - 1) {
                                                currentPageIndex++
                                            }
                                        }
                                    }
                                }
                                .verticalScroll(scrollState, enabled = !isCropMode)
                                .onGloballyPositioned { imageSize = it.size }
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = currentBitmap!!.asImageBitmap(),
                                contentDescription = "PDF Page",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                            
                            if (isCropMode) {
                                val primaryColor = MaterialTheme.colorScheme.primary
                                Canvas(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { alpha = 0.99f }
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val touchPos = change.position
                                                
                                                val cornerThreshold = 80f
                                                val isTopLeft = touchPos.x < cropRect.left + cornerThreshold && touchPos.y < cropRect.top + cornerThreshold
                                                val isBottomRight = touchPos.x > cropRect.right - cornerThreshold && touchPos.y > cropRect.bottom - cornerThreshold
                                                
                                                if (isTopLeft) {
                                                    cropRect = Rect(
                                                        left = (cropRect.left + dragAmount.x).coerceAtMost(cropRect.right - 100f).coerceAtLeast(0f),
                                                        top = (cropRect.top + dragAmount.y).coerceAtMost(cropRect.bottom - 100f).coerceAtLeast(0f),
                                                        right = cropRect.right,
                                                        bottom = cropRect.bottom
                                                    )
                                                } else if (isBottomRight) {
                                                    cropRect = Rect(
                                                        left = cropRect.left,
                                                        top = cropRect.top,
                                                        right = (cropRect.right + dragAmount.x).coerceAtLeast(cropRect.left + 100f).coerceAtMost(size.width.toFloat()),
                                                        bottom = (cropRect.bottom + dragAmount.y).coerceAtLeast(cropRect.top + 100f).coerceAtMost(size.height.toFloat())
                                                    )
                                                } else {
                                                    // Move entire rect
                                                    cropRect = cropRect.translate(dragAmount)
                                                    
                                                    // Clamp to bounds
                                                    if (cropRect.left < 0) cropRect = cropRect.translate(-cropRect.left, 0f)
                                                    if (cropRect.top < 0) cropRect = cropRect.translate(0f, -cropRect.top)
                                                    if (cropRect.right > size.width) cropRect = cropRect.translate(size.width - cropRect.right, 0f)
                                                    if (cropRect.bottom > size.height) cropRect = cropRect.translate(0f, size.height - cropRect.bottom)
                                                }
                                            }
                                        }
                                ) {
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        size = size
                                    )
                                    drawRect(
                                        color = Color.Transparent,
                                        topLeft = Offset(cropRect.left, cropRect.top),
                                        size = Size(cropRect.width, cropRect.height),
                                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                                    )
                                    drawRect(
                                        color = primaryColor,
                                        topLeft = Offset(cropRect.left, cropRect.top),
                                        size = Size(cropRect.width, cropRect.height),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)
                                    )
                                    
                                    // Draw corner handles
                                    drawCircle(
                                        color = primaryColor,
                                        radius = 30f,
                                        center = Offset(cropRect.left, cropRect.top)
                                    )
                                    drawCircle(
                                        color = primaryColor,
                                        radius = 30f,
                                        center = Offset(cropRect.right, cropRect.bottom)
                                    )
                                }
                            }
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                }
                
                if (!isCropMode && currentBitmap != null) {
                    ExtendedFloatingActionButton(
                        onClick = { 
                            isCropMode = true 
                            cropRect = Rect(100f, 100f, (imageSize.width - 100f).coerceAtLeast(200f), 400f)
                        },
                        modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
                        icon = { Icon(Icons.Default.Crop, "Crop") },
                        text = { Text("Activate Crop Tool") },
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
