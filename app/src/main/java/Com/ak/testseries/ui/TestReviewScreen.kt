package Com.ak.testseries.ui

import Com.ak.testseries.stringRes
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.viewmodel.NEETViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestReviewScreen(
    testId: String,
    testTitle: String,
    viewModel: NEETViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // In our SQLite Database we have local questions
    val questionsFlow = remember(testId) { viewModel.getQuestionsForTest(testId) }
    val questions by questionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    
    var selectedQuestionForCrop by remember { mutableStateOf<QuestionEntity?>(null) }
    var showCropperOverlay by remember { mutableStateOf(false) }
    var pdfUriForCrop by remember { mutableStateOf<Uri?>(null) }
    
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pdfUriForCrop = uri
            showCropperOverlay = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Review & Attach Graphs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = testTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (questions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No questions found in this test.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(questions) { question ->
                        QuestionReviewCard(
                            question = question,
                            onAttachGraphClick = {
                                selectedQuestionForCrop = question
                                pdfPickerLauncher.launch("application/pdf")
                            },
                            onSaveQuestion = { updatedQ ->
                                scope.launch {
                                    viewModel.updateQuestion(updatedQ)
                                    Toast.makeText(context, "Question Saved Successfully", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Full Screen PDF Cropper Overlay
        AnimatedVisibility(visible = showCropperOverlay) {
            selectedQuestionForCrop?.let { question ->
                pdfUriForCrop?.let { uri ->
                    PdfCropperOverlay(
                        pdfUri = uri,
                        questionId = question.id,
                        onCropSuccess = { webpUrl ->
                            scope.launch {
                                // Update local database state reactively
                                val updatedQ = question.copy(imageUrl = webpUrl)
                                viewModel.updateQuestion(updatedQ)
                                Toast.makeText(context, "Graph Attached Successfully!", Toast.LENGTH_LONG).show()
                                showCropperOverlay = false
                                selectedQuestionForCrop = null
                                pdfUriForCrop = null
                            }
                        },
                        onDismiss = {
                            showCropperOverlay = false
                            selectedQuestionForCrop = null
                            pdfUriForCrop = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun QuestionReviewCard(
    question: QuestionEntity,
    onAttachGraphClick: () -> Unit,
    onSaveQuestion: (QuestionEntity) -> Unit
) {
    var needsImageState by remember(question) { mutableStateOf(question.needsImage) }
    var imageUrlState by remember(question) { mutableStateOf(question.imageUrl) }
    
    // UI Visibility Conditions
    val showMcqOptions = !needsImageState || (needsImageState && imageUrlState != null)
    val showAddGraphButton = needsImageState && imageUrlState == null
    val showCoilImage = needsImageState && imageUrlState != null
    val canSaveOrPublish = !needsImageState || (needsImageState && imageUrlState != null)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Subject & Section badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = question.subject.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Text(
                    text = question.section,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Question Text
            Text(
                text = "Q. " + question.questionText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Switch to toggle needsImage
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Requires Graph / Diagram",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = needsImageState,
                    onCheckedChange = { checked ->
                        needsImageState = checked
                        if (!checked) {
                            imageUrlState = null
                        }
                    }
                )
            }

            // Image Display
            if (showCoilImage) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .background(Color.White)
                ) {
                    AsyncImage(
                        model = imageUrlState,
                        contentDescription = "Attached Graph",
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                    IconButton(
                        onClick = { imageUrlState = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .background(Color.Red.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // [ + Add Graph from PDF ] Button
            if (showAddGraphButton) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAttachGraphClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[ + Add Graph from PDF ]",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // MCQ Options Visibility Logic
            if (showMcqOptions) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Options:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OptionRowIndicator("A", question.optionA, question.correctAnswer == "A")
                    OptionRowIndicator("B", question.optionB, question.correctAnswer == "B")
                    OptionRowIndicator("C", question.optionC, question.correctAnswer == "C")
                    OptionRowIndicator("D", question.optionD, question.correctAnswer == "D")
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Yellow.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "⚠️ Options are hidden until a graph is attached to prevent invalid preview.",
                        fontSize = 11.sp,
                        color = Color(0xFFC49000),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save / Publish actions
            Button(
                onClick = {
                    onSaveQuestion(question.copy(needsImage = needsImageState, imageUrl = imageUrlState))
                },
                enabled = canSaveOrPublish,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canSaveOrPublish) MaterialTheme.colorScheme.primary else Color.LightGray
                )
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Save", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save & Validate", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun OptionRowIndicator(label: String, text: String, isCorrect: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCorrect) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .border(
                width = 1.dp,
                color = if (isCorrect) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isCorrect) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun PdfCropperOverlay(
    pdfUri: Uri,
    questionId: String,
    onCropSuccess: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var pdfRendererState by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    var isUploading by remember { mutableStateOf(false) }
    
    // Bounding Box / Crop Area selection states
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }
    
    // Native PdfRenderer Loading Logic
    LaunchedEffect(pdfUri) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                if (pfd != null) {
                    val renderer = PdfRenderer(pfd)
                    pdfRendererState = renderer
                    pageCount = renderer.pageCount
                    
                    // Render first page
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        
                        currentPageBitmap = bitmap
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // Page switcher logic
    fun loadPage(index: Int) {
        val renderer = pdfRendererState ?: return
        if (index < 0 || index >= pageCount) return
        scope.launch(Dispatchers.IO) {
            try {
                val page = renderer.openPage(index)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                currentPageBitmap = bitmap
                currentPageIndex = index
                startPoint = null
                currentPoint = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.95f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringRes("Close"), tint = Color.White)
                }
                Text("Select & Crop Graph", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Green)
                } else {
                    Button(
                        onClick = {
                            val originalBitmap = currentPageBitmap
                            val start = startPoint
                            val current = currentPoint
                            if (originalBitmap != null && start != null && current != null) {
                                isUploading = true
                                scope.launch {
                                    // Bounding Box Calculation relative to the UI size
                                    // For robustness, let's capture the portion from Bitmap
                                    // Since we are simulating standard canvas bounds:
                                    try {
                                        val minX = start.x.coerceAtMost(current.x).coerceAtLeast(0f)
                                        val minY = start.y.coerceAtMost(current.y).coerceAtLeast(0f)
                                        val maxX = start.x.coerceAtLeast(current.x).coerceAtMost(originalBitmap.width.toFloat())
                                        val maxY = start.y.coerceAtLeast(current.y).coerceAtMost(originalBitmap.height.toFloat())
                                        
                                        var cropWidth = (maxX - minX).toInt().coerceAtLeast(10)
                                        var cropHeight = (maxY - minY).toInt().coerceAtLeast(10)
                                        
                                        // Ensure crop bounding box lies inside bitmap bounds
                                        val adjustedX = minX.toInt().coerceIn(0, (originalBitmap.width - cropWidth).coerceAtLeast(0))
                                        val adjustedY = minY.toInt().coerceIn(0, (originalBitmap.height - cropHeight).coerceAtLeast(0))
                                        
                                        if (cropWidth + adjustedX > originalBitmap.width) {
                                            cropWidth = originalBitmap.width - adjustedX
                                        }
                                        if (cropHeight + adjustedY > originalBitmap.height) {
                                            cropHeight = originalBitmap.height - adjustedY
                                        }

                                        val croppedBitmap = Bitmap.createBitmap(
                                            originalBitmap,
                                            adjustedX,
                                            adjustedY,
                                            cropWidth,
                                            cropHeight
                                        )
                                        
                                        // Compress and Upload
                                        compressAndUploadToFirebase(
                                            bitmap = croppedBitmap,
                                            questionId = questionId,
                                            onSuccess = { downloadUrl ->
                                                isUploading = false
                                                onCropSuccess(downloadUrl)
                                            },
                                            onFailure = { error ->
                                                isUploading = false
                                                Toast.makeText(context, "Upload Failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        isUploading = false
                                        Toast.makeText(context, "Invalid Crop Box. Try again.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Please drag & select a graph first!", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                    ) {
                        Icon(Icons.Rounded.Crop, contentDescription = "Crop", tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Crop & Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // PDF Canvas & Selector Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.DarkGray)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                startPoint = offset
                                currentPoint = offset
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentPoint = (currentPoint ?: Offset.Zero) + dragAmount
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                currentPageBitmap?.let { bitmap ->
                    Box(modifier = Modifier.wrapContentSize()) {
                        ComposeCanvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                        ) {
                            drawImage(image = bitmap.asImageBitmap())
                            
                            // Draw bounding box selector
                            val s = startPoint
                            val c = currentPoint
                            if (s != null && c != null) {
                                val rect = Rect(
                                    left = s.x.coerceAtMost(c.x),
                                    top = s.y.coerceAtMost(c.y),
                                    right = s.x.coerceAtLeast(c.x),
                                    bottom = s.y.coerceAtLeast(c.y)
                                )
                                drawRect(
                                    color = Color.Green,
                                    topLeft = rect.topLeft,
                                    size = rect.size,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                )
                                drawRect(
                                    color = Color.Green.copy(alpha = 0.2f),
                                    topLeft = rect.topLeft,
                                    size = rect.size
                                )
                            }
                        }
                    }
                } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            // Footer / Swipe / Page controllers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { loadPage(currentPageIndex - 1) },
                    enabled = currentPageIndex > 0
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Prev", tint = if (currentPageIndex > 0) Color.White else Color.Gray)
                }

                Text(
                    text = "Page ${currentPageIndex + 1} of $pageCount",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                IconButton(
                    onClick = { loadPage(currentPageIndex + 1) },
                    enabled = currentPageIndex < pageCount - 1
                ) {
                    Icon(Icons.Rounded.ArrowForward, contentDescription = "Next", tint = if (currentPageIndex < pageCount - 1) Color.White else Color.Gray)
                }
            }
        }
    }
}

/**
 * Suspend function to compress a Bitmap to WebP at 75% quality and upload to Firebase Storage.
 */
suspend fun compressAndUploadToFirebase(
    bitmap: Bitmap,
    questionId: String,
    onSuccess: (String) -> Unit,
    onFailure: (Exception) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val baos = ByteArrayOutputStream()
            // Android 11 (API 30) added lossy WEBP format. Check and support backward compatibility
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 75, baos)
            } else {
                bitmap.compress(Bitmap.CompressFormat.WEBP, 75, baos)
            }
            val data = baos.toByteArray()

                        val storageRef = try { FirebaseStorage.getInstance().reference } catch (e: Exception) { null }
            if (storageRef == null) {
                onFailure(Exception("Firebase Storage not initialized"))
                return@withContext
            }
            val imageRef = storageRef.child("question_images/$questionId.webp")

            val uploadTask = imageRef.putBytes(data)
            uploadTask.addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    val downloadUrl = uri.toString()
                    
                    // Update Firestore document with downloaded image URL
                                        try {
                        FirebaseFirestore.getInstance()
                            .collection("questions")
                            .document(questionId)
                            .update("imageUrl", downloadUrl)
                            .addOnSuccessListener {
                                onSuccess(downloadUrl)
                            }
                            .addOnFailureListener { e ->
                                onFailure(e)
                            }
                    } catch (e: Exception) {
                        onFailure(e)
                    }
                }.addOnFailureListener { e ->
                    onFailure(e)
                }
            }.addOnFailureListener { e ->
                onFailure(e)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onFailure(e)
            }
        }
    }
}


