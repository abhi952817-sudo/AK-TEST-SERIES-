package Com.ak.testseries.exam
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Universal Exam Management & Question Review Admin UI
 * Features:
 * - 20 Indian Competitive Exam Configurations
 * - PDF Upload & Controlled Batch Processing
 * - Multi-Stage Validation Badges & Quality Scores
 * - Manual Diagram Attachment Integration
 * - Question Approval / Rejection Workflow
 * - Production Test Generation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalExamAdminScreen(
    onBack: () -> Unit,
    isAiEnabled: Boolean = true,
    onOpenPdfCropper: (Uri, Int, (String) -> Unit) -> Unit = { _, _, _ -> },
    onPublishTest: (examName: String, questions: List<UniversalQuestionModel>, durationMinutes: Int) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) } // 0: 20 Exams, 1: Process PDF, 2: Review Questions
    val allExams = remember { mutableStateListOf(*UniversalExamRegistry.getAllExams().toTypedArray()) }
    var selectedExam by remember { mutableStateOf(allExams.first()) }
    var selectedSubjectId by remember { mutableStateOf(selectedExam.stages.firstOrNull()?.papers?.firstOrNull()?.subjects?.firstOrNull()?.subjectId ?: "") }

    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    val extractedQuestions = remember { mutableStateListOf<UniversalQuestionModel>() }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pdfUri = uri
            Toast.makeText(context, "PDF Selected: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Universal 20-Exam Engine", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("AK Test Series • Admin Hub", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("${allExams.size} Exams", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Process PDF", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Review (${extractedQuestions.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> ExamListTab(
                    exams = allExams,
                    onToggleStatus = { examId, active ->
                        UniversalExamRegistry.updateExamStatus(examId, active)
                        val idx = allExams.indexOfFirst { it.examId == examId }
                        if (idx != -1) {
                            allExams[idx] = allExams[idx].copy(active = active)
                        }
                    }
                )
                1 -> ProcessPdfTab(
                    exams = allExams.filter { it.active },
                    selectedExam = selectedExam,
                    onSelectExam = {
                        selectedExam = it
                        selectedSubjectId = it.stages.firstOrNull()?.papers?.firstOrNull()?.subjects?.firstOrNull()?.subjectId ?: ""
                    },
                    selectedSubjectId = selectedSubjectId,
                    onSelectSubject = { selectedSubjectId = it },
                    pdfUri = pdfUri,
                    onPickPdf = { pdfPickerLauncher.launch("application/pdf") },
                    isProcessing = isProcessing,
                    progressStatus = progressStatus,
                    onStartProcessing = {
                        if (!isAiEnabled) {
                            Toast.makeText(context, "AI processing is disabled by Admin (Global Killswitch active).", Toast.LENGTH_LONG).show()
                            return@ProcessPdfTab
                        }
                        if (pdfUri == null) {
                            Toast.makeText(context, "Please select a PDF first", Toast.LENGTH_SHORT).show()
                            return@ProcessPdfTab
                        }
                        isProcessing = true
                        scope.launch {
                            try {
                                val results = UniversalPdfPipeline.processPdfForExam(
                                    context = context,
                                    pdfUri = pdfUri!!,
                                    examConfig = selectedExam,
                                    subjectId = selectedSubjectId,
                                    expectedQuestions = 20,
                                    isAiEnabled = isAiEnabled,
                                    onProgress = { prog ->
                                        progressStatus = prog.statusMessage
                                    }
                                )
                                extractedQuestions.clear()
                                extractedQuestions.addAll(results)
                                isProcessing = false
                                selectedTab = 2 // Move to review
                                Toast.makeText(context, "Extracted ${results.size} questions successfully!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                isProcessing = false
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
                2 -> QuestionReviewTab(
                    questions = extractedQuestions,
                    examConfig = selectedExam,
                    onApprove = { q ->
                        val idx = extractedQuestions.indexOf(q)
                        if (idx != -1) {
                            extractedQuestions[idx] = q.copy(validationStatus = ValidationStatus.APPROVED)
                        }
                    },
                    onReject = { q ->
                        val idx = extractedQuestions.indexOf(q)
                        if (idx != -1) {
                            extractedQuestions[idx] = q.copy(validationStatus = ValidationStatus.REJECTED)
                        }
                    },
                    onAttachDiagram = { q ->
                        if (pdfUri != null) {
                            onOpenPdfCropper(pdfUri!!, maxOf(0, q.sourcePage - 1)) { croppedImageUrl ->
                                val idx = extractedQuestions.indexOf(q)
                                if (idx != -1) {
                                    extractedQuestions[idx] = q.copy(
                                        diagramUrl = croppedImageUrl,
                                        validationStatus = ValidationStatus.APPROVED
                                    )
                                    Toast.makeText(context, "Diagram linked to question!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Source PDF not available for cropping", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPublishAllApproved = { duration ->
                        val approved = extractedQuestions.filter { it.validationStatus == ValidationStatus.APPROVED }
                        if (approved.isEmpty()) {
                            Toast.makeText(context, "No approved questions to publish!", Toast.LENGTH_SHORT).show()
                        } else {
                            onPublishTest(selectedExam.examName, approved, duration)
                            Toast.makeText(context, "Published test with ${approved.size} approved questions!", Toast.LENGTH_LONG).show()
                            onBack()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ExamListTab(
    exams: List<ExamConfig>,
    onToggleStatus: (String, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(exams) { exam ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (exam.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(exam.examName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    exam.category,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Marking: +${exam.stages.firstOrNull()?.papers?.firstOrNull()?.subjects?.firstOrNull()?.sections?.firstOrNull()?.positiveMarks ?: 1.0} / -${exam.stages.firstOrNull()?.papers?.firstOrNull()?.subjects?.firstOrNull()?.sections?.firstOrNull()?.negativeMarks ?: 0.0} • Duration: ${exam.stages.firstOrNull()?.papers?.firstOrNull()?.durationMinutes ?: 60} min",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val stagesText = exam.stages.joinToString(", ") { it.stageName }
                        Text("Stages: $stagesText", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Difficulty: ${exam.difficultyRatio.summary()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Types: ${exam.supportedQuestionTypes.joinToString(", ") { it.name }}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Switch(
                        checked = exam.active,
                        onCheckedChange = { onToggleStatus(exam.examId, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProcessPdfTab(
    exams: List<ExamConfig>,
    selectedExam: ExamConfig,
    onSelectExam: (ExamConfig) -> Unit,
    selectedSubjectId: String,
    onSelectSubject: (String) -> Unit,
    pdfUri: Uri?,
    onPickPdf: () -> Unit,
    isProcessing: Boolean,
    progressStatus: String,
    onStartProcessing: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Step 1: Select Target Exam", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(exams) { exam ->
                    FilterChip(
                        selected = exam.examId == selectedExam.examId,
                        onClick = { onSelectExam(exam) },
                        label = { Text(exam.shortName) }
                    )
                }
            }
        }

        item {
            Text("Step 2: Select Subject / Paper", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            val subjects = selectedExam.stages.firstOrNull()?.papers?.firstOrNull()?.subjects ?: emptyList()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(subjects) { subj ->
                    FilterChip(
                        selected = subj.subjectId == selectedSubjectId,
                        onClick = { onSelectSubject(subj.subjectId) },
                        label = { Text(subj.subjectName) }
                    )
                }
            }
        }

        item {
            Text("Step 3: Source PDF Document", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { onPickPdf() },
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (pdfUri != null) "PDF Selected" else "Tap to Choose Question Paper PDF",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            pdfUri?.lastPathSegment ?: "Supported: Official exam papers, mock tests",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(onClick = onPickPdf) {
                        Text("Browse")
                    }
                }
            }
        }

        item {
            if (isProcessing) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(progressStatus, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            } else {
                Button(
                    onClick = onStartProcessing,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start High-Accuracy Batch Extraction", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun QuestionReviewTab(
    questions: List<UniversalQuestionModel>,
    examConfig: ExamConfig,
    onApprove: (UniversalQuestionModel) -> Unit,
    onReject: (UniversalQuestionModel) -> Unit,
    onAttachDiagram: (UniversalQuestionModel) -> Unit,
    onPublishAllApproved: (Int) -> Unit
) {
    val approvedCount = questions.count { it.validationStatus == ValidationStatus.APPROVED }
    
    val tableDuration = examConfig.stages.firstOrNull()?.papers?.firstOrNull()?.durationMinutes ?: (if (approvedCount > 0) approvedCount else 60)
    var durationMinutes by remember(examConfig) { mutableStateOf(tableDuration.toString()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Review Questions for ${examConfig.examName}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            
            OutlinedTextField(
                value = durationMinutes,
                onValueChange = { durationMinutes = it },
                label = { Text("Timer (Min)") },
                modifier = Modifier.width(100.dp).padding(end = 8.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                singleLine = true
            )

            Button(
                onClick = { onPublishAllApproved(durationMinutes.toIntOrNull() ?: approvedCount) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Publish Test")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (questions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No questions in review. Process a PDF to start.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(questions) { q ->
                    QuestionReviewCard(
                        question = q,
                        onApprove = { onApprove(q) },
                        onReject = { onReject(q) },
                        onAttachDiagram = { onAttachDiagram(q) }
                    )
                }
            }
        }
    }
}

@Composable
fun QuestionReviewCard(
    question: UniversalQuestionModel,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onAttachDiagram: () -> Unit,
    onEdit: () -> Unit = {},
    onRetryValidation: () -> Unit = {},
    onRegenerateExplanation: () -> Unit = {},
    onRemoveDiagram: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Question Type Badge
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        question.questionType.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                // Difficulty Badge
                Surface(
                    color = when (question.difficulty) {
                        DifficultyLevel.EASY -> Color(0xFFE8F5E9)
                        DifficultyLevel.MEDIUM -> Color(0xFFFFF9C4)
                        DifficultyLevel.HARD -> Color(0xFFFFEBEE)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        question.difficulty.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (question.difficulty) {
                            DifficultyLevel.EASY -> Color(0xFF2E7D32)
                            DifficultyLevel.MEDIUM -> Color(0xFFF57F17)
                            DifficultyLevel.HARD -> Color(0xFFC62828)
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Quality Score Badge
                Surface(
                    color = when {
                        question.qualityScore >= 80 -> Color(0xFFE8F5E9)
                        question.qualityScore >= 60 -> Color(0xFFFFF9C4)
                        else -> Color(0xFFFFEBEE)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "Quality: ${question.qualityScore}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            question.qualityScore >= 80 -> Color(0xFF2E7D32)
                            question.qualityScore >= 60 -> Color(0xFFF57F17)
                            else -> Color(0xFFC62828)
                        }
                    )
                }

                // Validation Status Badge
                Surface(
                    color = when (question.validationStatus) {
                        ValidationStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer
                        ValidationStatus.NEEDS_REVIEW -> Color(0xFFFFE0B2)
                        ValidationStatus.REJECTED -> Color(0xFFFFCDD2)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        question.validationStatus.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (question.validationStatus) {
                            ValidationStatus.APPROVED -> MaterialTheme.colorScheme.onPrimaryContainer
                            ValidationStatus.NEEDS_REVIEW -> Color(0xFFE65100)
                            ValidationStatus.REJECTED -> Color(0xFFB71C1C)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (question.generationModel.isNotBlank() || question.sourceName.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (question.generationModel.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Model: ${question.generationModel}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    if (question.sourceName.isNotBlank()) {
                        Text(
                            text = "Source: ${question.sourceName}",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(question.questionText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (question.validationReasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                question.validationReasons.forEach { reason ->
                    Text(reason, fontSize = 11.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Options
            val optionLabels = listOf("A", "B", "C", "D")
            question.options.forEachIndexed { index, opt ->
                val label = optionLabels.getOrElse(index) { "$index" }
                val isCorrect = label == question.correctAnswer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(
                            if (isCorrect) Color(0xFFE8F5E9) else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "($label) $opt",
                        fontSize = 13.sp,
                        fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Explanation: ${question.explanation}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (question.diagramUrl != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cropped Diagram Attached", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve", fontSize = 11.sp)
                }
                
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject", fontSize = 11.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Secondary Actions (Horizontal Scroll)
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedButton(onClick = onRetryValidation, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry Validation", fontSize = 11.sp)
                    }
                }
                item {
                    OutlinedButton(onClick = onRegenerateExplanation, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Regenerate Explanation", fontSize = 11.sp)
                    }
                }
                item {
                    OutlinedButton(onClick = onAttachDiagram, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Attach Diagram", fontSize = 11.sp)
                    }
                }
                if (question.diagramUrl != null) {
                    item {
                        OutlinedButton(
                            onClick = onRemoveDiagram, 
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove Diagram", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
