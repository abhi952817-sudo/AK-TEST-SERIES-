package Com.ak.testseries.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import Com.ak.testseries.R
import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.exam.ExamScoringEngine
import Com.ak.testseries.ui.theme.*
import Com.ak.testseries.viewmodel.AppLanguage
import Com.ak.testseries.viewmodel.NEETViewModel
import kotlinx.coroutines.launch

/**
 * Realistic CBT/OMR Exam Simulation UI
 * Supports all competitive examinations with dynamic countdown timers,
 * horizontal swipeable question pagers, and interactive OMR question palettes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamSimulationScreen(
    viewModel: NEETViewModel
) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val activeTest by viewModel.activeTest.collectAsStateWithLifecycle()
    val questions by viewModel.activeQuestions.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentQuestionIndex.collectAsStateWithLifecycle()
    val answers by viewModel.userAnswers.collectAsStateWithLifecycle()
    val marked by viewModel.markedForReview.collectAsStateWithLifecycle()
    val visited by viewModel.visitedQuestions.collectAsStateWithLifecycle()
    val timerRemaining by viewModel.examTimeRemaining.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isHindi = selectedLanguage == AppLanguage.HINDI

    // Fullscreen Immersive Mode for authentic CBT feel
    val activity = context as? Activity
    DisposableEffect(Unit) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var showPaletteSheet by remember { mutableStateOf(false) }
    var showPauseConfirmDialog by remember { mutableStateOf(false) }
    var showSubmitConfirmDialog by remember { mutableStateOf(false) }
    var showCalculatorDialog by remember { mutableStateOf(false) }
    var fontScale by remember { mutableFloatStateOf(1.0f) }

    // Intercept back button to prompt pause/save
    BackHandler {
        showPauseConfirmDialog = true
    }

    if (activeTest == null || questions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LogoLoader()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isHindi) "परीक्षा पत्र तैयार किया जा रहा है..." else "Loading exam paper...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        return
    }

    val currentTest = activeTest!!
    val targetExamName = currentTest.targetExam.ifBlank { "NEET UG" }
    val (posMark, negMark) = remember(targetExamName) {
        ExamScoringEngine.getMarkingScheme(targetExamName)
    }

    // Pager State for horizontal swipeable questions
    val pagerState = rememberPagerState(
        initialPage = currentIndex.coerceIn(0, questions.size - 1)
    ) {
        questions.size
    }

    // Sync Pager swipes with ViewModel
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentIndex) {
            viewModel.jumpToQuestion(pagerState.currentPage)
        }
        val qId = questions.getOrNull(pagerState.currentPage)?.id
        if (qId != null) {
            viewModel.markQuestionVisited(qId)
        }
    }

    // Sync ViewModel index changes with Pager
    LaunchedEffect(currentIndex) {
        if (pagerState.currentPage != currentIndex && currentIndex in 0 until questions.size) {
            pagerState.animateScrollToPage(currentIndex)
        }
    }

    // Total test duration calculation
    val totalDurationSeconds = remember(currentTest, questions.size) {
        if (currentTest.durationMinutes > 0) {
            currentTest.durationMinutes * 60L
        } else {
            questions.size * 60L
        }
    }

    val userPhone = remember(userProfile) {
        userProfile.phoneNumber.ifBlank {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.phoneNumber ?: ""
            } catch (e: Exception) { "" }
        }
    }

    // Wrap with Anti-Piracy Watermark
    com.ak.pdfextractor.ui.components.AntiPiracyWatermarkWrapper(
        phoneNumber = userPhone,
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                ExamSimulationTopBar(
                    test = currentTest,
                    targetExamName = targetExamName,
                    timerRemaining = timerRemaining,
                    totalDurationSeconds = totalDurationSeconds,
                    isHindi = isHindi,
                    fontScale = fontScale,
                    answeredCount = answers.size,
                    totalQuestions = questions.size,
                    onToggleLanguage = { toHindi ->
                        viewModel.setLanguage(if (toHindi) AppLanguage.HINDI else AppLanguage.ENGLISH)
                    },
                    onFontScaleToggle = {
                        fontScale = when (fontScale) {
                            1.0f -> 1.15f
                            1.15f -> 1.3f
                            1.3f -> 0.9f
                            else -> 1.0f
                        }
                    },
                    onCalculatorClick = { showCalculatorDialog = true },
                    onPauseClick = { showPauseConfirmDialog = true },
                    onSubmitClick = { showSubmitConfirmDialog = true },
                    onPaletteClick = { showPaletteSheet = true }
                )
            },
            bottomBar = {
                ExamSimulationBottomBar(
                    questions = questions,
                    currentIndex = currentIndex,
                    answers = answers,
                    marked = marked,
                    isHindi = isHindi,
                    onPrevious = {
                        if (currentIndex > 0) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(currentIndex - 1)
                            }
                        }
                    },
                    onSaveAndNext = {
                        if (currentIndex < questions.size - 1) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(currentIndex + 1)
                            }
                        } else {
                            showSubmitConfirmDialog = true
                        }
                    },
                    onSaveAndMarkForReview = {
                        val currentQ = questions.getOrNull(currentIndex)
                        if (currentQ != null) {
                            if (!marked.contains(currentQ.id)) {
                                viewModel.toggleMarkForReview(currentQ.id)
                            }
                            if (currentIndex < questions.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentIndex + 1)
                                }
                            } else {
                                showSubmitConfirmDialog = true
                            }
                        }
                    },
                    onMarkForReviewAndNext = {
                        val currentQ = questions.getOrNull(currentIndex)
                        if (currentQ != null) {
                            if (!marked.contains(currentQ.id)) {
                                viewModel.toggleMarkForReview(currentQ.id)
                            }
                            if (currentIndex < questions.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(currentIndex + 1)
                                }
                            } else {
                                showSubmitConfirmDialog = true
                            }
                        }
                    },
                    onClearResponse = {
                        val currentQ = questions.getOrNull(currentIndex)
                        if (currentQ != null) {
                            viewModel.clearResponse(currentQ.id)
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Section & Subject Navigation Strip + Quick Palette
                ExamNavigationHeader(
                    questions = questions,
                    currentIndex = currentIndex,
                    answers = answers,
                    marked = marked,
                    visited = visited,
                    isHindi = isHindi,
                    onSelectQuestion = { targetIndex ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetIndex)
                        }
                    },
                    onOpenFullPalette = { showPaletteSheet = true }
                )

                // Horizontal Swipeable Question Pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { pageIndex ->
                    val question = questions[pageIndex]
                    val (qPosMark, qNegMark) = remember(targetExamName, question) {
                        Com.ak.testseries.exam.UniversalExamRegistry.getMarkingSchemeForQuestion(
                            examName = targetExamName,
                            sectionIdOrName = question.section,
                            subjectName = question.subject,
                            questionTypeStr = question.questionType
                        )
                    }
                    ExamQuestionPageView(
                        question = question,
                        questionIndex = pageIndex,
                        totalQuestions = questions.size,
                        selectedAnswer = answers[question.id] ?: "",
                        isMarked = marked.contains(question.id),
                        posMark = qPosMark,
                        negMark = qNegMark,
                        isHindi = isHindi,
                        fontScale = fontScale,
                        onSelectAnswer = { opt ->
                            val success = viewModel.selectAnswer(question.id, opt)
                            if (!success) {
                                val limit = Com.ak.testseries.exam.UniversalExamRegistry.getMaximumAttemptQuestionsCountForSection(
                                    examName = targetExamName,
                                    sectionNameOrId = question.section,
                                    subjectName = question.subject
                                ) ?: 10
                                val limitMsg = if (isHindi) {
                                    "${question.subject.ifBlank { "इस विषय" }} के ${question.section} में अधिकतम $limit प्रश्नों की अनुमति है!"
                                } else {
                                    "Attempt limit reached: Max $limit questions allowed in ${question.subject.ifBlank { "this section" }}"
                                }
                                Toast.makeText(
                                    context,
                                    limitMsg,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onReportError = {
                            viewModel.reportQuestionError(
                                question.questionText,
                                question.optionA,
                                question.optionB,
                                question.optionC,
                                question.optionD,
                                question.correctAnswer
                            )
                        }
                    )
                }
            }
        }
    }

    // Full CBT Question Palette Bottom Sheet
    if (showPaletteSheet) {
        ExamPaletteBottomSheet(
            questions = questions,
            currentIndex = currentIndex,
            answers = answers,
            marked = marked,
            visited = visited,
            isHindi = isHindi,
            userProfile = userProfile,
            timerRemaining = timerRemaining,
            onDismiss = { showPaletteSheet = false },
            onSelectQuestion = { targetIndex ->
                showPaletteSheet = false
                coroutineScope.launch {
                    pagerState.animateScrollToPage(targetIndex)
                }
            }
        )
    }

    // Virtual Calculator Dialog
    if (showCalculatorDialog) {
        CbtVirtualCalculatorDialog(
            isHindi = isHindi,
            onDismiss = { showCalculatorDialog = false }
        )
    }

    // Pause Confirmation Dialog
    if (showPauseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPauseConfirmDialog = false },
            icon = { Icon(Icons.Rounded.PauseCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
            title = { Text(if (isHindi) "परीक्षा रोकें व सेव करें?" else "Pause & Save Test?") },
            text = {
                Text(
                    if (isHindi)
                        "क्या आप इस टेस्ट को यहीं सुरक्षित करना चाहते हैं? अगली बार आपका समय और उत्तर वहीं से शुरू होंगे।"
                    else
                        "Do you want to pause and save your test progress? You can resume exactly where you left off at any time."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPauseConfirmDialog = false
                    viewModel.pauseTest()
                }) {
                    Text(if (isHindi) "हाँ, सेव करें" else "Yes, Pause")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPauseConfirmDialog = false }) {
                    Text(if (isHindi) "जारी रखें" else "Continue Test")
                }
            }
        )
    }

    // Submit Exam Confirmation Dialog
    if (showSubmitConfirmDialog) {
        val answeredCount = answers.size
        val markedCount = marked.size
        val unansweredCount = questions.size - answeredCount

        AlertDialog(
            onDismissRequest = { showSubmitConfirmDialog = false },
            icon = { Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
            title = { Text(if (isHindi) "परीक्षा सबमिट करें?" else "Submit Test Attempt?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isHindi) "क्या आप निश्चित रूप से टेस्ट सबमिट करना चाहते हैं?" else "Are you sure you want to submit your test paper?",
                        fontWeight = FontWeight.SemiBold
                    )
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isHindi) "कुल प्रश्न:" else "Total Questions:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${questions.size}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isHindi) "हल किए गए:" else "Answered:", color = PaletteCorrect)
                        Text("$answeredCount", fontWeight = FontWeight.Bold, color = PaletteCorrect)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isHindi) "अनुत्तरित:" else "Unanswered:", color = PaletteWrong)
                        Text("$unansweredCount", fontWeight = FontWeight.Bold, color = PaletteWrong)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isHindi) "रिव्यू हेतु चिह्नित:" else "Marked for Review:", color = PaletteMarked)
                        Text("$markedCount", fontWeight = FontWeight.Bold, color = PaletteMarked)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirmDialog = false
                        viewModel.submitTest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isHindi) "अंतिम सबमिट करें" else "Final Submit", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSubmitConfirmDialog = false }) {
                    Text(if (isHindi) "वापस जाएं" else "Back to Test")
                }
            }
        )
    }
}

/**
 * High-precision dynamic countdown timer & exam header
 */
