package Com.ak.testseries.ui

import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.exam.UniversalExamRegistry
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Share-time verification card. Subject choices come only from the exact
 * exam -> stage -> paper entry in the local registry. Missing/ambiguous
 * metadata is shown as a blocker; no generic subject fallback is allowed.
 * The Cloud Function remains authoritative and revalidates every field.
 */
@Composable
fun CommunityShareSubjectDialog(
    test: TestEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val exam = remember(test.examId, test.targetExam) {
        test.examId?.takeIf { it.isNotBlank() }?.let(UniversalExamRegistry::getExamById)
            ?: UniversalExamRegistry.getExam(test.targetExam)
    }
    val stage = remember(exam, test.stageId) {
        when {
            exam == null -> null
            !test.stageId.isNullOrBlank() -> exam.stages.firstOrNull { it.stageId == test.stageId }
            exam.stages.size == 1 -> exam.stages.single()
            else -> null
        }
    }
    val paper = remember(stage, test.paperId) {
        when {
            stage == null -> null
            !test.paperId.isNullOrBlank() -> stage.papers.firstOrNull { it.paperId == test.paperId }
            stage.papers.size == 1 -> stage.papers.single()
            else -> null
        }
    }
    val isFullSyllabus = test.testType.equals("Full Syllabus", ignoreCase = true) ||
        test.subject.equals("Full Syllabus", ignoreCase = true)
    val subjectOptions = remember(paper, isFullSyllabus) {
        when {
            paper == null -> emptyList()
            isFullSyllabus -> listOf("Full Syllabus")
            else -> paper.subjects.map { it.subjectName.trim() }.filter { it.isNotBlank() }.distinct()
        }
    }
    val mappingError = when {
        exam == null -> "यह परीक्षा स्थानीय Exam Registry में नहीं मिली।"
        stage == null -> "इस टेस्ट का Stage ID गायब है या Registry में एक से अधिक Stage हैं। पहले सही Stage metadata जोड़ना जरूरी है।"
        paper == null -> "इस टेस्ट का Paper ID गायब है या चुने हुए Stage में एक से अधिक Paper हैं। बिना सही Paper के शेयर नहीं किया जा सकता।"
        subjectOptions.isEmpty() -> "इस Exam, Stage और Paper के लिए कोई सत्यापित Subject mapping उपलब्ध नहीं है।"
        else -> null
    }

    var selectedSubject by remember(subjectOptions, test.subject) {
        mutableStateOf(
            subjectOptions.firstOrNull { it.equals(test.subject, ignoreCase = true) }
                ?: subjectOptions.singleOrNull().orEmpty()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Community Test Verification") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Exam: ${exam?.examName ?: test.targetExam}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Stage: ${stage?.stageName ?: test.stageId?.ifBlank { "Not set" } ?: "Not set"}")
                Text("Paper: ${paper?.paperName ?: test.paperId?.ifBlank { "Not set" } ?: "Not set"}")
                Spacer(modifier = Modifier.height(12.dp))
                if (mappingError != null) {
                    Text(
                        mappingError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "सुरक्षा के लिए कोई सामान्य Subject सूची नहीं दिखाई जाएगी। Source test की Exam/Stage/Paper जानकारी सही होने के बाद ही शेयर करें।",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("इस टेस्ट के लिए सही Subject चुनें:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    subjectOptions.forEach { subject ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSubject = subject }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSubject == subject,
                                onClick = { selectedSubject = subject }
                            )
                            Text(subject, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Server source questions, ownership, exam policy और marking को दोबारा जाँचेगा। UI में Subject चुनना अपने-आप Academic Verification नहीं है।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = mappingError == null && selectedSubject.isNotBlank(),
                onClick = { onConfirm(selectedSubject) }
            ) {
                Text("Verify & Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
