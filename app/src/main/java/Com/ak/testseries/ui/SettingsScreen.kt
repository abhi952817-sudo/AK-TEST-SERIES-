package Com.ak.testseries.ui

import Com.ak.testseries.stringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import Com.ak.testseries.viewmodel.NEETViewModel

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import Com.ak.testseries.viewmodel.AppLanguage
import Com.ak.testseries.viewmodel.AppTheme
import Com.ak.testseries.exam.UniversalExamRegistry
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onEditProfile: () -> Unit,
    viewModel: NEETViewModel
) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val isHindi = selectedLanguage == AppLanguage.HINDI
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val isOfflineMode by viewModel.isOfflineMode.collectAsStateWithLifecycle()
    val userProfileData by viewModel.userProfile.collectAsStateWithLifecycle()
    val currentAuthUid by viewModel.currentUserId.collectAsStateWithLifecycle()
    val backupUiState by viewModel.backupUiState.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showExamDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val createDocumentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri, context)
        }
    }

    val openDocumentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.stageAndValidateBackupFromUri(uri, context)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(if (isHindi) "लॉग आउट करें?" else "Log Out?") },
            text = { Text(if (isHindi) "क्या आप वाकई लॉग आउट करना चाहते हैं?" else "Are you sure you want to log out?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text(if (isHindi) "हाँ" else "Yes") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(if (isHindi) "नहीं" else "No") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isHindi) "सेटिंग्स" else stringRes("Settings"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            SettingsSectionTitle(if (isHindi) "खाता" else "Account")
            SettingsMenuItem(icon = Icons.Rounded.Person, title = if (isHindi) "प्रोफ़ाइल संपादित करें" else "Edit Profile", subtitle = if (isHindi) "अपना व्यक्तिगत विवरण अपडेट करें" else "Update your personal details", onClick = { onEditProfile() })
            
            Box {
                SettingsMenuItem(icon = Icons.Rounded.Assignment, title = if (isHindi) "लक्ष्य परीक्षा" else stringRes("Target Exam"), subtitle = if (isHindi) "वर्तमान: ${userProfileData.targetExam}" else "Current: ${userProfileData.targetExam}", onClick = { showExamDialog = true })
            }
            SettingsMenuItem(icon = Icons.Rounded.Star, title = if (isHindi) "मेरी सदस्यता" else "My Subscription", subtitle = if (isHindi) "प्रीमियम प्लान प्रबंधित करें" else "Manage premium plans", onClick = { viewModel.navigateToScreen(Com.ak.testseries.viewmodel.AppScreen.SUBSCRIPTION) })
            SettingsMenuItem(icon = Icons.Rounded.Notifications, title = if (isHindi) "सूचनाएं" else stringRes("Notifications"), subtitle = if (isHindi) "पुश और ईमेल प्राथमिकताएं" else "Push and email preferences", onClick = { viewModel.navigateToScreen(Com.ak.testseries.viewmodel.AppScreen.USER_NOTIFICATIONS) })
            
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionTitle(if (isHindi) "डेटा सुरक्षा व बैकअप (Google Drive & SAF)" else "Data Safety & Backup (Google Drive & SAF)")

            val isUserAuthenticated = currentAuthUid.isNotBlank() || userProfileData.email.isNotEmpty()
            if (isUserAuthenticated) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { viewModel.navigateToScreen(Com.ak.testseries.viewmodel.AppScreen.BACKUP_RESTORE) }
                        .testTag("backup_center_highlight_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isHindi) "खाता बैकअप और पुनर्स्थापना केंद्र" else "Backup & Restore Center",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isHindi) "गूगल ड्राइव ऑटो-सिंक, SAF (.aktsbak) बैकअप व रीस्टोर" else "Google Drive auto-sync, SAF (.aktsbak) export & restore",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                SettingsMenuItem(
                    icon = Icons.Rounded.CloudUpload,
                    title = if (isHindi) "त्वरित बैकअप निर्यात (.aktsbak)" else "Quick Export (.aktsbak)",
                    subtitle = if (isHindi) "निजी टेस्ट, प्रयास और बुकमार्क फ़ाइल में सहेजें" else "Save private tests, attempts, and bookmarks to SAF file",
                    onClick = {
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                        createDocumentLauncher.launch("ak_test_series_backup_$timestamp.aktsbak")
                    },
                    modifier = Modifier.testTag("export_backup_menu_item")
                )
                SettingsMenuItem(
                    icon = Icons.Rounded.CloudDownload,
                    title = if (isHindi) "त्वरित बैकअप पुनर्स्थापित करें" else "Quick Restore (.aktsbak)",
                    subtitle = if (isHindi) "सत्यापन और सारांश के साथ .aktsbak फ़ाइल से पुनर्स्थापित करें" else "Safely validate, review summary and restore from .aktsbak file",
                    onClick = {
                        openDocumentLauncher.launch(arrayOf("*/*", "application/octet-stream", "application/zip"))
                    },
                    modifier = Modifier.testTag("restore_backup_menu_item")
                )
            } else {
                SettingsMenuItem(
                    icon = Icons.Rounded.CloudUpload,
                    title = if (isHindi) "स्थानीय बैकअप (साइन इन आवश्यक)" else "Local Backup (Sign In Required)",
                    subtitle = if (isHindi) "खाता-स्वामित्व वाले बैकअप के लिए कृपया साइन इन करें" else "Sign in to export or restore account-owned data",
                    onClick = {
                        android.widget.Toast.makeText(
                            context,
                            if (isHindi) "बैकअप के लिए कृपया पहले साइन इन करें" else "Please sign in to export or restore account data",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.testTag("backup_disabled_menu_item")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionTitle(if (isHindi) "ऐप प्राथमिकताएं" else "App Preferences")
            
            val languageSubtitle = if (isHindi) "वर्तमान: हिंदी" else "Current: English"
            SettingsMenuItem(icon = Icons.Rounded.Public, title = if (isHindi) "भाषा" else "Language", subtitle = languageSubtitle, onClick = { 
                showLanguageDialog = true
            })
            
            val themeSubtitle = if (isHindi) "वर्तमान: " + appTheme.name else "Current: " + appTheme.name
            SettingsMenuItem(icon = Icons.Rounded.DarkMode, title = if (isHindi) "थीम" else "Theme", subtitle = themeSubtitle, onClick = { 
                showThemeDialog = true
            })
            
            val offlineSubtitle = if (isHindi) (if (isOfflineMode) "सक्षम" else "अक्षम") else (if (isOfflineMode) "Enabled" else "Disabled")
            SettingsMenuItem(icon = Icons.Rounded.Download, title = if (isHindi) "ऑफ़लाइन मोड" else "Offline Mode", subtitle = offlineSubtitle, onClick = { 
                viewModel.toggleOfflineMode(!isOfflineMode)
            })
            
            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionTitle(if (isHindi) "सहायता और हमारे बारे में" else "Support & About")
            
            SettingsMenuItem(icon = Icons.Rounded.HelpCenter, title = if (isHindi) "सहायता और समर्थन" else "Help & Support", subtitle = if (isHindi) "पूछे जाने वाले प्रश्न और हमसे संपर्क करें" else "FAQs and contact us", onClick = { 
                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:abhi952817@gmail.com")
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Support: AK Test Series")
                }
                try { context.startActivity(intent) } catch (e: Exception) {}
            })
            SettingsMenuItem(icon = Icons.Rounded.Info, title = if (isHindi) "AK Test Series के बारे में" else "About AK Test Series", subtitle = if (isHindi) "संस्करण 1.0.0" else "Version 1.0.0", onClick = { 
                android.widget.Toast.makeText(context, "AK Test Series Version 1.0.0", android.widget.Toast.LENGTH_SHORT).show()
            })
            SettingsMenuItem(icon = Icons.Rounded.Security, title = if (isHindi) "गोपनीयता नीति" else "Privacy Policy", subtitle = if (isHindi) "नियम और शर्तें" else "Terms and conditions", onClick = { 
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://policies.google.com/privacy"))
                try { context.startActivity(intent) } catch (e: Exception) {}
            })
            
            Spacer(modifier = Modifier.height(32.dp))
            
            val isLoggedIn = userProfileData.email.isNotEmpty() || userProfileData.name.isNotEmpty()
            if (isLoggedIn) {
                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isHindi) "लॉग आउट" else "Log Out", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { viewModel.navigateToScreen(Com.ak.testseries.viewmodel.AppScreen.LOGIN) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Rounded.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isHindi) "साइन इन" else "Sign In", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }


    if (showThemeDialog) {
        FullScreenSettingsDialog(
            title = if (isHindi) "थीम चुनें" else "Select Theme",
            onDismiss = { showThemeDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val themes = listOf(
                    Com.ak.testseries.viewmodel.AppTheme.SYSTEM to (if (isHindi) "सिस्टम डिफ़ॉल्ट" else "System Default"),
                    Com.ak.testseries.viewmodel.AppTheme.LIGHT to (if (isHindi) "लाइट मोड" else "Light Mode"),
                    Com.ak.testseries.viewmodel.AppTheme.DARK to (if (isHindi) "डार्क मोड" else "Dark Mode"),
                    Com.ak.testseries.viewmodel.AppTheme.YELLOW to (if (isHindi) "एम्बर थीम" else "Amber Theme"),
                    Com.ak.testseries.viewmodel.AppTheme.BLUE to (if (isHindi) "नियॉन ब्लू" else "Neon Blue")
                )
                themes.forEach { (themeEnum, themeName) ->
                    val isSelected = appTheme == themeEnum
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { 
                            viewModel.setTheme(themeEnum)
                            showThemeDialog = false
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(themeName, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    if (showLanguageDialog) {
        FullScreenSettingsDialog(
            title = if (isHindi) "भाषा चुनें" else "Select Language",
            onDismiss = { showLanguageDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val languages = listOf(
                    Com.ak.testseries.viewmodel.AppLanguage.ENGLISH to "English",
                    Com.ak.testseries.viewmodel.AppLanguage.HINDI to "हिंदी (Hindi)"
                )
                languages.forEach { (langEnum, langName) ->
                    val isSelected = selectedLanguage == langEnum
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { 
                            viewModel.setLanguage(langEnum)
                            showLanguageDialog = false
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(langName, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    if (showExamDialog) {
        FullScreenSettingsDialog(
            title = if (isHindi) "लक्ष्य परीक्षा चुनें" else "Select Target Exam",
            onDismiss = { showExamDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                data class SettingsBatchGroup(
                    val hindiTitle: String,
                    val englishTitle: String,
                    val color: Color,
                    val exams: List<Pair<String, String>>
                )

                val batches = listOf(
                    SettingsBatchGroup(
                        if (isHindi) "बैच 1: राष्ट्रीय प्रवेश परीक्षाएँ" else "Batch 1: National Entrance & Competitive Exams",
                        "Batch 1: National Entrance & Competitive Exams",
                        MaterialTheme.colorScheme.primary,
                        listOf(
                            "NEET UG" to "🩺 NEET UG (Medical)",
                            "JEE Main" to "📐 JEE Main (Engineering)",
                            "CUET UG" to "🏛️ CUET UG (Universities)",
                            "CAT" to "📊 CAT (IIMs / Management)",
                            "UPSC CSE" to "🇮🇳 UPSC Civil Services (Prelims)",
                            "GATE" to "⚙️ GATE (Engineering CBT)",
                            "NDA" to "🎖️ NDA & NA (Defence)"
                        )
                    ),
                    SettingsBatchGroup(
                        if (isHindi) "बैच 2: कर्मचारी चयन आयोग (SSC Exams)" else "Batch 2: Staff Selection Commission (SSC)",
                        "Batch 2: Staff Selection Commission (SSC)",
                        Color(0xFFF59E0B),
                        listOf(
                            "SSC CGL" to "🦁 SSC CGL (Combined Graduate Level)",
                            "SSC CHSL" to "📝 SSC CHSL (10+2 Level)",
                            "SSC MTS" to "⚡ SSC MTS & Havaldar",
                            "SSC GD" to "🛡️ SSC GD Constable",
                            "SSC CPO" to "👮 SSC CPO (SI Delhi Police/CAPF)"
                        )
                    ),
                    SettingsBatchGroup(
                        if (isHindi) "बैच 3: भारतीय रेलवे भर्ती बोर्ड (RRB Exams)" else "Batch 3: Railway Recruitment Board (RRB)",
                        "Batch 3: Railway Recruitment Board (RRB)",
                        Color(0xFF059669),
                        listOf(
                            "RRB NTPC" to "🚆 RRB NTPC (Non-Technical)",
                            "RRB Group D" to "🔨 RRB Group D (RRC Level-1)",
                            "RRB ALP" to "🚂 RRB ALP (Assistant Loco Pilot)",
                            "RRB Technician" to "🔧 RRB Technician Grade III",
                            "RRB JE" to "⚙️ RRB JE (Junior Engineer)"
                        )
                    ),
                    SettingsBatchGroup(
                        if (isHindi) "बैच 4: बैंकिंग एवं वित्तीय परीक्षाएँ (Banking Exams)" else "Batch 4: Banking & Financial Services",
                        "Batch 4: Banking & Financial Services",
                        Color(0xFF0284C7),
                        listOf(
                            "SBI PO" to "🏦 SBI PO (Probationary Officer)",
                            "SBI Clerk" to "💼 SBI Clerk (Junior Associate)",
                            "IBPS PO" to "🏛️ IBPS PO (Probationary Officer)",
                            "IBPS Clerk" to "📋 IBPS Clerk (CRP CSA)"
                        )
                    ),
                    SettingsBatchGroup(
                        if (isHindi) "बैच 5: शिक्षण एवं अन्य परीक्षाएँ (Teaching & Eligibility)" else "Batch 5: Teaching & Eligibility",
                        "Batch 5: Teaching & Eligibility",
                        Color(0xFF0D9488),
                        listOf(
                            "CTET" to "📚 CTET (Paper 1 & 2)",
                            "UGC NET" to "🎓 UGC NET (JRF & Asst. Professor)"
                        )
                    )
                )

                batches.forEachIndexed { bIndex, batch ->
                    if (bIndex > 0) Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = batch.hindiTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = batch.color,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    batch.exams.forEach { (examId, examTitle) ->
                        val examPattern = UniversalExamRegistry.getExamDisplaySubtitleByName(examId)
                        val isSelected = userProfileData.targetExam.equals(examId, ignoreCase = true) ||
                                (examId == "NEET UG" && userProfileData.targetExam.equals("NEET", ignoreCase = true)) ||
                                (examId == "JEE Main" && userProfileData.targetExam.equals("JEE", ignoreCase = true)) ||
                                (examId == "UPSC CSE" && userProfileData.targetExam.contains("UPSC", ignoreCase = true)) ||
                                (examId == "GATE" && userProfileData.targetExam.contains("GATE", ignoreCase = true)) ||
                                (examId == "NDA" && userProfileData.targetExam.contains("NDA", ignoreCase = true)) ||
                                (examId == "SSC CGL" && userProfileData.targetExam.contains("CGL", ignoreCase = true)) ||
                                (examId == "SSC CHSL" && userProfileData.targetExam.contains("CHSL", ignoreCase = true)) ||
                                (examId == "SSC MTS" && userProfileData.targetExam.contains("MTS", ignoreCase = true)) ||
                                (examId == "SSC GD" && userProfileData.targetExam.contains("GD", ignoreCase = true)) ||
                                (examId == "SSC CPO" && userProfileData.targetExam.contains("CPO", ignoreCase = true)) ||
                                (examId == "RRB NTPC" && userProfileData.targetExam.contains("NTPC", ignoreCase = true)) ||
                                (examId == "RRB Group D" && (userProfileData.targetExam.contains("Group D", ignoreCase = true) || userProfileData.targetExam.contains("Level 1", ignoreCase = true))) ||
                                (examId == "RRB ALP" && userProfileData.targetExam.contains("ALP", ignoreCase = true)) ||
                                (examId == "RRB JE" && userProfileData.targetExam.contains("JE", ignoreCase = true)) ||
                                (examId == "RRB Technician" && userProfileData.targetExam.contains("Tech", ignoreCase = true)) ||
                                (examId == "SBI PO" && userProfileData.targetExam.contains("SBI", ignoreCase = true) && userProfileData.targetExam.contains("PO", ignoreCase = true)) ||
                                (examId == "SBI Clerk" && userProfileData.targetExam.contains("SBI", ignoreCase = true) && (userProfileData.targetExam.contains("Clerk", ignoreCase = true) || userProfileData.targetExam.contains("JA", ignoreCase = true))) ||
                                (examId == "IBPS PO" && userProfileData.targetExam.contains("IBPS", ignoreCase = true) && userProfileData.targetExam.contains("PO", ignoreCase = true)) ||
                                (examId == "IBPS Clerk" && userProfileData.targetExam.contains("IBPS", ignoreCase = true) && userProfileData.targetExam.contains("Clerk", ignoreCase = true)) ||
                                (examId == "CTET" && userProfileData.targetExam.contains("CTET", ignoreCase = true)) ||
                                (examId == "UGC NET" && userProfileData.targetExam.contains("NET", ignoreCase = true))

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { 
                                viewModel.saveUserProfile(
                                    name = userProfileData.name,
                                    email = userProfileData.email,
                                    stateName = userProfileData.stateName,
                                    cityName = userProfileData.cityName,
                                    profileImageUri = userProfileData.profileImageUri,
                                    targetExam = examId
                                )
                                showExamDialog = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) batch.color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) batch.color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) batch.color else Color.Gray
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(examTitle, fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                    Text(examPattern, fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (val state = backupUiState) {
        is Com.ak.testseries.data.backup.BackupUiState.Exporting -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(if (isHindi) "बैकअप निर्यात हो रहा है" else "Exporting Backup") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(state.message, fontSize = 14.sp)
                    }
                }
            )
        }
        is Com.ak.testseries.data.backup.BackupUiState.Validating -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(if (isHindi) "बैकअप की पुष्टि हो रही है" else "Validating Backup Archive") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(state.message, fontSize = 14.sp)
                    }
                }
            )
        }
        is Com.ak.testseries.data.backup.BackupUiState.Restoring -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(if (isHindi) "डेटा पुनर्स्थापित हो रहा है" else "Restoring Account Data") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(state.message, fontSize = 14.sp)
                    }
                }
            )
        }
        is Com.ak.testseries.data.backup.BackupUiState.ValidationReady -> {
            val manifest = state.manifest
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupDialog() },
                title = { Text(if (isHindi) "बैकअप पुनर्स्थापना की पुष्टि करें" else "Confirm Data Restore") },
                text = {
                    val staged = state.stagedData
                    val testsCount = staged?.tests?.size ?: manifest.stats.privateTestsCount
                    val questionsCount = staged?.questions?.size ?: manifest.stats.questionsCount
                    val attemptsCount = staged?.attempts?.size ?: manifest.stats.attemptsCount
                    val bookmarksCount = staged?.bookmarks?.testBookmarks?.size ?: manifest.stats.bookmarkedCount
                    val diagramsCount = staged?.stagedDiagramFiles?.size ?: manifest.stats.diagramsCount
                    val syllabusCount = staged?.syllabus?.completedChaptersByExam?.values?.sumOf { it.size } ?: 0

                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text(
                            text = if (isHindi) "बैकअप फ़ाइल सफलतापूर्वक सत्यापित हुई। विवरण:" else "Backup archive verified successfully. Summary:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Account: ${manifest.owner.email ?: manifest.owner.displayName ?: manifest.owner.uid}", fontSize = 13.sp)
                        Text("• Private Tests: $testsCount", fontSize = 13.sp)
                        Text("• Questions: $questionsCount", fontSize = 13.sp)
                        Text("• Completed Attempts: $attemptsCount", fontSize = 13.sp)
                        Text("• Bookmarks: $bookmarksCount", fontSize = 13.sp)
                        Text("• Media Diagrams: $diagramsCount", fontSize = 13.sp)
                        if (syllabusCount > 0) {
                            Text("• Completed Topics: $syllabusCount", fontSize = 13.sp)
                        }
                        Text("• Completeness: ${manifest.completeness.name}", fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isHindi) "सूचना: यह बैकअप आपके मौजूदा खाते के डेटा में विलय हो जाएगा। स्थानीय नए टेस्ट सुरक्षित रहेंगे।"
                                else "Notice: Restoring will merge data into your active account. Live tests with newer edits are preserved.",
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
                        Text(if (isHindi) "पुनर्स्थापित करें" else "Restore Now")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.dismissBackupDialog() },
                        modifier = Modifier.testTag("cancel_restore_button")
                    ) {
                        Text(if (isHindi) "रद्द करें" else "Cancel")
                    }
                }
            )
        }
        is Com.ak.testseries.data.backup.BackupUiState.ExportSuccess -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupDialog() },
                icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(if (isHindi) "बैकअप निर्यात सफल" else "Backup Export Complete") },
                text = { Text(state.message, fontSize = 14.sp) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissBackupDialog() },
                        modifier = Modifier.testTag("export_success_dismiss_button")
                    ) {
                        Text("OK")
                    }
                }
            )
        }
        is Com.ak.testseries.data.backup.BackupUiState.RestoreSuccess -> {
            val res = state.result
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupDialog() },
                icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(if (isHindi) "पुनर्स्थापना पूर्ण" else "Restore Successful") },
                text = {
                    Column {
                        Text(
                            if (isHindi) "खाता डेटा सफलतापूर्वक पुनर्स्थापित किया गया।" else "Account data restored successfully.",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• Tests Restored: ${res.restoredTestsCount}", fontSize = 13.sp)
                        Text("• Questions: ${res.restoredQuestionsCount}", fontSize = 13.sp)
                        Text("• Attempts: ${res.restoredAttemptsCount} (${res.skippedDuplicateAttemptsCount} duplicates skipped)", fontSize = 13.sp)
                        Text("• Diagrams: ${res.restoredDiagramsCount}", fontSize = 13.sp)
                        if (res.preservedNewerTestsCount > 0) {
                            Text("• Newer local tests preserved: ${res.preservedNewerTestsCount}", fontSize = 13.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissBackupDialog() },
                        modifier = Modifier.testTag("restore_success_dismiss_button")
                    ) {
                        Text("OK")
                    }
                }
            )
        }
        is Com.ak.testseries.data.backup.BackupUiState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupDialog() },
                icon = { Icon(Icons.Rounded.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(state.title) },
                text = { Text(state.message, fontSize = 14.sp) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissBackupDialog() },
                        modifier = Modifier.testTag("error_dialog_dismiss_button")
                    ) {
                        Text(if (isHindi) "ठीक है" else "Dismiss")
                    }
                }
            )
        }
        is Com.ak.testseries.data.backup.BackupUiState.Idle -> {
            // Idle state
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.LightGray)
    }
}

@Composable
fun FullScreenSettingsDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    Surface(
                        tonalElevation = 4.dp,
                        shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    content()
                }
            }
        }
    }
}