@Composable
private fun ExamSimulationTopBar(
    test: TestEntity,
    targetExamName: String,
    timerRemaining: Long,
    totalDurationSeconds: Long,
    isHindi: Boolean,
    fontScale: Float,
    answeredCount: Int,
    totalQuestions: Int,
    onToggleLanguage: (Boolean) -> Unit,
    onFontScaleToggle: () -> Unit,
    onCalculatorClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSubmitClick: () -> Unit,
    onPaletteClick: () -> Unit
) {
    val hours = timerRemaining / 3600
    val minutes = (timerRemaining % 3600) / 60
    val seconds = timerRemaining % 60
    val timeFormatted = if (hours > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }

    val isUrgent = timerRemaining <= 300 // <= 5 mins
    val isWarning = timerRemaining in 301..600 // 5-10 mins

    val timerColor by animateColorAsState(
        targetValue = when {
            isUrgent -> Color(0xFFDC2626) // Red
            isWarning -> Color(0xFFD97706) // Amber
            else -> MaterialTheme.colorScheme.primary
        },
        label = "timerColor"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Pause/Exit & Test Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onPauseClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Pause",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.padding(end = 6.dp)) {
                        Text(
                            text = test.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = targetExamName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Center/Right: Language Toggle, Font Zoom, Calculator, Timer, Palette & Submit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Bilingual Language Switcher Pill (Instant CBT switch)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (!isHindi) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onToggleLanguage(false) }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "EN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isHindi) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isHindi) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onToggleLanguage(true) }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "हिन्दी",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isHindi) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Font Zoom Control
                    IconButton(
                        onClick = onFontScaleToggle,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FormatSize,
                            contentDescription = "Zoom Text",
                            tint = if (fontScale != 1.0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Virtual Calculator
                    IconButton(
                        onClick = onCalculatorClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Calculate,
                            contentDescription = "Calculator",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Timer Badge
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = timerColor.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, timerColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isUrgent) Icons.Rounded.HourglassBottom else Icons.Rounded.Timer,
                                contentDescription = "Timer",
                                tint = timerColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = timeFormatted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = timerColor
                            )
                        }
                    }

                    // Grid / CBT Question Palette Button with counter badge
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = PaletteCorrect,
                                modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                            ) {
                                Text("$answeredCount", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    ) {
                        IconButton(
                            onClick = onPaletteClick,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GridView,
                                contentDescription = "CBT Palette",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Submit Button
                    Button(
                        onClick = onSubmitClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isHindi) "सबमिट" else "Submit",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Elapsed Time Linear Progress
            val progress = remember(timerRemaining, totalDurationSeconds) {
                if (totalDurationSeconds > 0) {
                    1f - (timerRemaining.toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
                } else 0f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = timerColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

/**
 * Subject Tabs & Quick Horizontal Question Palette Strip
 */
@Composable
private fun ExamNavigationHeader(
    questions: List<QuestionEntity>,
    currentIndex: Int,
    answers: Map<String, String>,
    marked: Set<String>,
    visited: Set<String>,
    isHindi: Boolean,
    onSelectQuestion: (Int) -> Unit,
    onOpenFullPalette: () -> Unit
) {
    val subjects = remember(questions) { questions.map { it.subject.ifBlank { "General" } }.distinct() }
    val currentSubject = questions.getOrNull(currentIndex)?.subject ?: subjects.firstOrNull() ?: ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Subject Selector Tabs with Answered Counters
        if (subjects.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = subjects.indexOf(currentSubject).coerceAtLeast(0),
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                divider = {}
            ) {
                subjects.forEach { subject ->
                    val isSelected = currentSubject == subject
                    val subjQuestions = remember(questions, subject) { questions.filter { it.subject.ifBlank { "General" } == subject } }
                    val subjAnswered = subjQuestions.count { answers.containsKey(it.id) }
                    Tab(
                        selected = isSelected,
                        onClick = {
                            val firstIdx = questions.indexOfFirst { it.subject == subject }
                            if (firstIdx != -1) onSelectQuestion(firstIdx)
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = subject,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "$subjAnswered/${subjQuestions.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        // Quick OMR Palette Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(questions) { index, q ->
                    val isCurrent = index == currentIndex
                    val isAnswered = answers.containsKey(q.id)
                    val isMarkedForReview = marked.contains(q.id)
                    val isVisited = visited.contains(q.id) || isCurrent

                    val cellColor = when {
                        isMarkedForReview && isAnswered -> PaletteMarkedAnswered
                        isMarkedForReview -> PaletteMarked
                        isAnswered -> PaletteCorrect
                        isVisited -> PaletteWrong
                        else -> PaletteUnvisited
                    }

                    val shape = if (isMarkedForReview) CircleShape else RoundedCornerShape(8.dp)

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(shape)
                            .background(cellColor)
                            .then(
                                if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                                else Modifier
                            )
                            .clickable { onSelectQuestion(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        if (isMarkedForReview && isAnswered) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(PaletteCorrect)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onOpenFullPalette,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ListAlt,
                    contentDescription = "Palette Legend",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

/**
 * Individual Swipeable Question Page View
 */
@Composable
private fun ExamQuestionPageView(
    question: QuestionEntity,
    questionIndex: Int,
    totalQuestions: Int,
    selectedAnswer: String,
    isMarked: Boolean,
    posMark: Double,
    negMark: Double,
    isHindi: Boolean,
    fontScale: Float = 1.0f,
    onSelectAnswer: (String) -> Unit,
    onReportError: () -> Unit
) {
    var viewLang by remember(isHindi) { mutableStateOf(if (isHindi) AppLanguage.HINDI else AppLanguage.ENGLISH) }

    val questionText = if (viewLang == AppLanguage.HINDI && !question.questionTextHi.isNullOrBlank()) {
        question.questionTextHi
    } else {
        question.questionText
    }

    val optA = if (viewLang == AppLanguage.HINDI && !question.optionAHi.isNullOrBlank()) question.optionAHi else question.optionA
    val optB = if (viewLang == AppLanguage.HINDI && !question.optionBHi.isNullOrBlank()) question.optionBHi else question.optionB
    val optC = if (viewLang == AppLanguage.HINDI && !question.optionCHi.isNullOrBlank()) question.optionCHi else question.optionC
    val optD = if (viewLang == AppLanguage.HINDI && !question.optionDHi.isNullOrBlank()) question.optionDHi else question.optionD

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Question Meta Header: Question No, Marking Chip, Section Tag, Language Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isHindi) "प्रश्न ${questionIndex + 1} / $totalQuestions" else "Q. ${questionIndex + 1} of $totalQuestions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Marking scheme badge
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    val badgeText = if (negMark <= 0.0) {
                        "+${ExamScoringEngine.formatMark(posMark)} / 0"
                    } else {
                        "+${ExamScoringEngine.formatMark(posMark)} / -${ExamScoringEngine.formatMark(negMark)}"
                    }
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Language & Report Actions
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Per-Question Bilingual Language Switcher
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewLang == AppLanguage.ENGLISH) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewLang = AppLanguage.ENGLISH }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "EN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewLang == AppLanguage.ENGLISH) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewLang == AppLanguage.HINDI) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewLang = AppLanguage.HINDI }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "HI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewLang == AppLanguage.HINDI) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onReportError, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = "Report Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Section & Chapter Tag
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (question.chapter.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Book, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${question.subject} • ${question.chapter}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (question.section.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = question.section.replace("_", " "),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Section B optional limit notice
        if (question.section.contains("B", ignoreCase = true)) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isHindi) "सेक्शन B (वैकल्पिक): अनुभाग में से अधिकतम 10 प्रश्नों को हल करें"
                        else "Section B (Optional): Attempt any 10 out of 15 questions",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Question Statement Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = questionText,
                    fontSize = (16 * fontScale).sp,
                    lineHeight = (24 * fontScale).sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Optional Diagram Image Card
        if (!question.imageUrl.isNullOrBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = question.imageUrl,
                        contentDescription = "Question Diagram",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
            }
        }

        // Options List (CBT Selection Style)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OmrOptionCard(
                optionLabel = "A",
                optionText = optA,
                isSelected = selectedAnswer == "A",
                fontScale = fontScale,
                onClick = { onSelectAnswer("A") }
            )
            OmrOptionCard(
                optionLabel = "B",
                optionText = optB,
                isSelected = selectedAnswer == "B",
                fontScale = fontScale,
                onClick = { onSelectAnswer("B") }
            )
            OmrOptionCard(
                optionLabel = "C",
                optionText = optC,
                isSelected = selectedAnswer == "C",
                fontScale = fontScale,
                onClick = { onSelectAnswer("C") }
            )
            OmrOptionCard(
                optionLabel = "D",
                optionText = optD,
                isSelected = selectedAnswer == "D",
                fontScale = fontScale,
                onClick = { onSelectAnswer("D") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Interactive CBT Option Card with tactile selection and font scaling
 */
@Composable
private fun OmrOptionCard(
    optionLabel: String,
    optionText: String,
    isSelected: Boolean,
    fontScale: Float = 1.0f,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        label = "optionBorder"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        label = "optionBg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CBT Circular Option Bubble
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.5.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = optionLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Option Content
            Text(
                text = optionText,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = (15 * fontScale).sp,
                lineHeight = (22 * fontScale).sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Authentic CBT Bottom Navigation Bar:
 * [Clear Response] [Mark for Review & Next] [Save & Mark for Review] [Previous] [Save & Next]
 */
@Composable
private fun ExamSimulationBottomBar(
    questions: List<QuestionEntity>,
    currentIndex: Int,
    answers: Map<String, String>,
    marked: Set<String>,
    isHindi: Boolean,
    onPrevious: () -> Unit,
    onSaveAndNext: () -> Unit,
    onSaveAndMarkForReview: () -> Unit,
    onMarkForReviewAndNext: () -> Unit,
    onClearResponse: () -> Unit
) {
    val currentQ = questions.getOrNull(currentIndex)
    val isAnswered = currentQ != null && answers.containsKey(currentQ.id)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Review, Clear & Save Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clear response
                if (isAnswered) {
                    TextButton(
                        onClick = onClearResponse,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (isHindi) "उत्तर हटाएं" else "Clear Response", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Mark for Review & Next (Leaves unselected or marks)
                    OutlinedButton(
                        onClick = onMarkForReviewAndNext,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PaletteMarked),
                        border = BorderStroke(1.dp, PaletteMarked),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(14.dp), tint = PaletteMarked)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isHindi) "रिव्यू व अगला" else "Review & Next",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Save & Mark for Review (Only if answered)
                    if (isAnswered) {
                        Button(
                            onClick = onSaveAndMarkForReview,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PaletteMarkedAnswered),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isHindi) "सेव व रिव्यू" else "Save & Review",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Row 2: Navigation & Final Save & Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Question
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = currentIndex > 0,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isHindi) "पिछला" else "Previous", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Save & Next / Final Submit
                Button(
                    onClick = onSaveAndNext,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentIndex == questions.size - 1) MaterialTheme.colorScheme.error else PaletteCorrect
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (currentIndex == questions.size - 1) {
                            if (isHindi) "सबमिट करें" else "Final Submit"
                        } else {
                            if (isHindi) "सेव व अगला" else "Save & Next"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (currentIndex == questions.size - 1) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Authentic NTA / TCS CBT Question Palette Modal Sheet:
 * - Candidate Details & Remaining Timer
 * - 5 NTA CBT Status Badges with Live Counts
 * - Subject / Section Filter Tabs
 * - Status Filter Chips (All, Answered, Unanswered, Marked)
 * - Interactive Question Matrix (1..N) with 1-tap jump
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamPaletteBottomSheet(
    questions: List<QuestionEntity>,
    currentIndex: Int,
    answers: Map<String, String>,
    marked: Set<String>,
    visited: Set<String>,
    isHindi: Boolean,
    userProfile: Com.ak.testseries.viewmodel.UserProfileData,
    timerRemaining: Long,
    onDismiss: () -> Unit,
    onSelectQuestion: (Int) -> Unit
) {
    val answeredCount = answers.size
    val markedCount = marked.count { !answers.containsKey(it) }
    val markedAnsweredCount = marked.count { answers.containsKey(it) }
    val visitedUnansweredCount = visited.count { !answers.containsKey(it) && !marked.contains(it) }
    val notVisitedCount = (questions.size - visited.size).coerceAtLeast(0)

    val subjects = remember(questions) { listOf("All") + questions.map { it.subject.ifBlank { "General" } }.distinct() }
    var selectedSubjectFilter by remember { mutableStateOf("All") }

    // Status filter: "ALL", "ANSWERED", "UNANSWERED", "MARKED"
    var selectedStatusFilter by remember { mutableStateOf("ALL") }

    val filteredQuestionsWithIndices = remember(questions, selectedSubjectFilter, selectedStatusFilter, answers, marked, visited) {
        questions.mapIndexed { idx, q -> idx to q }.filter { (_, q) ->
            val matchSubject = selectedSubjectFilter == "All" || q.subject.equals(selectedSubjectFilter, ignoreCase = true)
            val isAns = answers.containsKey(q.id)
            val isMark = marked.contains(q.id)
            val matchStatus = when (selectedStatusFilter) {
                "ANSWERED" -> isAns
                "UNANSWERED" -> !isAns
                "MARKED" -> isMark
                else -> true
            }
            matchSubject && matchStatus
        }
    }

    val hours = timerRemaining / 3600
    val minutes = (timerRemaining % 3600) / 60
    val seconds = timerRemaining % 60
    val timeFormatted = if (hours > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Header: Candidate Info & Remaining Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userProfile.name.take(1).ifBlank { "C" }.uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column {
                        Text(
                            text = userProfile.name.ifBlank { if (isHindi) "परीक्षार्थी" else "Candidate" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isHindi) "CBT प्रश्न तालिका" else "CBT Question Palette",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Time remaining chip
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = timeFormatted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5 NTA CBT Official Legend Cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CbtLegendBadge(PaletteCorrect, RoundedCornerShape(6.dp), if (isHindi) "उत्तर दिया" else "Answered", answeredCount)
                    CbtLegendBadge(PaletteWrong, RoundedCornerShape(6.dp), if (isHindi) "उत्तर नहीं दिया" else "Not Answered", visitedUnansweredCount)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CbtLegendBadge(PaletteMarked, CircleShape, if (isHindi) "रिव्यू हेतु चिह्नित" else "Marked for Review", markedCount)
                    CbtLegendBadge(PaletteMarkedAnswered, CircleShape, if (isHindi) "उत्तर दिया व रिव्यू चिह्नित" else "Ans & Marked", markedAnsweredCount, hasGreenTick = true)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CbtLegendBadge(PaletteUnvisited, RoundedCornerShape(6.dp), if (isHindi) "देखा नहीं गया" else "Not Visited", notVisitedCount)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips (All, Answered, Unanswered, Marked)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedStatusFilter == "ALL",
                    onClick = { selectedStatusFilter = "ALL" },
                    label = { Text(if (isHindi) "सभी (${questions.size})" else "All (${questions.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedStatusFilter == "ANSWERED",
                    onClick = { selectedStatusFilter = "ANSWERED" },
                    label = { Text(if (isHindi) "उत्तर ($answeredCount)" else "Ans ($answeredCount)", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedStatusFilter == "UNANSWERED",
                    onClick = { selectedStatusFilter = "UNANSWERED" },
                    label = { Text(if (isHindi) "शेष" else "Unans", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedStatusFilter == "MARKED",
                    onClick = { selectedStatusFilter = "MARKED" },
                    label = { Text(if (isHindi) "रिव्यू ($markedCount)" else "Review ($markedCount)", fontSize = 11.sp) }
                )
            }

            // Subject Filter Tabs inside Palette
            if (subjects.size > 2) {
                ScrollableTabRow(
                    selectedTabIndex = subjects.indexOf(selectedSubjectFilter).coerceAtLeast(0),
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    subjects.forEach { subj ->
                        val isSel = selectedSubjectFilter == subj
                        Tab(
                            selected = isSel,
                            onClick = { selectedSubjectFilter = subj },
                            text = { Text(subj, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // CBT Questions Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 46.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredQuestionsWithIndices) { (originalIdx, q) ->
                    val isCurrent = originalIdx == currentIndex
                    val isAns = answers.containsKey(q.id)
                    val isMark = marked.contains(q.id)
                    val isVis = visited.contains(q.id) || isCurrent

                    val bubbleColor = when {
                        isMark && isAns -> PaletteMarkedAnswered
                        isMark -> PaletteMarked
                        isAns -> PaletteCorrect
                        isVis -> PaletteWrong
                        else -> PaletteUnvisited
                    }

                    val shape = if (isMark) CircleShape else RoundedCornerShape(8.dp)

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(shape)
                            .background(bubbleColor)
                            .then(
                                if (isCurrent) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, shape)
                                else Modifier
                            )
                            .clickable { onSelectQuestion(originalIdx) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${originalIdx + 1}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (isMark && isAns) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(PaletteCorrect)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CbtLegendBadge(
    color: Color,
    shape: androidx.compose.ui.graphics.Shape,
    label: String,
    count: Int,
    hasGreenTick: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(shape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            if (hasGreenTick) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PaletteCorrect)
                )
            }
        }
        Text(
            text = "$label: ",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$count",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Built-in Virtual CBT Calculator Dialog
 */
@Composable
private fun CbtVirtualCalculatorDialog(
    isHindi: Boolean,
    onDismiss: () -> Unit
) {
    var expression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Calculate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isHindi) "वर्चुअल कैलकुलेटर" else "Virtual Calculator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Calculator Display
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (expression.isEmpty()) "0" else expression,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (resultText.isNotEmpty()) {
                            Text(
                                text = "= $resultText",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Calculator Keypad
                val buttons = listOf(
                    listOf("C", "⌫", "%", "÷"),
                    listOf("7", "8", "9", "×"),
                    listOf("4", "5", "6", "−"),
                    listOf("1", "2", "3", "+"),
                    listOf("0", ".", "√", "=")
                )

                fun evaluate(expr: String): String {
                    return try {
                        val sanitized = expr.replace("×", "*").replace("÷", "/").replace("−", "-")
                        val clean = sanitized.trim()
                        if (clean.isEmpty()) return ""
                        val tokens = clean.split(Regex("(?<=[-+*/])|(?=[-+*/])"))
                        if (tokens.size == 1) {
                            val v = tokens[0].toDoubleOrNull() ?: return ""
                            return if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
                        }
                        var current = tokens[0].toDoubleOrNull() ?: 0.0
                        var i = 1
                        while (i < tokens.size - 1) {
                            val op = tokens[i]
                            val next = tokens[i + 1].toDoubleOrNull() ?: 0.0
                            current = when (op) {
                                "+" -> current + next
                                "-" -> current - next
                                "*" -> current * next
                                "/" -> if (next != 0.0) current / next else Double.NaN
                                else -> current
                            }
                            i += 2
                        }
                        if (current.isNaN()) "Error"
                        else if (current % 1.0 == 0.0) current.toLong().toString()
                        else String.format(java.util.Locale.US, "%.4f", current).trimEnd('0').trimEnd('.')
                    } catch (e: Exception) {
                        "Error"
                    }
                }

                buttons.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { btn ->
                            val isAction = btn in listOf("÷", "×", "−", "+", "=", "√", "%")
                            val isClear = btn in listOf("C", "⌫")
                            val containerCol = when {
                                btn == "=" -> MaterialTheme.colorScheme.primary
                                isClear -> MaterialTheme.colorScheme.errorContainer
                                isAction -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            val textCol = when {
                                btn == "=" -> Color.White
                                isClear -> MaterialTheme.colorScheme.onErrorContainer
                                isAction -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }

                            Button(
                                onClick = {
                                    when (btn) {
                                        "C" -> {
                                            expression = ""
                                            resultText = ""
                                        }
                                        "⌫" -> {
                                            if (expression.isNotEmpty()) {
                                                expression = expression.dropLast(1)
                                            }
                                        }
                                        "=" -> {
                                            if (expression.isNotEmpty()) {
                                                resultText = evaluate(expression)
                                            }
                                        }
                                        "√" -> {
                                            val v = evaluate(expression).toDoubleOrNull()
                                            if (v != null && v >= 0) {
                                                val sqrtVal = Math.sqrt(v)
                                                expression = if (sqrtVal % 1.0 == 0.0) sqrtVal.toLong().toString() else String.format(java.util.Locale.US, "%.4f", sqrtVal).trimEnd('0').trimEnd('.')
                                                resultText = expression
                                            } else {
                                                resultText = "Error"
                                            }
                                        }
                                        "%" -> {
                                            val v = evaluate(expression).toDoubleOrNull()
                                            if (v != null) {
                                                val pctVal = v / 100.0
                                                expression = if (pctVal % 1.0 == 0.0) pctVal.toLong().toString() else String.format(java.util.Locale.US, "%.4f", pctVal).trimEnd('0').trimEnd('.')
                                                resultText = expression
                                            }
                                        }
                                        else -> {
                                            expression += btn
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = containerCol),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = btn,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textCol
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
