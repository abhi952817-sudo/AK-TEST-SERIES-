package Com.ak.testseries.ui

import Com.ak.testseries.stringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.data.AttemptEntity
import Com.ak.testseries.data.SyllabusData
import Com.ak.testseries.viewmodel.NEETViewModel
import Com.ak.testseries.viewmodel.AppLanguage
import Com.ak.testseries.BilingualText
import Com.ak.testseries.ui.TestCard

data class SubjectProgress(
    val subject: String,
    val completedChapters: Int,
    val totalChapters: Int,
    val percentage: Float
)

@Composable
fun calculateSyllabusProgress(
    tests: List<TestEntity>,
    attempts: List<AttemptEntity>,
    targetExam: String = "NEET"
): Map<String, SubjectProgress> {
    val attemptedTestIds = remember(attempts) { attempts.map { it.testId }.toSet() }
    val attemptedTests = remember(tests, attemptedTestIds) { tests.filter { it.id in attemptedTestIds } }
    
    val attemptedChaptersMap = remember(attemptedTests) {
        val map = mutableMapOf<String, MutableSet<String>>()
        attemptedTests.forEach { test ->
            if (test.subject.isNotBlank() && test.chapter.isNotBlank() && test.chapter != "All") {
                map.getOrPut(test.subject) { mutableSetOf() }.add(test.chapter)
            }
        }
        map
    }
    
    val totalChaptersMap = remember(targetExam) {
        val map = mutableMapOf<String, MutableSet<String>>()
        val examSyllabus = SyllabusData.getSyllabusForExam(targetExam, "12th")
        examSyllabus.forEach { (subject, chapters) ->
            map.getOrPut(subject) { mutableSetOf() }.addAll(chapters)
        }
        map
    }
    
    val subjects = remember(targetExam) {
        SyllabusData.getSubjectsForExam(targetExam)
    }
    
    return remember(attempts, tests, targetExam) {
        subjects.associateWith { subject ->
            val totalChapters = totalChaptersMap[subject]?.size ?: 1
            val completedChapters = attemptedChaptersMap[subject]?.size ?: 0
            val percentage = if (totalChapters > 0) (completedChapters.toFloat() / totalChapters) else 0f
            
            SubjectProgress(
                subject = subject,
                completedChapters = completedChapters,
                totalChapters = totalChapters,
                percentage = percentage
            )
        }
    }
}

