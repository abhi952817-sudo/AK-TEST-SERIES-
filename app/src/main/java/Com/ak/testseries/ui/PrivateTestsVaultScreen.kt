package Com.ak.testseries.ui

import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.viewmodel.NEETViewModel
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateTestsVaultScreen(
    viewModel: NEETViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val tests by viewModel.testsList.collectAsStateWithLifecycle()
    val attempts by viewModel.attemptHistory.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All Private") }
    var selectedSubject by remember { mutableStateOf("All") }

    var showImportDialog by remember { mutableStateOf(false) }
    var activePreviewTest by remember { mutableStateOf<TestEntity?>(null) }

    // Filter private tests: Authenticated students see only tests they created; Admins can see all
    val privateTests = remember(tests, currentUserId, viewModel.isAdmin) {
        tests.filter { test ->
            val isOwner = currentUserId.isNotBlank() && test.uploaderId == currentUserId
            val isPrivateTest = !test.isPublic || test.institute.contains("Private", ignoreCase = true)
            if (viewModel.isAdmin) {
                isPrivateTest || isOwner
            } else {
                isOwner && isPrivateTest
            }
        }
    }

    val availableSubjects = remember(privateTests) {
        listOf("All") + privateTests.map { it.subject }.filter { it.isNotBlank() }.distinct()
    }

    val filteredTests = remember(privateTests, searchQuery, selectedFilter, selectedSubject) {
        privateTests.filter { test ->
            val matchesSearch = searchQuery.isBlank() ||
                    test.title.contains(searchQuery, ignoreCase = true) ||
                    test.subject.contains(searchQuery, ignoreCase = true) ||
                    test.chapter.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "All Private" -> true
                "With JSON" -> !test.questionsJson.isNullOrEmpty()
                "Attempted" -> attempts.any { it.testId == test.id }
                "Downloaded" -> test.isDownloaded
                else -> true
            }

            val matchesSubject = selectedSubject == "All" || test.subject.equals(selectedSubject, ignoreCase = true)

            matchesSearch && matchesFilter && matchesSubject
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Private Tests Vault",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "🔒 Vault",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Local pre-formatted questions, options & images from JSON without AI",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { showImportDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search private tests by title, subject, chapter...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            // Filter Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("All Private", "With JSON", "Attempted", "Downloaded")
                items(filters) { f ->
                    FilterChip(
                        selected = selectedFilter == f,
                        onClick = { selectedFilter = f },
                        label = { Text(f, fontSize = 12.sp) },
                        leadingIcon = {
                            if (selectedFilter == f) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    )
                }
            }

            // Subject Filter Row
            if (availableSubjects.size > 2) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(availableSubjects) { subj ->
                        val isSel = selectedSubject == subj
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.clickable { selectedSubject = subj }
                        ) {
                            Text(
                                text = subj,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Test List
            if (filteredTests.isEmpty()) {
                EmptyPrivateVaultView(
                    searchQuery = searchQuery,
                    onImportClick = { showImportDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredTests, key = { it.id }) { test ->
                        val testAttempts = attempts.filter { it.testId == test.id }
                        val isOwner = !currentUserId.isNullOrBlank() &&
                                test.uploaderId == currentUserId &&
                                !test.institute.equals("Community Vault", ignoreCase = true)
                        PrivateTestCardItem(
                            test = test,
                            attemptsCount = testAttempts.size,
                            lastScore = testAttempts.lastOrNull()?.let { "${it.score}/${it.maxScore}" },
                            isOwner = isOwner,
                            onStartExam = { viewModel.initiateTest(test) },
                            onDirectPractice = { activePreviewTest = test },
                            onSaveToMemory = { viewModel.downloadTestOffline(test) },
                            onShareToCommunity = { viewModel.shareTestToCommunity(test) }
                        )
                    }
                }
            }
        }
    }

    // Direct Question Practice / Render Modal
    activePreviewTest?.let { test ->
        LocalPreformattedQuestionModal(
            test = test,
            viewModel = viewModel,
            onDismiss = { activePreviewTest = null }
        )
    }

    // Pre-formatted JSON Import Modal
    if (showImportDialog) {
        ImportPreformattedJsonDialog(
            viewModel = viewModel,
            onDismiss = { showImportDialog = false }
        )
    }
}

@Composable
fun PrivateTestCardItem(
    test: TestEntity,
    attemptsCount: Int,
    lastScore: String?,
    isOwner: Boolean = false,
    onStartExam: () -> Unit,
    onDirectPractice: () -> Unit,
    onSaveToMemory: () -> Unit,
    onShareToCommunity: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Private Vault",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = test.subject,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = test.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (test.chapter.isNotBlank() && test.chapter != "All") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Chapter: ${test.chapter}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Quiz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${test.questionsCount} Qs",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (test.durationMinutes > 0) "${test.durationMinutes} Mins" else "${test.questionsCount} Mins",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (lastScore != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MilitaryTech,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Score: $lastScore",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Direct Practice / Render Questions from JSON
                OutlinedButton(
                    onClick = onDirectPractice,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Render Questions", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Give Test (Exam Mode)
                Button(
                    onClick = onStartExam,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Give Test", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (isOwner && onShareToCommunity != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onShareToCommunity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_to_community_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share to Community",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share to Community", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EmptyPrivateVaultView(
    searchQuery: String,
    onImportClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (searchQuery.isNotBlank()) "No private tests match your search" else "No Private Tests Found",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "You can import pre-formatted questions, options & images directly from JSON into your private vault without Gemini AI.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onImportClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Pre-formatted JSON Test", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPreformattedQuestionModal(
    test: TestEntity,
    viewModel: NEETViewModel,
    onDismiss: () -> Unit
) {
    var questions by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentIndex by remember { mutableStateOf(0) }
    var isHindiMode by remember { mutableStateOf(false) }

    // User selected answers map: questionId -> selectedOption ("A", "B", "C", "D")
    val selectedAnswers = remember { mutableStateMapOf<String, String>() }
    // User checked explanations state
    val checkedExplanations = remember { mutableStateMapOf<String, Boolean>() }

    var isSubmittingAttempt by remember { mutableStateOf(false) }

    // Direct JSON / Room Parser without Gemini API
    LaunchedEffect(test) {
        isLoading = true
        val parsedList = mutableListOf<QuestionEntity>()

        if (!test.questionsJson.isNullOrEmpty()) {
            try {
                val cleanJson = test.questionsJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val jsonArray = JSONArray(cleanJson)
                for (i in 0 until jsonArray.length()) {
                    val qObj = jsonArray.getJSONObject(i)
                    parsedList.add(
                        QuestionEntity(
                            id = qObj.optString("id", "${test.id}_q$i"),
                            testId = test.id,
                            questionText = qObj.optString("questionText", qObj.optString("questionTextEn", "Question ${i + 1}")),
                            optionA = qObj.optString("optionA", qObj.optString("optionAEn", "Option A")),
                            optionB = qObj.optString("optionB", qObj.optString("optionBEn", "Option B")),
                            optionC = qObj.optString("optionC", qObj.optString("optionCEn", "Option C")),
                            optionD = qObj.optString("optionD", qObj.optString("optionDEn", "Option D")),
                            correctAnswer = qObj.optString("correctAnswer", "").uppercase().take(1),
                            explanation = qObj.optString("explanation", qObj.optString("explanationEn", "")),
                            subject = qObj.optString("subject", test.subject),
                            chapter = qObj.optString("chapter", test.chapter),
                            topic = qObj.optString("topic", test.topic),
                            difficulty = qObj.optString("difficulty", "Medium"),
                            questionTextHi = qObj.optString("questionTextHi", "").takeIf { it.isNotEmpty() },
                            optionAHi = qObj.optString("optionAHi", "").takeIf { it.isNotEmpty() },
                            optionBHi = qObj.optString("optionBHi", "").takeIf { it.isNotEmpty() },
                            optionCHi = qObj.optString("optionCHi", "").takeIf { it.isNotEmpty() },
                            optionDHi = qObj.optString("optionDHi", "").takeIf { it.isNotEmpty() },
                            explanationHi = qObj.optString("explanationHi", "").takeIf { it.isNotEmpty() },
                            questionType = qObj.optString("questionType", "MCQ"),
                            section = qObj.optString("section", "SECTION_A"),
                            needsImage = qObj.optBoolean("needsImage", false),
                            imageUrl = qObj.optString("imageUrl", "").takeIf { it.isNotEmpty() }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (parsedList.isEmpty()) {
            val dbQuestions = viewModel.getQuestionsForTestDirect(test.id)
            if (dbQuestions.isNotEmpty()) {
                parsedList.addAll(dbQuestions)
            }
        }

        questions = parsedList
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Modal Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = test.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Direct Local JSON Renderer • No AI API",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Language Toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isHindiMode) "हिंदी" else "ENG",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isHindiMode,
                            onCheckedChange = { isHindiMode = it },
                            modifier = Modifier.height(24.dp)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (questions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No questions found in JSON or local database.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val currentQ = questions.getOrNull(currentIndex) ?: questions[0]
                    val userSelectedOpt = selectedAnswers[currentQ.id]
                    val showExpl = checkedExplanations[currentQ.id] == true

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Question Header Progress
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Q ${currentIndex + 1} of ${questions.size}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            if (currentQ.section.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = currentQ.section,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Question Text (Bilingual)
                        val qText = if (isHindiMode && !currentQ.questionTextHi.isNullOrBlank()) {
                            currentQ.questionTextHi
                        } else {
                            currentQ.questionText
                        }

                        Text(
                            text = qText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )

                        // Render Image if available
                        if (!currentQ.imageUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                AsyncImage(
                                    model = currentQ.imageUrl,
                                    contentDescription = "Question Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .padding(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Options A, B, C, D
                        val optionA = if (isHindiMode && !currentQ.optionAHi.isNullOrBlank()) currentQ.optionAHi else currentQ.optionA
                        val optionB = if (isHindiMode && !currentQ.optionBHi.isNullOrBlank()) currentQ.optionBHi else currentQ.optionB
                        val optionC = if (isHindiMode && !currentQ.optionCHi.isNullOrBlank()) currentQ.optionCHi else currentQ.optionC
                        val optionD = if (isHindiMode && !currentQ.optionDHi.isNullOrBlank()) currentQ.optionDHi else currentQ.optionD

                        val optionList = listOf(
                            "A" to optionA,
                            "B" to optionB,
                            "C" to optionC,
                            "D" to optionD
                        )

                        optionList.forEach { (key, valText) ->
                            val isSelected = userSelectedOpt == key
                            val isCorrect = key.equals(currentQ.correctAnswer, ignoreCase = true)

                            val cardBg = when {
                                showExpl && isCorrect -> Color(0xFFDCFCE7)
                                showExpl && isSelected && !isCorrect -> Color(0xFFFEE2E2)
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            }

                            val borderClr = when {
                                showExpl && isCorrect -> Color(0xFF22C55E)
                                showExpl && isSelected && !isCorrect -> Color(0xFFEF4444)
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        selectedAnswers[currentQ.id] = key
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = cardBg,
                                border = BorderStroke(1.2.dp, borderClr)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = key,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = valText,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Toggle Solution / Explanation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                checkedExplanations[currentQ.id] = !(checkedExplanations[currentQ.id] ?: false)
                            }) {
                                Icon(
                                    if (showExpl) Icons.Default.VisibilityOff else Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (showExpl) "Hide Explanation" else "Show Answer & Explanation")
                            }

                            if (userSelectedOpt != null) {
                                Text(
                                    text = "Selected: ($userSelectedOpt)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (showExpl) {
                            val explText = if (isHindiMode && !currentQ.explanationHi.isNullOrBlank()) {
                                currentQ.explanationHi
                            } else {
                                currentQ.explanation
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Correct Answer: (${currentQ.correctAnswer})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = explText,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Bottom Navigation / Save to Memory Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { if (currentIndex > 0) currentIndex-- },
                            enabled = currentIndex > 0,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Previous")
                        }

                        // Save Attempt Result to Memory
                        Button(
                            onClick = {
                                isSubmittingAttempt = true
                                viewModel.saveLocalTestAttemptToMemory(
                                    test = test,
                                    questions = questions,
                                    answersMap = selectedAnswers.toMap(),
                                    onComplete = {
                                        isSubmittingAttempt = false
                                        onDismiss()
                                    }
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981)
                            )
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Attempt to Memory", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { if (currentIndex < questions.size - 1) currentIndex++ },
                            enabled = currentIndex < questions.size - 1,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Next")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportPreformattedJsonDialog(
    viewModel: NEETViewModel,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("Physics") }
    var jsonText by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val sampleJson = """[
  {
    "id": "p_q1",
    "questionText": "What is the SI unit of electric current?",
    "optionA": "Ampere",
    "optionB": "Volt",
    "optionC": "Ohm",
    "optionD": "Watt",
    "correctAnswer": "A",
    "explanation": "Ampere (A) is the SI base unit of electric current.",
    "subject": "Physics",
    "imageUrl": ""
  }
]"""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Pre-formatted JSON Test", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Paste pre-formatted JSON containing questions, options, correct answers & images. The app renders and saves it into local Room memory without AI API calls.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Test Title") },
                    placeholder = { Text("e.g. Physics Chapter 1 Private Practice") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it }
                    )
                    Text("Share publicly to community (default: Private Vault)", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("JSON Payload:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { jsonText = sampleJson }) {
                        Text("Fill Sample JSON", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    placeholder = { Text("Paste JSON array here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (jsonText.isBlank()) {
                        errorMessage = "Please enter or paste JSON payload"
                        return@Button
                    }
                    isSaving = true
                    viewModel.savePreformattedJsonTest(
                        title = title,
                        subject = subject,
                        jsonString = jsonText,
                        isPublic = isPublic,
                        onSuccess = {
                            isSaving = false
                            onDismiss()
                        },
                        onError = { err ->
                            isSaving = false
                            errorMessage = err
                        }
                    )
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save to Vault & Memory")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
