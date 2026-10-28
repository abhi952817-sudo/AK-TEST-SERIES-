package Com.ak.testseries.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import Com.ak.testseries.data.backup.*
import Com.ak.testseries.data.backup.drive.*
import Com.ak.testseries.viewmodel.AppLanguage
import Com.ak.testseries.viewmodel.NEETViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Account Backup & Restore Screen.
 *
 * Implements Android Storage Access Framework (SAF) export and import with
 * strict zero-trust account containment, two-phase staging validation,
 * selective media asset packaging, export history log, and data safety health inspection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: NEETViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val isHindi = selectedLanguage == AppLanguage.HINDI
    val backupState by viewModel.backupUiState.collectAsStateWithLifecycle()
    val includeDiagrams by viewModel.includeDiagramsInBackup.collectAsStateWithLifecycle()
    val lastBackupTimestamp by viewModel.lastBackupTimestamp.collectAsStateWithLifecycle()
    val lastBackupStats by viewModel.lastBackupStats.collectAsStateWithLifecycle()
    val recentExports by viewModel.recentExports.collectAsStateWithLifecycle()
    val backupReminderEnabled by viewModel.backupReminderEnabled.collectAsStateWithLifecycle()

    // Google Drive Cloud Backup State
    val isDriveConnected by viewModel.isDriveConnected.collectAsStateWithLifecycle()
    val driveAccountEmail by viewModel.driveAccountEmail.collectAsStateWithLifecycle()
    val driveAccountDisplayName by viewModel.driveAccountDisplayName.collectAsStateWithLifecycle()
    val lastDriveSyncTimestamp by viewModel.lastDriveSyncTimestamp.collectAsStateWithLifecycle()
    val lastDriveSyncStats by viewModel.lastDriveSyncStats.collectAsStateWithLifecycle()
    val autoBackupFrequency by viewModel.autoBackupFrequency.collectAsStateWithLifecycle()
    val autoBackupWifiOnly by viewModel.autoBackupWifiOnly.collectAsStateWithLifecycle()
    val autoBackupChargingOnly by viewModel.autoBackupChargingOnly.collectAsStateWithLifecycle()
    val remoteBackupsList by viewModel.remoteBackupsList.collectAsStateWithLifecycle()
    val isRemoteBackupsLoading by viewModel.isRemoteBackupsLoading.collectAsStateWithLifecycle()
    val remoteOperationState by viewModel.remoteBackupOperationState.collectAsStateWithLifecycle()
    val cloudRestoreRecommendation by viewModel.cloudRestoreRecommendation.collectAsStateWithLifecycle()
    val autoRestoreStatus by viewModel.autoRestoreStatus.collectAsStateWithLifecycle()

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.handleGoogleDriveSignInResult(result.data, context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshGoogleDriveStatus(context)
    }

    val allTests by viewModel.testsList.collectAsStateWithLifecycle()
    val attemptHistory by viewModel.attemptHistory.collectAsStateWithLifecycle()
    val bookmarkedIds by viewModel.userBookmarkedTestIds.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val currentAuthUid = currentUser?.uid?.trim() ?: ""
    val isUserAuthenticated = currentAuthUid.isNotBlank() || userProfile.email.isNotEmpty()

    // SAF Launchers
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri, context, includeDiagrams = includeDiagrams)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.stageAndValidateBackupFromUri(uri, context)
        }
    }

    val eligibleTestsCount = remember(allTests, currentAuthUid) {
        if (currentAuthUid.isBlank()) 0
        else allTests.count { !it.isOfficial && !it.isPublic && it.uploaderId == currentAuthUid }
    }

    val backupHealth = remember(lastBackupTimestamp, eligibleTestsCount, attemptHistory.size, isHindi) {
        evaluateBackupHealth(
            lastBackupTimestamp = lastBackupTimestamp,
            eligibleVaultTestsCount = eligibleTestsCount,
            attemptsCount = attemptHistory.size,
            isHindi = isHindi
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isHindi) "खाता बैकअप और पुनर्स्थापना" else "Backup & Restore Center",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isHindi) "स्थानीय .aktsbak डेटा केंद्र" else "Local .aktsbak Data Center",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("backup_screen_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isHindi) "वापस जाएं" else "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 0. Cloud Backup Found (Fresh Device / Empty Vault Recovery Banner)
            cloudRestoreRecommendation?.let { rec ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloud_restore_recommendation_banner"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isHindi) "क्लाउड बैकअप मिला!" else "Cloud Backup Found!",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isHindi)
                                "आपके गूगल ड्राइव पर ${rec.formattedDate} का एक बैकअप मिला (${rec.statsSummary})। क्या आप इसे इस डिवाइस पर पुनर्स्थापित करना चाहते हैं?"
                            else
                                "A cloud backup from ${rec.formattedDate} was found in your Google Drive (${rec.statsSummary}). Would you like to restore your tests and attempts?",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { viewModel.dismissCloudRestoreRecommendation() },
                                modifier = Modifier.testTag("dismiss_cloud_restore_banner_button")
                            ) {
                                Text(if (isHindi) "अभी नहीं" else "Not Now")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    viewModel.dismissCloudRestoreRecommendation()
                                    viewModel.stageAndValidateRemoteBackup(context, rec.fileId)
                                },
                                modifier = Modifier.testTag("restore_from_recommendation_banner_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Restore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isHindi) "जांचें और पुनर्स्थापित करें" else "Inspect & Restore")
                            }
                        }
                    }
                }
            }

            // 1. Account Ownership & Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_identity_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (isUserAuthenticated) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isUserAuthenticated) Icons.Rounded.AccountCircle else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (isUserAuthenticated) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (currentUser?.displayName?.isNotBlank() == true) currentUser.displayName!!
                            else if (userProfile.name.isNotBlank()) userProfile.name
                            else if (isUserAuthenticated) "Student Account"
                            else if (isHindi) "अतिथि उपयोगकर्ता" else "Guest User",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = if (currentUser?.email?.isNotBlank() == true) currentUser.email!!
                            else if (userProfile.email.isNotBlank()) userProfile.email
                            else if (isHindi) "कोई ईमेल लिंक नहीं" else "No email linked",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isUserAuthenticated) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (isUserAuthenticated) {
                                    val shortUid = if (currentAuthUid.length > 8) "${currentAuthUid.take(4)}...${currentAuthUid.takeLast(4)}" else currentAuthUid
                                    if (isHindi) "सत्यापित खाता (UID: $shortUid)" else "Verified Account (UID: $shortUid)"
                                } else {
                                    if (isHindi) "साइन-इन आवश्यक" else "Sign In Required"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isUserAuthenticated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 2. Data Health & Safety Indicator Card
            val healthColor = when (backupHealth.status) {
                BackupHealthStatus.GOOD -> Color(0xFF2E7D32)
                BackupHealthStatus.WARNING -> Color(0xFFF57C00)
                BackupHealthStatus.CRITICAL -> MaterialTheme.colorScheme.error
            }
            val healthBg = healthColor.copy(alpha = 0.10f)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup_health_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = healthBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, healthColor.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (backupHealth.status) {
                            BackupHealthStatus.GOOD -> Icons.Default.CheckCircle
                            BackupHealthStatus.WARNING -> Icons.Default.Info
                            BackupHealthStatus.CRITICAL -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        tint = healthColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = backupHealth.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = healthColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = backupHealth.message,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 3. Backup Status & Live Snapshot Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup_status_summary_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            text = if (isHindi) "अंतिम बैकअप स्थिति" else "Backup Status",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (lastBackupTimestamp != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2E7D32).copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isHindi) "सहेजा गया" else "Backed Up",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isHindi) "कोई बैकअप नहीं" else "No Backup Yet",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (lastBackupTimestamp != null) {
                        val dateFormatted = remember(lastBackupTimestamp) {
                            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            sdf.format(Date(lastBackupTimestamp!!))
                        }
                        Text(
                            text = if (isHindi) "अंतिम निर्यात: $dateFormatted" else "Last Export: $dateFormatted",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!lastBackupStats.isNullOrBlank()) {
                            Text(
                                text = lastBackupStats!!,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = if (isHindi) "आपने अभी तक इस खाते के लिए कोई .aktsbak संग्रह निर्यात नहीं किया है।"
                            else "You haven't exported an .aktsbak archive for this account yet.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isHindi) "वर्तमान खाता डेटा अवलोकन" else "Current Local Data Snapshot",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DataCounterItem(
                            count = eligibleTestsCount.toString(),
                            label = if (isHindi) "निजी टेस्ट" else "Vault Tests",
                            icon = Icons.Rounded.Science
                        )
                        DataCounterItem(
                            count = attemptHistory.size.toString(),
                            label = if (isHindi) "परीक्षण प्रयास" else "Attempts",
                            icon = Icons.Rounded.History
                        )
                        DataCounterItem(
                            count = bookmarkedIds.size.toString(),
                            label = if (isHindi) "बुकमार्क" else "Bookmarks",
                            icon = Icons.Rounded.Bookmark
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Periodic Backup Reminder Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isHindi) "नियमित बैकअप अनुस्मारक" else "Periodic Backup Reminders",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isHindi) "बैकअप पुराना होने पर सूचना दें" else "Notify when data backup is overdue",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = backupReminderEnabled,
                            onCheckedChange = { viewModel.setBackupReminderEnabled(it, context) },
                            modifier = Modifier.testTag("backup_reminder_switch")
                        )
                    }
                }
            }

            // 3B. Google Drive Cloud Backup & Sync Card
            GoogleDriveBackupCard(
                viewModel = viewModel,
                context = context,
                isHindi = isHindi,
                isDriveConnected = isDriveConnected,
                driveAccountEmail = driveAccountEmail,
                driveAccountDisplayName = driveAccountDisplayName,
                lastDriveSyncTimestamp = lastDriveSyncTimestamp,
                lastDriveSyncStats = lastDriveSyncStats,
                autoBackupFrequency = autoBackupFrequency,
                autoBackupWifiOnly = autoBackupWifiOnly,
                autoBackupChargingOnly = autoBackupChargingOnly,
                remoteBackupsList = remoteBackupsList,
                isRemoteBackupsLoading = isRemoteBackupsLoading,
                googleSignInLauncher = googleSignInLauncher
            )

            // 4. Export Section Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_backup_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isHindi) "नया बैकअप निर्यात करें" else "Export Account Backup",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isHindi)
                            "आपके स्वामित्व वाले सभी निजी टेस्ट, प्रश्न, टेस्ट परिणाम, बुकमार्क और पाठ्यक्रम प्रगति को एक सत्यापित .aktsbak फ़ाइल में संपीड़ित करता है।"
                        else
                            "Packages your owned vault tests, questions, attempt history, bookmarks, and syllabus progress into a verifiable .aktsbak archive.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Diagram inclusion toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHindi) "चित्र और आरेख शामिल करें" else "Include Diagram & Media Assets",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isHindi) "टेस्ट में संलग्न आरेख और छवियां (अनुशंसित)" else "Referenced question diagrams and images (recommended)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = includeDiagrams,
                                onCheckedChange = { viewModel.setIncludeDiagramsInBackup(it) },
                                modifier = Modifier.testTag("include_diagrams_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (!isUserAuthenticated) {
                                Toast.makeText(
                                    context,
                                    if (isHindi) "बैकअप के लिए कृपया पहले साइन इन करें" else "Please sign in to export account data",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                createDocumentLauncher.launch("ak_test_series_backup_$timestamp.aktsbak")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_backup_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SaveAlt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHindi) "बैकअप फ़ाइल सहेजें (.aktsbak)" else "Save Backup Archive (.aktsbak)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 5. Restore Section Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restore_backup_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isHindi) "बैकअप पुनर्स्थापित करें" else "Restore Existing Backup",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isHindi)
                            "अपने डिवाइस या फ़ाइल मैनेजर से .aktsbak संग्रह चुनें। सिस्टम पहले फ़ाइल के चेकसम, मैनिफेस्ट और यूज़र आईडी की पूर्ण पुष्टि करेगा, फिर पूर्वावलोकन दिखाएगा।"
                        else
                            "Select an .aktsbak archive from storage. The engine validates checksums, manifest integrity, and account UID containment before prompting for confirmation.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            if (!isUserAuthenticated) {
                                Toast.makeText(
                                    context,
                                    if (isHindi) "पुनर्स्थापना के लिए कृपया पहले साइन इन करें" else "Please sign in to restore account data",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                openDocumentLauncher.launch(arrayOf("*/*", "application/octet-stream", "application/zip"))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("select_restore_file_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHindi) "बैकअप फ़ाइल चुनें (.aktsbak)" else "Select Backup File (.aktsbak)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 6. Recent Exports History Log Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recent_exports_history_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isHindi) "हाल के निर्यात इतिहास" else "Recent Export History",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (recentExports.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearBackupHistory(context) },
                                modifier = Modifier.testTag("clear_backup_history_button")
                            ) {
                                Text(
                                    text = if (isHindi) "साफ़ करें" else "Clear",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (recentExports.isEmpty()) {
                        Text(
                            text = if (isHindi)
                                "अभी तक कोई स्थानीय निर्यात इतिहास रिकॉर्ड नहीं किया गया है।"
                            else
                                "No local export history recorded yet.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recentExports.forEachIndexed { index, record ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val dateStr = remember(record.timestamp) {
                                                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(record.timestamp))
                                            }
                                            Text(
                                                text = dateStr,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val sizeKb = (record.totalBytes / 1024L).coerceAtLeast(1L)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF2E7D32).copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = "${sizeKb} KB",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = record.statsSummary,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. Zero-Trust Security & Privacy Information Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("security_info_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(22.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isHindi) "शून्य-विश्वास खाता पृथक्करण (Zero-Trust Isolation)" else "Zero-Trust Account Isolation",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isHindi)
                                "बैकअप संग्रह सीधे आपके खाते के विशिष्ट यूआईडी से जुड़े होते हैं। किसी भी अन्य खाते का बैकअप आपके खाते में ओवरराइट नहीं किया जा सकता है। सभी फ़ाइलें SHA-256 हैश द्वारा सत्यापित की जाती हैं।"
                            else
                                "Backup archives are strictly bound to your authenticated UID. Backups from foreign accounts cannot overwrite your data. All entries are verified with SHA-256 checksums.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Modal Overlays for Backup & Restore States
    when (val state = backupState) {
        is BackupUiState.Exporting -> {
            AlertDialog(
                onDismissRequest = { /* Non-cancellable during export */ },
                icon = { CircularProgressIndicator(modifier = Modifier.size(36.dp)) },
                title = { Text(if (isHindi) "बैकअप निर्यात हो रहा है" else "Exporting Backup") },
                text = { Text(state.message) },
                confirmButton = {}
            )
        }

        is BackupUiState.Validating -> {
            AlertDialog(
                onDismissRequest = { /* Non-cancellable during validation */ },
                icon = { CircularProgressIndicator(modifier = Modifier.size(36.dp)) },
                title = { Text(if (isHindi) "बैकअप की पुष्टि हो रही है" else "Validating Backup Archive") },
                text = { Text(state.message) },
                confirmButton = {}
            )
        }

        is BackupUiState.Restoring -> {
            AlertDialog(
                onDismissRequest = { /* Non-cancellable during restore commit */ },
                icon = { CircularProgressIndicator(modifier = Modifier.size(36.dp)) },
                title = { Text(if (isHindi) "डेटा पुनर्स्थापित हो रहा है" else "Restoring Account Data") },
                text = { Text(state.message) },
                confirmButton = {}
            )
        }

        is BackupUiState.ValidationReady -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = if (isHindi) "सत्यापित बैकअप सारांश" else "Verified Backup Summary",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isHindi) "बैकअप फ़ाइल सफलतापूर्वक सत्यापित हुई। विवरण:" else "Backup archive verified successfully. Summary:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "UID: ${state.manifest.owner.uid}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!state.manifest.owner.email.isNullOrBlank()) {
                                    Text(
                                        text = "Email: ${state.manifest.owner.email}",
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    text = if (isHindi) "पूर्णता स्थिति: ${state.manifest.completeness}" else "Completeness: ${state.manifest.completeness}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        val staged = state.stagedData
                        val testsCount = staged?.tests?.size ?: state.manifest.stats.privateTestsCount
                        val questionsCount = staged?.questions?.size ?: state.manifest.stats.questionsCount
                        val attemptsCount = staged?.attempts?.size ?: state.manifest.stats.attemptsCount
                        val bookmarksCount = staged?.bookmarks?.testBookmarks?.size ?: state.manifest.stats.bookmarkedCount
                        val diagramsCount = staged?.stagedDiagramFiles?.size ?: state.manifest.stats.diagramsCount
                        val syllabusCount = staged?.syllabus?.completedChaptersByExam?.size ?: 0

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PreviewStatRow(label = if (isHindi) "निजी टेस्ट" else "Private Tests", value = testsCount.toString())
                            PreviewStatRow(label = if (isHindi) "संबंधित प्रश्न" else "Questions", value = questionsCount.toString())
                            PreviewStatRow(label = if (isHindi) "परीक्षण प्रयास" else "Attempts", value = attemptsCount.toString())
                            PreviewStatRow(label = if (isHindi) "बुकमार्क" else "Bookmarks", value = bookmarksCount.toString())
                            PreviewStatRow(label = if (isHindi) "चित्र और आरेख" else "Diagram Assets", value = diagramsCount.toString())
                            PreviewStatRow(label = if (isHindi) "पाठ्यक्रम श्रेणियां" else "Syllabus Exams", value = syllabusCount.toString())

                            val uncompressedKb = (state.manifest.stats.uncompressedBytes / 1024L).coerceAtLeast(1L)
                            PreviewStatRow(label = if (isHindi) "कुल आकार (असम्पीडित)" else "Uncompressed Size", value = "${uncompressedKb} KB")
                            PreviewStatRow(label = if (isHindi) "फ़ाइल अखंडता (SHA-256)" else "Integrity Check", value = "100% Passed")
                        }

                        // Subject distribution breakdown if tests exist
                        val subjectDistribution = remember(staged) {
                            staged?.tests?.groupBy { it.subject }?.mapValues { it.value.size } ?: emptyMap()
                        }
                        if (subjectDistribution.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = if (isHindi) "विषयवार टेस्ट वर्गीकरण:" else "Tests by Subject:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        subjectDistribution.forEach { (subj, count) ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            ) {
                                                Text(
                                                    text = "$subj: $count",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isHindi)
                                "पुष्टि के बाद, यह डेटा आपके खाते में जोड़ दिया जाएगा। डुप्लिकेट प्रयासों को छोड़ दिया जाएगा और नए टेस्ट सुरक्षित रहेंगे।"
                            else
                                "Upon confirmation, verified records will be merged into your account. Duplicate attempts are skipped, and newer live tests are preserved.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmAndExecuteRestore(context) },
                        modifier = Modifier.testTag("confirm_restore_button")
                    ) {
                        Text(if (isHindi) "अभी पुनर्स्थापित करें" else "Restore Now")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissBackupDialog() },
                        modifier = Modifier.testTag("dismiss_restore_button")
                    ) {
                        Text(if (isHindi) "रद्द करें" else "Cancel")
                    }
                }
            )
        }

        is BackupUiState.ExportSuccess -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(if (isHindi) "बैकअप निर्यात सफल" else "Backup Export Complete") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissBackupDialog() },
                        modifier = Modifier.testTag("export_success_ok_button")
                    ) {
                        Text(if (isHindi) "ठीक है" else "OK")
                    }
                }
            )
        }

        is BackupUiState.RestoreSuccess -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(if (isHindi) "पुनर्स्थापना पूर्ण" else "Restore Complete") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isHindi) "आपका बैकअप डेटा सफलतापूर्वक पुनर्स्थापित किया गया है:" else "Your account data was successfully restored:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PreviewStatRow(label = if (isHindi) "पुनर्स्थापित टेस्ट" else "Restored Tests", value = state.result.restoredTestsCount.toString())
                        PreviewStatRow(label = if (isHindi) "पुनर्स्थापित प्रश्न" else "Restored Questions", value = state.result.restoredQuestionsCount.toString())
                        PreviewStatRow(label = if (isHindi) "पुनर्स्थापित प्रयास" else "Restored Attempts", value = state.result.restoredAttemptsCount.toString())
                        PreviewStatRow(label = if (isHindi) "पुनर्स्थापित आरेख" else "Restored Diagrams", value = state.result.restoredDiagramsCount.toString())
                        PreviewStatRow(label = if (isHindi) "बुकमार्क" else "Bookmarks", value = state.result.mergedBookmarksCount.toString())
                        if (state.result.skippedDuplicateAttemptsCount > 0) {
                            Text(
                                text = if (isHindi) "छोड़े गए डुप्लिकेट प्रयास: ${state.result.skippedDuplicateAttemptsCount}"
                                else "Skipped duplicate attempts: ${state.result.skippedDuplicateAttemptsCount}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissBackupDialog() },
                        modifier = Modifier.testTag("restore_success_ok_button")
                    ) {
                        Text(if (isHindi) "ठीक है" else "OK")
                    }
                }
            )
        }

        is BackupUiState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(state.title) },
                text = { Text(state.message) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissBackupDialog() },
                        modifier = Modifier.testTag("backup_error_ok_button")
                    ) {
                        Text(if (isHindi) "ठीक है" else "Dismiss")
                    }
                }
            )
        }

        is BackupUiState.Idle -> {
            // No modal needed
        }
    }

    // Remote Google Drive Operation Modal Overlays
    when (val remoteState = remoteOperationState) {
        is RemoteBackupOperationState.Loading -> {
            AlertDialog(
                onDismissRequest = { /* Non-cancellable during cloud operation */ },
                icon = { CircularProgressIndicator(modifier = Modifier.size(36.dp)) },
                title = { Text(if (isHindi) "क्लाउड ऑपरेशन प्रगति पर है" else "Google Drive Operation") },
                text = { Text(remoteState.message) },
                confirmButton = {}
            )
        }

        is RemoteBackupOperationState.Progress -> {
            AlertDialog(
                onDismissRequest = { /* Non-cancellable during progress */ },
                icon = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { remoteState.progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "${(remoteState.progressFraction * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                title = { Text(if (isHindi) "क्लाउड ट्रांसफ़र प्रगति पर" else "Transfer in Progress") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(remoteState.message)
                        LinearProgressIndicator(
                            progress = { remoteState.progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {}
            )
        }

        is RemoteBackupOperationState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissRemoteOperationDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(if (isHindi) "क्लाउड ऑपरेशन सफल" else "Cloud Operation Successful") },
                text = { Text(remoteState.message) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissRemoteOperationDialog() },
                        modifier = Modifier.testTag("remote_operation_success_ok_button")
                    ) {
                        Text(if (isHindi) "ठीक है" else "OK")
                    }
                }
            )
        }

        is RemoteBackupOperationState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissRemoteOperationDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(remoteState.title) },
                text = { Text(remoteState.message) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissRemoteOperationDialog() },
                        modifier = Modifier.testTag("remote_operation_error_ok_button")
                    ) {
                        Text(if (isHindi) "ठीक है" else "Dismiss")
                    }
                }
            )
        }

        is RemoteBackupOperationState.Idle -> {
            // No modal needed
        }
    }

    // Auto-Restore on New Device Granular Modals
    when (val status = autoRestoreStatus) {
        is AutoRestoreStatus.Downloading, is AutoRestoreStatus.Validating, is AutoRestoreStatus.Restoring -> {
            val title = when (status) {
                is AutoRestoreStatus.Downloading -> if (isHindi) "क्लाउड बैकअप डाउनलोड हो रहा है..." else "Downloading Cloud Backup..."
                is AutoRestoreStatus.Validating -> if (isHindi) "बैकअप सत्यापित हो रहा है..." else "Validating Cloud Backup..."
                else -> if (isHindi) "डेटा पुनर्स्थापित हो रहा है..." else "Restoring Account Data..."
            }
            AlertDialog(
                onDismissRequest = { /* non-cancellable during auto-restore */ },
                icon = { CircularProgressIndicator(modifier = Modifier.size(36.dp)) },
                title = { Text(title) },
                text = { Text(if (isHindi) "कृपया प्रतीक्षा करें, आपका डेटा सुरक्षित रूप से पुनर्स्थापित किया जा रहा है।" else "Please wait while your data is safely restored.") },
                confirmButton = {}
            )
        }
        is AutoRestoreStatus.Completed -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAutoRestoreStatus() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(if (isHindi) "ऑटो-रिस्टोर सफल" else "Auto-Restore Complete") },
                text = { Text(status.message) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissAutoRestoreStatus() },
                        modifier = Modifier.testTag("auto_restore_completed_ok_button")
                    ) {
                        Text("OK")
                    }
                }
            )
        }
        is AutoRestoreStatus.Failed -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAutoRestoreStatus() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(if (isHindi) "ऑटो-रिस्टोर विफल" else "Auto-Restore Failed") },
                text = { Text(status.reason) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissAutoRestoreStatus() },
                        modifier = Modifier.testTag("auto_restore_failed_ok_button")
                    ) {
                        Text(if (isHindi) "ठीक है" else "Dismiss")
                    }
                }
            )
        }
        is AutoRestoreStatus.AccountMismatch -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAutoRestoreStatus() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(if (isHindi) "खाता बेमेल (Account Mismatch)" else "Account Mismatch") },
                text = {
                    Text(
                        if (isHindi)
                            "क्लाउड बैकअप वर्तमान खाते से मेल नहीं खाता है। डेटा सुरक्षा के लिए रिस्टोर रद्द कर दिया गया है।"
                        else
                            "Cloud backup belongs to a different account (Expected UID: ${status.expectedUid}, Actual: ${status.actualUid}). Operation rejected for security."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissAutoRestoreStatus() },
                        modifier = Modifier.testTag("auto_restore_mismatch_ok_button")
                    ) {
                        Text(if (isHindi) "ठीक है" else "Dismiss")
                    }
                }
            )
        }
        else -> { /* Idle, CheckingForBackup, BackupFound, WaitingForDriveAuth, NoBackupAvailable */ }
    }
}

@Composable
private fun DataCounterItem(
    count: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = count,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PreviewStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