@Composable
fun SyllabusProgressDashboard(
    tests: List<TestEntity>,
    attempts: List<AttemptEntity>,
    targetExam: String = "NEET",
    modifier: Modifier = Modifier
) {
    val progressMap = calculateSyllabusProgress(tests, attempts, targetExam)
    val totalChaptersAll = progressMap.values.sumOf { it.totalChapters }
    val completedChaptersAll = progressMap.values.sumOf { it.completedChapters }
    val overallPercentage = if (totalChaptersAll > 0) (completedChaptersAll.toFloat() / totalChaptersAll) else 0f
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Syllabus Completion Dashboard",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "$completedChaptersAll of $totalChaptersAll chapters prepared",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF512F), Color(0xFFDD2476))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${(overallPercentage * 100).toInt()}% Total",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            LinearProgressIndicator(
                progress = { overallPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF9333EA),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val examConfig = Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(targetExam)
            val dynamicSubjects = examConfig?.stages?.flatMap { it.papers.flatMap { p -> p.subjects.map { s -> s.subjectName } } }?.distinct()
            val subjects = if (!dynamicSubjects.isNullOrEmpty()) dynamicSubjects else Com.ak.testseries.exam.UniversalExamRegistry.getSubjectsForExamName(targetExam)
            val palette = listOf(
                Pair(Color(0xFF3B82F6), Icons.Rounded.Bolt),
                Pair(Color(0xFFF59E0B), Icons.Rounded.Science),
                Pair(Color(0xFFEC4899), Icons.Rounded.Calculate),
                Pair(Color(0xFF10B981), Icons.Rounded.Spa),
                Pair(Color(0xFF8B5CF6), Icons.Rounded.MenuBook),
                Pair(Color(0xFF06B6D4), Icons.Rounded.AutoAwesome)
            )

            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subjects.size) { index ->
                    val subject = subjects[index]
                    val progress = progressMap[subject] ?: SubjectProgress(subject, 0, 1, 0f)
                    val (subjectColor, subjectIcon) = when (subject) {
                        "Physics" -> Pair(Color(0xFF3B82F6), Icons.Rounded.Bolt)
                        "Chemistry" -> Pair(Color(0xFFF59E0B), Icons.Rounded.Science)
                        "Mathematics", "Maths", "Quantitative Aptitude" -> Pair(Color(0xFFEC4899), Icons.Rounded.Calculate)
                        "Biology", "Botany", "Zoology" -> Pair(Color(0xFF10B981), Icons.Rounded.Spa)
                        else -> palette[index % palette.size]
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(subjectColor.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = subjectIcon,
                                        contentDescription = subject,
                                        tint = subjectColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = subject,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "${progress.completedChapters}/${progress.totalChapters} Ch",
                                fontSize = 10.sp,
                                color = Color.LightGray
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            LinearProgressIndicator(
                                progress = { progress.percentage },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = subjectColor,
                                trackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                            
                            Spacer(modifier = Modifier.height(2.dp))
                            
                            Text(
                                text = "${(progress.percentage * 100).toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = subjectColor,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NcertTrackerScreen(viewModel: NEETViewModel) {
    val tests by viewModel.testsList.collectAsStateWithLifecycle()
    val attempts by viewModel.attemptHistory.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    
    val recentTests = tests.sortedByDescending { it.timestamp }.take(10)
    var selectedSection by remember { mutableStateOf("11th") }
    val sections = listOf("11th", "12th", stringRes("Dropper"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // NCERT Tracker Header
        val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
        val targetExam = userProfile.targetExam
        val examConfig = Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(targetExam)
        val examLabel = examConfig?.shortName ?: if (targetExam.isNotBlank()) targetExam else "Exam"

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                "$examLabel Syllabus Tracker",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Chapter & Topic-wise tests for $examLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Progress Dashboard Visualizer
        SyllabusProgressDashboard(tests = tests, attempts = attempts, targetExam = targetExam)
        
        Spacer(modifier = Modifier.height(8.dp))

        // Hierarchical Test Preparation Flow Launch Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⚡ Structured Test Flow",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Exam ➔ Subject ➔ Chapter ➔ Topic ➔ PYQs",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Button(
                    onClick = { viewModel.navigateToScreen(Com.ak.testseries.viewmodel.AppScreen.HIERARCHICAL_TEST_FLOW) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Open Flow", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // TabRow for Class 11, Class 12, Dropper
        TabRow(
            selectedTabIndex = sections.indexOf(selectedSection),
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            sections.forEach { section ->
                Tab(
                    selected = selectedSection == section,
                    onClick = { selectedSection = section },
                    text = { Text(if (section == stringRes("Dropper")) stringRes("Dropper") else "Class $section", fontWeight = FontWeight.Bold) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Section Content
        if (selectedSection == stringRes("Dropper")) {
            DropperSectionContent(tests, selectedLanguage, viewModel)
        } else {
            ClassSectionContent(selectedSection, tests, selectedLanguage, viewModel)
        }
    }
}

@Composable
fun ClassSectionContent(classLevel: String, allTests: List<TestEntity>, selectedLanguage: AppLanguage, viewModel: NEETViewModel) {
    val attempts by viewModel.attemptHistory.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val targetExam = userProfile.targetExam
    val subjects = remember(targetExam, classLevel) {
        SyllabusData.getSyllabusForExam(targetExam, classLevel)
    }
    var selectedSubject by remember { mutableStateOf<String?>(null) }
    var selectedChapter by remember { mutableStateOf<String?>(null) }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        if (selectedSubject == null) {
            items(subjects.keys.toList().size) { index ->
                val subject = subjects.keys.toList()[index]
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { selectedSubject = subject },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(subject, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                }
            }
        } else if (selectedChapter == null) {
            item {
                TextButton(onClick = { selectedSubject = null }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("← Back to Subjects") }
            }
            val chapters = subjects[selectedSubject] ?: return@LazyColumn
            items(chapters.size) { index ->
                val chapter = chapters[index]
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { selectedChapter = chapter },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Text(chapter, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            item {
                TextButton(onClick = { selectedChapter = null }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("← Back to Chapters") }
                Text("Tests for $selectedChapter", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            }
            
            val chapterTests = allTests.filter { 
                it.classLevel == classLevel && 
                it.subject == selectedSubject && 
                it.chapter == selectedChapter &&
                it.testType != "Full Syllabus" && 
                it.testType != "Multi-Chapter"
            }
            
            if (chapterTests.isEmpty()) {
                item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("Tests Coming Soon", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f)) } }
            } else {
                items(chapterTests.size) { index ->
                    val test = chapterTests[index]
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        TestCard(
                        test = test,
                        currentUserAttempts = attempts.count { it.testId == test.id },
                        selectedLanguage = selectedLanguage,
                        onStartTest = { viewModel.initiateTest(test) },
                        onViewSyllabus = { viewModel.viewTestSyllabus(test) },
                        onBookmark = { viewModel.toggleTestBookmark(test) },
                        onDownload = { viewModel.downloadTestOffline(test) }
                    )
                    }
                }
            }
        }
    }
}

@Composable
fun DropperSectionContent(allTests: List<TestEntity>, selectedLanguage: AppLanguage, viewModel: NEETViewModel) {
    val attempts by viewModel.attemptHistory.collectAsStateWithLifecycle()
    val dropperTests = allTests.filter { it.classLevel == stringRes("Dropper") || it.classLevel == "All" || it.testType == "Full Syllabus" || it.testType == "Multi-Chapter" }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)) {
        if (dropperTests.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("Tests Coming Soon", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f)) } }
        } else {
            items(dropperTests.size) { index ->
                val test = dropperTests[index]
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TestCard(
                        test = test,
                        currentUserAttempts = attempts.count { it.testId == test.id },
                        selectedLanguage = selectedLanguage,
                        onStartTest = { viewModel.initiateTest(test) },
                        onViewSyllabus = { viewModel.viewTestSyllabus(test) },
                        onBookmark = { viewModel.toggleTestBookmark(test) },
                        onDownload = { viewModel.downloadTestOffline(test) }
                    )
                }
            }
        }
    }
}
