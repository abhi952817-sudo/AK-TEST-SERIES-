package Com.ak.testseries.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Crop

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import Com.ak.testseries.viewmodel.NEETViewModel
import Com.ak.testseries.data.TestEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop

@Composable
fun AdminTestListContent(
    viewModel: NEETViewModel,
    onReviewTest: (String, String) -> Unit = { _, _ -> }
) {
    val tests by viewModel.testsList.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Uploaded Tests History", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (tests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No tests found. Upload a test from 'Test Gen'.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(tests) { test ->
                    val timeString = SimpleDateFormat("dd MMM, yyyy • hh:mm a", Locale.getDefault()).format(Date(test.timestamp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onReviewTest(test.id, test.title) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isUnderReview = test.status == "Under Review"
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isUnderReview) Color(0xFFFEF3C7) else if (test.isPublic) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                                        border = BorderStroke(1.dp, if (isUnderReview) Color(0xFFF59E0B) else if (test.isPublic) Color(0xFF22C55E) else Color(0xFFCBD5E1))
                                    ) {
                                        Text(
                                            text = if (isUnderReview) "Under Review" else if (test.isPublic) "Published" else "Private",
                                            color = if (isUnderReview) Color(0xFFB45309) else if (test.isPublic) Color(0xFF15803D) else Color(0xFF475569),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Uploaded: $timeString", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                Text("Attempts: ${test.attemptsCount}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Assignment, contentDescription = "Test", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(test.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text(test.subject, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                IconButton(onClick = { onReviewTest(test.id, test.title) }) {
                                    Icon(Icons.Rounded.Crop, contentDescription = "Review Graphs", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            
                            // Private / Public Toggle for Admin
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (test.isPublic) "Visibility: Public" else "Visibility: Private",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (test.isPublic) Color(0xFF00C853) else Color.Red
                                )
                                Switch(
                                    checked = test.isPublic,
                                    onCheckedChange = { isPublic ->
                                        viewModel.updateTestVisibility(test.id, isPublic)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF00C853),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color.Red
                                    )
                                )
                            }

                            if (test.status == "Under Review") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.publishReviewedTest(test.id) { success, errorMsg ->
                                            if (success) {
                                                Toast.makeText(context, "Test approved & published to Public!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, errorMsg ?: "Publishing failed", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Approve & Publish to Public", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            
                            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))
                            
                            // Direct Action Button for Review & Attachment
                            Button(
                                onClick = { onReviewTest(test.id, test.title) },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Rounded.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Review & Attach PDF Graphs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Test Quality Insights", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            PlayStoreRatingBar(test = test)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayStoreRatingBar(test: TestEntity) {
    val total = test.rating1Count + test.rating2Count + test.rating3Count + test.rating4Count + test.rating5Count
    val maxCount = listOf(test.rating1Count, test.rating2Count, test.rating3Count, test.rating4Count, test.rating5Count).maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
    
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 16.dp)) {
            Text(String.format("%.1f", test.rating), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Row {
                repeat(5) { i ->
                    Icon(
                        Icons.Rounded.Star, 
                        contentDescription = null, 
                        tint = if (i < test.rating.toInt()) Color(0xFFFFB300) else Color.LightGray,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Text("$total total", fontSize = 10.sp, color = Color.Gray)
        }
        
        Column(modifier = Modifier.weight(1f)) {
            RatingRowBar(5, test.rating5Count, maxCount)
            RatingRowBar(4, test.rating4Count, maxCount)
            RatingRowBar(3, test.rating3Count, maxCount)
            RatingRowBar(2, test.rating2Count, maxCount)
            RatingRowBar(1, test.rating1Count, maxCount)
        }
    }
}

@Composable
fun RatingRowBar(stars: Int, count: Int, maxCount: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(14.dp)) {
        Text("$stars", fontSize = 10.sp, modifier = Modifier.width(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        LinearProgressIndicator(
            progress = { count / maxCount },
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF00C853),
            trackColor = Color.LightGray
        )
    }
}
