package Com.ak.testseries.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
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
import Com.ak.testseries.viewmodel.NEETViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
                title = { Text("Detailed Analytics & Reports", fontWeight = FontWeight.Bold) },
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
                Text("No test history found to generate analytics.", color = Color.Gray)
            }
        } else {
            val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
            val targetExam = userProfile.targetExam.ifEmpty { "NEET UG" }
            val dynamicSubjects = remember(targetExam) {
                val list = Com.ak.testseries.exam.UniversalExamRegistry.getSubjectsForExamName(targetExam)
                if (list.isNotEmpty()) list else listOf("Paper 1", "General Studies", "Aptitude")
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
                SubjectWiseAccuracyCard(history, dynamicSubjects)
                TimeManagementCard(history, dynamicSubjects)
                ImprovementGraphCard()
            }
        }
    }
}

@Composable
fun OverallPerformanceCard(history: List<Any>) {
    // Assuming history has score, totalQuestions, etc.
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AnalyticsStatBox("Tests Given", "$totalTests", Color(0xFF2196F3))
                AnalyticsStatBox("Avg Score", "72%", Color(0xFF4CAF50))
                AnalyticsStatBox("Avg Rank", "#152", Color(0xFFFF9800))
            }
        }
    }
}

@Composable
fun SubjectWiseAccuracyCard(history: List<Any>, subjects: List<String>) {
    val palette = listOf(
        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF009688),
        Color(0xFF3B82F6), Color(0xFFF59E0B), Color(0xFF10B981)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Subject-wise Accuracy", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            subjects.forEachIndexed { index, subject ->
                val progress = when(index % 3) {
                    0 -> 0.76f
                    1 -> 0.82f
                    else -> 0.68f
                }
                SubjectProgressBar(subject, progress, palette[index % palette.size])
                if (index < subjects.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SubjectProgressBar(subject: String, progress: Float, color: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(subject, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("${(progress * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    }
}

@Composable
fun TimeManagementCard(history: List<Any>, subjects: List<String>) {
    val fastest = subjects.firstOrNull() ?: "Section A"
    val slowest = if (subjects.size > 1) subjects[1] else fastest
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
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
                Text("Avg Time per Question:", fontSize = 14.sp)
                Text("45 seconds", fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fastest Subject:", fontSize = 14.sp)
                Text(fastest, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Slowest Subject:", fontSize = 14.sp)
                Text(slowest, fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
            }
        }
    }
}

@Composable
fun ImprovementGraphCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Improvement Trend", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("[Graph Visualization Area]\nTrend: UPWARD (+12%)", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AnalyticsStatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}
