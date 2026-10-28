package Com.ak.testseries.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import Com.ak.testseries.data.backup.drive.GoogleDriveAuthManager
import Com.ak.testseries.data.backup.drive.GoogleDriveRemoteBackup
import Com.ak.testseries.viewmodel.NEETViewModel

/**
 * Material 3 Card Component displaying Google Drive Cloud Backup connection,
 * auto-backup synchronization preferences, manual cloud upload triggers,
 * and remote backup listings with restore staging.
 */
@Composable
fun GoogleDriveBackupCard(
    viewModel: NEETViewModel,
    context: Context,
    isHindi: Boolean,
    isDriveConnected: Boolean,
    driveAccountEmail: String?,
    driveAccountDisplayName: String?,
    lastDriveSyncTimestamp: Long?,
    lastDriveSyncStats: String?,
    autoBackupFrequency: String,
    autoBackupWifiOnly: Boolean,
    autoBackupChargingOnly: Boolean,
    remoteBackupsList: List<GoogleDriveRemoteBackup>,
    isRemoteBackupsLoading: Boolean,
    googleSignInLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    modifier: Modifier = Modifier
) {
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<GoogleDriveRemoteBackup?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("google_drive_backup_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDriveConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Connection Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDriveConnected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDriveConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                        contentDescription = "Google Drive",
                        tint = if (isDriveConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHindi) "गूगल ड्राइव क्लाउड बैकअप" else "Google Drive Cloud Backup",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isDriveConnected) {
                            driveAccountEmail ?: (if (isHindi) "कनेक्टेड" else "Connected")
                        } else {
                            if (isHindi) "स्वचालित सिंक के लिए कनेक्ट करें" else "Connect for automatic sync"
                        },
                        fontSize = 12.sp,
                        color = if (isDriveConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Connect / Disconnect button
                if (isDriveConnected) {
                    IconButton(
                        onClick = { showDisconnectConfirmDialog = true },
                        modifier = Modifier.testTag("disconnect_drive_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LinkOff,
                            contentDescription = if (isHindi) "डिस्कनेक्ट करें" else "Disconnect Drive",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isDriveConnected) {
                // Not Connected Prompt
                Text(
                    text = if (isHindi)
                        "अपने स्वयं के गूगल ड्राइव खाते से कनेक्ट करें। आपके टेस्ट, परिणाम और चित्र पूरी तरह सुरक्षित और निजी रहेंगे।"
                    else
                        "Connect your own personal Google Account to automatically sync and safely back up your tests, results, and diagrams.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val signInIntent = GoogleDriveAuthManager.getSignInIntent(context)
                        googleSignInLauncher.launch(signInIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connect_google_drive_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHindi) "गूगल ड्राइव कनेक्ट करें" else "Connect Google Drive",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                // Connected State: Last Sync & Auto Backup Configuration
                if (lastDriveSyncTimestamp != null && lastDriveSyncTimestamp > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                val dateStr = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
                                    .format(java.util.Date(lastDriveSyncTimestamp))
                                Text(
                                    text = if (isHindi) "अंतिम सिंक: $dateStr" else "Last Sync: $dateStr",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!lastDriveSyncStats.isNullOrBlank()) {
                                    Text(
                                        text = lastDriveSyncStats,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Auto-Backup Frequency Options
                Text(
                    text = if (isHindi) "स्वचालित बैकअप आवृत्ति" else "Automatic Backup Schedule",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("DAILY" to if (isHindi) "दैनिक (Daily)" else "Daily",
                        "WEEKLY" to if (isHindi) "साप्ताहिक (Weekly)" else "Weekly",
                        "OFF" to if (isHindi) "बंद (Off)" else "Off"
                    ).forEach { (freqKey, label) ->
                        val isSelected = autoBackupFrequency.equals(freqKey, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.updateAutoBackupSettings(
                                    context = context,
                                    frequency = freqKey,
                                    wifiOnly = autoBackupWifiOnly,
                                    chargingOnly = autoBackupChargingOnly
                                )
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.testTag("frequency_chip_$freqKey")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Constraints: Wi-Fi Only & Charging Only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHindi) "केवल वाई-फ़ाई पर बैकअप" else "Wi-Fi Only Sync",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isHindi) "मोबाइल डेटा बचाने के लिए" else "Avoid consuming cellular data",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoBackupWifiOnly,
                        onCheckedChange = { wifiOnly ->
                            viewModel.updateAutoBackupSettings(
                                context = context,
                                frequency = autoBackupFrequency,
                                wifiOnly = wifiOnly,
                                chargingOnly = autoBackupChargingOnly
                            )
                        },
                        modifier = Modifier.testTag("drive_wifi_only_switch")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHindi) "केवल चार्जिंग के दौरान" else "Only While Charging",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isHindi) "बैटरी बचाने के लिए" else "Preserve device battery life",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoBackupChargingOnly,
                        onCheckedChange = { chargingOnly ->
                            viewModel.updateAutoBackupSettings(
                                context = context,
                                frequency = autoBackupFrequency,
                                wifiOnly = autoBackupWifiOnly,
                                chargingOnly = chargingOnly
                            )
                        },
                        modifier = Modifier.testTag("drive_charging_only_switch")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val autoRestoreOnNewDevice by viewModel.autoRestoreOnNewDeviceEnabled.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHindi) "नए डिवाइस पर ऑटो-रिस्टोर" else "Auto-Restore on New Device",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isHindi) "खाली वॉल्ट मिलने पर स्वचालित रूप से रिस्टोर करें" else "Automatically restore backup when local vault is empty",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoRestoreOnNewDevice,
                        onCheckedChange = { enabled ->
                            viewModel.setAutoRestoreOnNewDeviceEnabled(context, enabled)
                        },
                        modifier = Modifier.testTag("auto_restore_on_new_device_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Manual Cloud Backup Now Action Button
                FilledTonalButton(
                    onClick = { viewModel.uploadBackupToGoogleDrive(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_now_to_drive_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHindi) "अभी गूगल ड्राइव पर बैकअप लें" else "Back Up to Drive Now",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Remote Backups List Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isHindi) "ड्राइव पर सहेजे गए बैकअप" else "Cloud Backups in Drive",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (remoteBackupsList.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = remoteBackupsList.size.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.fetchRemoteGoogleDriveBackups(context) },
                        modifier = Modifier.size(32.dp).testTag("refresh_drive_backups_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = if (isHindi) "रिफ्रेश" else "Refresh",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (isRemoteBackupsLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (remoteBackupsList.isEmpty() && !isRemoteBackupsLoading) {
                    Text(
                        text = if (isHindi)
                            "ड्राइव पर अभी कोई बैकअप मौजूद नहीं है। ऊपर दिए गए बटन पर टैप करके बैकअप लें।"
                        else
                            "No backups found in your Google Drive yet. Tap 'Back Up to Drive Now' above.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        remoteBackupsList.forEach { remoteBackup ->
                            RemoteBackupItem(
                                backup = remoteBackup,
                                isHindi = isHindi,
                                onRestoreClick = {
                                    viewModel.stageAndValidateRemoteBackup(context, remoteBackup.fileId)
                                },
                                onDeleteClick = {
                                    fileToDelete = remoteBackup
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Disconnect Confirmation Dialog
    if (showDisconnectConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmDialog = false },
            title = { Text(if (isHindi) "गूगल ड्राइव डिस्कनेक्ट करें?" else "Disconnect Google Drive?") },
            text = {
                Text(
                    if (isHindi)
                        "गूगल ड्राइव डिस्कनेक्ट करने से स्वचालित क्लाउड बैकअप बंद हो जाएगा। ड्राइव पर मौजूद पुरानी बैकअप फ़ाइलें सुरक्षित रहेंगी।"
                    else
                        "Disconnecting will disable automatic cloud backups. Your existing backups on Google Drive will remain safe."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectConfirmDialog = false
                        viewModel.disconnectGoogleDrive(context)
                    },
                    modifier = Modifier.testTag("confirm_disconnect_drive_button")
                ) {
                    Text(
                        text = if (isHindi) "डिस्कनेक्ट करें" else "Disconnect",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirmDialog = false }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel")
                }
            }
        )
    }

    // Delete Remote File Confirmation Dialog
    fileToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(if (isHindi) "क्लाउड बैकअप हटाएं?" else "Delete Cloud Backup?") },
            text = {
                Text(
                    if (isHindi)
                        "क्या आप वाकई गूगल ड्राइव से '${target.name}' को हटाना चाहते हैं? यह क्रिया वापस नहीं ली जा सकती।"
                    else
                        "Are you sure you want to delete '${target.name}' from your Google Drive? This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val fileId = target.fileId
                        fileToDelete = null
                        viewModel.deleteRemoteDriveBackup(context, fileId)
                    },
                    modifier = Modifier.testTag("confirm_delete_remote_backup_button")
                ) {
                    Text(
                        text = if (isHindi) "हटाएं" else "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text(if (isHindi) "रद्द करें" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun RemoteBackupItem(
    backup: GoogleDriveRemoteBackup,
    isHindi: Boolean,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("remote_backup_item_${backup.fileId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = backup.formattedDate,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${backup.formattedSize} • ${backup.name}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp).testTag("delete_remote_backup_button_${backup.fileId}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = if (isHindi) "हटाएं" else "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!backup.statsSummary.isBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = backup.statsSummary,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Inspect & Restore Button
            OutlinedButton(
                onClick = onRestoreClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restore_remote_backup_button_${backup.fileId}"),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SettingsBackupRestore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isHindi) "जांचें और पुनर्स्थापित करें" else "Inspect & Restore to Account",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
