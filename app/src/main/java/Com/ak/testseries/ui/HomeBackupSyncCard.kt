package Com.ak.testseries.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import Com.ak.testseries.data.backup.drive.GoogleDriveRemoteBackup
import Com.ak.testseries.viewmodel.AppLanguage
import Com.ak.testseries.viewmodel.NEETViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Prominent Home screen card surfacing Google Drive cloud sync and local backup (.aktsbak) status.
 * Provides immediate user visibility and 1-tap navigation to the Backup & Restore Center.
 */
@Composable
fun HomeBackupSyncCard(
    viewModel: NEETViewModel,
    onOpenBackupCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val isHindi = selectedLanguage == AppLanguage.HINDI
    val isDriveConnected by viewModel.isDriveConnected.collectAsStateWithLifecycle()
    val driveEmail by viewModel.driveAccountEmail.collectAsStateWithLifecycle()
    val lastSyncTimestamp by viewModel.lastDriveSyncTimestamp.collectAsStateWithLifecycle()
    val lastBackupTimestamp by viewModel.lastBackupTimestamp.collectAsStateWithLifecycle()
    val cloudRecommendation by viewModel.cloudRestoreRecommendation.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // High-Priority Banner: Cloud Restore Recommendation for New Device
        AnimatedVisibility(visible = cloudRecommendation != null) {
            cloudRecommendation?.let { recommendation ->
                CloudRestoreAlertBanner(
                    recommendation = recommendation,
                    isHindi = isHindi,
                    onRestore = onOpenBackupCenter,
                    onDismiss = { viewModel.dismissCloudRestoreRecommendation() }
                )
            }
        }

        // Main Backup & Cloud Sync Quick Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_backup_sync_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            border = BorderStroke(
                1.5.dp,
                if (isDriveConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDriveConnected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isDriveConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                                    contentDescription = "Cloud Status",
                                    tint = if (isDriveConnected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = if (isHindi) "डेटा बैकअप व क्लाउड सिंक" else "Backup & Cloud Sync",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isDriveConnected) {
                                    val emailPart = driveEmail ?: (if (isHindi) "कनेक्टेड" else "Connected")
                                    if (isHindi) "गूगल ड्राइव: $emailPart" else "Google Drive: $emailPart"
                                } else {
                                    if (isHindi) "गूगल ड्राइव एवं स्थानीय .aktsbak" else "Google Drive & Local .aktsbak"
                                },
                                fontSize = 11.sp,
                                color = if (isDriveConnected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDriveConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (isDriveConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isDriveConnected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = if (isDriveConnected) {
                                    if (isHindi) "सिंक्ड" else "Active"
                                } else {
                                    if (isHindi) "सुरक्षित रखें" else "Ready"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDriveConnected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val backupTs = lastBackupTimestamp
                val formattedSync = when {
                    lastSyncTimestamp != null && lastSyncTimestamp!! > 0L -> {
                        val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(lastSyncTimestamp!!))
                        if (isHindi) "अंतिम क्लाउड सिंक: $dateStr" else "Last cloud sync: $dateStr"
                    }
                    backupTs != null && backupTs > 0L -> {
                        val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(backupTs))
                        if (isHindi) "अंतिम बैकअप: $dateStr" else "Last backup: $dateStr"
                    }
                    else -> {
                        if (isHindi) "अपने टेस्ट, स्कोर, बुकमार्क व प्रगति को सुरक्षित रखें"
                        else "Safeguard your tests, scores, bookmarks & progress safely"
                    }
                }

                Text(
                    text = formattedSync,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onOpenBackupCenter,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("home_backup_center_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsBackupRestore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHindi) "बैकअप केंद्र खोलें" else "Backup Center",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenBackupCenter,
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("home_quick_sync_btn"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (isDriveConnected) Icons.Rounded.Sync else Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isHindi) "विवरण" else "Details",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudRestoreAlertBanner(
    recommendation: GoogleDriveRemoteBackup,
    isHindi: Boolean,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_cloud_restore_alert"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = "Cloud Restore",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (isHindi) "गूगल ड्राइव बैकअप मिला! ☁️" else "Cloud Backup Found! ☁️",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val backupDate = recommendation.formattedDate

            Text(
                text = if (isHindi) {
                    "आपके खाते का क्लाउड बैकअप ($backupDate) उपलब्ध है। अपने निजी टेस्ट और परिणाम पुनर्स्थापित करें।"
                } else {
                    "A cloud backup from $backupDate is available for your account. Restore your private tests & attempts now."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onRestore,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("home_alert_restore_now_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isHindi) "अभी पुनर्स्थापित करें (Restore Now)" else "Restore Now",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
