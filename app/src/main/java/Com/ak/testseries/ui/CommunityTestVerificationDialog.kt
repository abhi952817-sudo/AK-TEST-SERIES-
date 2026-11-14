package Com.ak.testseries.ui

import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.exam.UniversalExamRegistry
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
 * Requires an explicit exam/subject confirmation before a test is shared to Community.
 * Subject options are restricted to canonical subjects found in the test's question data.
 * The ViewModel performs the same validation again after loading the authoritative questions.
 */
@Composable
fun CommunityTestVerificationDialog(
    test: TestEntity,
    availableSubjects: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val exam = remember(test.targetExam, test.examId) {
        UniversalExamRegistry.findExamByNameOrShortName(test.targetExam)
    }
    val examName = exam?.examName ?: test.targetExam
    val choices = remember(test.id, availableSubjects) { availableSubjects.distinct() }
    var selectedSubject by remember(test.id, choices) {
        mutableStateOf(choices.firstOrNull().orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test Verification Card") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(test.title, style = MaterialTheme.typography.titleMedium)
                        Text("Exam: $examName")
                        Text("Questions: ${test.questionsCount}")
                        Text("Duration: ${test.durationMinutes} minutes")
                        if (test.stageId.isNullOrBlank().not()) Text("Stage: ${test.stageId}")
                        if (test.paperId.isNullOrBlank().not()) Text("Paper: ${test.paperId}")
                    }
                }
                Text(
                    "Select the subject that matches the actual questions in this test. The app checks the question data again before publishing.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (choices.isEmpty()) {
                    Text(
                        "No verified subject mapping is available in this test's question data. Sharing is blocked until the exam and question subjects are corrected.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    choices.forEach { subject ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSubject == subject,
                                onClick = { selectedSubject = subject }
                            )
                            Text(subject)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            Button(
                enabled = selectedSubject.isNotBlank() && choices.contains(selectedSubject),
                onClick = { onConfirm(selectedSubject) }
            ) {
                Text("Verify and Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
