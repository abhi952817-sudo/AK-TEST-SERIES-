package Com.ak.testseries.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import Com.ak.testseries.data.AttemptEntity
import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.viewmodel.NEETViewModel
import Com.ak.testseries.viewmodel.AppLanguage
import Com.ak.testseries.exam.UniversalExamRegistry

@Composable
fun FullNewHomeHeader(viewModel: NEETViewModel, onCategoryClick: (String) -> Unit) {
    val tests by viewModel.testsList.collectAsStateWithLifecycle()
    val attempts by viewModel.attemptHistory.collectAsStateWithLifecycle()
    val userRank by viewModel.userRank.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val totalTests = tests.size.toString()
    val totalAttempted = attempts.size.toString()
    
    val overallAccuracy = if (attempts.isNotEmpty()) {
        val totalCorrect = attempts.sumOf { it.correctCount }
        val totalAns = attempts.sumOf { it.correctCount + it.incorrectCount }
        if (totalAns > 0) ((totalCorrect.toFloat() / totalAns) * 100).toInt().toString() + "%" else "0%"
    } else "0%"

    val currentRank = if (userRank != "-") "#$userRank" else "-"
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Batch1ExamSelectorBar(viewModel = viewModel)
        Spacer(modifier = Modifier.height(16.dp))
        NoTestsCard(
            latestTest = tests.firstOrNull(),
            onSaveClick = { viewModel.setTab(Com.ak.testseries.viewmodel.HomeTab.SAVED_TESTS) },
            onShareClick = {
                val sendIntent: android.content.Intent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out AK TEST SERIES! Download the app to try it out. \n\nhttps://play.google.com/store/apps/details?id=Com.ak.testseries")
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                context.startActivity(shareIntent)
            },
            onStartTest = { test ->
                viewModel.initiateTest(test)
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        StatCardsRow(total = totalTests, attempted = totalAttempted, accuracy = overallAccuracy, rank = currentRank)
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Test Categories", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onCategoryClick("All") }) {
                Text("सभी देखें", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        CategoryCardsRow(onCategoryClick)
    }
}

@Composable
fun NoTestsCard(latestTest: Com.ak.testseries.data.TestEntity?, onSaveClick: () -> Unit, onShareClick: () -> Unit, onStartTest: (Com.ak.testseries.data.TestEntity) -> Unit) {
    val cyan = MaterialTheme.colorScheme.primary
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, cyan.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background)))
        ) {
            // Background glowing dots or grid could go here if needed.
            
            Column(modifier = Modifier.padding(16.dp)) {
                // Top Row: Save Test | Live | Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Save Test Button
                    Surface(
                        modifier = Modifier.clickable { onSaveClick() },
                        color = Color(0xFF004D40).copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, cyan.copy(alpha = 0.6f))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.BookmarkBorder, contentDescription = null, tint = cyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("सेव टेस्ट", color = cyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Live indicator
                        Surface(
                            color = Color(0xFF004D40).copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF00FF00).copy(alpha = 0.5f))
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Sensors, contentDescription = null, tint = Color(0xFF00FF00), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LIVE (0)", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(Icons.Rounded.Shortcut, contentDescription = "Share", tint = cyan, modifier = Modifier.size(24.dp).clickable { onShareClick() })
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Titles
                Text("No Tests Available", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("0 Questions", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color.Gray))
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("0H 0M", color = Color.LightGray, fontSize = 12.sp)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Attempts: 0 / 3", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Stats Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // Circular Progress
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = Color.DarkGray,
                            strokeWidth = 4.dp,
                            strokeCap = StrokeCap.Round
                        )
                        CircularProgressIndicator(
                            progress = { 0f },
                            modifier = Modifier.fillMaxSize(),
                            color = cyan,
                            strokeWidth = 4.dp,
                            strokeCap = StrokeCap.Round
                        )
                        // Inner ring
                        Box(modifier = Modifier.size(56.dp).border(1.dp, cyan.copy(alpha=0.5f), CircleShape))
                        Text("0%", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    // 4 Stat Columns
                    StatColumn(Icons.Rounded.Group, Color(0xFF2196F3), "0", "Students\nJoined")
                    StatColumn(Icons.Rounded.Assignment, Color(0xFFA020F0), "0", "Tests\nAttempted")
                    StatColumn(Icons.Rounded.GpsFixed, Color(0xFF00FF00), "0%", "Accuracy")
                    StatColumn(Icons.Rounded.EmojiEvents, Color(0xFFFFD700), "-", "Best Score")
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Live Test Banner
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, cyan.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(cyan.copy(alpha=0.1f), Color.Transparent)))
                            .padding(12.dp), 
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CellTower, contentDescription = null, tint = cyan, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Be the first to join!", color = cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Get real-time rank & performance", color = Color.LightGray, fontSize = 10.sp)
                            }
                        }
                        Surface(
                            color = Color(0xFF014B5A),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, cyan),
                            modifier = Modifier.clickable { latestTest?.let { onStartTest(it) } }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Join Live Test", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Overall Progress
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Overall Progress", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.height(4.dp).weight(1f).background(cyan, RoundedCornerShape(2.dp)))
                        for (i in 0..5) {
                            Box(modifier = Modifier.height(4.dp).weight(1f).background(Color.DarkGray, RoundedCornerShape(2.dp)))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("0%", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun StatColumn(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent), radius = 50f), CircleShape)
                .border(1.dp, Brush.linearGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.LightGray, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 11.sp)
    }
}

