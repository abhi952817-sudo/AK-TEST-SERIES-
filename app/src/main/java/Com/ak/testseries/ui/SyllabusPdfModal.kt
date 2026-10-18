package Com.ak.testseries.ui

import Com.ak.testseries.stringRes
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.viewmodel.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyllabusPdfModal(
    test: TestEntity,
    language: AppLanguage,
    targetExam: String = "NEET",
    onClose: () -> Unit
) {
    var localLanguage by remember(language) { mutableStateOf(language) }
    val isHindi = localLanguage == AppLanguage.HINDI
    var selectedTab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(test.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { localLanguage = if (isHindi) AppLanguage.ENGLISH else AppLanguage.HINDI }) {
                    Icon(Icons.Default.Translate, contentDescription = "Translate")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(if (isHindi) "पाठ्यक्रम" else "Syllabus", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(if (isHindi) "सुरक्षा विवरण" else "Security & Access", fontSize = 12.sp) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (selectedTab == 0) {
                    val examConfig = Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(targetExam)
                    val examLabel = examConfig?.shortName ?: if (targetExam.isNotBlank()) targetExam else "Exam"
                    val (posMarks, negMarks) = Com.ak.testseries.exam.UniversalExamRegistry.getMarkingSchemeForExam(targetExam)
                    val posStr = if (posMarks % 1.0 == 0.0) "+${posMarks.toInt()}" else "+$posMarks"
                    val negStr = if (negMarks % 1.0 == 0.0) "-${negMarks.toInt()}" else "-$negMarks"

                    Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                        Text(if (isHindi) "पाठ्यक्रम ($examLabel Syllabus)" else "$examLabel Syllabus", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(test.description.ifEmpty { "Syllabus and guidelines for $examLabel" }, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(if (isHindi) "$examLabel नियम और शर्तें" else "$examLabel Rules & Guidelines", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(
                            if (isHindi) "1. स्थिर इंटरनेट कनेक्शन सुनिश्चित करें।\n2. टाइमर समाप्त होने पर परीक्षा स्वतः सबमिट हो जाएगी।"
                            else "1. Ensure a stable internet connection.\n2. The exam will be auto-submitted when the timer ends.",
                            fontSize = 13.sp, color = Color.Gray
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(if (isHindi) "मार्किंग स्कीम ($examLabel Marking Scheme)" else "$examLabel Marking Scheme", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            if (isHindi) "$examLabel मोड: $posStr अंक (सही उत्तर), $negStr अंक (गलत उत्तर), 0 अंक (छोड़ा गया)।"
                            else "$examLabel Mode: $posStr Marks (Correct), $negStr Marks (Incorrect), 0 Marks (Skipped).",
                            fontSize = 13.sp, color = Color.Gray
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(if (isHindi) "प्रश्नों की स्थिति के रंग (Question Status Colors)" else "Question Status Colors", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val indicatorSize = Modifier.size(20.dp)
                        val rowMod = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        
                        Row(modifier = rowMod, verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = indicatorSize.background(Color(0xFF4CAF50), CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isHindi) "सही उत्तर ($posStr अंक)" else "Correct Answer ($posStr Marks)", fontSize = 12.sp)
                        }
                        Row(modifier = rowMod, verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = indicatorSize.background(Color(0xFFF44336), CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isHindi) "गलत उत्तर ($negStr अंक)" else "Incorrect Answer ($negStr Marks)", fontSize = 12.sp)
                        }
                        Row(modifier = rowMod, verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = indicatorSize.background(Color(0xFFFF9800), CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isHindi) "समीक्षा के लिए चिह्नित (0 अंक)" else "Marked for Review (0 Marks)", fontSize = 12.sp)
                        }
                        Row(modifier = rowMod, verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = indicatorSize.background(Color.Gray.copy(alpha = 0.3f), CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isHindi) "छोड़ा गया (0 अंक)" else "Skipped (0 Marks)", fontSize = 12.sp)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.heightIn(min = 200.dp, max = 400.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Internal Secured", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(if (isHindi) "सुरक्षित इन-ऐप टेस्ट सामग्री" else "In-App Secured Test Content", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (isHindi) "सभी प्रश्न, उत्तर विकल्प और विस्तृत समाधान केवल ऐप की आंतरिक UI में प्रस्तुत किए जाते हैं। PDF डाउनलोड एवं फ़ाइल एक्सपोर्ट सुरक्षा के लिए निष्क्रय किए गए हैं।"
                            else "All questions, options and solution explanations are exclusively locked inside the app's internal UI. PDF downloads and external file exports are permanently disabled for data security.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) {
                Text(if (isHindi) "बंद करें" else stringRes("Close"))
            }
        }
    )
}
