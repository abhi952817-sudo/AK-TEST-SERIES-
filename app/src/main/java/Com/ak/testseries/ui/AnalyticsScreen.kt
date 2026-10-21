package Com.ak.testseries.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import Com.ak.testseries.data.AttemptEntity
import Com.ak.testseries.viewmodel.NEETViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NEETViewModel
) {
    val history by viewModel.attemptHistory.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance & Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No test attempts found to generate analytics.", color = Color.Gray)
            }
        } else {
            val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
            val targetExam = userProfile.targetExam.ifEmpty { "NEET UG" }
            val dynamicSubjects = remember(targetExam) {
                val list = Com.ak.testseries.exam.UniversalExamRegistry.getSubjectsForExamName(targetExam)
                if (list.isNotEmpty()) list else listOf("Physics", "Chemistry", "Biology")
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OverallPerformanceCard(history)
                QuestionBreakdownCard(history)
                TimeManagementCard(history)
                ImprovementTrendCard(history)
                ActionableGuidanceCard(history)
            }
        }
    }
}

@Composable
fun OverallPerformanceCard(history: List<AttemptEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Overall Performance", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            val totalTests = history.size
            val avgScorePct = if (totalTests > 0) {
                history.map { if (it.maxScore > 0) (it.score.toDouble() / it.maxScore) * 100 else 0.0 }.average().toInt()
            } else 0
            val avgAccuracy = if (totalTests > 0) {
                history.map { it.accuracy }.average().toInt()
            } else 0

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AnalyticsStatBox("Tests Taken", "$totalTests", Color(0xFF2196F3))
                AnalyticsStatBox("Avg Score", "$avgScorePct%", Color(0xFF4CAF50))
                AnalyticsStatBox("Avg Accuracy", "$avgAccuracy%", Color(0xFFFF9800))
            }

            Spacer(modifier = Modifier.height(12.dp))
            val verifiedCount = history.count { it.verifiedServerSide }
            Text(
                text = "✓ $verifiedCount of $totalTests attempts verified server-side.",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun QuestionBreakdownCard(history: List<AttemptEntity>) {
    val totalCorrect = history.sumOf { it.correctCount }
    val totalIncorrect = history.sumOf { it.incorrectCount }
    val totalSkipped = history.sumOf { it.skippedCount }
    val totalAnswered = totalCorrect + totalIncorrect + totalSkipped

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Question Outcomes Breakdown", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (totalAnswered > 0) {
                val correctPct = (totalCorrect.toFloat() / totalAnswered)
                val incorrectPct = (totalIncorrect.toFloat() / totalAnswered)
                val skippedPct = (totalSkipped.toFloat() / totalAnswered)

                OutcomeProgressBar("Correct Answers", totalCorrect, correctPct, Color(0xFF4CAF50))
                Spacer(modifier = Modifier.height(10.dp))
                OutcomeProgressBar("Incorrect Answers", totalIncorrect, incorrectPct, Color(0xFFF44336))
                Spacer(modifier = Modifier.height(10.dp))
                OutcomeProgressBar("Skipped / Unattempted", totalSkipped, skippedPct, Color(0xFF9E9E9E))
            } else {
                Text("No question attempts recorded yet.", fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun OutcomeProgressBar(label: String, count: Int, ratio: Float, color: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("$count (${(ratio * 100).toInt()}%)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    }
}

@Composable
fun TimeManagementCard(history: List<AttemptEntity>) {
    val totalTimeSeconds = history.sumOf { it.timeSpentSeconds }
    val totalAttemptedQuestions = history.sumOf { it.correctCount + it.incorrectCount }
    val avgSecondsPerQuestion = if (totalAttemptedQuestions > 0) totalTimeSeconds / totalAttemptedQuestions else 0L
    val avgMinutesPerTest = if (history.isNotEmpty()) (totalTimeSeconds / history.size) / 60 else 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Time Management", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Avg Time per Attempted Question:", fontSize = 14.sp)
                Text(if (avgSecondsPerQuestion > 0) "$avgSecondsPerQuestion sec" else "< 1 sec", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Avg Test Session Duration:", fontSize = 14.sp)
                Text("$avgMinutesPerTest min", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ImprovementTrendCard(history: List<AttemptEntity>) {
    val sorted = remember(history) { history.sortedBy { it.dateTaken } }
    val trendText = remember(sorted) {
        if (sorted.size >= 2) {
            val half = sorted.size / 2
            val firstHalfAvg = sorted.take(half).map { it.accuracy }.average()
            val secondHalfAvg = sorted.drop(half).map { it.accuracy }.average()
            val delta = secondHalfAvg - firstHalfAvg
            if (delta > 2.0) {
                "▲ Upward trend (+${delta.toInt()}% accuracy in recent tests)"
            } else if (delta < -2.0) {
                "▼ Decreased (${delta.toInt()}% accuracy in recent tests)"
            } else {
                "► Stable accuracy across test sessions"
            }
        } else {
            "Complete more tests to establish a long-term performance trend."
        }
    }

    val lastAttemptDate = remember(sorted) {
        if (sorted.isNotEmpty()) {
            val date = Date(sorted.last().dateTaken)
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(date)
        } else ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Improvement Trend", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(trendText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            if (lastAttemptDate.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Last evaluated attempt: $lastAttemptDate", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun ActionableGuidanceCard(history: List<AttemptEntity>) {
    val totalCorrect = history.sumOf { it.correctCount }
    val totalIncorrect = history.sumOf { it.incorrectCount }
    val totalSkipped = history.sumOf { it.skippedCount }

    val recommendation = remember(totalCorrect, totalIncorrect, totalSkipped) {
        when {
            totalIncorrect > totalCorrect ->
                "Focus on eliminating negative marking: reduce blind guessing on questions with low confidence to immediately boost your net score."
            totalSkipped > totalCorrect ->
                "Expand question coverage: your skip rate is high. Targeted chapter-wise revision will increase your total attempted questions."
            history.any { it.accuracy >= 80.0 } ->
                "Strong foundational accuracy! Continue taking full-length timed mock tests to enhance speed under realistic exam conditions."
            else ->
                "Maintain steady revision: review incorrect answers post-submission and complete weekly full syllabus practice."
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Rank Improvement Guidance", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(recommendation, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun AnalyticsStatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}