@Composable
fun StatCardsRow(total: String, attempted: String, accuracy: String, rank: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallStatCard(Modifier.weight(1f), Icons.Rounded.Assignment, Color(0xFF2196F3), total, "Total Tests")
        SmallStatCard(Modifier.weight(1f), Icons.Rounded.VerifiedUser, Color(0xFF00FF00), attempted, "Attempted")
        SmallStatCard(Modifier.weight(1f), Icons.Rounded.GpsFixed, Color(0xFF00E5FF), accuracy, "Accuracy")
        SmallStatCard(Modifier.weight(1f), Icons.Rounded.EmojiEvents, Color(0xFFFFD700), rank, "Rank")
    }
}

@Composable
fun SmallStatCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)))) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent), radius = 60f),
                            shape = CircleShape
                        )
                        .border(1.dp, Brush.linearGradient(listOf(color.copy(alpha = 0.6f), Color.Transparent)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(label, color = Color.LightGray, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun Batch1ExamSelectorBar(viewModel: NEETViewModel) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val currentTargetExam = userProfile.targetExam.ifBlank { "NEET UG" }
    var showAllExamsDialog by remember { mutableStateOf(false) }

    val defaultPrimary = MaterialTheme.colorScheme.primary
    val examDetails = remember(currentTargetExam, defaultPrimary) {
        when {
            currentTargetExam.contains("NEET", ignoreCase = true) -> Triple("NEET UG 2026", "चिकित्सा प्रवेश परीक्षा • 180 Qs • 720 Marks", Color(0xFF10B981))
            currentTargetExam.contains("JEE", ignoreCase = true) -> Triple("JEE Main 2026", "इंजीनियरिंग प्रवेश परीक्षा • 75 Qs • 300 Marks", Color(0xFF3B82F6))
            currentTargetExam.contains("CUET", ignoreCase = true) -> Triple("CUET UG 2026", "विश्वविद्यालय प्रवेश परीक्षा • 50 Qs • 250 Marks", Color(0xFF8B5CF6))
            currentTargetExam.contains("CAT", ignoreCase = true) -> Triple("CAT 2026", "आईआईएम मैनेजमेंट परीक्षा • 66 Qs • 198 Marks", Color(0xFFEC4899))
            currentTargetExam.contains("CGL", ignoreCase = true) -> Triple("SSC CGL (Tier-1)", "कर्मचारी चयन आयोग • 100 Qs • 200 Marks", Color(0xFFF59E0B))
            currentTargetExam.contains("CHSL", ignoreCase = true) -> Triple("SSC CHSL", "कर्मचारी चयन आयोग • 100 Qs • 200 Marks", Color(0xFF06B6D4))
            currentTargetExam.contains("MTS", ignoreCase = true) -> Triple("SSC MTS", "मल्टी-टास्किंग स्टाफ • 90 Qs • 270 Marks", Color(0xFFEF4444))
            currentTargetExam.contains("GD", ignoreCase = true) -> Triple("SSC GD Constable", "केंद्रीय सशस्त्र पुलिस बल • 80 Qs • 160 Marks", Color(0xFF6366F1))
            currentTargetExam.contains("NTPC", ignoreCase = true) -> Triple("RRB NTPC (CBT-1)", "रेलवे गैर-तकनीकी • 100 Qs • 100 Marks", Color(0xFF059669))
            currentTargetExam.contains("Group D", ignoreCase = true) || currentTargetExam.contains("Level 1", ignoreCase = true) -> Triple("RRB Group D", "रेलवे ग्रुप डी परीक्षा • 100 Qs • 100 Marks", Color(0xFFEA580C))
            currentTargetExam.contains("ALP", ignoreCase = true) -> Triple("RRB ALP (CBT-1)", "सहायक लोको पायलट • 75 Qs • 75 Marks", Color(0xFF4F46E5))
            currentTargetExam.contains("JE", ignoreCase = true) -> Triple("RRB JE", "रेलवे जूनियर इंजीनियर • 100 Qs • 100 Marks", Color(0xFFE11D48))
            currentTargetExam.contains("SBI PO", ignoreCase = true) -> Triple("SBI PO (Prelims)", "भारतीय स्टेट बैंक पीओ • 100 Qs • 100 Marks", Color(0xFF0284C7))
            currentTargetExam.contains("SBI Clerk", ignoreCase = true) -> Triple("SBI Clerk (Prelims)", "एसबीआई जूनियर एसोसिएट • 100 Qs • 100 Marks", Color(0xFF0EA5E9))
            currentTargetExam.contains("IBPS PO", ignoreCase = true) -> Triple("IBPS PO (Prelims)", "आईबीपीएस पीओ • 100 Qs • 100 Marks", Color(0xFF4338CA))
            currentTargetExam.contains("IBPS Clerk", ignoreCase = true) -> Triple("IBPS Clerk (Prelims)", "आईबीपीएस क्लर्क • 100 Qs • 100 Marks", Color(0xFF7C3AED))
            currentTargetExam.contains("CTET", ignoreCase = true) -> Triple("CTET 2026", "केंद्रीय शिक्षक पात्रता • 150 Qs • 150 Marks", Color(0xFF0D9488))
            currentTargetExam.contains("NET", ignoreCase = true) -> Triple("UGC NET 2026", "सहायक प्रोफेसर व जेआरएफ • 150 Qs • 300 Marks", Color(0xFF7C3AED))
            currentTargetExam.contains("CPO", ignoreCase = true) -> Triple("SSC CPO (Paper-1)", "दिल्ली पुलिस व सीएपीएफ एसआई • 200 Qs • 200 Marks", Color(0xFFD97706))
            currentTargetExam.contains("Tech", ignoreCase = true) -> Triple("RRB Technician III", "आरआरबी तकनीशियन • 100 Qs • 100 Marks", Color(0xFF64748B))
            else -> Triple(currentTargetExam, "राष्ट्रीय प्रतियोगी परीक्षा", defaultPrimary)
        }
    }

    // Clean, elegant Target Exam Hero Card (No crowded 20 buttons on home screen)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = examDetails.third.copy(alpha = 0.08f)),
        border = BorderStroke(1.2.dp, examDetails.third.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(examDetails.third.copy(alpha = 0.18f), CircleShape)
                        .border(1.dp, examDetails.third.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = null,
                        tint = examDetails.third,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (selectedLanguage == AppLanguage.HINDI) "लक्षित परीक्षा" else "Target Exam",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = examDetails.third
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                    }
                    Text(
                        text = examDetails.first,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = examDetails.second,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = examDetails.third.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, examDetails.third.copy(alpha = 0.4f)),
                modifier = Modifier.clickable { showAllExamsDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedLanguage == AppLanguage.HINDI) "बदलें ▾" else "Change ▾",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = examDetails.third
                    )
                }
            }
        }
    }

    if (showAllExamsDialog) {
        val all20Exams = remember { UniversalExamRegistry.getAllExams() }
        AlertDialog(
            onDismissRequest = { showAllExamsDialog = false },
            title = {
                Text(
                    text = if (selectedLanguage == AppLanguage.HINDI) "अपनी प्रतियोगी परीक्षा चुनें" else "Choose Your Target Exam",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    Text(
                        text = if (selectedLanguage == AppLanguage.HINDI) 
                            "20 राष्ट्रीय व राज्य स्तरीय प्रतियोगी परीक्षाओं का पूरा संग्रह:" 
                        else 
                            "Universal catalog of 20 National & State competitive exams:",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(all20Exams) { exam ->
                            val isCurrent = currentTargetExam.equals(exam.examName, ignoreCase = true)
                            val isBatch1 = exam.examName in listOf("NEET UG", "JEE Main", "CUET UG", "CAT")
                            val isBatch2 = exam.examName.contains("SSC") && (
                                    exam.examName.contains("CGL") || 
                                    exam.examName.contains("CHSL") || 
                                    exam.examName.contains("MTS") || 
                                    exam.examName.contains("GD")
                            )
                            val isBatch3 = exam.examName.contains("RRB") || 
                                    exam.examName.contains("Railway") || 
                                    exam.examName.contains("NTPC") || 
                                    exam.examName.contains("Group D") || 
                                    exam.examName.contains("ALP") || 
                                    exam.examName.contains("JE")
                            val isBatch4 = exam.examName.contains("SBI") || 
                                    exam.examName.contains("IBPS") || 
                                    exam.category.contains("Banking", ignoreCase = true)
                            val isBatch5 = exam.examName.contains("CTET") ||
                                    exam.examName.contains("NET") ||
                                    exam.examName.contains("CPO") ||
                                    exam.examName.contains("Technician") ||
                                    exam.examName.contains("Tech")

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setTargetExam(exam.examName)
                                        showAllExamsDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = exam.examName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isBatch1) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Batch 1",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF10B981),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else if (isBatch2) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Batch 2",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFFF59E0B),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else if (isBatch3) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF059669).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Batch 3",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF059669),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else if (isBatch4) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF0284C7).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Batch 4",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF0284C7),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else if (isBatch5) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF0D9488).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Batch 5",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF0D9488),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${exam.category} • ${exam.shortName}",
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            maxLines = 1
                                        )
                                    }
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllExamsDialog = false }) {
                    Text(if (selectedLanguage == AppLanguage.HINDI) "बंद करें" else "Close")
                }
            }
        )
    }
}

