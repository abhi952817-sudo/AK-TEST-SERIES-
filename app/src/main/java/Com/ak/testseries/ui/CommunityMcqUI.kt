package Com.ak.testseries.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

private data class CommunityMcqAttemptResult(
    val selectedOptionIndex: Int,
    val correctOptionIndex: Int,
    val isCorrect: Boolean
)

@Composable
fun CommunityMcqAttemptCard(post: CommunityPost) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val options = post.mcqOptions
    var selectedIndex by remember(post.id) { mutableStateOf<Int?>(null) }
    var result by remember(post.id, uid) { mutableStateOf<CommunityMcqAttemptResult?>(null) }
    var submitting by remember(post.id) { mutableStateOf(false) }
    var error by remember(post.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(post.id, uid) {
        if (uid.isNullOrBlank()) return@LaunchedEffect
        FirebaseFirestore.getInstance()
            .collection("Community_MCQ_Attempts").document(post.id)
            .collection("Users").document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val selected = snapshot.getLong("selectedOptionIndex")?.toInt()
                    val correct = snapshot.getLong("correctOptionIndex")?.toInt()
                    val isCorrect = snapshot.getBoolean("isCorrect")
                    if (selected != null && correct != null && isCorrect != null) {
                        selectedIndex = selected
                        result = CommunityMcqAttemptResult(selected, correct, isCorrect)
                    }
                }
            }
            .addOnFailureListener { error = it.localizedMessage ?: "Your previous attempt could not be loaded." }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Single-question MCQ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (options.size !in 4..5) {
                Text("This MCQ is unavailable because its options are invalid.", color = MaterialTheme.colorScheme.error)
            } else {
                options.forEachIndexed { index, option ->
                    val isCorrectAnswer = result?.correctOptionIndex == index
                    val isSelectedAnswer = result?.selectedOptionIndex == index
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedIndex == index || isSelectedAnswer,
                            onClick = { if (result == null && !submitting) selectedIndex = index },
                            enabled = result == null && !submitting && uid != null
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${index + 1}. $option",
                                color = when {
                                    result != null && isCorrectAnswer -> MaterialTheme.colorScheme.primary
                                    result != null && isSelectedAnswer && !result!!.isCorrect -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (result != null && isCorrectAnswer) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                if (result == null) {
                    Button(
                        onClick = {
                            val optionIndex = selectedIndex
                            if (uid.isNullOrBlank()) {
                                error = "Please sign in to attempt this MCQ."
                            } else if (optionIndex == null) {
                                error = "Select one option first."
                            } else {
                                submitting = true
                                error = null
                                FirebaseFunctions.getInstance().getHttpsCallable("submitCommunityMcqAttempt")
                                    .call(mapOf("postId" to post.id, "optionIndex" to optionIndex))
                                    .addOnSuccessListener { response ->
                                        @Suppress("UNCHECKED_CAST")
                                        val data = response.data as? Map<String, Any?>
                                        val selected = (data?.get("selectedOptionIndex") as? Number)?.toInt()
                                        val correct = (data?.get("correctOptionIndex") as? Number)?.toInt()
                                        val isCorrect = data?.get("isCorrect") as? Boolean
                                        if (selected != null && correct != null && isCorrect != null) {
                                            selectedIndex = selected
                                            result = CommunityMcqAttemptResult(selected, correct, isCorrect)
                                        } else {
                                            error = "The server returned an invalid attempt result."
                                        }
                                        submitting = false
                                    }
                                    .addOnFailureListener { failure ->
                                        submitting = false
                                        error = failure.localizedMessage ?: "Attempt could not be submitted."
                                    }
                            }
                        },
                        enabled = selectedIndex != null && !submitting && uid != null
                    ) {
                        if (submitting) CircularProgressIndicator()
                        else Text("Submit answer")
                    }
                } else {
                    Text(
                        text = if (result!!.isCorrect) "Correct answer." else "Your answer was incorrect.",
                        fontWeight = FontWeight.Bold,
                        color = if (result!!.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text("Correct option: ${result!!.correctOptionIndex + 1}. ${options.getOrNull(result!!.correctOptionIndex).orEmpty()}")
                    Text("Your first attempt is recorded. It cannot be replaced.", style = MaterialTheme.typography.bodySmall)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (uid == null) Text("Sign in to attempt this MCQ.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
