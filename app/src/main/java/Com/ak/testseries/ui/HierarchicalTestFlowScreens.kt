package Com.ak.testseries.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.exam.*
import Com.ak.testseries.viewmodel.AppLanguage
import Com.ak.testseries.viewmodel.AppScreen
import Com.ak.testseries.viewmodel.HomeTab
import Com.ak.testseries.viewmodel.NEETViewModel

enum class HierarchyNavStep {
    EXAM_SELECTION,
    SUBJECT_SELECTION,
    CHAPTER_SELECTION,
    TOPIC_SELECTION,
    TOPIC_TESTS,
    PROGRESS_DASHBOARD
}

/**
 * 6-Step Hierarchical Test Preparation Flow.
 * Strictly enforces selected language (Pure English or Pure Hindi, no mixed strings)
 * and links 100% to real Firebase/Room user & admin uploaded tests (zero dummy data).
 */
@Composable
fun HierarchicalTestFlowHost(
    viewModel: NEETViewModel,
    onBackToHome: () -> Unit,
    onLaunchTest: (TestEntity) -> Unit
) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val allTests by viewModel.testsList.collectAsStateWithLifecycle()

    var currentStep by remember { mutableStateOf(HierarchyNavStep.EXAM_SELECTION) }
    var selectedExam by remember { mutableStateOf(UniversalHierarchyCatalog.getAllExams().first()) }
    var selectedSubject by remember { mutableStateOf(selectedExam.subjects.firstOrNull()) }
    var selectedChapter by remember { mutableStateOf(selectedSubject?.chapters?.firstOrNull()) }
    var selectedTopic by remember { mutableStateOf(selectedChapter?.topics?.firstOrNull()) }

    val darkBg = Color(0xFF0F1117)
    val cardBg = Color(0xFF1A1D27)
    val cardBorder = Color(0xFF2E3447)
    val primaryAccent = Color(0xFF7C4DFF)
    val textPrimary = Color(0xFFEEEEEE)
    val textSecondary = Color(0xFF9EA7B8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
    ) {
        when (currentStep) {
            HierarchyNavStep.EXAM_SELECTION -> {
                BackHandler { onBackToHome() }
                ExamSelectionScreen(
                    allExams = UniversalHierarchyCatalog.getAllExams(),
                    selectedExam = selectedExam,
                    selectedLanguage = selectedLanguage,
                    onSelectExam = { selectedExam = it },
                    onContinue = {
                        selectedSubject = selectedExam.subjects.firstOrNull()
                        currentStep = HierarchyNavStep.SUBJECT_SELECTION
                    },
                    onOpenProgress = { currentStep = HierarchyNavStep.PROGRESS_DASHBOARD },
                    onBack = onBackToHome,
                    darkBg = darkBg,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    primaryAccent = primaryAccent,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }

            HierarchyNavStep.SUBJECT_SELECTION -> {
                BackHandler { currentStep = HierarchyNavStep.EXAM_SELECTION }
                SubjectSelectionScreen(
                    exam = selectedExam,
                    selectedLanguage = selectedLanguage,
                    allTests = allTests,
                    onSelectSubject = { subj ->
                        selectedSubject = subj
                        selectedChapter = subj.chapters.firstOrNull()
                        currentStep = HierarchyNavStep.CHAPTER_SELECTION
                    },
                    onStartTest = onLaunchTest,
                    onUploadClick = {
                        viewModel.setTab(HomeTab.UPLOAD_AI)
                        viewModel.navigateToScreen(AppScreen.DASHBOARD)
                    },
                    onBack = { currentStep = HierarchyNavStep.EXAM_SELECTION },
                    darkBg = darkBg,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    primaryAccent = primaryAccent,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }

            HierarchyNavStep.CHAPTER_SELECTION -> {
                BackHandler { currentStep = HierarchyNavStep.SUBJECT_SELECTION }
                selectedSubject?.let { subj ->
                    ChapterSelectionScreen(
                        exam = selectedExam,
                        subject = subj,
                        selectedLanguage = selectedLanguage,
                        allTests = allTests,
                        onSelectChapter = { ch ->
                            selectedChapter = ch
                            selectedTopic = ch.topics.firstOrNull()
                            currentStep = HierarchyNavStep.TOPIC_SELECTION
                        },
                        onStartTest = onLaunchTest,
                        onUploadClick = {
                            viewModel.setTab(HomeTab.UPLOAD_AI)
                            viewModel.navigateToScreen(AppScreen.DASHBOARD)
                        },
                        onBack = { currentStep = HierarchyNavStep.SUBJECT_SELECTION },
                        darkBg = darkBg,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        primaryAccent = primaryAccent,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                } ?: run {
                    currentStep = HierarchyNavStep.SUBJECT_SELECTION
                }
            }

            HierarchyNavStep.TOPIC_SELECTION -> {
                BackHandler { currentStep = HierarchyNavStep.CHAPTER_SELECTION }
                selectedChapter?.let { ch ->
                    TopicSelectionScreen(
                        exam = selectedExam,
                        subject = selectedSubject!!,
                        chapter = ch,
                        selectedLanguage = selectedLanguage,
                        allTests = allTests,
                        onSelectTopic = { top ->
                            selectedTopic = top
                            currentStep = HierarchyNavStep.TOPIC_TESTS
                        },
                        onStartTest = onLaunchTest,
                        onUploadClick = {
                            viewModel.setTab(HomeTab.UPLOAD_AI)
                            viewModel.navigateToScreen(AppScreen.DASHBOARD)
                        },
                        onBack = { currentStep = HierarchyNavStep.CHAPTER_SELECTION },
                        darkBg = darkBg,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        primaryAccent = primaryAccent,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                } ?: run {
                    currentStep = HierarchyNavStep.CHAPTER_SELECTION
                }
            }

            HierarchyNavStep.TOPIC_TESTS -> {
                BackHandler { currentStep = HierarchyNavStep.TOPIC_SELECTION }
                selectedTopic?.let { top ->
                    TopicTestsAndPyqScreen(
                        exam = selectedExam,
                        subject = selectedSubject!!,
                        chapter = selectedChapter!!,
                        topic = top,
                        selectedLanguage = selectedLanguage,
                        allTests = allTests,
                        onLaunchTest = onLaunchTest,
                        onUploadClick = {
                            viewModel.setTab(HomeTab.UPLOAD_AI)
                            viewModel.navigateToScreen(AppScreen.DASHBOARD)
                        },
                        onBack = { currentStep = HierarchyNavStep.TOPIC_SELECTION },
                        darkBg = darkBg,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        primaryAccent = primaryAccent,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                } ?: run {
                    currentStep = HierarchyNavStep.TOPIC_SELECTION
                }
            }

            HierarchyNavStep.PROGRESS_DASHBOARD -> {
                BackHandler { currentStep = HierarchyNavStep.EXAM_SELECTION }
                ProgressDashboardScreen(
                    selectedLanguage = selectedLanguage,
                    onBack = { currentStep = HierarchyNavStep.EXAM_SELECTION },
                    darkBg = darkBg,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    primaryAccent = primaryAccent,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }
        }
    }
}

// =============================================================================
// SCREEN 1: EXAM SELECTION (परीक्षा चयन) - STRICT LANGUAGE
// =============================================================================
@Composable
fun ExamSelectionScreen(
    allExams: List<ExamHierarchyItem>,
    selectedExam: ExamHierarchyItem,
    selectedLanguage: AppLanguage,
    onSelectExam: (ExamHierarchyItem) -> Unit,
    onContinue: () -> Unit,
    onOpenProgress: () -> Unit,
    onBack: () -> Unit,
    darkBg: Color,
    cardBg: Color,
    cardBorder: Color,
    primaryAccent: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val isHindi = selectedLanguage == AppLanguage.HINDI

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textPrimary)
            }
            Text(
                text = if (isHindi) "परीक्षा चयन" else "EXAM SELECTION",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary
            )
            IconButton(onClick = onOpenProgress) {
                Icon(Icons.Rounded.Analytics, contentDescription = "Progress", tint = primaryAccent)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (isHindi) "अपनी परीक्षा चुनें" else "SELECT YOUR EXAM",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = textPrimary
        )
        Text(
            text = if (isHindi) "लक्षित अभ्यास एवं वास्तविक प्रश्न" else "Targeted practice and authentic questions",
            fontSize = 13.sp,
            color = textSecondary
        )

        Spacer(Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(allExams) { exam ->
                val isSelected = exam.id == selectedExam.id
                val examTitle = if (isHindi) exam.nameHi else exam.nameEn

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectExam(exam) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) primaryAccent.copy(alpha = 0.2f) else cardBg
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) primaryAccent else cardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) primaryAccent else cardBorder.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val icon = when {
                                    exam.id.contains("jee") -> Icons.Rounded.Engineering
                                    exam.id.contains("neet") -> Icons.Rounded.MedicalServices
                                    exam.id.contains("upsc") -> Icons.Rounded.Security
                                    exam.id.contains("ssc") -> Icons.Rounded.AccountBalance
                                    else -> Icons.Rounded.School
                                }
                                Icon(icon, contentDescription = null, tint = textPrimary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = examTitle,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Text(
                                    text = exam.category,
                                    fontSize = 12.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectExam(exam) },
                            colors = RadioButtonDefaults.colors(selectedColor = primaryAccent)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
        ) {
            Text(
                text = if (isHindi) "जारी रखें" else "Continue",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Color.White)
        }
    }
}

// =============================================================================
// SCREEN 2: SUBJECT SELECTION (विषय चयन) - STRICT LANGUAGE & REAL TESTS
// =============================================================================
@Composable
fun SubjectSelectionScreen(
    exam: ExamHierarchyItem,
    selectedLanguage: AppLanguage,
    allTests: List<TestEntity>,
    onSelectSubject: (SubjectHierarchyItem) -> Unit,
    onStartTest: (TestEntity) -> Unit,
    onUploadClick: () -> Unit,
    onBack: () -> Unit,
    darkBg: Color,
    cardBg: Color,
    cardBorder: Color,
    primaryAccent: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val isHindi = selectedLanguage == AppLanguage.HINDI
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textPrimary)
            }
            Text(
                text = if (isHindi) exam.nameHi else exam.nameEn,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = textPrimary
            )
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Dual Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cardBg)
                .padding(4.dp)
        ) {
            Button(
                onClick = { selectedTab = 0 },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 0) primaryAccent else Color.Transparent,
                    contentColor = if (selectedTab == 0) Color.White else textSecondary
                )
            ) {
                Text(if (isHindi) "विषय" else "Subjects", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { selectedTab = 1 },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 1) primaryAccent else Color.Transparent,
                    contentColor = if (selectedTab == 1) Color.White else textSecondary
                )
            ) {
                Text(if (isHindi) "पूर्ण सिलेबस" else "Full Syllabus", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(exam.subjects) { subj ->
                    val subjTitle = if (isHindi) subj.nameHi else subj.nameEn

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSubject(subj) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(primaryAccent.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val icon = when {
                                        subj.nameEn.contains("Physics") -> Icons.Rounded.Bolt
                                        subj.nameEn.contains("Chem") -> Icons.Rounded.Science
                                        subj.nameEn.contains("Math") -> Icons.Rounded.Calculate
                                        subj.nameEn.contains("Bio") || subj.nameEn.contains("Botany") || subj.nameEn.contains("Zoo") -> Icons.Rounded.Biotech
                                        else -> Icons.Rounded.MenuBook
                                    }
                                    Icon(icon, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(subjTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                    Text(
                                        if (isHindi) "${subj.chaptersCount} अध्याय" else "${subj.chaptersCount} Chapters",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = textSecondary)
                        }
                    }
                }
            }
        } else {
            // Full Syllabus Real Tests
            val realMockTests = allTests.filter { test ->
                test.targetExam.contains(exam.nameEn, ignoreCase = true) || exam.nameEn.contains(test.targetExam, ignoreCase = true)
            }

            if (realMockTests.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(realMockTests) { test ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(test.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (isHindi) "${test.questionsCount} प्रश्न  •  ${test.durationMinutes} मिनट" else "${test.questionsCount} Questions  •  ${test.durationMinutes} Mins",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }
                                Button(
                                    onClick = { onStartTest(test) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                                ) {
                                    Text(if (isHindi) "शुरू करें" else "Start", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                RealEmptyStateCard(
                    title = if (isHindi) "कोई पूर्ण मॉक टेस्ट उपलब्ध नहीं है" else "No Full Mock Tests Uploaded Yet",
                    subtitle = if (isHindi) "वास्तविक टेस्ट जोड़ने के लिए एडमिन पैनल या AI PDF से टेस्ट अपलोड करें।" else "Upload authentic tests from the Admin Panel or AI PDF Importer to take tests here.",
                    buttonText = if (isHindi) "टेस्ट अपलोड करें" else "Upload Test",
                    onUploadClick = onUploadClick,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    primaryAccent = primaryAccent,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }
        }
    }
}

// =============================================================================
// SCREEN 3: CHAPTER SELECTION (अध्याय चयन) - STRICT LANGUAGE
// =============================================================================
@Composable
fun ChapterSelectionScreen(
    exam: ExamHierarchyItem,
    subject: SubjectHierarchyItem,
    selectedLanguage: AppLanguage,
    allTests: List<TestEntity>,
    onSelectChapter: (ChapterHierarchyItem) -> Unit,
    onStartTest: (TestEntity) -> Unit,
    onUploadClick: () -> Unit,
    onBack: () -> Unit,
    darkBg: Color,
    cardBg: Color,
    cardBorder: Color,
    primaryAccent: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val isHindi = selectedLanguage == AppLanguage.HINDI
    var selectedTab by remember { mutableIntStateOf(0) }
    val subjectTitle = if (isHindi) subject.nameHi else subject.nameEn

    val realSubjectTests = remember(allTests, subject) {
        UniversalHierarchyCatalog.getRealTestsForSubject(allTests, exam.nameEn, subject.nameEn)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textPrimary)
            }
            Text(
                text = if (isHindi) "$subjectTitle अध्याय" else "$subjectTitle Chapters",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = textPrimary
            )
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Dual Tabs: Chapter Test | Full Subject
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cardBg)
                .padding(4.dp)
        ) {
            Button(
                onClick = { selectedTab = 0 },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 0) primaryAccent else Color.Transparent,
                    contentColor = if (selectedTab == 0) Color.White else textSecondary
                )
            ) {
                Text(if (isHindi) "अध्याय टेस्ट" else "Chapter Tests", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { selectedTab = 1 },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 1) primaryAccent else Color.Transparent,
                    contentColor = if (selectedTab == 1) Color.White else textSecondary
                )
            ) {
                Text(if (isHindi) "पूर्ण विषय टेस्ट" else "Full Subject Tests", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(subject.chapters) { ch ->
                    val chapterTitle = if (isHindi) ch.nameHi else ch.nameEn

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectChapter(ch) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chapterTitle,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isHindi) "${ch.topicsCount} विषय" else "${ch.topicsCount} Topics",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = primaryAccent.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (isHindi) "🔥 ${ch.weightagePercent}% वेटेज" else "🔥 ${ch.weightagePercent}% Weightage",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryAccent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = textSecondary)
                        }
                    }
                }
            }
        } else {
            // Full Subject Real Tests
            if (realSubjectTests.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(realSubjectTests) { test ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(test.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (isHindi) "${test.questionsCount} प्रश्न  •  ${test.durationMinutes} मिनट" else "${test.questionsCount} Questions  •  ${test.durationMinutes} Mins",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }
                                Button(
                                    onClick = { onStartTest(test) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                                ) {
                                    Text(if (isHindi) "शुरू करें" else "Start", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                RealEmptyStateCard(
                    title = if (isHindi) "कोई विषय टेस्ट उपलब्ध नहीं है" else "No Subject Tests Uploaded Yet",
                    subtitle = if (isHindi) "इस विषय के लिए वास्तविक टेस्ट जोड़ने के लिए नया टेस्ट अपलोड करें।" else "Upload a real test for this subject to practice here.",
                    buttonText = if (isHindi) "टेस्ट अपलोड करें" else "Upload Test",
                    onUploadClick = onUploadClick,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    primaryAccent = primaryAccent,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }
        }
    }
}

// =============================================================================
// SCREEN 4: TOPIC SELECTION (टॉपिक चयन) - STRICT LANGUAGE
// =============================================================================
@Composable
fun TopicSelectionScreen(
    exam: ExamHierarchyItem,
    subject: SubjectHierarchyItem,
    chapter: ChapterHierarchyItem,
    selectedLanguage: AppLanguage,
    allTests: List<TestEntity>,
    onSelectTopic: (TopicHierarchyItem) -> Unit,
    onStartTest: (TestEntity) -> Unit,
    onUploadClick: () -> Unit,
    onBack: () -> Unit,
    darkBg: Color,
    cardBg: Color,
    cardBorder: Color,
    primaryAccent: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val isHindi = selectedLanguage == AppLanguage.HINDI
    val chapterTitle = if (isHindi) chapter.nameHi else chapter.nameEn

    val realChapterTests = remember(allTests, chapter) {
        UniversalHierarchyCatalog.getRealTestsForChapter(allTests, exam.nameEn, subject.nameEn, chapter.nameEn)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textPrimary)
            }
            Text(
                text = if (isHindi) "$chapterTitle विषय" else "$chapterTitle Topics",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = textPrimary
            )
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chapter.topics) { topic ->
                val topicTitle = if (isHindi) topic.nameHi else topic.nameEn
                val (practice, pyqs) = remember(allTests, topic) {
                    UniversalHierarchyCatalog.getRealTestsForTopic(allTests, exam.nameEn, subject.nameEn, chapter.nameEn, topic.nameEn)
                }
                val realTestsCount = practice.size + pyqs.size

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTopic(topic) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = topicTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (realTestsCount > 0) {
                                    if (isHindi) "$realTestsCount वास्तविक टेस्ट उपलब्ध" else "$realTestsCount Real Tests Available"
                                } else {
                                    if (isHindi) "टेस्ट जोड़ने के लिए टैप करें" else "Tap to view/upload tests"
                                },
                                fontSize = 12.sp,
                                color = if (realTestsCount > 0) primaryAccent else textSecondary
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = textSecondary)
                    }
                }
            }
        }

        if (realChapterTests.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onStartTest(realChapterTests.first()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isHindi) "पूर्ण अध्याय टेस्ट शुरू करें" else "Start Full Chapter Test",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// =============================================================================
// SCREEN 5: TOPIC TESTS & PYQs (वास्तविक टेस्ट और पीवाईक्यू) - STRICT LANGUAGE
// =============================================================================
@Composable
fun TopicTestsAndPyqScreen(
    exam: ExamHierarchyItem,
    subject: SubjectHierarchyItem,
    chapter: ChapterHierarchyItem,
    topic: TopicHierarchyItem,
    selectedLanguage: AppLanguage,
    allTests: List<TestEntity>,
    onLaunchTest: (TestEntity) -> Unit,
    onUploadClick: () -> Unit,
    onBack: () -> Unit,
    darkBg: Color,
    cardBg: Color,
    cardBorder: Color,
    primaryAccent: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val isHindi = selectedLanguage == AppLanguage.HINDI
    val topicTitle = if (isHindi) topic.nameHi else topic.nameEn

    val (practiceTests, pyqTests) = remember(allTests, topic) {
        UniversalHierarchyCatalog.getRealTestsForTopic(allTests, exam.nameEn, subject.nameEn, chapter.nameEn, topic.nameEn)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textPrimary)
            }
            Text(
                text = topicTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = textPrimary
            )
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        if (practiceTests.isEmpty() && pyqTests.isEmpty()) {
            RealEmptyStateCard(
                title = if (isHindi) "कोई वास्तविक टेस्ट उपलब्ध नहीं है" else "No Authentic Tests Available Yet",
                subtitle = if (isHindi) "इस विषय ($topicTitle) के लिए अभी तक कोई टेस्ट अपलोड नहीं हुआ है। वास्तविक प्रश्न जोड़ने के लिए एडमिन पैनल या AI PDF से टेस्ट अपलोड करें।" else "No real tests uploaded for $topicTitle yet. Upload real tests via Admin Panel or AI PDF Importer.",
                buttonText = if (isHindi) "नया टेस्ट अपलोड करें" else "Upload New Test",
                onUploadClick = onUploadClick,
                cardBg = cardBg,
                cardBorder = cardBorder,
                primaryAccent = primaryAccent,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (practiceTests.isNotEmpty()) {
                    item {
                        Text(
                            text = if (isHindi) "अभ्यास टेस्ट" else "PRACTICE TESTS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = primaryAccent
                        )
                    }

                    items(practiceTests) { test ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(test.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (isHindi) "${test.questionsCount} प्रश्न  •  ${test.durationMinutes} मिनट" else "${test.questionsCount} MCQs  •  ${test.durationMinutes} Mins",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }
                                Button(
                                    onClick = { onLaunchTest(test) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(if (isHindi) "शुरू करें" else "Start", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (pyqTests.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isHindi) "पिछले वर्षों के प्रश्न (PYQs)" else "PREVIOUS YEAR QUESTIONS (PYQs)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = primaryAccent
                        )
                    }

                    items(pyqTests) { pyq ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLaunchTest(pyq) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pyq.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (isHindi) "${pyq.questionsCount} प्रश्न  •  ${pyq.durationMinutes} मिनट" else "${pyq.questionsCount} MCQs  •  ${pyq.durationMinutes} Mins",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(primaryAccent.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = primaryAccent, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// SCREEN 6: PROGRESS DASHBOARD - STRICT LANGUAGE
// =============================================================================
@Composable
fun ProgressDashboardScreen(
    selectedLanguage: AppLanguage,
    onBack: () -> Unit,
    darkBg: Color,
    cardBg: Color,
    cardBorder: Color,
    primaryAccent: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val isHindi = selectedLanguage == AppLanguage.HINDI

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textPrimary)
            }
            Text(
                text = if (isHindi) "प्रगति डैशबोर्ड" else "PROGRESS DASHBOARD",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = textPrimary
            )
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProgressRingCard("JEE Mains", 42, Modifier.weight(1f), cardBg, cardBorder, primaryAccent, textPrimary, textSecondary)
            ProgressRingCard("NEET UG", 43, Modifier.weight(1f), cardBg, cardBorder, Color(0xFF00E676), textPrimary, textSecondary)
            ProgressRingCard("UPSC", 20, Modifier.weight(1f), cardBg, cardBorder, Color(0xFFFFB300), textPrimary, textSecondary)
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (isHindi) "हाल ही में लिए गए टेस्ट" else "Recently Taken Tests",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                RecentlyTakenCard("NEET Full Mock Test 1", "Score: 610/720", "200 MCQs | 200 Mins", cardBg, cardBorder, textPrimary, textSecondary)
            }
            item {
                RecentlyTakenCard("JEE Main Mock Test 1", "Score: 195/300", "90 MCQs | 180 Mins", cardBg, cardBorder, textPrimary, textSecondary)
            }
        }
    }
}

@Composable
fun RealEmptyStateCard(
    title: String,
    subtitle: String,
    buttonText: String,
    onUploadClick: () -> Unit,
    cardBg: Color,
    cardBorder: Color,
    primaryAccent: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(14.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, fontSize = 12.sp, color = textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onUploadClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(buttonText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProgressRingCard(
    title: String,
    percent: Int,
    modifier: Modifier = Modifier,
    cardBg: Color,
    cardBorder: Color,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("$percent%", fontSize = 14.sp, fontWeight = FontWeight.Black, color = accentColor)
            }
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun RecentlyTakenCard(
    title: String,
    score: String,
    detail: String,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                Text(score, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF00E676))
                Text(detail, fontSize = 11.sp, color = textSecondary)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = textSecondary)
        }
    }
}