@Composable
fun CategoryCardsRow(onCategoryClick: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonCategoryCard(Modifier.weight(1f), "Full Tests", "0 Tests", Icons.Rounded.MenuBook, Color(0xFFA020F0), { onCategoryClick("Full Syllabus") })
        NeonCategoryCard(Modifier.weight(1f), "Part Tests", "0 Tests", Icons.Rounded.EditNote, Color(0xFF2196F3), { onCategoryClick("Part Test") })
        NeonCategoryCard(Modifier.weight(1f), "PYQ Tests", "0 Tests", Icons.Rounded.Event, Color(0xFF00FF00), { onCategoryClick("PYQ") })
        NeonCategoryCard(Modifier.weight(1f), "Booster Tests", "0 Tests", Icons.Rounded.RocketLaunch, Color(0xFFFF5722), { onCategoryClick("Booster") })
    }
}

@Composable
fun NeonCategoryCard(modifier: Modifier, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(110.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), MaterialTheme.colorScheme.surface)))) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent), radius = 70f),
                            shape = CircleShape
                        )
                        .border(1.dp, Brush.linearGradient(listOf(color.copy(alpha = 0.8f), Color.Transparent)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 14.sp)
                Text(subtitle, color = Color.LightGray, fontSize = 9.sp)
            }
        }
    }
}


