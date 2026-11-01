package Com.ak.testseries

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import Com.ak.testseries.crash.CrashReportVault
import Com.ak.testseries.crash.CrashSanitizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INCIDENT_ID = "extra_incident_id"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_SAFE_MESSAGE = "extra_safe_message"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_IS_CRASH_LOOP = "extra_is_crash_loop"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isCrashLoop = intent.getBooleanExtra(EXTRA_IS_CRASH_LOOP, false)

        // Read safe intent extras or fallback to the latest stored report in private vault
        val incidentIdFromIntent = intent.getStringExtra(EXTRA_INCIDENT_ID)
        val report = CrashReportVault.loadReport(this, incidentIdFromIntent)
            ?: CrashReportVault.loadLatestReport(this)
            ?: CrashSanitizer.SanitizedCrash(
                incidentId = incidentIdFromIntent ?: "ERR-${System.currentTimeMillis() % 1000000}",
                category = intent.getStringExtra(EXTRA_CATEGORY) ?: "Application Stability",
                safeUserMessage = intent.getStringExtra(EXTRA_SAFE_MESSAGE)
                    ?: "An unexpected state was handled and the application was safely restarted.",
                rootExceptionType = "HandledException",
                sanitizedDetails = "Diagnostic trace scrubbed for privacy."
            )

        setContent {
            MaterialTheme {
                BackHandler {
                    restartApp()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CrashScreenContent(
                        report = report,
                        isCrashLoop = isCrashLoop,
                        onRestart = { restartApp() },
                        onClearCache = {
                            CrashReportVault.clearApplicationCache(this@CrashActivity)
                            CrashReportVault.resetCrashLoopCounter(this@CrashActivity)
                            Toast.makeText(this@CrashActivity, "Cache cleared successfully. Restarting...", Toast.LENGTH_SHORT).show()
                            restartApp()
                        },
                        onResetDatabase = {
                            Com.ak.testseries.data.AppDatabase.emergencyResetDatabase(this@CrashActivity)
                            CrashReportVault.resetCrashLoopCounter(this@CrashActivity)
                            Toast.makeText(this@CrashActivity, "Database re-initialized. Restarting...", Toast.LENGTH_SHORT).show()
                            restartApp()
                        },
                        onCopyIncidentId = { copyToClipboard("Incident ID", report.incidentId) },
                        onShareReport = { shareSanitizedReport(report) }
                    )
                }
            }
        }
    }

    private fun restartApp() {
        CrashReportVault.resetCrashLoopCounter(this@CrashActivity)
        val intent = Intent(this@CrashActivity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(this, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun shareSanitizedReport(report: CrashSanitizer.SanitizedCrash) {
        val shareText = buildString {
            appendLine("AK Test Series - Crash Diagnostic Summary")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})")
            appendLine("Incident ID: ${report.incidentId}")
            appendLine("Category: ${report.category}")
            appendLine("Time: ${formatTimestamp(report.timestamp)}")
            appendLine("Status: Safe & Data Preserved")
            if (BuildConfig.DEBUG) {
                appendLine("Diagnostic Note: Developer Debug Build")
            }
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Safe Error Report")
        startActivity(shareIntent)
    }

    private fun formatTimestamp(timestamp: Long): String {
        return try {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))
        } catch (_: Exception) {
            "Just now"
        }
    }
}

@Composable
private fun CrashScreenContent(
    report: CrashSanitizer.SanitizedCrash,
    isCrashLoop: Boolean,
    onRestart: () -> Unit,
    onClearCache: () -> Unit,
    onResetDatabase: () -> Unit,
    onCopyIncidentId: () -> Unit,
    onShareReport: () -> Unit
) {
    var showDebugDetails by remember { mutableStateOf(false) }
    var showDbResetDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    if (showDbResetDialog) {
        AlertDialog(
            onDismissRequest = { showDbResetDialog = false },
            title = {
                Text(
                    text = "Confirm Database Reset",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This will safely re-initialize the local SQLite database to fix corrupted tables. Your account login credentials will be preserved.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDbResetDialog = false
                        onResetDatabase()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_reset_db_button")
                ) {
                    Text("Reset & Restart")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDbResetDialog = false },
                    modifier = Modifier.testTag("cancel_reset_db_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Shield / Warning Icon based on Crash Loop State
        if (isCrashLoop) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = "Safe Mode Active",
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFFD97706)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Safe Mode Recovery",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Multiple launch crashes were detected. Safe Mode halted the restart loop. Choose a recovery option below to restore stability.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = "Safe Recovery",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "App Recovered Safely",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "An unexpected issue was caught. Your tests, offline files, and profile data remain completely safe.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Incident Reference Card (Sanitized)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                    Text(
                        text = "INCIDENT CODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    SuggestionChip(
                        onClick = onCopyIncidentId,
                        label = { Text("Copy Code", fontSize = 11.sp) },
                        icon = {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy Incident Code",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.testTag("copy_reference_button")
                    )
                }

                Text(
                    text = report.incidentId,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Issue Category",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = report.category,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = report.safeUserMessage,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isCrashLoop) {
            // Safe Mode Action 1: Clear Cache
            Button(
                onClick = onClearCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("clear_cache_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Clear Cache", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Temporary Cache & Restart", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Safe Mode Action 2: Reset Local Database
            OutlinedButton(
                onClick = { showDbResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("reset_db_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = "Reset Database", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Emergency Reset Database", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Safe Mode Action 3: Regular Restart
            TextButton(
                onClick = onRestart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("restart_app_button")
            ) {
                Text("Standard Restart", fontSize = 14.sp)
            }
        } else {
            // Standard Primary Action: Restart App
            Button(
                onClick = onRestart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("restart_app_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Restart App", modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restart App", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary Action: Share Diagnostic Reference
        OutlinedButton(
            onClick = onShareReport,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("share_report_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.Share, contentDescription = "Share Report", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Incident Reference", fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Information disclosure protection policy
        if (BuildConfig.DEBUG) {
            TextButton(
                onClick = { showDebugDetails = !showDebugDetails },
                modifier = Modifier.testTag("toggle_debug_details_button")
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (showDebugDetails) "Hide Sanitized Debug Log" else "View Sanitized Debug Log",
                    fontSize = 12.sp
                )
            }

            AnimatedVisibility(visible = showDebugDetails) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(14.dp)
                ) {
                    Text(
                        text = "SANITIZED STACK TRACE (DEBUG ONLY)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = report.sanitizedDetails,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            // In Production/Release mode: No raw stack traces exposed to end users!
            Text(
                text = "Protected under secure incident logging. Technical stack traces are redacted to ensure privacy.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
