package Com.ak.testseries

import Com.ak.testseries.exam.SubjectStatData
import Com.ak.testseries.exam.UniversalExamAdminScreen
import Com.ak.testseries.exam.UniversalQuestionModel
import Com.ak.testseries.stringRes
import Com.ak.testseries.ui.UploadAiTabScreen
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import android.annotation.SuppressLint
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import Com.ak.testseries.data.AttemptEntity
import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.data.SyllabusEntity
import Com.ak.testseries.data.TestEntity
import Com.ak.testseries.data.StudentData
import Com.ak.testseries.ui.theme.*
import Com.ak.testseries.viewmodel.*
import androidx.compose.ui.res.painterResource

import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.ExtendedFloatingActionButton

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val testIdFromDeepLink: String? = intent?.data?.let { data ->
            if ((data.scheme == "http" || data.scheme == "https") && data.host == "aktestseries.app" && data.pathSegments.firstOrNull() == "test") {
                data.pathSegments.getOrNull(1)
            } else null
        }
        
        val magicLink: String? = intent?.data?.let { data ->
            if ((data.scheme == "http" || data.scheme == "https") && data.host == "aktestseries.app" && data.pathSegments.firstOrNull() == "login") {
                data.toString()
            } else null
        }

        val targetTabFromIntent: String? = intent?.getStringExtra("EXTRA_TARGET_TAB")
        val autoWorkflowFromIntent: String? = intent?.getStringExtra("EXTRA_AUTO_WORKFLOW")

        enableEdgeToEdge()
        setContent {
            val application = applicationContext as Application
            val viewModelFactory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(NEETViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return NEETViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
            val viewModel: NEETViewModel = viewModel(factory = viewModelFactory)

            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            
            // Check magic link on launch
            LaunchedEffect(magicLink) {
                if (magicLink != null) {
                    val authManager = Com.ak.testseries.data.auth.FirebaseAuthManagerImpl()
                    val userRepository = Com.ak.testseries.data.repository.FirebaseUserRepositoryImpl()
                    viewModel.signInWithMagicLink(magicLink, this@MainActivity, authManager, userRepository) { success, error ->
                        if (success) {
                            android.widget.Toast.makeText(this@MainActivity, "Magic Link Login Successful!", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(this@MainActivity, "Magic Link Error: $error", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            // FCM token retrieval disabled in emulator environment to prevent TOO_MANY_REGISTRATIONS errors
            val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

            MyApplicationTheme(
                appTheme = appTheme,
                targetExam = userProfile.targetExam
            ) {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalAppLanguage provides selectedLanguage) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authManager = remember { Com.ak.testseries.data.auth.FirebaseAuthManagerImpl() }
                    val userRepository = remember { Com.ak.testseries.data.repository.FirebaseUserRepositoryImpl() }
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    
                    LaunchedEffect(testIdFromDeepLink) {
                        if (testIdFromDeepLink != null) {
                            viewModel.handleDeepLinkTest(testIdFromDeepLink)
                        }
                    }

                    LaunchedEffect(targetTabFromIntent, autoWorkflowFromIntent) {
                        if (targetTabFromIntent == "UPLOAD_AI") {
                            viewModel.navigateToScreen(AppScreen.DASHBOARD)
                            viewModel.setTab(Com.ak.testseries.viewmodel.HomeTab.UPLOAD_AI)
                            if (autoWorkflowFromIntent != null) {
                                viewModel.autoRoutedWorkflow.value = autoWorkflowFromIntent
                                val isPub = (autoWorkflowFromIntent == "public")
                                viewModel.genIsPublic.value = isPub
                                viewModel.autoShareToCommunity.value = isPub
                            }
                        }
                    }
                    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { isGranted -> }

                    // Check if already logged in on app start, request permission, and subscribe to FCM topics
                    LaunchedEffect(Unit) {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (!hasPermission) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }

                        if (authManager.getSignedInUser() != null) {
                            // If logged in, skip login screen
                            if (viewModel.currentScreen.value == AppScreen.LOGIN) {
                                viewModel.setLanguage(AppLanguage.ENGLISH) // or just navigate to dashboard
                            }
                        }
                    }

                    when (currentScreen) {
                        AppScreen.LOGIN -> {
                            val context = LocalContext.current
                            val scope = rememberCoroutineScope()
                            var authError by remember { mutableStateOf<String?>(null) }


                            val launcher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.StartActivityForResult()
                            ) { result ->
                                viewModel.handleSignInResult(context, result.data, authManager, userRepository)
                            }
                            Com.ak.testseries.ui.LoginScreen(
                                onGoogleSignInClick = {
                                    val intent = authManager.getSignInIntent(context)
                                    intent?.let { launcher.launch(it) }
                                },
                                onSignInClick = { email, password ->
                                    viewModel.signInWithEmail(email, password, authManager, userRepository) { success, error ->
                                        if (!success) authError = error ?: "Sign In Failed"
                                    }
                                },
                                onSignUpClick = { name, email, password ->
                                    viewModel.signUpWithEmail(name, email, password, authManager, userRepository) { success, error ->
                                        if (!success) authError = error ?: "Sign Up Failed"
                                    }
                                },
                                onMagicLinkClick = { email ->
                                    viewModel.sendMagicLink(email, context, authManager) { success, error ->
                                        if (success) {
                                            android.widget.Toast.makeText(context, "Magic Link sent to $email", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            authError = error ?: "Failed to send link"
                                        }
                                    }
                                },
                                authError = authError,
                                clearAuthError = { authError = null }
                            )
                        }
                        AppScreen.PROFILE_ONBOARDING -> {
                            Com.ak.testseries.ui.ProfileOnboardingScreen(
                                viewModel = viewModel,
                                onComplete = {
                                    viewModel.navigateToScreen(AppScreen.LANGUAGE_SELECTION)
                                }
                            )
                        }
                        AppScreen.LANGUAGE_SELECTION -> {
                            LanguageSelectionScreen(
                                onLanguageSelected = { lang ->
                                    viewModel.setLanguage(lang)
                                }
                            )
                        }
                        AppScreen.DASHBOARD -> {
                            DashboardScreen(
                                viewModel = viewModel,
                                appTheme = appTheme,
                                authManager = authManager
                            )
                        }
                        AppScreen.EXAM_MODE -> {
                            Com.ak.testseries.ui.ExamSimulationScreen(viewModel = viewModel)
                        }
                        AppScreen.REATTEMPT_AD -> {
                            Com.ak.testseries.ui.ReattemptAdScreen(viewModel = viewModel)
                        }
                        AppScreen.RESULT_ANALYSIS -> {
                            ResultScreen(viewModel = viewModel)
                        }
                        AppScreen.ANALYTICS -> {
                            Com.ak.testseries.ui.AnalyticsScreen(
                                onNavigateBack = { viewModel.navigateToScreen(AppScreen.DASHBOARD) },
                                viewModel = viewModel
                            )
                        }
                        AppScreen.STUDY_MATERIAL -> {
                            Com.ak.testseries.ui.StudyMaterialScreen(
                                onNavigateBack = { viewModel.navigateToScreen(AppScreen.DASHBOARD) },
                                viewModel = viewModel
                            )
                        }
                        AppScreen.SUBSCRIPTION -> {
                            Com.ak.testseries.ui.SubscriptionScreen(
                                onNavigateBack = { viewModel.navigateToScreen(AppScreen.DASHBOARD) },
                                viewModel = viewModel
                            )
                        }
                        AppScreen.SETTINGS -> {
                            Com.ak.testseries.ui.SettingsScreen(
                                onNavigateBack = { viewModel.navigateToScreen(AppScreen.DASHBOARD) },
                                onLogout = { viewModel.logout(authManager) },
                                onEditProfile = { viewModel.showProfileEditDialog.value = true },
                                viewModel = viewModel
                            )
                        }
                        AppScreen.BACKUP_RESTORE -> {
                            Com.ak.testseries.ui.BackupRestoreScreen(
                                viewModel = viewModel,
                                onNavigateBack = { viewModel.navigateToScreen(AppScreen.SETTINGS) }
                            )
                        }
                        AppScreen.HOW_IT_WORKS -> {
                            Com.ak.testseries.ui.HowItWorksScreen(
                                onNavigateBack = { viewModel.navigateToScreen(AppScreen.DASHBOARD) }
                            )
                        }
                        AppScreen.ADMIN_PANEL -> {
                            if (!viewModel.isAdmin) {
                                androidx.activity.compose.BackHandler {
                                    viewModel.navigateToScreen(AppScreen.DASHBOARD)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                Icons.Rounded.Security,
                                                contentDescription = "Access Restricted",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(56.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Access Restricted",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Administrator privileges are required to access the Master Admin Panel.",
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                            Spacer(modifier = Modifier.height(24.dp))
                                            Button(
                                                onClick = { viewModel.navigateToScreen(AppScreen.DASHBOARD) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("Return to Dashboard")
                                            }
                                        }
                                    }
                                }
                            } else {
                                AdminPanelScreen(viewModel = viewModel)
                            }
                        }
                        AppScreen.USER_NOTIFICATIONS -> {
                            UserNotificationsScreen(viewModel = viewModel)
                        }
                        AppScreen.PRIVATE_VAULT -> {
                            Com.ak.testseries.ui.PrivateTestsVaultScreen(
                                viewModel = viewModel,
                                onNavigateBack = { viewModel.navigateToScreen(AppScreen.DASHBOARD) }
                            )
                        }
                        AppScreen.HIERARCHICAL_TEST_FLOW -> {
                            Com.ak.testseries.ui.HierarchicalTestFlowHost(
                                viewModel = viewModel,
                                onBackToHome = { viewModel.navigateToScreen(AppScreen.DASHBOARD) },
                                onLaunchTest = { test ->
                                    viewModel.launchHierarchicalTest(test)
                                }
                            )
                        }
                    }

                    val isCurrentUserBanned by viewModel.isCurrentUserBanned.collectAsStateWithLifecycle()
                    if (isCurrentUserBanned && currentScreen != AppScreen.LOGIN) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .testTag("user_suspension_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = "Account Suspended",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Account Suspended",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Your account has been suspended by administration. Protected actions and test submissions are restricted.",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { viewModel.logout(authManager) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier.testTag("suspended_sign_out_button")
                                ) {
                                    Text("Sign Out", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    All180QuestionsPreviewDialog(viewModel = viewModel)
                    GlobalPdfCropperDialog(viewModel = viewModel)
                    ReportIssueDialog(viewModel = viewModel)
                    DeleteConfirmDialog(viewModel = viewModel)
                    AppLevelProfileEditDialog(viewModel = viewModel)
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

// ==========================================
// 🛠️ CUSTOM HIGH-FIDELITY COMPOSABLES (Avoid experimental API bugs)
// ==========================================
@Composable
fun CustomTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (navigationIcon != null) {
                    navigationIcon()
                    Spacer(modifier = Modifier.width(12.dp))
                }
                title()
            }
            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    actions()
                }
            }
        }
    }
}

@Composable
fun CustomBadge(
    containerColor: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ==========================================
// 🗣️ LANGUAGE SELECTION SCREEN
// ==========================================
@Composable
fun LanguageSelectionScreen(onLanguageSelected: (AppLanguage) -> Unit) {
    val brush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.background
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(brush)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Branding Banner
        Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_ak_logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(120.dp).clip(CircleShape).align(Alignment.CenterHorizontally)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "AK Test Series AI",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "नीट टेस्ट सीरीज प्लेटफॉर्म",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "इस ऐप को मुफ़्त रखने के लिए, ऐप ओपन करने पर (दिन में अधिकतम 3 बार, 6 घंटे के अंतराल पर) और टेस्ट शुरू करने से पहले एक छोटा विज्ञापन दिखाया जाएगा।\nTo keep this app 100% free, a short ad will be shown on app open (max 3 times/day, 6-hour gap) and before starting a test.",
            fontSize = 11.sp,
            color = Color(0xFF777777),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "चुनें अपनी भाषा / Choose Language",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(24.dp))

                LanguageButton("हिंदी (Hindi)", "पूर्ण हिंदी माध्यम पेपर और विश्लेषण", AppLanguage.HINDI, onLanguageSelected)
                Spacer(modifier = Modifier.height(12.dp))
                LanguageButton("English", "Complete English medium paper and analysis", AppLanguage.ENGLISH, onLanguageSelected)
            }
        }
    }
}

@Composable
fun LanguageButton(title: String, subtitle: String, languageValue: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    OutlinedButton(
        onClick = { onSelected(languageValue) },
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Select",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ==========================================
// 🏠 DASHBOARD CONTAINER (Bottom Tabs)
// ==========================================
@Composable
fun DashboardScreen(
    viewModel: NEETViewModel,
    appTheme: Com.ak.testseries.viewmodel.AppTheme,
    authManager: Com.ak.testseries.domain.auth.AuthManager
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val isOfflineMode by viewModel.isOfflineMode.collectAsStateWithLifecycle()
    val preTestStep by viewModel.preTestStep.collectAsStateWithLifecycle()
    val testPending by viewModel.testPendingStart.collectAsStateWithLifecycle()
    val viewSyllabusTest by viewModel.viewSyllabusTest.collectAsStateWithLifecycle()
    val isCommunityEnabled by viewModel.isCommunityEnabled.collectAsStateWithLifecycle()
    val currentStreak by viewModel.currentStreak.collectAsStateWithLifecycle()
    
    val userProfileData by viewModel.userProfile.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current 
    LaunchedEffect(Unit) { 
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) 
        // Mark first launch as false without blocking the dashboard view
        prefs.edit().putBoolean("is_first_time_launch", false).apply() 
    } 

    var showAdminPasswordDialog by remember { mutableStateOf(false) }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var adminTapCount by remember { mutableIntStateOf(0) }
    var adminTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isFabVisible by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y < -15f) {
                    isFabVisible = false
                } else if (available.y > 15f) {
                    isFabVisible = true
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    viewSyllabusTest?.let {
        Com.ak.testseries.ui.SyllabusPdfModal(
            test = it,
            language = selectedLanguage,
            targetExam = userProfileData.targetExam,
            onClose = { viewModel.closeSyllabusModal() }
        )
    }

    if (preTestStep > 0 && testPending != null) {
        PreTestModals(
            step = preTestStep,
            test = testPending!!,
            language = selectedLanguage,
            targetExam = userProfileData.targetExam,
            onNext = { viewModel.nextPreTestStep() },
            onCancel = { viewModel.cancelPreTestFlow() }
        )
    }

    if (showAdminPasswordDialog) {
        var passwordInput by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf("") }
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("AdminPrefs", android.content.Context.MODE_PRIVATE)
        val currentPassword = prefs.getString("admin_password", "admin123") ?: "admin123"

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAdminPasswordDialog = false },
            title = { Text(stringRes("Admin Access")) },
            text = {
                Column {
                    Text(stringRes("Enter admin password to continue:"))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it; errorMsg = "" },
                        label = { Text(stringRes("Password")) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
                    )
                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val isOwner = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let {
                        it.email?.equals("abhi952817@gmail.com", ignoreCase = true) == true && it.isEmailVerified
                    } == true
                    if (!viewModel.isAdmin && !isOwner) {
                        errorMsg = "Unauthorized: Admin privileges required on your account."
                    } else if (isOwner || (currentPassword.isNotBlank() && passwordInput == currentPassword)) {
                        showAdminPasswordDialog = false
                        viewModel.navigateToScreen(AppScreen.ADMIN_PANEL)
                    } else {
                        errorMsg = "Incorrect password"
                    }
                }) { Text(stringRes("Login")) }
            },
            dismissButton = {
                TextButton(onClick = { showAdminPasswordDialog = false }) { Text(stringRes("Cancel")) }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp), drawerContainerColor = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(24.dp))
                    
                    // App Logo & Name Row at the very top of Drawer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(40.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            adminTapCount++
                                            if (adminTapCount >= 3 && viewModel.isAdmin) {
                                                adminTapCount = 0
                                                adminTapJob?.cancel()
                                                showAdminPasswordDialog = true
                                            } else {
                                                adminTapJob?.cancel()
                                                adminTapJob = scope.launch {
                                                    kotlinx.coroutines.delay(400)
                                                    adminTapCount = 0
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = R.drawable.ic_ak_logo),
                                contentDescription = "App Logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "AK TEST SERIES",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedLanguage == AppLanguage.HINDI) "लक्षित परीक्षा" else "Target Exam",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = userProfileData.targetExam.ifEmpty { "NEET UG" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    viewModel.navigateToScreen(AppScreen.SETTINGS)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (selectedLanguage == AppLanguage.HINDI) "बदलें" else "Change",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    

                    
                    // Dynamic Exam Syllabus Tracker (First Item)
                    val examConfig = Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(userProfileData.targetExam)
                    val examShortLabel = examConfig?.shortName ?: userProfileData.targetExam.ifEmpty { "Exam" }
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.AccountTree, contentDescription = "Practice Flow") },
                        label = { Text("⚡ Test Flow (Exam ➔ Topic)", fontWeight = FontWeight.Bold) },
                        selected = viewModel.currentScreen.value == AppScreen.HIERARCHICAL_TEST_FLOW,
                        onClick = {
                            viewModel.navigateToScreen(AppScreen.HIERARCHICAL_TEST_FLOW)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.MenuBook, contentDescription = "Syllabus Tracker") },
                        label = { Text("$examShortLabel Syllabus Tracker", fontWeight = FontWeight.Bold) },
                        selected = currentTab == HomeTab.NCERT,
                        onClick = {
                            viewModel.setTab(HomeTab.NCERT)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    // Test History (Profile)
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.History, contentDescription = "History") },
                        label = { Text(stringRes("Test History")) },
                        selected = currentTab == HomeTab.PROFILE,
                        onClick = {
                            viewModel.setTab(HomeTab.PROFILE)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    
                    // Saved Tests
                    NavigationDrawerItem(
                        icon = { Icon(Com.ak.testseries.ui.CustomSaveIconFilled, contentDescription = stringRes("Saved Tests")) },
                        label = { Text(stringRes("Saved Tests")) },
                        selected = currentTab == HomeTab.SAVED_TESTS,
                        onClick = {
                            viewModel.setTab(HomeTab.SAVED_TESTS)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    // Backup & Restore Center (Google Drive & SAF)
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.CloudSync, contentDescription = "Backup & Cloud Sync", tint = MaterialTheme.colorScheme.primary) },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedLanguage == AppLanguage.HINDI) "बैकअप व क्लाउड सिंक" else "Backup & Cloud Sync",
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Drive & SAF",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        selected = viewModel.currentScreen.value == AppScreen.BACKUP_RESTORE,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.navigateToScreen(AppScreen.BACKUP_RESTORE)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_backup_restore_item")
                    )
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.PieChart, contentDescription = "Analytics") },
                        label = { Text(stringRes("Detailed Analytics")) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.navigateToScreen(AppScreen.ANALYTICS)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.HelpOutline, contentDescription = "How to Use") },
                        label = { Text("How to Use App") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.navigateToScreen(AppScreen.HOW_IT_WORKS)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.LibraryBooks, contentDescription = "Study Materials") },
                        label = { Text(stringRes("Study Materials & Notes")) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.navigateToScreen(AppScreen.STUDY_MATERIAL)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    // Offline Mode Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleOfflineMode(!isOfflineMode) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Download, contentDescription = "Offline Mode", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (selectedLanguage == AppLanguage.HINDI) "ऑफ़लाइन मोड" else "Offline Mode", fontWeight = FontWeight.Medium)
                        }
                        androidx.compose.material3.Switch(
                            checked = isOfflineMode,
                            onCheckedChange = { viewModel.toggleOfflineMode(it) }
                        )
                    }
                    Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                    

    
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = stringRes("Settings")) },
                        label = { Text(stringRes("Settings")) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.navigateToScreen(AppScreen.SETTINGS)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    
                    // Share App
                    NavigationDrawerItem(
                        icon = { Icon(painter = androidx.compose.ui.res.painterResource(id = Com.ak.testseries.R.drawable.ic_custom_share), contentDescription = stringRes("Share App"), tint = androidx.compose.ui.graphics.Color.Unspecified) },
                        label = { Text(stringRes("Share App")) },
                        selected = false,
                        onClick = {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "AK Test Series AI")
                                putExtra(android.content.Intent.EXTRA_TEXT, "Hey! Download the AK Test Series AI app to boost your preparation and track your progress! Join us on Telegram: https://t.me/AKTESTSERIESapp")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share via"))
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    
                    // Drawer end
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            topBar = {
                if (currentTab != HomeTab.COMMUNITY) {
                    Column {
                        CustomTopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically, 
                                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp, top = 2.dp, bottom = 2.dp)
                                ) {
                                    // Menu Icon
                                    IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Rounded.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
                                    }
                                    
                                    Spacer(modifier = Modifier.width(4.dp))
                                    
                                    // Search Bar in TopAppBar
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(22.dp))
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            androidx.compose.foundation.text.BasicTextField(
                                                value = viewModel.filterState.value.query,
                                                onValueChange = {
                                                    viewModel.filterState.value = viewModel.filterState.value.copy(query = it)
                                                    if (currentTab != HomeTab.SEARCH) viewModel.setTab(HomeTab.SEARCH)
                                                },
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                                decorationBox = { innerTextField ->
                                                    if (viewModel.filterState.value.query.isEmpty()) {
                                                        Text(if (selectedLanguage == AppLanguage.HINDI) "खोजें..." else "Search...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                                    }
                                                    innerTextField()
                                                },
                                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    // Notification Icon
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                                        val notifications by viewModel.userNotifications.collectAsStateWithLifecycle()
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                            modifier = Modifier.size(40.dp).clickable { viewModel.navigateToScreen(AppScreen.USER_NOTIFICATIONS) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Notifications,
                                                    contentDescription = stringRes("Notifications"),
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                                // Always show dot for visual demo as requested
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(top = 8.dp, end = 8.dp)
                                                        .size(8.dp)
                                                        .background(Color(0xFFFF5252), CircleShape)
                                                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Cloud Backup & Sync Quick Action
                                    IconButton(
                                        onClick = { viewModel.navigateToScreen(AppScreen.BACKUP_RESTORE) },
                                        modifier = Modifier.size(36.dp).testTag("topbar_backup_sync_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudSync,
                                            contentDescription = "Backup & Cloud Sync",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(4.dp))
                                    
                                    // Profile Icon
                                    val userProfileData by viewModel.userProfile.collectAsStateWithLifecycle()
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(
                                                androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF8E24AA), Color(0xFF5E35B1))), 
                                                androidx.compose.foundation.shape.CircleShape
                                            )
                                            .clickable { viewModel.navigateToScreen(AppScreen.SETTINGS) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (userProfileData.profileImageUri.isNotEmpty()) {
                                            coil.compose.AsyncImage(
                                                model = userProfileData.profileImageUri,
                                                contentDescription = "Profile",
                                                modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Rounded.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        )
                        Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                    }
                }
            },
            floatingActionButton = {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isFabVisible,
                    enter = androidx.compose.animation.scaleIn(),
                    exit = androidx.compose.animation.scaleOut()
                ) {
                    if (currentTab == HomeTab.PROFILE) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                viewModel.showCreateFolderDialog.value = true
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            icon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = "New Folder") },
                            text = { Text(if (selectedLanguage == AppLanguage.HINDI) "नया फ़ोल्डर" else "New Folder") }
                        )
                    } else if (currentTab == HomeTab.HOME) {
                        val tests by viewModel.testsList.collectAsStateWithLifecycle()
                        val latestTest = tests.firstOrNull()
                        FloatingActionButton(
                            onClick = { 
                                latestTest?.let { viewModel.initiateTest(it) } 
                            },
                            containerColor = Color(0xFF6C63FF),
                            contentColor = Color.White,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(32.dp))
                        }
                    }
                }
            },
            bottomBar = {
                if (currentTab != HomeTab.COMMUNITY) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            modifier = Modifier
                                .shadow(24.dp, RoundedCornerShape(32.dp), spotColor = MaterialTheme.colorScheme.primary, ambientColor = MaterialTheme.colorScheme.secondary)
                                .border(
                                    1.dp,
                                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), Color.Transparent, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))),
                                    RoundedCornerShape(32.dp)
                                ),
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                @Composable
                                fun CustomNavItem(
                                    icon: androidx.compose.ui.graphics.vector.ImageVector,
                                    label: @Composable () -> Unit,
                                    isSelected: Boolean,
                                    activeColor: Color,
                                    onClick: () -> Unit
                                ) {
                                    val iconColor = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    val bgColor = if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Transparent
                                    val borderBrush = if (isSelected) androidx.compose.ui.graphics.Brush.linearGradient(listOf(activeColor.copy(alpha = 0.5f), Color.Transparent)) else androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(onClick = onClick)
                                            .padding(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(bgColor, CircleShape)
                                                .border(1.dp, borderBrush, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .background(
                                                            androidx.compose.ui.graphics.Brush.radialGradient(
                                                                colors = listOf(activeColor.copy(alpha = 0.4f), Color.Transparent),
                                                                radius = 50f
                                                            )
                                                        )
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        label()
                                    }
                                }

                                CustomNavItem(
                                    icon = Icons.Rounded.Home,
                                    label = { BilingualText("Home", "होम", selectedLanguage, englishFontSize = 10.sp, hindiFontSize = 10.sp, englishColor = if (currentTab == HomeTab.HOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, hindiColor = if (currentTab == HomeTab.HOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                                    isSelected = currentTab == HomeTab.HOME,
                                    activeColor = MaterialTheme.colorScheme.primary,
                                    onClick = { viewModel.setTab(HomeTab.HOME) }
                                )
                                
                                if (isCommunityEnabled) {
                                    CustomNavItem(
                                        icon = Icons.Rounded.People,
                                        label = { BilingualText("Community", "समुदाय", selectedLanguage, englishFontSize = 10.sp, hindiFontSize = 10.sp, englishColor = if (currentTab == HomeTab.COMMUNITY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, hindiColor = if (currentTab == HomeTab.COMMUNITY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                                        isSelected = currentTab == HomeTab.COMMUNITY,
                                        activeColor = MaterialTheme.colorScheme.primary,
                                        onClick = { viewModel.setTab(HomeTab.COMMUNITY) }
                                    )
                                }

                                CustomNavItem(
                                    icon = Icons.Rounded.Add,
                                    label = { BilingualText("Add Test", "टेस्ट", selectedLanguage, englishFontSize = 10.sp, hindiFontSize = 10.sp, englishColor = if (currentTab == HomeTab.UPLOAD_AI) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, hindiColor = if (currentTab == HomeTab.UPLOAD_AI) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant) },
                                    isSelected = currentTab == HomeTab.UPLOAD_AI,
                                    activeColor = MaterialTheme.colorScheme.secondary,
                                    onClick = { viewModel.setTab(HomeTab.UPLOAD_AI) }
                                )

                                CustomNavItem(
                                    icon = Icons.Rounded.EmojiEvents,
                                    label = { BilingualText("Rank", "रैंक", selectedLanguage, englishFontSize = 10.sp, hindiFontSize = 10.sp, englishColor = if (currentTab == HomeTab.LEADERBOARD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, hindiColor = if (currentTab == HomeTab.LEADERBOARD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                                    isSelected = currentTab == HomeTab.LEADERBOARD,
                                    activeColor = MaterialTheme.colorScheme.primary,
                                    onClick = { viewModel.setTab(HomeTab.LEADERBOARD) }
                                )
                            }
                        }
                    }
                }
            }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                HomeTab.HOME -> HomeTabScreen(viewModel)
                HomeTab.SEARCH -> SearchTabScreen(viewModel)
                HomeTab.NCERT -> Com.ak.testseries.ui.NcertTrackerScreen(viewModel)
                HomeTab.UPLOAD_AI -> UploadAiTabScreen(viewModel)
                HomeTab.PROFILE -> ProfileTabScreen(viewModel, authManager)
                HomeTab.LEADERBOARD -> LeaderboardTabScreen(viewModel)
                HomeTab.COMMUNITY -> Com.ak.testseries.ui.CommunityScreen(
                    isAdmin = viewModel.isAdmin,
                    targetExam = userProfileData.targetExam,
                    onExit = { viewModel.setTab(HomeTab.HOME) },
                    onOpenTest = { testId -> viewModel.handleDeepLinkTest(testId) }
                )
                HomeTab.SAVED_TESTS -> Com.ak.testseries.ui.SavedTestsScreen(viewModel)
            }
        }
    }
    } // Close ModalNavigationDrawer
}

// ==========================================
// 🏠 SUB-TAB 1: HOME DASHBOARD
// ==========================================
@Composable
fun HomeTabScreen(viewModel: NEETViewModel) {
    val tests by viewModel.testsList.collectAsStateWithLifecycle()
    val attempts by viewModel.attemptHistory.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    val isNewUser = Com.ak.testseries.ui.rememberIsNewUser()
    var showTutorial by remember { mutableStateOf(isNewUser.value) }
    var showDiceResultDialog by remember { mutableStateOf(false) }
    var rolledDiceValue by remember { mutableStateOf(6) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var selectedStudyToken by remember { mutableStateOf("NEET UG / Physics") }

    // Quick Start Tutorial Overlay
    Com.ak.testseries.ui.QuickStartTutorialOverlay(
        isVisible = showTutorial,
        onDismiss = { showTutorial = false },
        onDiceRollHighlightedClick = {
            rolledDiceValue = (1..6).random()
            showDiceResultDialog = true
        },
        onTokenSelectedClick = {
            showTokenDialog = true
        }
    )

    // Dice Result Dialog
    if (showDiceResultDialog) {
        AlertDialog(
            onDismissRequest = { showDiceResultDialog = false },
            icon = { Icon(Icons.Rounded.Casino, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
            title = { Text("Dice Rolled: $rolledDiceValue! 🎲") },
            text = { Text("Fantastic! You rolled a $rolledDiceValue and unlocked bonus practice questions and streak points.") },
            confirmButton = {
                Button(onClick = { showDiceResultDialog = false }) {
                    Text("Claim Reward")
                }
            }
        )
    }

    // Token Selection Dialog
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            icon = { Icon(Icons.Rounded.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp)) },
            title = { Text("Select Study Token & Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tokens = listOf("NEET UG / Physics", "JEE Main / Mathematics", "UPSC / General Studies", "SSC CGL / Quantitative Aptitude", "CAT / Logical Reasoning")
                    tokens.forEach { token ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedStudyToken = token
                                    showTokenDialog = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedStudyToken == token) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(token, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                if (selectedStudyToken == token) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = listState,
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quick Start Interactive Banner (Span 2) with Dice Roll Button & Token Selection Interaction
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Quick Start Hub", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            OutlinedButton(
                                onClick = { showTutorial = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tutorial", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Dice Roll Button with semi-transparent focus feel
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        rolledDiceValue = (1..6).random()
                                        showDiceResultDialog = true
                                    }
                                    .testTag("dice_roll_button"),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Casino, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                    }
                                    Column {
                                        Text("Roll Dice", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text("Daily Bonus 🎲", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    }
                                }
                            }

                            // Token Selection Interaction
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showTokenDialog = true
                                    }
                                    .testTag("token_selection_interaction"),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(MaterialTheme.colorScheme.secondary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(20.dp))
                                    }
                                    Column {
                                        Text("Select Token", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Text(selectedStudyToken.take(15) + "...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ☁️ Prominent Cloud & Local Backup Sync Status Card (Span 2)
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Com.ak.testseries.ui.HomeBackupSyncCard(
                    viewModel = viewModel,
                    onOpenBackupCenter = {
                        viewModel.navigateToScreen(Com.ak.testseries.viewmodel.AppScreen.BACKUP_RESTORE)
                    }
                )
            }

            // New Home Header (Span 2)
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Com.ak.testseries.ui.FullNewHomeHeader(viewModel = viewModel, onCategoryClick = { type ->
                    if (type == "All") {
                        viewModel.setTab(HomeTab.SEARCH)
                    } else {
                        viewModel.filterState.value = viewModel.filterState.value.copy(type = type)
                        viewModel.setTab(HomeTab.SEARCH)
                    }
                })
            }
            
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("उपलब्ध टेस्ट", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.height(2.dp).width(40.dp).background(MaterialTheme.colorScheme.primary))
                    }
                    
                    IconButton(
                        onClick = { viewModel.setTab(HomeTab.SEARCH) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ArrowForward, 
                            contentDescription = "View All", 
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }



        val otherTests = tests.drop(1)
        if (otherTests.isEmpty() && tests.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    BilingualText("No tests found.", "कोई टेस्ट नहीं मिला।", selectedLanguage)
                }
            }
        } else {
            items(otherTests.take(4).size, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { index ->
                Com.ak.testseries.ui.TestCard(
                    test = otherTests[index],
                    currentUserAttempts = attempts.count { it.testId == otherTests[index].id },
                    selectedLanguage = selectedLanguage,
                    onStartTest = { viewModel.initiateTest(otherTests[index]) },
                    onViewSyllabus = { viewModel.viewTestSyllabus(otherTests[index]) },
                    onBookmark = { viewModel.toggleTestBookmark(otherTests[index]) },
                    onDownload = { viewModel.downloadTestOffline(otherTests[index]) },
                    currentUserId = viewModel.currentUserId.value,
                    onDeleteTest = { viewModel.promptDeleteTest(otherTests[index]) },
                    onReportIssue = { viewModel.openReportIssueDialog(otherTests[index]) },
                    onShareToCommunity = { viewModel.shareTestToCommunity(otherTests[index]) }
                )
            }
        }
    }
        

    }
}

@Composable
fun DashboardStatCard(
    title: String,
    value: String,
    subValue: String = "",
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp), // Sleek bento rounded corners
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (containerColor == Color(0xFF121212)) Color.White.copy(alpha = 0.7f) else contentColor.copy(alpha = 0.7f),
                lineHeight = 16.sp
            )
            
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 32.sp, // Larger font for Bento style
                    fontWeight = FontWeight.Black,
                    color = if (containerColor == Color(0xFF121212)) Color.White else contentColor
                )
                if (subValue.isNotEmpty()) {
                    Text(
                        text = subValue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (containerColor == Color(0xFF121212)) Color.White.copy(alpha = 0.7f) else contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
        shape = RoundedCornerShape(12.dp),
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun InstituteCircle(shortName: String, fullName: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = shortName,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(fullName.split(" ").first(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

// ==========================================
// 🔍 SUB-TAB 2: SEARCH & FILTERS
// ==========================================
@Composable
fun SearchTabScreen(viewModel: NEETViewModel) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val tests by viewModel.testsList.collectAsStateWithLifecycle()
    val attempts by viewModel.attemptHistory.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Level Filter (11th/12th and Batch) - Primary Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 11th/12th toggle
            var expandedClass by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { expandedClass = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Text(if (filterState.classLevel == "All") "Class Level" else filterState.classLevel, fontSize = 12.sp, maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, "", modifier = Modifier.size(16.dp))
                }
                androidx.compose.material3.DropdownMenu(expanded = expandedClass, onDismissRequest = { expandedClass = false }) {
                    listOf("All", "11th", "12th", stringRes("Dropper")).forEach { opt ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(opt) }, onClick = {
                            viewModel.filterState.value = filterState.copy(classLevel = opt)
                            expandedClass = false
                        })
                    }
                }
            }

            // Batch Picker
            var expandedBatch by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { expandedBatch = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Text(if (filterState.batch == "All") "Batch" else filterState.batch, fontSize = 12.sp, maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, "", modifier = Modifier.size(16.dp))
                }
                androidx.compose.material3.DropdownMenu(expanded = expandedBatch, onDismissRequest = { expandedBatch = false }) {
                    listOf("All", "Yakeen", "Lakshya", "Arjuna").forEach { opt ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(opt) }, onClick = {
                            viewModel.filterState.value = filterState.copy(batch = opt)
                            expandedBatch = false
                        })
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // Filter Toggle Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringRes("Search Results"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            IconButton(onClick = { showFilters = !showFilters }) {
                Icon(Icons.Default.Menu, contentDescription = "Filter", tint = if (showFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }


        // Expanded Filters
        androidx.compose.animation.AnimatedVisibility(visible = showFilters) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(stringRes("Subject Filters"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val examConfig = Com.ak.testseries.exam.UniversalExamRegistry.getAllExams().find { it.examName == viewModel.userProfile.value.targetExam }
                    val dynamicSubjects = examConfig?.stages?.firstOrNull()?.papers?.firstOrNull()?.subjects?.map { it.subjectName } ?: listOf("Physics", "Chemistry", "Biology")
                    val opts = listOf("All") + dynamicSubjects
                    opts.forEach { opt ->
                        FilterBadge(opt, filterState.subject == opt) {
                            viewModel.filterState.value = filterState.copy(subject = opt)
                        }
                    }
                }
                Text(stringRes("Difficulty"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Easy", "Medium", "Hard").forEach { opt ->
                        FilterBadge(opt, filterState.difficulty == opt) {
                            viewModel.filterState.value = filterState.copy(difficulty = opt)
                        }
                    }
                }
                Text(stringRes("Test Type"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Full Syllabus", "Part Test", "Chapter Test", "PYQ").forEach { opt ->
                        FilterBadge(opt, filterState.type == opt) {
                            viewModel.filterState.value = filterState.copy(type = opt)
                        }
                    }
                }
                
                // Reset Button
                androidx.compose.material3.TextButton(onClick = { 
                    viewModel.filterState.value = NEETViewModel.TestFilterState() 
                }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringRes("Reset All Filters"))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))


            // Results Summary
            Text(
                text = if (selectedLanguage == AppLanguage.HINDI) "परिणाम (${tests.size})" else "Results (${tests.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (tests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = "None", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(if (selectedLanguage == AppLanguage.HINDI) "कोई परीक्षा नहीं मिली।" else "No tests match filter.", fontWeight = FontWeight.Bold)
                    Text("कृप्या अन्य फिल्टर का चुनाव करें।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tests.size) { index ->
                    val test = tests[index]
                    Com.ak.testseries.ui.CompactTestCard(
                        test = test,
                        currentUserAttempts = attempts.count { it.testId == test.id },
                        onStartTest = { viewModel.initiateTest(test) },
                        onViewSyllabus = { viewModel.viewTestSyllabus(test) },
                        currentUserId = viewModel.currentUserId.value,
                        onDeleteTest = { viewModel.promptDeleteTest(test) },
                        onReportIssue = { viewModel.openReportIssueDialog(test) },
                        onShareToCommunity = { viewModel.shareTestToCommunity(test) }
                    )
                }
            }
        }
        
    }
}



@Composable
fun FilterBadge(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)),
        modifier = Modifier.height(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ==========================================
// 📚 SUB-TAB 3: NCERT SYLLABUS LINK
// ==========================================
@Composable
fun NcertTabScreen(viewModel: NEETViewModel) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    val syllabusList by viewModel.ncertSyllabus.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val targetExam = userProfile.targetExam

    val examConfig = Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(targetExam)
    val examLabel = examConfig?.shortName ?: if (targetExam.isNotBlank()) targetExam else "Exam"

    var selectedClass by remember { mutableStateOf("Class 11") }

    // Dynamic subjects from UniversalExamRegistry or fallback
    val availableSubjects = remember(targetExam, selectedClass) {
        val mapped = Com.ak.testseries.data.SyllabusData.getSyllabusForExam(targetExam, if (selectedClass == "Dropper") "Class 12" else selectedClass)
        val keys = mapped.keys.toList()
        if (keys.isNotEmpty()) keys else Com.ak.testseries.exam.UniversalExamRegistry.getSubjectsForExamName(targetExam)
    }

    var selectedSubject by remember(targetExam, selectedClass) {
        mutableStateOf(availableSubjects.firstOrNull() ?: "All")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Welcome Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectedLanguage == AppLanguage.HINDI) "$examLabel पाठ्यक्रम ट्रैकर" else "$examLabel Syllabus Tracker",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (selectedLanguage == AppLanguage.HINDI) "$examLabel आधारित अध्याय और लिंक टेस्ट" else "$examLabel based chapters and linked tests",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "🎯 $examLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Class Toggle Selector
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { 
                    selectedClass = "Class 11"
                    selectedSubject = availableSubjects.firstOrNull() ?: "All"
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedClass == "Class 11") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (selectedClass == "Class 11") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Text("11th", fontSize = 12.sp)
            }
            Button(
                onClick = { 
                    selectedClass = "Class 12"
                    selectedSubject = availableSubjects.firstOrNull() ?: "All"
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedClass == "Class 12") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (selectedClass == "Class 12") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Text("12th", fontSize = 12.sp)
            }
            Button(
                onClick = { 
                    selectedClass = "Dropper"
                    selectedSubject = "All"
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedClass == "Dropper" || selectedClass == stringRes("Dropper")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (selectedClass == "Dropper" || selectedClass == stringRes("Dropper")) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Text(stringRes("Dropper"), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val subjectOptions = if (selectedClass == "Dropper" || selectedClass == stringRes("Dropper")) {
            listOf("All") + availableSubjects
        } else {
            availableSubjects
        }

        // Subject selector Row / Scrollable Row
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(subjectOptions.size) { index ->
                val sub = subjectOptions[index]
                val isActive = selectedSubject == sub
                Surface(
                    onClick = { selectedSubject = sub },
                    color = if (isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (sub) {
                                "All" -> if (selectedLanguage == AppLanguage.HINDI) "सभी" else "All"
                                "Physics" -> if (selectedLanguage == AppLanguage.HINDI) "भौतिकी" else "Physics"
                                "Chemistry" -> if (selectedLanguage == AppLanguage.HINDI) "रसायन" else "Chemistry"
                                "Biology" -> if (selectedLanguage == AppLanguage.HINDI) "जीव विज्ञान" else "Biology"
                                "Mathematics" -> if (selectedLanguage == AppLanguage.HINDI) "गणित" else "Mathematics"
                                else -> sub
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (selectedLanguage == AppLanguage.HINDI) "अध्याय सूची ($examLabel)" else "Chapters List ($examLabel)",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        val context = LocalContext.current
        val userScope = currentUserId.ifBlank { "guest" }
        val prefs = remember(context, targetExam, userScope) { context.getSharedPreferences("SyllabusProgress", android.content.Context.MODE_PRIVATE) }
        var completedChapters by remember(targetExam, userScope) {
            val userKey = "completed_chapters_${userScope}_${targetExam}"
            val existing = prefs.getStringSet(userKey, null)
            val initial = if (existing != null) {
                existing
            } else if (userScope == "guest") {
                prefs.getStringSet("completed_chapters_${targetExam}", emptySet()) ?: emptySet()
            } else {
                emptySet()
            }
            mutableStateOf(initial)
        }

        val catalogList = remember(targetExam) {
            Com.ak.testseries.exam.SyllabusCatalog.getChapterDetailsForExam(targetExam)
        }
        val catalogMap = remember(catalogList) {
            catalogList.associateBy { it.chapter.lowercase().trim() }
        }

        // Dynamic chapters from SyllabusData for targetExam
        val examSyllabusMap = remember(targetExam, selectedClass) {
            Com.ak.testseries.data.SyllabusData.getSyllabusForExam(targetExam, if (selectedClass == "Dropper") "Class 12" else selectedClass)
        }

        val dynamicChapters: List<Pair<String, String>> = remember(examSyllabusMap, selectedSubject) {
            val list = mutableListOf<Pair<String, String>>()
            if (selectedSubject == "All") {
                examSyllabusMap.forEach { (sub, chList) ->
                    chList.forEach { ch -> list.add(Pair(sub, ch)) }
                }
            } else {
                examSyllabusMap[selectedSubject]?.forEach { ch ->
                    list.add(Pair(selectedSubject, ch))
                }
            }
            list
        }

        if (dynamicChapters.isNotEmpty()) {
            val completedCount = dynamicChapters.count { completedChapters.contains(it.second) }
            val progressRatio = completedCount.toFloat() / dynamicChapters.size

            // Revision Progress Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedLanguage == AppLanguage.HINDI) "पाठ्यक्रम तैयारी प्रगति" else "Syllabus Revision Progress",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$completedCount / ${dynamicChapters.size} (${(progressRatio * 100).toInt()}%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dynamicChapters.size) { index ->
                    val (subjectName, chapterName) = dynamicChapters[index]
                    val isDone = completedChapters.contains(chapterName)
                    var isExpanded by remember { mutableStateOf(false) }

                    val detail = catalogMap[chapterName.lowercase().trim()]
                        ?: catalogList.firstOrNull { it.chapter.contains(chapterName, ignoreCase = true) || chapterName.contains(it.chapter, ignoreCase = true) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = if (isDone) 0.02f else 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isDone,
                                    onCheckedChange = { checked ->
                                        val updated = if (checked) completedChapters + chapterName else completedChapters - chapterName
                                        completedChapters = updated
                                        val editor = prefs.edit().putStringSet("completed_chapters_${userScope}_${targetExam}", updated)
                                        if (userScope == "guest") {
                                            editor.putStringSet("completed_chapters_${targetExam}", updated)
                                        }
                                        editor.apply()
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapterName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        style = if (isDone) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle()
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$subjectName  |  Linked with $examLabel",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.filterState.value = viewModel.filterState.value.copy(query = chapterName, subject = subjectName)
                                        viewModel.setTab(HomeTab.SEARCH)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(if (selectedLanguage == AppLanguage.HINDI) "खोजें" else "Tests", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (detail != null) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (detail.priority == "High") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "${if (detail.priority == "High") "🔥" else "⚖️"} ${detail.weightagePercent}% Weightage  ·  ~${detail.totalQuestionsExpected} Qs",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (detail.priority == "High") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    if (detail.keyTopics.isNotEmpty()) {
                                        TextButton(
                                            onClick = { isExpanded = !isExpanded },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = if (isExpanded) "Hide Topics ▲" else "Topics (${detail.keyTopics.size}) ▼",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                if (isExpanded && detail.keyTopics.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        detail.keyTopics.forEach { topicItem ->
                                            Text(
                                                text = "• $topicItem",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Fallback to database syllabusList if dynamic list is empty
            val filteredSyllabus = syllabusList.filter {
                val classMatch = if (selectedClass == stringRes("Dropper") || selectedClass == "Dropper") true else it.classLevel.equals(selectedClass, ignoreCase = true)
                val subjectMatch = if (selectedSubject == "All") true else it.subject.equals(selectedSubject, ignoreCase = true)
                classMatch && subjectMatch
            }

            if (filteredSyllabus.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (selectedLanguage == AppLanguage.HINDI) "कोई अध्याय नहीं मिला।" else "No syllabus records loaded for $examLabel.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSyllabus) { topic ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = topic.chapter,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${topic.classLevel}  |  Linked with $examLabel",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.filterState.value = viewModel.filterState.value.copy(query = topic.chapter, subject = topic.subject)
                                        viewModel.setTab(HomeTab.SEARCH)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(if (selectedLanguage == AppLanguage.HINDI) "खोजें" else "View Tests", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend fun processPdfWithGemini(uri: Uri) {
    // Placeholder function that converts Uri to format acceptable by Gemini API
    // e.g., converting to byte array or using a File provider
    // Note: Do NOT save this PDF file into Room Database. Only send to AI and discard.
    
    // Simulate AI reading the PDF
    kotlinx.coroutines.delay(2500)
}

// ==========================================
// 🤖 SUB-TAB 4: AI PDF GENERATOR (Refactored to Com.ak.testseries.ui.UploadAiTabScreen)
// ==========================================

@Composable
fun AppLevelProfileEditDialog(viewModel: NEETViewModel) {
    val showProfileEditDialog by viewModel.showProfileEditDialog.collectAsStateWithLifecycle()
    val userProfileData by viewModel.userProfile.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()

    if (showProfileEditDialog) {
        ProfileEditDialog(
            currentName = userProfileData.name,
            currentEmail = userProfileData.email,
            currentPhone = userProfileData.phoneNumber,
            currentState = userProfileData.stateName,
            currentCity = userProfileData.cityName,
            currentTargetExam = userProfileData.targetExam,
            currentProfileImageUri = userProfileData.profileImageUri,
            selectedLanguage = selectedLanguage,
            onSave = { name, email, phone, state, city, profileImageUri, exam ->
                viewModel.saveUserProfile(
                    name = name,
                    email = email,
                    stateName = state,
                    cityName = city,
                    profileImageUri = profileImageUri,
                    targetExam = exam,
                    phoneNumber = phone
                )
                viewModel.showProfileEditDialog.value = false
            },
            onCancel = { viewModel.showProfileEditDialog.value = false }
        )
    }
}

@Composable
fun DeleteConfirmDialog(viewModel: NEETViewModel) {
    val showDialog by viewModel.showDeleteConfirmDialog.collectAsStateWithLifecycle()
    val test by viewModel.testToDelete.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    if (!showDialog || test == null) return
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { viewModel.showDeleteConfirmDialog.value = false },
        title = { Text(stringRes("Delete Test")) },
        text = { Text("Are you sure you want to permanently delete '${test?.title}' and all associated images from the server? This action cannot be undone.") },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { 
                    viewModel.deleteTest(test!!)
                    viewModel.showDeleteConfirmDialog.value = false
                    android.widget.Toast.makeText(context, "Test deleted permanently.", android.widget.Toast.LENGTH_SHORT).show()
                }
            ) {
                Text(stringRes("Delete"), color = Color.Red)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { viewModel.showDeleteConfirmDialog.value = false }) {
                Text(stringRes("Cancel"))
            }
        }
    )
}

@Composable
fun ReportIssueDialog(viewModel: NEETViewModel) {
    val showDialog by viewModel.showReportDialog.collectAsStateWithLifecycle()
    val test by viewModel.reportedTest.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    if (!showDialog || test == null) return
    
    var selectedCategory by remember { mutableStateOf("Wrong Answer") }
    var comment by remember { mutableStateOf("") }
    
    val categories = listOf("Wrong Answer", "Typo", "Bad Image", "Other")
    
    androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.showReportDialog.value = false }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringRes("Report Issue"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(stringRes("Category"), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Simple dropdown simulation with chips or buttons for categories
                @OptIn(ExperimentalLayoutApi::class) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringRes("Comment/Description")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                var suggestedEdit by remember { mutableStateOf("") }
                
                OutlinedTextField(
                    value = suggestedEdit,
                    onValueChange = { suggestedEdit = it },
                    label = { Text(stringRes("Suggest Edit / Correction (Optional)")) },
                    placeholder = { Text(stringRes("e.g. Correct text is XYZ, or option B is correct, or upload a clearer diagram reference")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(onClick = { /* Simulated Image Picker */ }) {
                    Icon(androidx.compose.material.icons.Icons.Default.Image, contentDescription = "Upload Image")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringRes("Attach Clearer Diagram Reference"))
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.showReportDialog.value = false }) {
                        Text(stringRes("Cancel"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { 
                        val finalComment = if (suggestedEdit.isNotBlank()) "$comment\nSuggested Edit: $suggestedEdit" else comment
                        viewModel.submitReportIssue(test!!.id, selectedCategory, finalComment, context)
                        viewModel.showReportDialog.value = false
                    }) {
                        Text(stringRes("Submit Report"))
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalPdfCropperDialog(viewModel: NEETViewModel) {
    val showPdfCropper by viewModel.showPdfCropper.collectAsStateWithLifecycle()
    val show180QuestionsDialog by viewModel.show180QuestionsDialog.collectAsStateWithLifecycle()
    val selectedPdfUri by viewModel.selectedPdfUri.collectAsStateWithLifecycle()
    val pdfCropperInitialPage by viewModel.pdfCropperInitialPage.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    if (showPdfCropper && selectedPdfUri != null && !show180QuestionsDialog) {
        Com.ak.testseries.ui.PdfCropperScreen(
            uri = selectedPdfUri!!,
            initialPage = pdfCropperInitialPage,
            onClose = {
                viewModel.showPdfCropper.value = false
                viewModel.pdfCropCallback = null
            },
            onCropResult = { bitmap ->
                viewModel.showPdfCropper.value = false
                val callback = viewModel.pdfCropCallback
                viewModel.pdfCropCallback = null
                scope.launch {
                    try {
                        val file = java.io.File(context.cacheDir, "cropped_chart_${System.currentTimeMillis()}.jpg")
                        val out = java.io.FileOutputStream(file)
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                        out.flush()
                        out.close()

                        val uploadedUrl = Com.ak.testseries.utils.FileUploaderUtil.uploadImage(context, android.net.Uri.fromFile(file))
                        val finalUrl = uploadedUrl ?: android.net.Uri.fromFile(file).toString()
                        callback?.invoke(finalUrl)
                        android.widget.Toast.makeText(context, "Chart Cropped Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun All180QuestionsPreviewDialog(viewModel: NEETViewModel) {
    val showDialog by viewModel.show180QuestionsDialog.collectAsStateWithLifecycle()
    val questions by viewModel.previewQuestions.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    if (!showDialog) return

    val showPdfCropper by viewModel.showPdfCropper.collectAsStateWithLifecycle()
    val pdfCropperInitialPage by viewModel.pdfCropperInitialPage.collectAsStateWithLifecycle()
    val selectedPdfUri by viewModel.selectedPdfUri.collectAsStateWithLifecycle()

    var selectedFilterSubject by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedQuestionForImage by remember { mutableStateOf<Com.ak.testseries.data.QuestionEntity?>(null) }
    var isProcessComplete by remember { mutableStateOf(false) }
    val pdfPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            viewModel.selectedPdfUri.value = it
            viewModel.showPdfCropper.value = true
        }
    }

    if (showPdfCropper && selectedPdfUri != null) {
        Com.ak.testseries.ui.PdfCropperScreen(
            uri = selectedPdfUri!!,
            initialPage = pdfCropperInitialPage,
            onClose = { viewModel.showPdfCropper.value = false },
            onCropResult = { bitmap ->
                viewModel.showPdfCropper.value = false
                selectedQuestionForImage?.let { q ->
                    scope.launch {
                        try {
                            val file = java.io.File(context.cacheDir, "cropped_image_${System.currentTimeMillis()}.jpg")
                            val out = java.io.FileOutputStream(file)
                            // Compress image as JPEG with 80 quality to reduce size
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                            out.flush()
                            out.close()
                            
                            val uploadedUrl = Com.ak.testseries.utils.FileUploaderUtil.uploadImage(context, android.net.Uri.fromFile(file))
                            val finalUrl = uploadedUrl ?: android.net.Uri.fromFile(file).toString()
                            viewModel.updatePreviewQuestionImage(q.id, finalUrl)
                            android.widget.Toast.makeText(context, "Cropped Chart attached!", android.widget.Toast.LENGTH_SHORT).show()
                        } catch(e:Exception){
                            e.printStackTrace()
                        }
                    }
                }
            }
        )
    }

    val filteredQuestions = remember(questions, selectedFilterSubject, searchQuery) {
        questions.filter { q ->
            val matchesSubject = when (selectedFilterSubject) {
                "Physics" -> q.subject.equals("Physics", ignoreCase = true)
                "Chemistry" -> q.subject.equals("Chemistry", ignoreCase = true)
                "Botany" -> q.subject.equals("Biology", ignoreCase = true) && (q.chapter.contains("Plant", true) || q.chapter.contains("Botany", true) || q.id.contains("bot"))
                "Zoology" -> q.subject.equals("Biology", ignoreCase = true) && (q.chapter.contains("Human", true) || q.chapter.contains("Animal", true) || q.id.contains("zoo"))
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    q.questionText.contains(searchQuery, ignoreCase = true) ||
                    (q.questionTextHi?.contains(searchQuery, ignoreCase = true) == true) ||
                    q.chapter.contains(searchQuery, ignoreCase = true)

            matchesSubject && matchesSearch
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { viewModel.close180QuestionsDialog() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedLanguage == AppLanguage.HINDI) "NEET 180 प्रश्न - सम्पूर्ण पेपर रिव्यु" else "NEET 180 Questions - Complete Paper Review",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Physics: 45 | Chem: 45 | Botany: 45 | Zoology: 45 (${questions.size} Loaded)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(onClick = { viewModel.close180QuestionsDialog() }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringRes("Close"), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Column(modifier = Modifier.padding(12.dp)) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringRes("Search 180 questions or topic..."), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subject Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("All", "Physics", "Chemistry", "Botany", "Zoology").forEach { filter ->
                            FilterChip(
                                selected = selectedFilterSubject == filter,
                                onClick = { selectedFilterSubject = filter },
                                label = { Text(filter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = if (selectedFilterSubject == filter) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Missing Diagrams Warning Summary Banner
                val missingDiagramsCount = remember(filteredQuestions) {
                    filteredQuestions.count { q ->
                        val combined = (q.questionText + " " + (q.questionTextHi ?: "") + " " + q.explanation + " " + (q.explanationHi ?: "")).lowercase()
                        val keywords = listOf("diagram", "figure", "graph", "circuit", "chart", "given below", "shown below", "refer to", "as shown", "plot", "चित्र", "ग्राफ", "आरेख", "चित्रानुसार", "नीचे दिए गए चित्र", "परिपथ", "रेखाचित्र", "दिए गए ग्राफ")
                        val requiresImg = q.needsImage || keywords.any { combined.contains(it) }
                        requiresImg && q.imageUrl.isNullOrBlank()
                    }
                }

                if (missingDiagramsCount > 0) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedLanguage == AppLanguage.HINDI)
                                    "⚠️ $missingDiagramsCount प्रश्नों में चित्र गायब हैं (लाल निशान)। कृपया उन पर 'Crop from PDF' दबाकर मैनुअल क्रॉप करें।"
                                else
                                    "⚠️ $missingDiagramsCount questions require diagrams (marked with Red indicator). Tap 'Crop from PDF' to manually crop.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB91C1C),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Questions List
                if (filteredQuestions.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringRes("No questions matched search criteria."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        itemsIndexed(items = filteredQuestions, key = { index, item -> item.id }) { index: Int, q: QuestionEntity ->
                            val combinedText = remember(q) {
                                (q.questionText + " " + (q.questionTextHi ?: "") + " " + q.explanation + " " + (q.explanationHi ?: "")).lowercase()
                            }
                            val keywords = remember {
                                listOf("diagram", "figure", "graph", "circuit", "chart", "given below", "shown below", "refer to", "as shown", "plot", "चित्र", "ग्राफ", "आरेख", "चित्रानुसार", "नीचे दिए गए चित्र", "परिपथ", "रेखाचित्र", "दिए गए ग्राफ")
                            }
                            val requiresImage = q.needsImage || keywords.any { combinedText.contains(it) }
                            val isImageMissing = requiresImage && q.imageUrl.isNullOrBlank()

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isImageMissing) Color(0xFFFFF5F5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                border = if (isImageMissing) {
                                    BorderStroke(1.8.dp, Color(0xFFEF4444))
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                color = if (isImageMissing) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Q${index + 1} • ${q.subject}",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }

                                            // RED MARK / BADGE IF IMAGE IS MISSING
                                            if (isImageMissing) {
                                                Surface(
                                                    color = Color(0xFFFEE2E2),
                                                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .background(Color(0xFFDC2626), CircleShape)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = if (selectedLanguage == AppLanguage.HINDI) "चित्र आवश्यक / RED ALERT" else "DIAGRAM MISSING",
                                                            color = Color(0xFFB91C1C),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            } else if (!q.imageUrl.isNullOrBlank()) {
                                                Surface(
                                                    color = Color(0xFFECFDF5),
                                                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(10.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = if (selectedLanguage == AppLanguage.HINDI) "चित्र संलग्न ✅" else "Attached ✅",
                                                            color = Color(0xFF047857),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Text(
                                            text = q.chapter,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // RED WARNING BANNER INSIDE CARD WHEN IMAGE IS MISSING
                                    if (isImageMissing) {
                                        Surface(
                                            color = Color(0xFFFEE2E2),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Crop, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (selectedLanguage == AppLanguage.HINDI)
                                                        "🔴 चित्र गायब है: जब तक आप PDF से इमेज क्रॉप नहीं कर लेते, यह लाल निशान रहेगा।"
                                                    else
                                                        "🔴 Image Missing: This red mark remains until manually cropped from PDF.",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFB91C1C),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    // Question Text (English & Hindi) - LARGE & STEP-BY-STEP
                                    Text(
                                        text = q.questionText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        lineHeight = 24.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (!q.questionTextHi.isNullOrBlank() && q.questionTextHi != q.questionText) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = q.questionTextHi,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 16.sp,
                                            lineHeight = 22.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    
                                    // Chart Image Preview / Attachment
                                    if (!q.imageUrl.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                        ) {
                                            coil.compose.AsyncImage(
                                                model = q.imageUrl,
                                                contentDescription = "Compressed Chart",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(4.dp),
                                                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Action buttons for this question (Chart Image Upload / Manual Crop)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        if (isImageMissing) {
                                            Button(
                                                onClick = {
                                                    selectedQuestionForImage = q
                                                    if (viewModel.selectedPdfUri.value != null) {
                                                        viewModel.showPdfCropper.value = true
                                                    } else {
                                                        pdfPickerLauncher.launch("application/pdf")
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFFDC2626),
                                                    contentColor = Color.White
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.Crop, contentDescription = "Crop PDF", modifier = Modifier.size(15.dp), tint = Color.White)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (selectedLanguage == AppLanguage.HINDI) "🔴 PDF से चित्र क्रॉप करें (मैन्युअल)" else "🔴 Crop from PDF (Required)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    selectedQuestionForImage = q
                                                    if (viewModel.selectedPdfUri.value != null) {
                                                        viewModel.showPdfCropper.value = true
                                                    } else {
                                                        pdfPickerLauncher.launch("application/pdf")
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Crop, contentDescription = "Crop PDF", modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (q.imageUrl.isNullOrBlank()) "Crop from PDF" else "Replace from PDF",
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }


                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Options List
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val options = listOf(
                                            Pair("A", q.optionA),
                                            Pair("B", q.optionB),
                                            Pair("C", q.optionC),
                                            Pair("D", q.optionD)
                                        )
                                        options.forEach { pair ->
                                            val key = pair.first
                                            val optText = pair.second
                                            val isCorrect = key.equals(q.correctAnswer, ignoreCase = true)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        color = if (isCorrect) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .border(
                                                        width = if (isCorrect) 1.5.dp else 1.dp,
                                                        color = if (isCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "$key) ",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                                Text(
                                                    text = optText.removePrefix("$key)").removePrefix("$key )").removePrefix("$key.").trim(),
                                                    fontSize = 15.sp,
                                                    fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (isCorrect) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Correct",
                                                        tint = Color(0xFF2E7D32),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))


                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Bottom Action Buttons & Confirmation
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isProcessComplete = !isProcessComplete }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = isProcessComplete,
                            onCheckedChange = { isProcessComplete = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedLanguage == AppLanguage.HINDI) "मैंने सभी आवश्यक चित्र अपलोड कर दिए हैं और प्रक्रिया पूरी हो गई है।" else "I have uploaded all necessary images and the process is complete.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Button(
                        onClick = {
                            viewModel.close180QuestionsDialog()
                            android.widget.Toast.makeText(context, "Test is now ready to take!", android.widget.Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = isProcessComplete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            if (selectedLanguage == AppLanguage.HINDI) "संपूर्ण (Done) - टेस्ट दें" else "Completed (Done) - Take Test", 
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 🎨 AI QUEUE STEP HELPER
// ==========================================
@Composable
fun AiStateStep(title: String, isPassed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 12.sp, color = if (isPassed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            imageVector = if (isPassed) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = "Status",
            tint = if (isPassed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ==========================================
// 📈 SUB-TAB 5: PROFILE & RESULTS HISTORY
// ==========================================
@Composable
fun ProfileTabScreen(viewModel: NEETViewModel, authManager: Com.ak.testseries.domain.auth.AuthManager) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val history by viewModel.attemptHistory.collectAsStateWithLifecycle()
    val testsList by viewModel.testsList.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val mappings by viewModel.testFolderMappings.collectAsStateWithLifecycle()
    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()
    val showCreateFolderDialog by viewModel.showCreateFolderDialog.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var testToMove by remember { mutableStateOf<String?>(null) }

    val tabs = listOf(
        if (selectedLanguage == AppLanguage.HINDI) "अपलोड किए गए" else "Uploaded",
        if (selectedLanguage == AppLanguage.HINDI) "अटेम्प्ट किए गए" else "Attempted"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Profile Info Header
        if (currentFolderId == null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (userProfile.profileImageUri.isNotEmpty()) {
                                coil.compose.AsyncImage(
                                    model = userProfile.profileImageUri,
                                    contentDescription = "Profile Pic",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile.name.ifEmpty { if (selectedLanguage == AppLanguage.HINDI) "अज्ञात उपयोगकर्ता" else "Unknown User" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = userProfile.email.ifEmpty { "no-email@aktest.com" },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Target Exam: ${userProfile.targetExam}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                            if (userProfile.stateName.isNotEmpty() || userProfile.cityName.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                    Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${userProfile.cityName}, ${userProfile.stateName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.showProfileEditDialog.value = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Profile", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.navigateToScreen(Com.ak.testseries.viewmodel.AppScreen.BACKUP_RESTORE) },
                            modifier = Modifier.testTag("profile_backup_settings_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (selectedLanguage == AppLanguage.HINDI) "खाता बैकअप व पुनर्स्थापना" else "Backup & Restore",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        
        // Folder Navigation Header (if we are inside a folder)
        val currentFolder = folders.find { it.id == currentFolderId }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            if (currentFolderId != null) {
                IconButton(onClick = { viewModel.currentFolderId.value = currentFolder?.parentId }) {
                    Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = currentFolder?.name ?: (if (selectedLanguage == AppLanguage.HINDI) "टेस्ट हिस्ट्री" else stringRes("Test History")),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (currentFolderId != null) {
                // Button to Delete the current folder
                IconButton(onClick = { viewModel.deleteFolder(currentFolderId!!) }) {
                    Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "Delete Folder", tint = Color.Red)
                }
            }
        }

        // Subfolders row
        val subfolders = folders.filter { it.parentId == currentFolderId }
        if (subfolders.isNotEmpty()) {
            Text(
                text = if (selectedLanguage == AppLanguage.HINDI) "सब-फ़ोल्डर्स / श्रेणी" else "Folders / Subfolders",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                items(subfolders.size) { index ->
                    val folder = subfolders[index]
                    Card(
                        modifier = Modifier
                            .width(135.dp)
                            .height(72.dp)
                            .clickable { viewModel.currentFolderId.value = folder.id },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = "Folder",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = folder.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Delete subfolder button
                            IconButton(
                                onClick = { viewModel.deleteFolder(folder.id) },
                                modifier = Modifier.align(Alignment.BottomEnd).size(20.dp)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                                    contentDescription = stringRes("Delete"),
                                    tint = Color.Red.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.material3.TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                androidx.compose.material3.Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedTab == 0) {
                // Uploaded Tests
                val currentUid = viewModel.currentUserId.value
                val uploadedTests = testsList.filter { test ->
                    !test.isOfficial && mappings[test.id] == currentFolderId && 
                        Com.ak.testseries.data.TestVisibilityManager.isTestVisible(test, currentUid, viewModel.isAdmin) &&
                        (test.isPublic || (currentUid.isNotBlank() && test.uploaderId == currentUid))
                }
                
                if (uploadedTests.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(if (selectedLanguage == AppLanguage.HINDI) "इस फ़ोल्डर में कोई अपलोड किया गया टेस्ट नहीं है।" else "No uploaded tests inside this folder.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                } else {
                    items(uploadedTests.size) { index ->
                        val test = uploadedTests[index]
                        val isAttempted = history.any { it.testId == test.id }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(test.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { viewModel.shareTestToCommunity(test) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share to Community",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    // Move to Folder button
                                    IconButton(
                                        onClick = { testToMove = test.id },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Folder,
                                            contentDescription = "Move",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(test.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Button(
                                    onClick = {
                                        viewModel.initiateTest(test)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAttempted) MaterialTheme.colorScheme.primary else Color(0xFFD32F2F)
                                    )
                                ) {
                                    Text(if (isAttempted) (if (selectedLanguage == AppLanguage.HINDI) "फिर से अटेम्प्ट करें" else "Re-attempt") else (if (selectedLanguage == AppLanguage.HINDI) "अटेम्प्ट करें" else "Attempt"))
                                }
                            }
                        }
                    }
                }
            } else {
                // Attempted Tests History
                val attemptedItems = history.filter { mappings[it.testId] == currentFolderId }
                
                if (attemptedItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(androidx.compose.material.icons.Icons.Default.List, contentDescription = "None", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (selectedLanguage == AppLanguage.HINDI) "इस फ़ोल्डर में कोई अटेम्प्टेड टेस्ट नहीं है।" else "No attempted tests in this folder.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(attemptedItems.size) { index ->
                        val attempt = attemptedItems[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(attempt.testTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = { testToMove = attempt.testId },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Folder,
                                                contentDescription = "Move",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Accuracy: ${String.format("%.2f", attempt.accuracy)}%  |  Percentile: ${String.format("%.2f", attempt.percentile)}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Score: ${attempt.score} / ${attempt.maxScore}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    IconButton(
                                        onClick = {
                                            viewModel.viewAttemptResult(attempt)
                                        }
                                    ) {
                                        Icon(androidx.compose.material.icons.Icons.Default.ArrowForward, contentDescription = "Detail", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    TextButton(onClick = { 
                                        val testToResume = testsList.find { it.id == attempt.testId }
                                        if (testToResume != null) viewModel.initiateTest(testToResume)
                                    }) {
                                        Text(if (selectedLanguage == AppLanguage.HINDI) "फिर से" else "Re-attempt", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Create Folder Dialog ---
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.showCreateFolderDialog.value = false },
            title = { Text(if (selectedLanguage == AppLanguage.HINDI) "नया फ़ोल्डर / श्रेणी" else "Create Folder") },
            text = {
                Column {
                    Text(
                        text = if (currentFolderId == null) {
                            if (selectedLanguage == AppLanguage.HINDI) "नया फ़ोल्डर बनाएं:" else "Create a new folder in Root:"
                        } else {
                            if (selectedLanguage == AppLanguage.HINDI) "सब-फ़ोल्डर का नाम लिखें:" else "Create a new subfolder:"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        placeholder = { Text("Zoology, Physics Wallah...", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.trim().isNotEmpty()) {
                            viewModel.createFolder(folderName.trim(), currentFolderId)
                            viewModel.showCreateFolderDialog.value = false
                        }
                    }
                ) {
                    Text(stringRes("OK"))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showCreateFolderDialog.value = false }) {
                    Text(stringRes("Cancel"))
                }
            }
        )
    }

    // --- Move Test To Folder Dialog ---
    if (testToMove != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { testToMove = null },
            title = { Text(if (selectedLanguage == AppLanguage.HINDI) "फ़ोल्डर चुनें" else "Select Folder") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(if (selectedLanguage == AppLanguage.HINDI) "इस टेस्ट को किस फ़ोल्डर में डालना है?" else "Choose folder to move this test:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.heightIn(max = 250.dp).fillMaxWidth()
                    ) {
                        // Root folder option
                        item {
                            Card(
                                onClick = {
                                    viewModel.moveTestToFolder(testToMove!!, null)
                                    testToMove = null
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = if (currentFolderId == null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(if (selectedLanguage == AppLanguage.HINDI) "मुख्य फ़ोल्डर (Root)" else "Root Folder", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        // All other folders options
                        items(folders.size) { index ->
                            val f = folders[index]
                            Card(
                                onClick = {
                                    viewModel.moveTestToFolder(testToMove!!, f.id)
                                    testToMove = null
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = if (currentFolderId == f.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(f.name)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { testToMove = null }) {
                    Text(stringRes("Cancel"))
                }
            }
        )
    }
}

fun extractLanguage(text: String, language: AppLanguage): String {
    if (text.isBlank()) return text
    val delimiters = listOf("\n\n", " / ", " | ", " // ")
    for (del in delimiters) {
        if (text.contains(del)) {
            val parts = text.split(del)
            if (parts.size >= 2) {
                // If Hindi requested, search for part with Devanagari characters or last part
                return if (language == AppLanguage.HINDI) {
                    val hindiPart = parts.firstOrNull { part -> part.any { it in '\u0900'..'\u097F' } }
                    hindiPart?.trim() ?: parts.last().trim()
                } else {
                    val englishPart = parts.firstOrNull { part -> part.none { it in '\u0900'..'\u097F' } }
                    englishPart?.trim() ?: parts.first().trim()
                }
            }
        }
    }
    return text
}

@Composable
fun VirtualNumericKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    onDisabledClick: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Read-only text field displaying the entered value
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.5.dp,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable {
                    if (!isEnabled) onDisabledClick()
                }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "Type answer using keypad below",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            } else {
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid-based Keypad containing 0-9, decimal point, negative sign, and Backspace/Clear
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("-", "0", "."),
            listOf("Clear", "⌫")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { key ->
                        val isActionButton = key == "Clear" || key == "⌫"
                        val weight = if (isActionButton && row.size == 2) 1.5f else 1f
                        
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isActionButton) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (!isEnabled) {
                                        onDisabledClick()
                                        return@clickable
                                    }
                                    when (key) {
                                        "Clear" -> {
                                            onValueChange("")
                                        }
                                        "⌫" -> {
                                            if (value.isNotEmpty()) {
                                                onValueChange(value.dropLast(1))
                                            }
                                        }
                                        "-" -> {
                                            // Negative sign can only be at the beginning
                                            if (value.isEmpty()) {
                                                onValueChange("-")
                                            } else if (value == "-") {
                                                onValueChange("")
                                            }
                                        }
                                        "." -> {
                                            // Only one decimal point allowed
                                            if (!value.contains(".")) {
                                                if (value.isEmpty() || value == "-") {
                                                    onValueChange(value + "0.")
                                                } else {
                                                    onValueChange(value + ".")
                                                }
                                            }
                                        }
                                        else -> {
                                            // Digits
                                            if (value == "0") {
                                                onValueChange(key)
                                            } else {
                                                onValueChange(value + key)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                color = if (isActionButton) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ✍️ EXAM MODE SCREEN (FULL SCREEN EXAM)
// ==========================================
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExamModeScreen(viewModel: NEETViewModel) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val activeTest by viewModel.activeTest.collectAsStateWithLifecycle()
    val questions by viewModel.activeQuestions.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentQuestionIndex.collectAsStateWithLifecycle()
    val answers by viewModel.userAnswers.collectAsStateWithLifecycle()
    val marked by viewModel.markedForReview.collectAsStateWithLifecycle()
    val checked by viewModel.checkedQuestions.collectAsStateWithLifecycle()
    val timerRemaining by viewModel.examTimeRemaining.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val activity = context as? android.app.Activity
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = activity?.window
        val insetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        
        insetsController?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        
        onDispose {
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }


    val currentQuestion = questions.getOrNull(currentIndex)
    var showPaletteSheet by remember { mutableStateOf(false) }
    var showPauseConfirmDialog by remember { mutableStateOf(false) }
    var questionLanguage by remember(currentQuestion) { mutableStateOf(selectedLanguage) }

    val displayQuestionText = currentQuestion?.let { q -> 
        if (questionLanguage == AppLanguage.HINDI && !q.questionTextHi.isNullOrEmpty()) q.questionTextHi 
        else extractLanguage(q.questionText, questionLanguage) 
    } ?: ""
    val displayOptA = currentQuestion?.let { q -> 
        if (questionLanguage == AppLanguage.HINDI && !q.optionAHi.isNullOrEmpty()) q.optionAHi 
        else extractLanguage(q.optionA, questionLanguage) 
    } ?: ""
    val displayOptB = currentQuestion?.let { q -> 
        if (questionLanguage == AppLanguage.HINDI && !q.optionBHi.isNullOrEmpty()) q.optionBHi 
        else extractLanguage(q.optionB, questionLanguage) 
    } ?: ""
    val displayOptC = currentQuestion?.let { q -> 
        if (questionLanguage == AppLanguage.HINDI && !q.optionCHi.isNullOrEmpty()) q.optionCHi 
        else extractLanguage(q.optionC, questionLanguage) 
    } ?: ""
    val displayOptD = currentQuestion?.let { q -> 
        if (questionLanguage == AppLanguage.HINDI && !q.optionDHi.isNullOrEmpty()) q.optionDHi 
        else extractLanguage(q.optionD, questionLanguage) 
    } ?: ""
    val displayExplanation = currentQuestion?.let { q -> 
        if (questionLanguage == AppLanguage.HINDI && !q.explanationHi.isNullOrEmpty()) q.explanationHi 
        else extractLanguage(q.explanation, questionLanguage) 
    } ?: ""

    val reportErrorLoading by viewModel.reportErrorLoading.collectAsStateWithLifecycle()
    val reportErrorResponse by viewModel.reportErrorResponse.collectAsStateWithLifecycle()
    
    if (showPauseConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPauseConfirmDialog = false },
            title = { Text(if (selectedLanguage == AppLanguage.HINDI) "टेस्ट रोकें?" else "Pause Test?") },
            text = { Text(if (selectedLanguage == AppLanguage.HINDI) "क्या आप इस टेस्ट को यहीं रोकना और सेव करना चाहते हैं? अगली बार यह यहीं से शुरू होगा।" else "Do you want to pause and save this test? Next time it will resume from exactly where you left off.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showPauseConfirmDialog = false
                    viewModel.pauseTest()
                }) {
                    Text(if (selectedLanguage == AppLanguage.HINDI) "हाँ, सेव करें" else "Yes, Pause", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPauseConfirmDialog = false }) {
                    Text(if (selectedLanguage == AppLanguage.HINDI) "नहीं" else "No", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (reportErrorLoading || reportErrorResponse != null) {
        Dialog(onDismissRequest = { viewModel.dismissReportError() }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (reportErrorLoading) {
                        Com.ak.testseries.ui.LogoLoader()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("AI is analyzing the question for errors...", fontWeight = FontWeight.Medium)
                    } else {
                        Icon(Icons.Rounded.Warning, contentDescription = "Report", tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("AI Question Analysis", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.Red)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(reportErrorResponse ?: "", fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Justify)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.dismissReportError() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(stringRes("Close"))
                        }
                    }
                }
            }
        }
    }

    if (showPaletteSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showPaletteSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(
                    if (selectedLanguage == AppLanguage.HINDI) "प्रश्न सूची" else "Question Palette",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(questions.size) { idx ->
                        val q = questions[idx]
                        val isAnswered = answers.containsKey(q.id)
                        val isMarked = marked.contains(q.id)
                        val isCurrent = idx == currentIndex

                        val cellColor = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isMarked -> PaletteMarked
                            isAnswered -> PaletteCorrect
                            else -> PaletteUnvisited
                        }

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(cellColor)
                                .clickable { 
                                    viewModel.jumpToQuestion(idx)
                                    showPaletteSheet = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${idx + 1}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LegendLine(PaletteCorrect, "Answered")
                    LegendLine(PaletteUnvisited, "Not Answered")
                    LegendLine(PaletteMarked, "Marked")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val userPhone = remember(userProfile) {
        userProfile.phoneNumber.ifBlank {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.phoneNumber ?: ""
            } catch (e: Exception) { "" }
        }
    }

    com.ak.pdfextractor.ui.components.AntiPiracyWatermarkWrapper(
        phoneNumber = userPhone,
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
            Column {
                CustomTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { showPauseConfirmDialog = true }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Pause Test")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = activeTest?.title ?: "NEET Mock Test",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            // Live clock countdown formatting (hh:mm:ss)
                            val hours = timerRemaining / 3600
                            val minutes = (timerRemaining % 3600) / 60
                            val seconds = timerRemaining % 60
                            val timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                            Text(
                                text = "⏰ Time Remaining: $timeStr",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = if (timerRemaining < 600) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        Button(
                            onClick = { viewModel.submitTest() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(if (selectedLanguage == AppLanguage.HINDI) "सबमिट करें" else "Submit", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                        }
                    }
                )
                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (currentQuestion == null) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Com.ak.testseries.ui.LogoLoader()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringRes("Loading exam paper..."))
                        }
                    }
                } else {
                    // Subject Tabs
                    val subjects = questions.map { it.subject }.distinct()
                    ScrollableTabRow(
                        selectedTabIndex = subjects.indexOf(currentQuestion.subject).takeIf { it >= 0 } ?: 0,
                        edgePadding = 0.dp,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        subjects.forEach { subject ->
                            val isSelected = currentQuestion.subject == subject
                            Tab(
                                selected = isSelected,
                                onClick = { 
                                    // Find first question of this subject
                                    val firstIdx = questions.indexOfFirst { it.subject == subject }
                                    if (firstIdx != -1) viewModel.jumpToQuestion(firstIdx)
                                },
                                text = { Text(subject, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) },
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Quick Palette row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(questions.size) { idx ->
                                val q = questions[idx]
                                val isAnswered = answers.containsKey(q.id)
                                val isMarked = marked.contains(q.id)
                                val isCurrent = idx == currentIndex

                                val cellColor = when {
                                    isCurrent -> MaterialTheme.colorScheme.primary
                                    isMarked -> PaletteMarked
                                    isAnswered -> PaletteCorrect
                                    else -> PaletteUnvisited
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(cellColor)
                                        .clickable { viewModel.jumpToQuestion(idx) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${idx + 1}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showPaletteSheet = true }) {
                            Icon(Icons.Default.GridView, contentDescription = "Show All Questions", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Question Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Section Tag
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CustomBadge(containerColor = MaterialTheme.colorScheme.secondary) {
                                    Text(currentQuestion.subject, color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Chapter: ${currentQuestion.chapter}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            IconButton(
                                onClick = { viewModel.reportQuestionError(currentQuestion.questionText, currentQuestion.optionA, currentQuestion.optionB, currentQuestion.optionC, currentQuestion.optionD, currentQuestion.correctAnswer) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.Warning, contentDescription = "Report Error", tint = Color.Red, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Question Text
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (selectedLanguage == AppLanguage.HINDI) "प्रश्न ${currentIndex + 1}" else "Question ${currentIndex + 1}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row {
                                androidx.compose.material3.TextButton(
                                    onClick = { questionLanguage = AppLanguage.ENGLISH },
                                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                        contentColor = if (questionLanguage == AppLanguage.ENGLISH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("EN", fontWeight = if (questionLanguage == AppLanguage.ENGLISH) FontWeight.Bold else FontWeight.Normal)
                                }
                                androidx.compose.material3.TextButton(
                                    onClick = { questionLanguage = AppLanguage.HINDI },
                                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                        contentColor = if (questionLanguage == AppLanguage.HINDI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("HI", fontWeight = if (questionLanguage == AppLanguage.HINDI) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = displayQuestionText,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        val isSectionB = currentQuestion.section == "SECTION_B"
                        val answeredSectionBCount = remember(answers, questions) {
                            questions.count { q -> q.section == "SECTION_B" && answers.containsKey(q.id) }
                        }
                        val isInputDisabled = isSectionB && !answers.containsKey(currentQuestion.id) && answeredSectionBCount >= 5
                        
                        val onDisabledClick = {
                            android.widget.Toast.makeText(context, "You can only attempt 5 out of 10 questions in Section B. Please clear a previous answer to attempt this one.", android.widget.Toast.LENGTH_LONG).show()
                        }

                        if (isSectionB) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isInputDisabled) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) 
                                                     else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isInputDisabled) Icons.Rounded.Lock else Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = if (isInputDisabled) Color.Red else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isInputDisabled) {
                                            "Section B Limit Reached: You have already attempted 5 questions. Clear a previous answer to attempt this one."
                                        } else {
                                            "Section B: Attempted $answeredSectionBCount of 5 allowed questions."
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isInputDisabled) Color.Red else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Options cards
                        val selectedAns = answers[currentQuestion.id] ?: ""
                        val showImmediateAnswer = activeTest?.isOfficial != true && selectedAns.isNotEmpty()

                        if (currentQuestion.questionType == "NUMERICAL") {
                            VirtualNumericKeypad(
                                value = selectedAns,
                                onValueChange = { newValue ->
                                    if (isInputDisabled) {
                                        onDisabledClick()
                                    } else {
                                        val success = viewModel.selectAnswer(currentQuestion.id, newValue)
                                        if (!success) {
                                            onDisabledClick()
                                        }
                                    }
                                },
                                onDisabledClick = onDisabledClick,
                                isEnabled = !isInputDisabled,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OptionRow("A", displayOptA, selectedAns == "A", showImmediateAnswer, currentQuestion.correctAnswer == "A") {
                                if (isInputDisabled) {
                                    onDisabledClick()
                                } else {
                                    val success = viewModel.selectAnswer(currentQuestion.id, "A")
                                    if (!success) {
                                        onDisabledClick()
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OptionRow("B", displayOptB, selectedAns == "B", showImmediateAnswer, currentQuestion.correctAnswer == "B") {
                                if (isInputDisabled) {
                                    onDisabledClick()
                                } else {
                                    val success = viewModel.selectAnswer(currentQuestion.id, "B")
                                    if (!success) {
                                        onDisabledClick()
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OptionRow("C", displayOptC, selectedAns == "C", showImmediateAnswer, currentQuestion.correctAnswer == "C") {
                                if (isInputDisabled) {
                                    onDisabledClick()
                                } else {
                                    val success = viewModel.selectAnswer(currentQuestion.id, "C")
                                    if (!success) {
                                        onDisabledClick()
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OptionRow("D", displayOptD, selectedAns == "D", showImmediateAnswer, currentQuestion.correctAnswer == "D") {
                                if (isInputDisabled) {
                                    onDisabledClick()
                                } else {
                                    val success = viewModel.selectAnswer(currentQuestion.id, "D")
                                    if (!success) {
                                        onDisabledClick()
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Mark for review toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleMarkForReview(currentQuestion.id) }
                                .padding(8.dp)
                                .background(if (marked.contains(currentQuestion.id)) PaletteMarked.copy(alpha = 0.1f) else Color.Transparent)
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = marked.contains(currentQuestion.id),
                                onCheckedChange = { viewModel.toggleMarkForReview(currentQuestion.id) },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = PaletteMarked)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (selectedLanguage == AppLanguage.HINDI) "बाद के लिए रिव्यू हेतु चिह्नित करें" else "Mark for Review",
                                fontSize = 13.sp,
                                color = if (marked.contains(currentQuestion.id)) PaletteMarked else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Immediate answer + video/AI logic
                        if (showImmediateAnswer) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(if (selectedLanguage == AppLanguage.HINDI) "उत्तर और स्पष्टीकरण" else "Answer & Explanation", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Correct Answer: ${currentQuestion.correctAnswer}", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(displayExplanation, fontSize = 13.sp, lineHeight = 18.sp)
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { 
                                                val queryModifier = if (selectedLanguage == AppLanguage.HINDI) "hindi explanation video solution" else "video solution explanation"
                                                val examPrefix = activeTest?.targetExam?.takeIf { it.isNotBlank() } ?: userProfile.targetExam.takeIf { it.isNotBlank() } ?: ""
                                                val queryExamPart = if (examPrefix.isNotBlank()) "${android.net.Uri.encode(examPrefix)}+" else ""
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/results?search_query=$queryExamPart${java.net.URLEncoder.encode(displayQuestionText + " " + queryModifier, "UTF-8")}"))
                                                try { context.startActivity(intent) } catch (e: Exception) {}
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) {
                                            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringRes("Video Search"), fontSize = 11.sp)
                                        }
                                        Button(
                                            onClick = { viewModel.askAiDoubt(displayQuestionText, displayOptA, displayOptB, displayOptC, displayOptD, selectedLanguage) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringRes("Ask AI"), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))

                    
                    // Bottom Action buttons: Prev, Skip/Clear, Next
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.navigateQuestion(-1) },
                            enabled = currentIndex > 0,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(if (selectedLanguage == AppLanguage.HINDI) "पिछला" else "Prev", fontSize = 11.sp)
                        }
                        
                        val hasAnswered = answers.containsKey(currentQuestion.id)
                        OutlinedButton(
                            onClick = { 
                                if (hasAnswered) {
                                    viewModel.clearResponse(currentQuestion.id)
                                } else {
                                    viewModel.navigateQuestion(1)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = if (hasAnswered) {
                                    if (selectedLanguage == AppLanguage.HINDI) "हटाएं" else "Clear"
                                } else {
                                    if (selectedLanguage == AppLanguage.HINDI) "स्किप" else "Skip"
                                },
                                fontSize = 11.sp
                            )
                        }
                        
                        if (activeTest?.isOfficial != true && hasAnswered) {
                            Button(
                                onClick = { 
                                    viewModel.checkCurrentQuestion(currentQuestion.id)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PaletteCorrect)
                            ) {
                                Text(if (selectedLanguage == AppLanguage.HINDI) "चेक करें" else "Check", fontSize = 11.sp)
                            }
                        }
                        
                        Button(
                            onClick = { viewModel.navigateQuestion(1) },
                            enabled = currentIndex < questions.size - 1,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = if (hasAnswered) {
                                    if (selectedLanguage == AppLanguage.HINDI) "सेव और अगला" else "Save & Next"
                                } else {
                                    if (selectedLanguage == AppLanguage.HINDI) "अगला" else "Next"
                                },
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

    // Ask AI Dialog Overlay
    val askAiResponse by viewModel.askAiResponse.collectAsStateWithLifecycle()
    val askAiLoading by viewModel.askAiLoading.collectAsStateWithLifecycle()
    val askAiVideoLink by viewModel.askAiVideoLink.collectAsStateWithLifecycle()

    if (askAiLoading || askAiResponse != null) {
        Dialog(onDismissRequest = { viewModel.dismissAskAi() }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (askAiLoading) {
                        Com.ak.testseries.ui.LogoLoader()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("AI Mentor is analyzing NCERT concepts...", fontWeight = FontWeight.Medium)
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI", tint = Color(0xFF6C63FF), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringRes("Smart Doubt Resolver"), fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF6C63FF))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(askAiResponse ?: "", fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Justify)
                        
                        if (!askAiVideoLink.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { 
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(askAiVideoLink))
                                    try { context.startActivity(intent) } catch (e: Exception) {}
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)) // Vibrant Green for action
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Video", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringRes("Search Best Video Lecture"))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.dismissAskAi() }) {
                            Text(stringRes("Got it"), color = Color(0xFF6C63FF), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OptionRow(
    letter: String,
    text: String,
    isSelected: Boolean,
    showImmediateAnswer: Boolean = false,
    isCorrectOption: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = when {
        showImmediateAnswer && isCorrectOption -> Color(0xFF4CAF50)
        showImmediateAnswer && isSelected && !isCorrectOption -> Color(0xFFF44336)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
    }
    
    val bgColor = when {
        showImmediateAnswer && isCorrectOption -> Color(0xFF4CAF50).copy(alpha = 0.1f)
        showImmediateAnswer && isSelected && !isCorrectOption -> Color(0xFFF44336).copy(alpha = 0.1f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    val circleColor = when {
        showImmediateAnswer && isCorrectOption -> Color(0xFF4CAF50)
        showImmediateAnswer && isSelected && !isCorrectOption -> Color(0xFFF44336)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    }
    
    val textColor = when {
        showImmediateAnswer && (isCorrectOption || (isSelected && !isCorrectOption)) -> Color.White
        isSelected -> Color.White
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = {
            if (!showImmediateAnswer) onClick()
        },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, borderColor),
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(circleColor),
                contentAlignment = Alignment.Center
            ) {
                Text(letter, fontWeight = FontWeight.Bold, color = textColor, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun LegendLine(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ==========================================
// 📊 RESULTS ANALYSIS SCREEN
// ==========================================
@Composable
fun ResultScreen(viewModel: NEETViewModel) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val activeResult by viewModel.activeResult.collectAsStateWithLifecycle()
    val activeTest by viewModel.activeTest.collectAsStateWithLifecycle()
    val questions by viewModel.activeQuestions.collectAsStateWithLifecycle()
    var showSolutions by remember { mutableStateOf(false) }
    var selectedQuestionForSolution by remember { mutableStateOf<QuestionEntity?>(null) }
    var showWeakTopics by remember { mutableStateOf<String?>(null) }
    var isAdLoading by remember { mutableStateOf(false) }
    var selectedSubjectForDetails by remember { mutableStateOf<String?>(null) }

    val result = activeResult
    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (selectedLanguage == AppLanguage.HINDI) "नो रिजल्ट डेटा" else "No active score found.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                if (selectedLanguage == AppLanguage.HINDI) "परीक्षण समाप्त" else "Test Completed",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                activeTest?.title ?: "",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Scoring circular banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (selectedLanguage == AppLanguage.HINDI) "आपका स्कोर" else "Your Score", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${result.score} / ${result.maxScore}", fontWeight = FontWeight.Black, fontSize = 36.sp, color = MaterialTheme.colorScheme.primary)
                    val isPrivateTest = activeTest?.isPublic == false || activeTest?.countsTowardRanking == false
                    val percentileDisplay = if (isPrivateTest) {
                        if (selectedLanguage == AppLanguage.HINDI) "पर्सेंटाइल: लागू नहीं (निजी टेस्ट)" else "Percentile: N/A (Private Test)"
                    } else if (result.percentile > 0.0) {
                        if (selectedLanguage == AppLanguage.HINDI) "पर्सेंटाइल: ${String.format(java.util.Locale.US, "%.2f", result.percentile)}%" else "Percentile: ${String.format(java.util.Locale.US, "%.2f", result.percentile)}%"
                    } else if (!result.verifiedServerSide) {
                        if (selectedLanguage == AppLanguage.HINDI) "पर्सेंटाइल: सत्यापन प्रतीक्षारत" else "Percentile: Verification Pending"
                    } else {
                        if (selectedLanguage == AppLanguage.HINDI) "पर्सेंटाइल: लागू नहीं (गैर-रैंकिंग)" else "Percentile: N/A (Non-ranking)"
                    }
                    Text(percentileDisplay, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isPrivateTest -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            result.verifiedServerSide -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(1.dp, when {
                            isPrivateTest -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                            result.verifiedServerSide -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        })
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    isPrivateTest -> Icons.Rounded.Lock
                                    result.verifiedServerSide -> Icons.Rounded.Verified
                                    else -> Icons.Rounded.Schedule
                                },
                                contentDescription = "Verification status",
                                tint = when {
                                    isPrivateTest -> MaterialTheme.colorScheme.secondary
                                    result.verifiedServerSide -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val statusText = when {
                                isPrivateTest -> {
                                    if (selectedLanguage == AppLanguage.HINDI) "निजी टेस्ट • गैर-रैंकिंग (व्यक्तिगत अभ्यास)" else "Private Test • Non-Ranking (Personal Practice)"
                                }
                                result.verifiedServerSide -> {
                                    if (selectedLanguage == AppLanguage.HINDI) "सर्वर द्वारा प्रमाणित परिणाम" else "Verified Server Result"
                                }
                                result.submissionStatus == "PENDING" -> {
                                    if (selectedLanguage == AppLanguage.HINDI) "सबमिशन प्रतीक्षारत (स्थानीय पूर्वावलोकन)" else "Submission Pending (Local Preview)"
                                }
                                else -> {
                                    if (selectedLanguage == AppLanguage.HINDI) "ऑफलाइन अभ्यास परिणाम" else "Offline Practice Result"
                                }
                            }
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    isPrivateTest -> MaterialTheme.colorScheme.secondary
                                    result.verifiedServerSide -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }

        // Stats grid
        item {
            val breakdown = remember(questions, result, activeTest) {
                val ansObj = try {
                    org.json.JSONObject(result.answersJson)
                } catch (e: Exception) {
                    org.json.JSONObject()
                }
                val userAnswers = mutableMapOf<String, String>()
                for (q in questions) {
                    if (ansObj.has(q.id)) {
                        val selected = when (val optVal = ansObj.opt(q.id)) {
                            is org.json.JSONObject -> optVal.optString("ans", "")
                            is String -> optVal
                            else -> ""
                        }
                        if (selected.isNotEmpty()) {
                            userAnswers[q.id] = selected
                        }
                    }
                }
                Com.ak.testseries.exam.ExamScoringEngine.evaluateTestAttempt(
                    targetExam = activeTest?.targetExam ?: "NEET UG",
                    questions = questions,
                    userAnswers = userAnswers,
                    timeSpentSeconds = result.timeSpentSeconds
                )
            }
            val penaltyTotal = breakdown.negativeMarksPenalty
            val penaltyStr = if (penaltyTotal <= 0.0) "0" else "-${Com.ak.testseries.exam.ExamScoringEngine.formatMark(penaltyTotal)}"

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSolutions = !showSolutions },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatMetricColumn(if (selectedLanguage == AppLanguage.HINDI) "सही" else "Correct", "${result.correctCount}", MaterialTheme.colorScheme.tertiary)
                        StatMetricColumn(if (selectedLanguage == AppLanguage.HINDI) "गलत" else "Wrong", "${result.incorrectCount}", MaterialTheme.colorScheme.error)
                        StatMetricColumn(if (selectedLanguage == AppLanguage.HINDI) "माइनस" else "Minus", penaltyStr, MaterialTheme.colorScheme.error)
                        StatMetricColumn(if (selectedLanguage == AppLanguage.HINDI) "छोड़े" else "Skipped", "${result.skippedCount}", MaterialTheme.colorScheme.onSurfaceVariant)
                        StatMetricColumn(if (selectedLanguage == AppLanguage.HINDI) "समय" else "Time", "${result.timeSpentSeconds / 60}m", MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val solutionsText = if (showSolutions) {
                        if (selectedLanguage == AppLanguage.HINDI) "विस्तृत समाधान छुपाएं" else "Hide Solutions"
                    } else {
                        if (selectedLanguage == AppLanguage.HINDI) "विस्तृत समाधान देखने के लिए यहां क्लिक करें" else "Click for details"
                    }
                    Text(solutionsText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Subject-wise Analysis
        item {
            Text(if (selectedLanguage == AppLanguage.HINDI) "विषयवार विश्लेषण" else "Subject-wise Analysis", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            val subjectStats = remember(questions, result, activeTest) {
                val ansObj = try {
                    org.json.JSONObject(result.answersJson)
                } catch (e: Exception) {
                    org.json.JSONObject()
                }
                val userAnswers = mutableMapOf<String, String>()
                for (q in questions) {
                    if (ansObj.has(q.id)) {
                        val selected = when (val optVal = ansObj.opt(q.id)) {
                            is org.json.JSONObject -> optVal.optString("ans", "")
                            is String -> optVal
                            else -> ""
                        }
                        if (selected.isNotEmpty()) {
                            userAnswers[q.id] = selected
                        }
                    }
                }
                Com.ak.testseries.exam.ExamScoringEngine.evaluateSubjectBreakdown(
                    targetExam = activeTest?.targetExam ?: "NEET UG",
                    questions = questions,
                    userAnswers = userAnswers
                )
            }

            if (subjectStats.isNotEmpty()) {
                val ansObj = try { org.json.JSONObject(result.answersJson) } catch (e: Exception) { org.json.JSONObject() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    subjectStats.forEach { stat ->
                        val scorePercentage = (stat.score.coerceAtLeast(0.0).toFloat() / stat.maxScore.coerceAtLeast(1.0).toFloat()).coerceIn(0f, 1f)
                        val accuracyPercentage = stat.accuracy / 100f
                        val isExpanded = selectedSubjectForDetails == stat.subject

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSubjectForDetails = if (isExpanded) null else stat.subject
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp, 
                                if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    when {
                                                        accuracyPercentage >= 0.8f -> Color(0xFF2E7D32)
                                                        accuracyPercentage >= 0.5f -> Color(0xFFEF6C00)
                                                        else -> MaterialTheme.colorScheme.error
                                                    }, 
                                                    androidx.compose.foundation.shape.CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stat.subject, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isExpanded) {
                                                if (selectedLanguage == AppLanguage.HINDI) "विवरण छुपाएं ▲" else "Hide Details ▲"
                                            } else {
                                                if (selectedLanguage == AppLanguage.HINDI) "विवरण देखें ▾" else "Details ▾"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Score details summary
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (selectedLanguage == AppLanguage.HINDI) "स्कोर: ${stat.formattedScore}/${stat.formattedMaxScore}" else "Score: ${stat.formattedScore}/${stat.formattedMaxScore}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (selectedLanguage == AppLanguage.HINDI) "सटीकता: ${String.format("%.1f", stat.accuracy)}%" else "Accuracy: ${String.format("%.1f", stat.accuracy)}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            accuracyPercentage >= 0.8f -> Color(0xFF2E7D32)
                                            accuracyPercentage >= 0.5f -> Color(0xFFEF6C00)
                                            else -> MaterialTheme.colorScheme.error
                                        }
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Progress bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(accuracyPercentage)
                                            .background(
                                                when {
                                                    accuracyPercentage >= 0.8f -> Color(0xFF2E7D32)
                                                    accuracyPercentage >= 0.5f -> Color(0xFFEF6C00)
                                                    else -> MaterialTheme.colorScheme.error
                                                }, 
                                                RoundedCornerShape(4.dp)
                                            )
                                    )
                                }

                                // EXPANDED DETAILS PANEL ("गहरी तरीके से पूरा सोच कर")
                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = if (selectedLanguage == AppLanguage.HINDI) "गहन विषय विश्लेषण" else "Topic Insights",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Spacing-wise separated metric row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(if (selectedLanguage == AppLanguage.HINDI) "कुल प्रश्न" else "Total Qs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${stat.total}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(if (selectedLanguage == AppLanguage.HINDI) "सही" else "Correct", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${stat.correct}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(if (selectedLanguage == AppLanguage.HINDI) "गलत" else "Wrong", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${stat.incorrect}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                        val skippedForSubject = stat.total - (stat.correct + stat.incorrect)
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(if (selectedLanguage == AppLanguage.HINDI) "छोड़े गए" else "Skipped", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$skippedForSubject", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Custom learning recommendation feedback based on subject performance
                                    val feedbackText = when {
                                        accuracyPercentage >= 0.8f -> {
                                            if (selectedLanguage == AppLanguage.HINDI) {
                                                if (selectedLanguage == AppLanguage.HINDI) "🔥 शानदार! आपकी इस विषय के NCERT कॉन्सेप्ट्स पर बहुत मजबूत पकड़ है। इसे बनाए रखने के लिए मॉक टेस्ट का अभ्यास जारी रखें।" else "🔥 Excellent! You have a very strong grasp on the NCERT concepts for this subject. Keep practicing mock tests to maintain it."
                                            } else {
                                                "🔥 Excellent! You have fully mastered this subject's NCERT concepts. Keep doing mock tests to maintain your edge."
                                            }
                                        }
                                        accuracyPercentage >= 0.5f -> {
                                            if (selectedLanguage == AppLanguage.HINDI) {
                                                if (selectedLanguage == AppLanguage.HINDI) "⚠️ अच्छा प्रयास है! NCERT बेसिक्स काफी मजबूत हैं लेकिन कुछ चैप्टरों में कमी है। बेहतर सुधार के लिए नीचे दिए गए प्रश्नों को दोबारा हल करें।" else "⚠️ Good effort! NCERT basics are quite strong but lacking in some chapters. Resolve the questions below for better improvement."
                                            } else {
                                                "⚠️ Good effort! Your NCERT basics are solid, but some chapters need attention. Revise the questions listed below to improve."
                                            }
                                        }
                                        else -> {
                                            if (selectedLanguage == AppLanguage.HINDI) {
                                                if (selectedLanguage == AppLanguage.HINDI) "🚨 ध्यान दें! इस विषय के NCERT कॉन्सेप्ट्स काफी कमजोर हैं। हम सुझाव देते हैं कि आप चैप्टर टेक्स्टबुक को पुनः पढ़ें और AI Doubt Solver की सहायता लें।" else "🚨 Attention! NCERT concepts for this subject are quite weak. We suggest you reread the chapter textbook and seek help from the AI Doubt Solver."
                                            } else {
                                                "🚨 Action Required! Your NCERT concepts in this subject are weak. We suggest reading textbook chapters and using the AI Doubt Solver to clear your doubts."
                                            }
                                        }
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                            .padding(14.dp)
                                    ) {
                                        Text(text = feedbackText, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Quick questions reviewer
                                    val subjectQuestions = questions.filter { it.subject == stat.subject }
                                    if (subjectQuestions.isNotEmpty()) {
                                        Text(
                                            text = if (selectedLanguage == AppLanguage.HINDI) "प्रश्नों की समीक्षा करें:" else "Review Questions:",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            subjectQuestions.forEach { q ->
                                                val qObj = if (ansObj.has(q.id)) ansObj.getJSONObject(q.id) else null
                                                val selected = qObj?.optString("ans", "") ?: ""
                                                val isQCorrect = selected == q.correctAnswer
                                                val isQSkipped = selected.isEmpty()
                                                
                                                val itemBgColor = when {
                                                    isQSkipped -> Color.LightGray
                                                    isQCorrect -> Color(0xFF4CAF50)
                                                    else -> Color(0xFFF44336)
                                                }
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(itemBgColor)
                                                        .clickable { selectedQuestionForSolution = q },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${questions.indexOf(q) + 1}",
                                                        color = if (isQSkipped) Color.Black else Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text(stringRes("No subject data available."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Expanded solutions
        if (showSolutions) {
            val ansObj = try {
                org.json.JSONObject(result.answersJson)
            } catch (e: Exception) {
                org.json.JSONObject()
            }
            
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(if (selectedLanguage == AppLanguage.HINDI) "प्रश्न पैलेट" else "Question Palette", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    val subjects = questions.map { it.subject }.distinct()
                    var selectedSubject by remember { mutableStateOf(subjects.firstOrNull()) }

                    if (subjects.isNotEmpty()) {
                        androidx.compose.material3.ScrollableTabRow(
                            selectedTabIndex = subjects.indexOf(selectedSubject).takeIf { it >= 0 } ?: 0,
                            edgePadding = 0.dp,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            containerColor = Color.Transparent,
                            divider = {}
                        ) {
                            subjects.forEach { subject ->
                                val isSelected = selectedSubject == subject
                                Tab(
                                    selected = isSelected,
                                    onClick = { selectedSubject = subject },
                                    text = { Text(subject, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) },
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        val subjectQuestions = questions.filter { it.subject == selectedSubject }
                        val chunks = subjectQuestions.chunked(5)
                        
                        chunks.forEach { rowQs ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowQs.forEach { q ->
                                    val qObj = if (ansObj.has(q.id)) ansObj.getJSONObject(q.id) else null
                                    val selectedAns = qObj?.optString("ans", "") ?: ""
                                    val isCorrect = selectedAns == q.correctAnswer
                                    val isSkipped = selectedAns.isEmpty()
                                    
                                    val cellColor = when {
                                        isSkipped -> Color.LightGray
                                        isCorrect -> Color(0xFF4CAF50)
                                        else -> Color(0xFFF44336)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(cellColor)
                                            .clickable { selectedQuestionForSolution = q },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${questions.indexOf(q) + 1}",
                                            color = if (isSkipped) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                repeat(5 - rowQs.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Return button
        item {
            var userRating by remember { mutableStateOf(0) }
            var customComments by remember { mutableStateOf("") }
            val shortcutOptions = if (selectedLanguage == AppLanguage.HINDI) {
                listOf("मिसिंग इमेज", "व्याख्या अधूरी", "गलत उत्तर", "अनुवाद त्रुटि", "टाइपिंग मिस्टेक")
            } else {
                listOf("Missing Image", "Incomplete Explanation", "Wrong Answer Key", "Translation Error", "Typing Error")
            }
            var selectedShortcuts by remember { mutableStateOf(setOf<String>()) }
            var feedbackSubmitted by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (selectedLanguage == AppLanguage.HINDI) "इस टेस्ट को रेटिंग और फीडबैक दें" else "Rate & Give Feedback",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Star Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        for (i in 1..5) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = "Star $i",
                                tint = if (i <= userRating) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        if (!feedbackSubmitted) {
                                            userRating = i
                                        }
                                    }
                            )
                        }
                    }
                    
                    if (userRating > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (selectedLanguage == AppLanguage.HINDI) "संभावित कमियां (शॉर्टकट चुनें):" else "Select Issues (Shortcuts):",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(shortcutOptions.size) { index ->
                                val option = shortcutOptions[index]
                                val isSelected = selectedShortcuts.contains(option)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (!feedbackSubmitted) {
                                            selectedShortcuts = if (isSelected) {
                                                selectedShortcuts - option
                                            } else {
                                                selectedShortcuts + option
                                            }
                                        }
                                    },
                                    label = { Text(option, fontSize = 11.sp) }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = customComments,
                            onValueChange = { if (!feedbackSubmitted) customComments = it },
                            label = { Text(if (selectedLanguage == AppLanguage.HINDI) "फीडबैक या कमी टाइप करें..." else "Type details of any issue...", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 3
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                if (userRating > 0 && !feedbackSubmitted) {
                                    feedbackSubmitted = true
                                    val testId = activeTest?.id ?: ""
                                    val testTitle = activeTest?.title ?: "Unknown Test"
                                    viewModel.submitTestRating(testId, userRating)
                                    viewModel.submitTestFeedback(testId, testTitle, userRating, selectedShortcuts.toList(), customComments)
                                }
                            },
                            enabled = !feedbackSubmitted,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                if (feedbackSubmitted) {
                                    if (selectedLanguage == AppLanguage.HINDI) "फीडबैक सबमिट हो गया!" else "Feedback Submitted!"
                                } else {
                                    if (selectedLanguage == AppLanguage.HINDI) "फीडबैक सबमिट करें" else "Submit Feedback"
                                }
                            )
                        }
                    }
                    
                    if (feedbackSubmitted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedLanguage == AppLanguage.HINDI) "रेटिंग और फीडबैक देने के लिए धन्यवाद!" else "Thank you for your rating & feedback!",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
            val localScope = rememberCoroutineScope()
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { 
                    isAdLoading = true
                    localScope.launch {
                        kotlinx.coroutines.delay(2000) // Simulate ad
                        isAdLoading = false
                        val ansObj = try { org.json.JSONObject(result.answersJson) } catch (e: Exception) { org.json.JSONObject() }
                        val weak = questions.filter { 
                            val obj = if (ansObj.has(it.id)) ansObj.getJSONObject(it.id) else null
                            val selected = obj?.optString("ans", "") ?: ""
                            selected != it.correctAnswer
                        }.groupBy { it.chapter }.map { "${it.key} (${it.value.size} wrong)" }.joinToString("\n")
                        showWeakTopics = if (weak.isEmpty()) if (selectedLanguage == AppLanguage.HINDI) "कोई कमजोर टॉपिक नहीं मिला!" else "No weak topics found!" else weak
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isAdLoading) "Ad Loading..." else if (selectedLanguage == AppLanguage.HINDI) "कमजोर टॉपिक एनालाइज करें (Ad देखें)" else "Analyze Weak Topics (Watch Ad to Unlock AI Analysis)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.navigateToScreen(AppScreen.DASHBOARD) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (selectedLanguage == AppLanguage.HINDI) "होम स्क्रीन पर जाएं" else "Go to Dashboard")
            }
        }
    }

    if (selectedQuestionForSolution != null && activeTest != null) {
        FullScreenSolutionPage(
            test = activeTest!!,
            question = selectedQuestionForSolution!!,
            result = result,
            selectedLanguage = selectedLanguage,
            viewModel = viewModel,
            onDismiss = { selectedQuestionForSolution = null }
        )
    }

    if (showWeakTopics != null) {
        Dialog(onDismissRequest = { showWeakTopics = null }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI Analysis", tint = Color(0xFF6C63FF), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (selectedLanguage == AppLanguage.HINDI) "कमजोर टॉपिक विश्लेषण" else "Weak Topics Analysis", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF6C63FF))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(showWeakTopics ?: "", fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showWeakTopics = null }) {
                        Text(if (selectedLanguage == AppLanguage.HINDI) "बंद करें" else stringRes("Close"))
                    }
                }
            }
        }
    }

    // Ask AI Dialog Overlay
    val askAiResponse by viewModel.askAiResponse.collectAsStateWithLifecycle()
    val askAiLoading by viewModel.askAiLoading.collectAsStateWithLifecycle()
    val askAiVideoLink by viewModel.askAiVideoLink.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (askAiLoading || askAiResponse != null) {
        Dialog(onDismissRequest = { viewModel.dismissAskAi() }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (askAiLoading) {
                        Com.ak.testseries.ui.LogoLoader()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("AI Mentor is analyzing NCERT concepts...", fontWeight = FontWeight.Medium)
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI", tint = Color(0xFF6C63FF), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringRes("Smart Doubt Resolver"), fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF6C63FF))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(askAiResponse ?: "", fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Justify)
                        
                        if (!askAiVideoLink.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { 
                                    // Normally we would launch an intent here.
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(askAiVideoLink))
                                    try { context.startActivity(intent) } catch (e: Exception) {}
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)) // Vibrant Green for action
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Video", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringRes("Search Best Video Lecture"))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.dismissAskAi() }) {
                            Text(if (selectedLanguage == AppLanguage.HINDI) "समझ आ गया" else stringRes("Got it"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenSolutionPage(
    test: Com.ak.testseries.data.TestEntity,
    question: QuestionEntity,
    result: AttemptEntity,
    selectedLanguage: AppLanguage,
    viewModel: NEETViewModel,
    onDismiss: () -> Unit
) {
    androidx.activity.compose.BackHandler {
        onDismiss()
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val ansObj = remember(result.answersJson) {
        try { org.json.JSONObject(result.answersJson) } catch (e: Exception) { org.json.JSONObject() }
    }
    val qObj = ansObj.optJSONObject(question.id)
    val selectedAns = qObj?.optString("ans", "") ?: ""
    val timeTaken = qObj?.optLong("time", 0L) ?: 0L
    val isCorrect = selectedAns == question.correctAnswer
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val isHindi = selectedLanguage == AppLanguage.HINDI
    val displayQuestionText = if (isHindi && !question.questionTextHi.isNullOrEmpty()) question.questionTextHi else question.questionText
    val displayOptionA = if (isHindi && !question.optionAHi.isNullOrEmpty()) question.optionAHi else question.optionA
    val displayOptionB = if (isHindi && !question.optionBHi.isNullOrEmpty()) question.optionBHi else question.optionB
    val displayOptionC = if (isHindi && !question.optionCHi.isNullOrEmpty()) question.optionCHi else question.optionC
    val displayOptionD = if (isHindi && !question.optionDHi.isNullOrEmpty()) question.optionDHi else question.optionD
    val displayExplanation = if (isHindi && !question.explanationHi.isNullOrEmpty()) question.explanationHi else question.explanation

    var isDoubtSolverOpen by remember { mutableStateOf(false) }
    var activeDoubtTab by remember { mutableStateOf(DoubtTab.AI) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack, 
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isHindi) "प्रश्न का विस्तृत समाधान" else "Detailed Explanation", 
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                
                val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
                var expanded by remember { mutableStateOf(false) }
                
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (currentUserId.isNotBlank() && currentUserId == test.uploaderId) {
                            DropdownMenuItem(
                                text = { Text(stringRes("Edit")) },
                                onClick = { expanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringRes("Share to Community")) },
                                onClick = {
                                    expanded = false
                                    viewModel.shareTestToCommunity(test)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringRes("Delete Test"), color = Color.Red) },
                                onClick = { 
                                    expanded = false 
                                    viewModel.promptDeleteTest(test)
                                    onDismiss() // Go back as test is deleted
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringRes("Report Issue")) },
                                onClick = {
                                    expanded = false
                                    viewModel.openReportIssueDialog(test)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringRes("Suggest Edit")) },
                                onClick = {
                                    expanded = false
                                    viewModel.openReportIssueDialog(test)
                                }
                            )
                        }
                    }
                }
            }

            // 2. MAIN SCROLLABLE CONTENT
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Status Badge, Dynamic Exam Marking & Time Spent
                val (posMark, negMark) = remember(test.targetExam, question) {
                    Com.ak.testseries.exam.UniversalExamRegistry.getMarkingSchemeForQuestion(
                        examName = test.targetExam,
                        sectionIdOrName = question.section,
                        subjectName = question.subject,
                        questionTypeStr = question.questionType
                    )
                }
                val marksAwardedStr = when {
                    selectedAns.isEmpty() -> "0 M"
                    isCorrect -> "+${Com.ak.testseries.exam.ExamScoringEngine.formatMark(posMark)} M"
                    else -> if (negMark <= 0.0) "0 M" else "-${Com.ak.testseries.exam.ExamScoringEngine.formatMark(negMark)} M"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when {
                        selectedAns.isEmpty() -> if (isHindi) "छोड़ा हुआ" else "Skipped"
                        isCorrect -> if (isHindi) "सही उत्तर" else "Correct"
                        else -> if (isHindi) "गलत उत्तर" else "Incorrect"
                    }
                    val badgeColor = when {
                        selectedAns.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                        isCorrect -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.error
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(badgeColor, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(statusText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${test.targetExam}: $marksAwardedStr (+$posMark / -$negMark)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = if (isHindi) "समय: ${timeTaken}s" else "Time: ${timeTaken}s",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Question Card (Heading-wise consistency)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isHindi) "प्रश्न" else "QUESTION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = displayQuestionText ?: "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Options Section (Spaced out and consistent)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isHindi) "विकल्प" else "OPTIONS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    @Composable
                    fun SolutionOptionRow(letter: String, text: String) {
                        val isThisCorrect = letter == question.correctAnswer
                        val isThisSelected = letter == selectedAns
                        
                        val bgColor = when {
                            isThisCorrect -> Color(0xFFE8F5E9)
                            isThisSelected -> Color(0xFFFFEBEE)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        }
                        
                        val borderColor = when {
                            isThisCorrect -> Color(0xFF2E7D32)
                            isThisSelected -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }

                        val textColor = when {
                            isThisCorrect -> Color(0xFF1B5E20)
                            isThisSelected -> Color(0xFFB71C1C)
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(bgColor, RoundedCornerShape(12.dp))
                                .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(borderColor, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(letter, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = text, 
                                fontSize = 14.sp, 
                                color = textColor, 
                                fontWeight = if (isThisCorrect || isThisSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isThisCorrect) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "Correct", tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                            } else if (isThisSelected) {
                                Icon(Icons.Rounded.Cancel, contentDescription = "Selected Incorrect", tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    SolutionOptionRow("A", displayOptionA ?: "")
                    SolutionOptionRow("B", displayOptionB ?: "")
                    SolutionOptionRow("C", displayOptionC ?: "")
                    SolutionOptionRow("D", displayOptionD ?: "")
                }

                // Detailed Step-by-Step Solution & AI Reasoning Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isHindi) "विस्तृत चरणबद्ध समाधान व AI विश्लेषण" else "DETAILED SOLUTION & AI REASONING",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Verified Analysis",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val explanationText = displayExplanation?.ifEmpty {
                                if (isHindi) "स्पष्टीकरण अभी उपलब्ध नहीं है।" else "Explanation is not available for this question."
                            } ?: ""

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF2E7D32).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isHindi) "सही उत्तर: विकल्प ${question.correctAnswer}" else "Correct Option: ${question.correctAnswer}",
                                        color = Color(0xFF1B5E20),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                if (question.chapter.isNotBlank() && question.chapter != "All") {
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = question.chapter,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Text(
                                text = explanationText,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // AI & Web Resources Section (Google Search, YouTube Search, Ask AI)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isHindi) "स्मार्ट लर्निंग टूल्स" else "SMART LEARNING TOOLS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // AI Button: Sparkle/Magic wand icon
                        IconButton(
                            onClick = { 
                                activeDoubtTab = DoubtTab.AI
                                isDoubtSolverOpen = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF6C63FF).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF6C63FF), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "AI Solution",
                                tint = Color(0xFF6C63FF),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Google Button: Search icon
                        IconButton(
                            onClick = { 
                                activeDoubtTab = DoubtTab.GOOGLE
                                isDoubtSolverOpen = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4285F4).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF4285F4), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Google Search",
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // YouTube Button: Play Arrow icon
                        IconButton(
                            onClick = { 
                                activeDoubtTab = DoubtTab.YOUTUBE
                                isDoubtSolverOpen = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF0000).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFFF0000), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "YouTube Video",
                                tint = Color(0xFFFF0000),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        AnimatedVisibility(
            visible = isDoubtSolverOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            DoubtSolverUnifiedScreen(
                questionText = displayQuestionText ?: "",
                optionA = displayOptionA ?: "",
                optionB = displayOptionB ?: "",
                optionC = displayOptionC ?: "",
                optionD = displayOptionD ?: "",
                selectedLanguage = selectedLanguage,
                viewModel = viewModel,
                initialTab = activeDoubtTab,
                onClose = { isDoubtSolverOpen = false }
            )
        }
    }
}

enum class DoubtTab {
    AI, YOUTUBE, GOOGLE
}

@Composable
fun DoubtSolverUnifiedScreen(
    questionText: String,
    optionA: String,
    optionB: String,
    optionC: String,
    optionD: String,
    selectedLanguage: AppLanguage,
    viewModel: NEETViewModel,
    initialTab: DoubtTab,
    onClose: () -> Unit
) {
    var currentTab by remember { mutableStateOf(initialTab) }
    var webViewInstance by remember { mutableStateOf<android.webkit.WebView?>(null) }
    
    androidx.activity.compose.BackHandler(enabled = true) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onClose()
        }
    }
    
    val context = LocalContext.current
    val isHindi = selectedLanguage == AppLanguage.HINDI
    var isWebLoading by remember { mutableStateOf(false) }
    
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val examPrefix = userProfile.targetExam.takeIf { it.isNotBlank() } ?: ""
    val queryExamPart = if (examPrefix.isNotBlank()) "${android.net.Uri.encode(examPrefix)}+" else ""
    val encodedQuery = android.net.Uri.encode(questionText)
    val googleUrl = "https://www.google.com/search?q=${encodedQuery}+${queryExamPart}solution"
    val youtubeUrl = "https://www.youtube.com/results?search_query=${encodedQuery}+${queryExamPart}Physics+Wallah+Allen"
    val aiUrl = "https://www.google.com/search?q=${encodedQuery}+${queryExamPart}AI+Concept+Explanation"
    
    LaunchedEffect(currentTab, webViewInstance) {
        if (currentTab == DoubtTab.AI) {
            viewModel.askAiDoubt(questionText, optionA, optionB, optionC, optionD, selectedLanguage)
        }
        webViewInstance?.let { webView ->
            when (currentTab) {
                DoubtTab.YOUTUBE -> {
                    webView.loadUrl(youtubeUrl)
                }
                DoubtTab.GOOGLE -> {
                    webView.loadUrl(googleUrl)
                }
                else -> {}
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Header (Top): Native App Toolbar with Back Arrow and Title if (selectedLanguage == AppLanguage.HINDI) "NEET सारथी" else "NEET Saarthi"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (webViewInstance?.canGoBack() == true) {
                            webViewInstance?.goBack()
                        } else {
                            onClose()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = if (selectedLanguage == AppLanguage.HINDI) "NEET सारथी" else "NEET Saarthi",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Exit",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            // 2. Search Section (Fixed): Clean read-only text displaying the query inside OutlinedTextField
            OutlinedTextField(
                value = questionText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                label = { Text(if (isHindi) "संदेह / प्रश्न" else "Doubt / Query") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search Icon"
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            
            // 3. Tab/Action Buttons (Fixed - Horizontal Row) - Compact, text-less icon buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AI Solution Button
                IconButton(
                    onClick = { currentTab = DoubtTab.AI },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (currentTab == DoubtTab.AI) Color(0xFF6C63FF).copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .border(
                            width = if (currentTab == DoubtTab.AI) 2.dp else 1.dp,
                            color = if (currentTab == DoubtTab.AI) Color(0xFF6C63FF) else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI Solution",
                        tint = if (currentTab == DoubtTab.AI) Color(0xFF6C63FF) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Google Solution Button
                IconButton(
                    onClick = { currentTab = DoubtTab.GOOGLE },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (currentTab == DoubtTab.GOOGLE) Color(0xFF4285F4).copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .border(
                            width = if (currentTab == DoubtTab.GOOGLE) 2.dp else 1.dp,
                            color = if (currentTab == DoubtTab.GOOGLE) Color(0xFF4285F4) else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Google Solution",
                        tint = if (currentTab == DoubtTab.GOOGLE) Color(0xFF4285F4) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // YouTube Video Button
                IconButton(
                    onClick = { currentTab = DoubtTab.YOUTUBE },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (currentTab == DoubtTab.YOUTUBE) Color(0xFFFF0000).copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .border(
                            width = if (currentTab == DoubtTab.YOUTUBE) 2.dp else 1.dp,
                            color = if (currentTab == DoubtTab.YOUTUBE) Color(0xFFFF0000) else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "YouTube Video",
                        tint = if (currentTab == DoubtTab.YOUTUBE) Color(0xFFFF0000) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            // 4. Dynamic Content Container (Bottom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (currentTab == DoubtTab.AI) {
                    val askAiResponse by viewModel.askAiResponse.collectAsStateWithLifecycle()
                    val askAiLoading by viewModel.askAiLoading.collectAsStateWithLifecycle()
                    
                    if (askAiLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF6C63FF))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (isHindi) "AI मेंटर जवाब सोच रहा है..." else "AI Mentor is generating solution...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp)
                        ) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFF6C63FF), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("AI Mentor Solution", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6C63FF))
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = askAiResponse ?: "No response",
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                } else {
                    AndroidView(
                    factory = { context ->
                        android.webkit.WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            
                            webChromeClient = android.webkit.WebChromeClient()
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isWebLoading = true
                                }
                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isWebLoading = false
                                    
                                    val cleanScript = """
                                        javascript:(function() {
                                            var style = document.createElement('style');
                                            style.innerHTML = `
                                                #related, 
                                                .ytd-watch-next-secondary-results-renderer, 
                                                .ytd-rich-grid-renderer, 
                                                ytd-ad-slot-renderer { 
                                                    display: none !important; 
                                                }
                                            `;
                                            document.head.appendChild(style);
                                        })()
                                    """.trimIndent()
                                    
                                    view?.evaluateJavascript(cleanScript, null)
                                }
                                override fun shouldOverrideUrlLoading(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    return false
                                }
                            }
                            webViewInstance = this
                        }
                    },
                    update = {
                        // Let LaunchedEffect handle url updates
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                }
                if (isWebLoading && currentTab != DoubtTab.AI) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = when (currentTab) {
                                DoubtTab.AI -> Color(0xFF6C63FF)
                                DoubtTab.YOUTUBE -> Color(0xFFFF0000)
                                DoubtTab.GOOGLE -> Color(0xFF4285F4)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DoubtTabButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isActive) activeColor.copy(alpha = 0.15f) else Color.Transparent
    val contentColor = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    val strokeColor = if (isActive) activeColor else MaterialTheme.colorScheme.outlineVariant
    val strokeWidth = if (isActive) 1.5.dp else 1.dp
    
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor,
        contentColor = contentColor,
        border = BorderStroke(strokeWidth, strokeColor)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

fun generateAiSolutionHtml(aiResponseText: String, isHindi: Boolean): String {
    var html = aiResponseText
    val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
    html = boldRegex.replace(html) { matchResult ->
        "<b>${matchResult.groupValues[1]}</b>"
    }
    html = html.replace("\n", "<br>")
    
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://polyfill.io/v3/polyfill.min.js?features=es6"></script>
            <script id="MathJax-script" async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    margin: 0;
                    padding: 16px;
                    background-color: #F8F9FA;
                    color: #202124;
                    line-height: 1.6;
                }
                .container {
                    background: #FFFFFF;
                    border-radius: 12px;
                    padding: 18px;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.05);
                    border: 1px solid #E0E0E0;
                }
                .header {
                    display: flex;
                    align-items: center;
                    margin-bottom: 16px;
                    border-bottom: 2px solid #6C63FF;
                    padding-bottom: 8px;
                }
                .header h2 {
                    margin: 0;
                    font-size: 18px;
                    color: #6C63FF;
                    font-weight: 700;
                }
                .badge {
                    background-color: #E8F0FE;
                    color: #1A73E8;
                    font-size: 12px;
                    padding: 4px 8px;
                    border-radius: 6px;
                    font-weight: 600;
                    margin-left: auto;
                }
                .section-title {
                    font-size: 14px;
                    font-weight: 700;
                    color: #5F6368;
                    text-transform: uppercase;
                    letter-spacing: 0.5px;
                    margin-top: 16px;
                    margin-bottom: 8px;
                }
                .content {
                    font-size: 15px;
                    color: #3C4043;
                    background: #F8F9FA;
                    padding: 14px;
                    border-radius: 8px;
                    border-left: 4px solid #6C63FF;
                    margin-bottom: 16px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h2>${if (isHindi) "NEET सारथी AI समाधान" else "NEET Sarthi AI Solution"}</h2>
                    <span class="badge">${if (isHindi) "सत्यापित उत्तर" else "Verified Answer"}</span>
                </div>
                <div class="section-title">${if (isHindi) "अवधारणा और समाधान" else "Concept & Solution"}</div>
                <div class="content">$html</div>
            </div>
        </body>
        </html>
    """.trimIndent()
}

@Composable
fun StatMetricColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = color)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 11.sp)
    }
}

// ==========================================
// 🛠️ ADMIN CONTROL PANEL SCREEN
// ==========================================
@Composable
fun AdminPanelScreen(viewModel: NEETViewModel) {
    var selectedMenu by remember { mutableStateOf(AdminMenu.DASHBOARD) }
    var activeReviewTestId by remember { mutableStateOf<String?>(null) }
    var activeReviewTestTitle by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                
                    ModalDrawerSheet(modifier = Modifier.width(300.dp), drawerContainerColor = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        ) {
                            Spacer(Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_ak_logo),
                                    contentDescription = "Logo",
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(stringRes("Admin Panel"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("AK Test Series AI", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            HorizontalDivider()

                            val adminMenuItems = listOf(
                                Triple(AdminMenu.UNIVERSAL_EXAMS, "Universal 20-Exams", Icons.Rounded.School),
                                Triple(AdminMenu.AI_MANAGER, "AI Manager", Icons.Rounded.AutoAwesome),
                                Triple(AdminMenu.MONETIZATION, "Monetization", Icons.Rounded.MonetizationOn),
                                Triple(AdminMenu.DATABASE_CONFIGS, "DB Configs", Icons.Rounded.Storage),
                                Triple(AdminMenu.NOTIFICATIONS, "Announce", Icons.Rounded.Campaign)
                            )

                            adminMenuItems.forEach { (menu, title, icon) ->
                                NavigationDrawerItem(
                                    icon = { Icon(icon, contentDescription = title, modifier = Modifier.size(16.dp)) },
                                    label = { Text(title, fontSize = 12.sp) },
                                    selected = selectedMenu == menu,
                                    onClick = {
                                        selectedMenu = menu
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
            }
        ) {
            
                Scaffold(
                    topBar = {
                        CustomTopAppBar(
                            title = { Text(stringRes("Master Admin Panel"), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { viewModel.navigateToScreen(AppScreen.DASHBOARD) }) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Rounded.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            val bottomNavItems = listOf(
                                Triple(AdminMenu.DASHBOARD, stringRes("Home"), Icons.Rounded.Home),
                                Triple(AdminMenu.TEST_LIST, "Tests", Icons.Rounded.List),
                                Triple(AdminMenu.COMMUNITY_CONTROLS, stringRes("Community"), Icons.Rounded.Group),
                                Triple(AdminMenu.USERS_ANALYTICS, "Users", Icons.Rounded.People)
                            )
                            bottomNavItems.forEach { (menu, title, icon) ->
                                NavigationBarItem(
                                    icon = { Icon(icon, contentDescription = title) },
                                    label = { Text(title) },
                                    selected = selectedMenu == menu,
                                    onClick = { selectedMenu = menu }
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        androidx.compose.material3.FloatingActionButton(
                            onClick = { selectedMenu = AdminMenu.TEST_GENERATION },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = stringRes("Add Test"), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        if (activeReviewTestId != null) {
                            Com.ak.testseries.ui.TestReviewScreen(
                                testId = activeReviewTestId!!,
                                testTitle = activeReviewTestTitle,
                                viewModel = viewModel,
                                onBack = { activeReviewTestId = null }
                            )
                        } else {
                            when (selectedMenu) {
                                AdminMenu.DASHBOARD -> AdminDashboardContent(viewModel)
                                AdminMenu.UNIVERSAL_EXAMS -> {
                                    UniversalExamAdminScreen(
                                        onBack = { selectedMenu = AdminMenu.DASHBOARD },
                                        isAiEnabled = viewModel.isGlobalAiEnabled.collectAsStateWithLifecycle().value,
                                        onOpenPdfCropper = { uri: android.net.Uri, page: Int, onResult: (String) -> Unit ->
                                            viewModel.pdfCropperInitialPage.value = page
                                            viewModel.selectedPdfUri.value = uri
                                            viewModel.pdfCropCallback = onResult
                                            viewModel.showPdfCropper.value = true
                                        },
                                        onPublishTest = { examName: String, questions: List<UniversalQuestionModel>, durationMinutes: Int ->
                                            viewModel.publishUniversalTest(examName, questions, durationMinutes)
                                        }
                                    )
                                }
                                AdminMenu.TEST_LIST -> Com.ak.testseries.ui.AdminTestListContent(viewModel) { testId, testTitle ->
                                    activeReviewTestId = testId
                                    activeReviewTestTitle = testTitle
                                }
                                AdminMenu.TEST_GENERATION -> AdminTestGenerationContent(viewModel)
                                AdminMenu.AI_MANAGER -> AdminAiManagerContent(viewModel)
                                AdminMenu.MONETIZATION -> AdminMonetizationContent()
                                AdminMenu.DATABASE_CONFIGS -> AdminDbConfigsContent(viewModel)
                                AdminMenu.USERS_ANALYTICS -> AdminUsersAnalyticsContent(viewModel)
                                AdminMenu.COMMUNITY_CONTROLS -> Com.ak.testseries.ui.CommunityAdminScreen(viewModel)
                                AdminMenu.NOTIFICATIONS -> AdminNotificationsContent(viewModel)
                            }
                        }
                    }
                }
    }
}

enum class AdminMenu {
    DASHBOARD,
    UNIVERSAL_EXAMS,
    TEST_LIST,
    TEST_GENERATION,
    AI_MANAGER,
    MONETIZATION,
    DATABASE_CONFIGS,
    USERS_ANALYTICS,
    COMMUNITY_CONTROLS,
    NOTIFICATIONS
}

@Composable
fun AdminSidebarItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        contentColor = contentColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
fun AdminTestGenerationContent(viewModel: NEETViewModel) {
    var classLevel by remember { mutableStateOf("11th") }
    var classLevelExpanded by remember { mutableStateOf(false) }
    
    var subject by remember { mutableStateOf("Physics") }
    var subjectExpanded by remember { mutableStateOf(false) }
    
    var testType by remember { mutableStateOf("Single Chapter") }
    var testTypeExpanded by remember { mutableStateOf(false) }
    
    var chapterName by remember { mutableStateOf("") }
    var chapterExpanded by remember { mutableStateOf(false) }

    var timerMinutes by remember { mutableStateOf("60") }
    var negativeMarking by remember { mutableStateOf("1") }

    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(stringRes("Upload New Test"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Class Dropdown
                Box {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { classLevelExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Class: $classLevel", modifier = Modifier.weight(1f), textAlign = TextAlign.Start, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Class", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = classLevelExpanded, onDismissRequest = { classLevelExpanded = false }) {
                        Com.ak.testseries.data.SyllabusData.classes.forEach { clazz ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(clazz, fontSize = 12.sp) }, onClick = { classLevel = clazz; classLevelExpanded = false })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Subject Dropdown
                Box {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { subjectExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Subject: $subject", modifier = Modifier.weight(1f), textAlign = TextAlign.Start, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Subject", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = subjectExpanded, onDismissRequest = { subjectExpanded = false }) {
                        Com.ak.testseries.data.SyllabusData.subjects.forEach { sub ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(sub, fontSize = 12.sp) }, onClick = { subject = sub; subjectExpanded = false })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Test Type Dropdown
                Box {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { testTypeExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Test Type: $testType", modifier = Modifier.weight(1f), textAlign = TextAlign.Start, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Type", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = testTypeExpanded, onDismissRequest = { testTypeExpanded = false }) {
                        listOf("Single Chapter", "Multi-Chapter").forEach { type ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(type, fontSize = 12.sp) }, onClick = { testType = type; testTypeExpanded = false })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (classLevel != stringRes("Dropper") && testType == "Single Chapter") {
                    val availableChapters = Com.ak.testseries.data.SyllabusData.syllabus[classLevel]?.get(subject) ?: emptyList()
                    Box {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { chapterExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Chapter: ${chapterName.ifEmpty { "Select Chapter" }}", modifier = Modifier.weight(1f), textAlign = TextAlign.Start, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Chapter", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        }
                        androidx.compose.material3.DropdownMenu(expanded = chapterExpanded, onDismissRequest = { chapterExpanded = false }) {
                            availableChapters.forEach { chap ->
                                androidx.compose.material3.DropdownMenuItem(text = { Text(chap, fontSize = 12.sp) }, onClick = { chapterName = chap; chapterExpanded = false })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                
                var title by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringRes("Test Title"), fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                var imageUrl by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text(stringRes("Test Image URL (Optional)"), fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Timer and Negative Marking
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = timerMinutes,
                        onValueChange = { timerMinutes = it },
                        label = { Text(stringRes("Timer (Mins)"), fontSize = 10.sp) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                    OutlinedTextField(
                        value = negativeMarking,
                        onValueChange = { negativeMarking = it },
                        label = { Text(stringRes("Negative Marks"), fontSize = 10.sp) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val targetClassLevel = if (testType == "Multi-Chapter") "Dropper" else classLevel
                            val newTest = Com.ak.testseries.data.TestEntity(
                                id = java.util.UUID.randomUUID().toString(),
                                title = title.ifEmpty { "$testType - $subject" },
                                description = "Admin uploaded test",
                                institute = "Admin",
                                batchName = targetClassLevel,
                                facultyName = "Admin",
                                language = "English",
                                subject = subject,
                                difficulty = "Medium",
                                testType = testType,
                                questionsCount = 0,
                                isOfficial = true,
                                isVerifiedCommunity = true,
                                classLevel = targetClassLevel,
                                chapter = chapterName,
                                timestamp = System.currentTimeMillis(),
                                imageUrl = imageUrl.ifEmpty { null }
                            )
                            viewModel.addNewTest(newTest) // Need to define this in viewModel
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                    
                ) {
                    Icon(Icons.Default.Build, contentDescription = "Upload", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringRes("Upload Test"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AdminDashboardContent(viewModel: NEETViewModel) {
    val context = LocalContext.current
    var isSyncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CloudUpload,
                        contentDescription = "Sync",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Official Mock Tests & Question Bank",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Publish full-length NEET UG (200Q) & JEE Mains (90Q) bilingual tests to cloud database for all students.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isSyncing = true
                            syncStatus = "Publishing to Firestore Tests_Data..."
                            viewModel.syncBundledTestsToCloud { success, error ->
                                isSyncing = false
                                if (success) {
                                    syncStatus = "Published successfully! Available to all students."
                                    android.widget.Toast.makeText(context, "All Official Tests Published to Cloud!", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    syncStatus = "Failed: ${error ?: "Unknown error"}"
                                    android.widget.Toast.makeText(context, "Sync Failed: $error", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Publishing...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Rounded.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Publish to Cloud", fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.seedBundledMockTestsIfNeeded()
                            android.widget.Toast.makeText(context, "Local Mock Tests Seeded!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Seed Local DB", fontSize = 12.sp)
                    }
                }
                if (syncStatus != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        syncStatus!!,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (syncStatus!!.startsWith("Failed")) Color.Red else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Com.ak.testseries.ui.SuperAdminDashboardContentOnly()
        Spacer(Modifier.height(16.dp))
        Com.ak.testseries.ui.ReportedQuestionsSection(viewModel)
    }
}

@Composable
fun ServiceSparklineCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = color)
            // Mock Sparkline
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                val path = androidx.compose.ui.graphics.Path()
                val width = size.width
                val height = size.height
                path.moveTo(0f, height * 0.8f)
                path.lineTo(width * 0.2f, height * 0.6f)
                path.lineTo(width * 0.4f, height * 0.7f)
                path.lineTo(width * 0.6f, height * 0.3f)
                path.lineTo(width * 0.8f, height * 0.5f)
                path.lineTo(width, height * 0.1f)
                drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@Composable
fun AdminConfigItem(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    isActive: Boolean,
    onActiveChange: (Boolean) -> Unit,
    usagePercentage: Float,
    usageText: String,
    isPassword: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Title
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            // Row with Switch and Circular Progress
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left: Switch
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(checked = isActive, onCheckedChange = onActiveChange)
                    Text(if (isActive) "Active" else "Inactive", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Middle: Circular Progress Tracking
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(50.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = Color.LightGray.copy(alpha = 0.5f),
                            strokeWidth = 4.dp
                        )
                        androidx.compose.material3.CircularProgressIndicator(
                            progress = { usagePercentage },
                            modifier = Modifier.fillMaxSize(),
                            color = if (usagePercentage > 0.8f) Color.Red else Color.Green,
                            strokeWidth = 4.dp
                        )
                        Text("${(usagePercentage * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(usageText, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Right: Secured Lock Icon
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringRes("Secured"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringRes("Secured"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Secured via GitHub Secrets",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AdminAiManagerContent(viewModel: NEETViewModel) {
    val isGlobalAiEnabled by viewModel.isGlobalAiEnabled.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var isUpdating by remember { mutableStateOf(false) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(stringRes("Gemini AI Dashboard"), fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(stringRes("Powered by Google"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        Com.ak.testseries.ui.GeminiKeyManagerSection(viewModel)
        
        Spacer(modifier = Modifier.height(16.dp))

        // Main Gemini Activation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(stringRes("Gemini AI Engine"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            if (isGlobalAiEnabled) "Running & Active" else "Deactivated (Global Killswitch Active)",
                            fontSize = 12.sp,
                            color = if (isGlobalAiEnabled) Color(0xFF34A853) else Color.Red
                        )
                    }
                }
                
                Switch(
                    checked = isGlobalAiEnabled,
                    enabled = !isUpdating,
                    onCheckedChange = { newState ->
                        isUpdating = true
                        viewModel.setGlobalAiEnabled(newState) { success, errorMsg ->
                            isUpdating = false
                            if (!success) {
                                android.widget.Toast.makeText(context, errorMsg ?: "Failed to update AI Engine setting", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                val status = if (newState) "AI Engine Enabled" else "Global AI Killswitch Activated!"
                                android.widget.Toast.makeText(context, status, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4285F4)
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Daily Free Generation Limit Card (Config/admin_settings -> dailyFreeTestLimit)
        val dailyFreeLimit by viewModel.dailyFreeTestLimit.collectAsStateWithLifecycle()
        var editableLimit by remember(dailyFreeLimit) { mutableStateOf(dailyFreeLimit) }
        var isSavingLimit by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFE0E7FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Daily Free Test Limit", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Default: 1 test/day per student", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Stepper: - and +
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (editableLimit > 1) editableLimit-- },
                            enabled = editableLimit > 1 && !isSavingLimit
                        ) {
                            Text("-", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                "$editableLimit",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                        IconButton(
                            onClick = { editableLimit++ },
                            enabled = !isSavingLimit
                        ) {
                            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (editableLimit != dailyFreeLimit) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            isSavingLimit = true
                            viewModel.setDailyFreeTestLimit(editableLimit) { success, errorMsg ->
                                isSavingLimit = false
                                if (success) {
                                    android.widget.Toast.makeText(context, "Daily generation limit updated to $editableLimit!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    editableLimit = dailyFreeLimit
                                    android.widget.Toast.makeText(context, errorMsg ?: "Failed to update daily limit", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        enabled = !isSavingLimit,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text(if (isSavingLimit) "Saving Limit..." else "Save Daily Limit ($editableLimit tests/day)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Offline Mock Fallback Switch Card (Config/admin_settings -> offlineMockFallbackEnabled)
        val isOfflineMockFallbackEnabled by viewModel.isOfflineMockFallbackEnabled.collectAsStateWithLifecycle()
        var isUpdatingMockFallback by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(if (isOfflineMockFallbackEnabled) Color(0xFFDCFCE7) else Color(0xFFF1F5F9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Storage, 
                            contentDescription = null, 
                            tint = if (isOfflineMockFallbackEnabled) Color(0xFF16A34A) else Color.Gray, 
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Offline Mock Fallback", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isOfflineMockFallbackEnabled) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                            ) {
                                Text(
                                    if (isOfflineMockFallbackEnabled) "ON" else "OFF",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOfflineMockFallbackEnabled) Color(0xFF15803D) else Color(0xFFB91C1C),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (isOfflineMockFallbackEnabled) 
                                "ON: Existing mock fallback can be used when real AI generation fails." 
                            else 
                                "OFF: No mock/fake test generation; show an error instead.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }

                Switch(
                    checked = isOfflineMockFallbackEnabled,
                    enabled = !isUpdatingMockFallback,
                    onCheckedChange = { newState ->
                        isUpdatingMockFallback = true
                        viewModel.setOfflineMockFallbackEnabled(newState) { success, errorMsg ->
                            isUpdatingMockFallback = false
                            if (!success) {
                                android.widget.Toast.makeText(context, errorMsg ?: "Failed to update Offline Mock Fallback setting", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                val status = if (newState) "Offline Mock Fallback Enabled (ON)" else "Offline Mock Fallback Disabled (OFF)"
                                android.widget.Toast.makeText(context, status, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF16A34A)
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Active Capabilities", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        
        // Capabilities Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GeminiCapabilityCard(
                title = "Test Generation",
                description = "Auto-generates Mock Tests and PYQs",
                icon = Icons.Rounded.Article,
                isActive = isGlobalAiEnabled,
                modifier = Modifier.weight(1f)
            )
            GeminiCapabilityCard(
                title = "Smart Upload",
                description = "Parses PDF notes into interactive Tests",
                icon = Icons.Rounded.PictureAsPdf,
                isActive = isGlobalAiEnabled,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GeminiCapabilityCard(
                title = "Doubt Solver",
                description = "Explains incorrect answers to students",
                icon = Icons.Rounded.Lightbulb,
                isActive = isGlobalAiEnabled,
                modifier = Modifier.weight(1f)
            )
            GeminiCapabilityCard(
                title = "Analytics",
                description = "Finds weak topics for students",
                icon = Icons.Rounded.Insights,
                isActive = isGlobalAiEnabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun GeminiCapabilityCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isActive: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isActive) 0.8f else 0.4f)),
        border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, fontSize = 10.sp, color = if (isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
        }
    }
}

@Composable
fun AdminMonetizationContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var appOpenAdId by remember { mutableStateOf("") }
    var interstitialAdId by remember { mutableStateOf("") }
    var areAdsEnabled by remember { mutableStateOf(true) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(stringRes("Monetization (AdMob)"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringRes("Enable All Ads"), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Switch(checked = areAdsEnabled, onCheckedChange = { areAdsEnabled = it })
                }
                Text(stringRes("Master toggle to enable or disable all ads in the app."), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = appOpenAdId,
                    onValueChange = { appOpenAdId = it },
                    label = { Text(stringRes("App Open Ad ID"), fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = interstitialAdId,
                    onValueChange = { interstitialAdId = it },
                    label = { Text(stringRes("Interstitial Ad ID"), fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { 
                    android.widget.Toast.makeText(context, "Ad Configurations Saved!", android.widget.Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth().height(36.dp), ) {
                    Text(stringRes("Save Ad Configurations"), fontSize = 11.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(stringRes("Subscriptions & Premium"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        
        var proPrice by remember { mutableStateOf("299") }
        var elitePrice by remember { mutableStateOf("2499") }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = proPrice,
                    onValueChange = { proPrice = it },
                    label = { Text(stringRes("Pro Plan Price (Monthly)"), fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = elitePrice,
                    onValueChange = { elitePrice = it },
                    label = { Text(stringRes("Elite Plan Price (Yearly)"), fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { 
                    android.widget.Toast.makeText(context, "Subscription Pricing Updated!", android.widget.Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth().height(36.dp), ) {
                    Text(stringRes("Update Subscription Pricing"), fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AdminDbConfigsContent(viewModel: NEETViewModel) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val masterPrivateState by viewModel.masterPrivateSwitch.collectAsStateWithLifecycle()

    var firebaseJson by remember { mutableStateOf("") }
    var isFirebaseActive by remember { mutableStateOf(true) }

    var appwriteProjectId by remember { mutableStateOf("") }
    var isAppwriteActive by remember { mutableStateOf(false) }

    // Live test schedule state
    var title by remember { mutableStateOf("") }
    var classLevel by remember { mutableStateOf("11th") }
    var classLevelExpanded by remember { mutableStateOf(false) }
    
    var subject by remember { mutableStateOf("Physics") }
    var subjectExpanded by remember { mutableStateOf(false) }
    
    var chapterName by remember { mutableStateOf("") }
    
    var scheduledTime by remember { mutableStateOf("2026-07-20 10:00 AM") }
    var durationMinutes by remember { mutableStateOf("180") }
    var questionsCount by remember { mutableStateOf("90") }
    
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("") }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(if (selectedLanguage == AppLanguage.HINDI) "मास्टर विजिबिलिटी कंट्रोल" else "Master Visibility Control", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (masterPrivateState) Icons.Rounded.Lock else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = if (masterPrivateState) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Force Community Tests to Private",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedLanguage == AppLanguage.HINDI) "सारे यूजर/कम्युनिटी टेस्ट छुपाएं (PW / Allen etc.)" else "Hide all User/Community Tests (PW / Allen etc.)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = masterPrivateState,
                        onCheckedChange = { viewModel.setMasterPrivateCommunityTests(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.error,
                            checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (masterPrivateState) 
                        "🔒 Currently Active: All user/community uploaded tests are set to PRIVATE and hidden from regular students in real-time."
                        else "🌍 Currently Disabled: All community uploaded tests are PUBLIC and fully accessible by default.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (masterPrivateState) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(if (selectedLanguage == AppLanguage.HINDI) "लाइव टेस्ट अपलोड और शेड्यूल करें" else stringRes("Upload & Schedule Live Test"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Class Selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { classLevelExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Class: $classLevel", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    androidx.compose.material3.DropdownMenu(expanded = classLevelExpanded, onDismissRequest = { classLevelExpanded = false }) {
                        listOf("11th", "12th", stringRes("Dropper")).forEach { clazz ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(clazz) }, onClick = { classLevel = clazz; classLevelExpanded = false })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Subject Selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { subjectExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Subject: $subject", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    androidx.compose.material3.DropdownMenu(expanded = subjectExpanded, onDismissRequest = { subjectExpanded = false }) {
                        listOf("Physics", "Chemistry", "Biology").forEach { sub ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(sub) }, onClick = { subject = sub; subjectExpanded = false })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Chapter
                OutlinedTextField(
                    value = chapterName,
                    onValueChange = { chapterName = it },
                    label = { Text(stringRes("Chapter / Syllabus")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringRes("Live Test Title")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Scheduled Time
                OutlinedTextField(
                    value = scheduledTime,
                    onValueChange = { scheduledTime = it },
                    label = { Text(stringRes("Schedule Date & Time")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Duration & Questions count
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = durationMinutes,
                        onValueChange = { durationMinutes = it },
                        label = { Text(stringRes("Duration (Mins)")) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = questionsCount,
                        onValueChange = { questionsCount = it },
                        label = { Text(stringRes("Questions Count")) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (title.isNotEmpty() && chapterName.isNotEmpty()) {
                            viewModel.scheduleLiveTest(
                                title = title,
                                subject = subject,
                                classLevel = classLevel,
                                chapter = chapterName,
                                scheduledTime = scheduledTime,
                                durationMinutes = durationMinutes.toIntOrNull() ?: 180,
                                questionsCount = questionsCount.toIntOrNull() ?: 90
                            )
                            statusMessage = "Successfully Scheduled & Synchronized to Firestore!"
                            title = ""
                            chapterName = ""
                        } else {
                            statusMessage = "Please enter Title and Chapter Name first."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringRes("Upload & Schedule Live Test"), fontWeight = FontWeight.Bold)
                }

                if (statusMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(statusMessage, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(stringRes("Database Configs & Tracking"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        // Firebase
        AdminConfigItem(
            title = "Firebase Config JSON",
            value = firebaseJson,
            onValueChange = { firebaseJson = it },
            isActive = isFirebaseActive,
            onActiveChange = { isFirebaseActive = it },
            usagePercentage = 0.40f, // Mock memory tracking
            usageText = "{memory_usage}",
            isPassword = true
        )

        // Appwrite
        AdminConfigItem(
            title = "Appwrite Project ID",
            value = appwriteProjectId,
            onValueChange = { appwriteProjectId = it },
            isActive = isAppwriteActive,
            onActiveChange = { isAppwriteActive = it },
            usagePercentage = 0.05f, // Mock storage tracking
            usageText = "{storage_usage}",
            isPassword = true
        )
    }
}

// ==========================================
// 📊 NCERT PROGRESS CHART
// ==========================================
@Composable
fun NcertProgressChart(selectedLanguage: AppLanguage, onClick: () -> Unit = {}) {
    val totalChapters = 30
    val completedChapters = 12 // Mock progress, ideally from ViewModel
    val pendingChapters = totalChapters - completedChapters
    
    val progress = completedChapters.toFloat() / totalChapters.toFloat()
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            BilingualText(
                englishText = "NCERT Syllabus Progress",
                hindiText = if (selectedLanguage == AppLanguage.HINDI) "NCERT पाठ्यक्रम प्रगति" else "NCERT Syllabus Progress",
                language = selectedLanguage,
                englishFontSize = 16.sp,
                hindiFontSize = 16.sp,
                englishFontWeight = FontWeight.Bold,
                hindiFontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Donut Chart
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(100.dp)) {
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                        drawArc(
                            color = primaryColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // Legend
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem(color = MaterialTheme.colorScheme.primary, text = "Completed ($completedChapters)")
                    LegendItem(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), text = "Pending ($pendingChapters)")
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(14.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ==========================================
// 🌍 BILINGUAL TEXT COMPOSER
// ==========================================
@Composable
fun BilingualText(
    englishText: String,
    hindiText: String,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    englishFontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    hindiFontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    englishColor: Color = Color.Unspecified,
    hindiColor: Color = Color.Unspecified,
    englishFontWeight: FontWeight? = null,
    hindiFontWeight: FontWeight? = null,
    style: TextStyle = LocalTextStyle.current
) {
    if (language == AppLanguage.ENGLISH) {
        Text(
            text = englishText,
            modifier = modifier,
            fontSize = englishFontSize,
            color = if (englishColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else englishColor,
            fontWeight = englishFontWeight ?: FontWeight.Normal,
            style = style
        )
    } else {
        Text(
            text = hindiText,
            modifier = modifier,
            fontSize = hindiFontSize,
            color = if (hindiColor == Color.Unspecified) MaterialTheme.colorScheme.primary else hindiColor,
            fontWeight = hindiFontWeight ?: FontWeight.Normal,
            style = style
        )
    }
}

// ==========================================
// 🏆 MASTER LEADERBOARD & GAMIFICATION SYSTEM
// ==========================================
@Composable
fun MiniLeaderboardCard(
    selectedLanguage: AppLanguage,
    leaderboard: List<Com.ak.testseries.domain.auth.LeaderboardEntry>,
    userProfile: Com.ak.testseries.viewmodel.UserProfileData,
    viewModel: NEETViewModel? = null
) {
    val examFilter = viewModel?.leaderboardExamFilter?.collectAsStateWithLifecycle()?.value ?: userProfile.targetExam.ifBlank { "NEET" }
    val userRank = viewModel?.userRank?.collectAsStateWithLifecycle()?.value ?: "-"
    val userRankStatus = viewModel?.userRankStatus?.collectAsStateWithLifecycle()?.value ?: "Rank Pending"
    val eligibleCount = viewModel?.eligibleParticipantsCount?.collectAsStateWithLifecycle()?.value ?: 0
    val isOfficialAir = viewModel?.isOfficialAirUnlocked?.collectAsStateWithLifecycle()?.value ?: false
    val nextTargetMsg = viewModel?.nextTargetMessage?.collectAsStateWithLifecycle()?.value ?: ""
    val qCount = viewModel?.userQualifyingTestsCount?.collectAsStateWithLifecycle()?.value ?: 0
    val compScore = viewModel?.userCompetitiveScore?.collectAsStateWithLifecycle()?.value ?: 0
    val userPercentile = viewModel?.userPercentile?.collectAsStateWithLifecycle()?.value ?: 0.0

    val examsList = remember {
        (listOf("All") + Com.ak.testseries.exam.UniversalExamRegistry.getAllExams().map { it.shortName }).distinct()
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Title & Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    BilingualText(
                        englishText = "Master Leaderboard",
                        hindiText = "मास्टर लीडरबोर्ड",
                        language = selectedLanguage,
                        englishFontSize = 18.sp,
                        hindiFontSize = 18.sp,
                        englishFontWeight = FontWeight.ExtraBold,
                        hindiFontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Fair • Exam-Specific • Competitive",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFD700).copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = "Trophy",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Exam Filter Selector Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(examsList) { exam ->
                    val isSelected = exam.equals(examFilter, ignoreCase = true) ||
                            (!exam.equals("All", ignoreCase = true) && !examFilter.equals("All", ignoreCase = true) &&
                             Com.ak.testseries.exam.UniversalExamRegistry.getCanonicalExamKey(exam) == Com.ak.testseries.exam.UniversalExamRegistry.getCanonicalExamKey(examFilter))
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel?.fetchLeaderboard(exam)
                            viewModel?.fetchUserRank(exam)
                        },
                        label = {
                            Text(
                                text = exam,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status Indicator Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (userRankStatus) {
                        "Official AIR" -> Color(0xFFE8F5E9)
                        "Provisional Rank" -> Color(0xFFFFF3E0)
                        else -> Color(0xFFEDE7F6)
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (userRankStatus) {
                            "Official AIR" -> Icons.Rounded.Verified
                            "Provisional Rank" -> Icons.Rounded.Analytics
                            else -> Icons.Rounded.HourglassEmpty
                        },
                        contentDescription = "Status",
                        tint = when (userRankStatus) {
                            "Official AIR" -> Color(0xFF2E7D32)
                            "Provisional Rank" -> Color(0xFFE65100)
                            else -> Color(0xFF512DA8)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when (userRankStatus) {
                                "Official AIR" -> "🟢 Official AK All India Rank Active"
                                "Provisional Rank" -> "🟠 Provisional Rank ($eligibleCount / 1,000 candidates for AIR)"
                                else -> "🟡 Rank Pending (Complete 2 qualifying tests)"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = when (userRankStatus) {
                                "Platform Rank" -> Color(0xFF1B5E20)
                                "Provisional Rank" -> Color(0xFFBF360C)
                                else -> Color(0xFF311B92)
                            }
                        )
                        Text(
                            text = "Platform rank across verified test series candidates.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // User's Personal Rank Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = userRank,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                val displayName = userProfile.name.ifEmpty { "Student" }
                                Text(
                                    text = "$displayName (You)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (userPercentile > 0.0) {
                                        "Qualifying: $qCount | Percentile: ${String.format("%.2f", userPercentile)}%"
                                    } else {
                                        "Qualifying Tests: $qCount | Exam: $examFilter"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$compScore pts",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Cumulative",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (nextTargetMsg.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.TrendingUp,
                                contentDescription = "Target",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = nextTargetMsg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Leaderboard Pool Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🏆 Top $examFilter Performers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${leaderboard.size} Ranked",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Top Students List
            if (leaderboard.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedLanguage == AppLanguage.HINDI) "$examFilter के लिए कोई प्रतिस्पर्धी स्कोर उपलब्ध नहीं है। पहली परीक्षा दें!" else "No competitive scores yet for $examFilter. Take a test to claim Rank #1!",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                leaderboard.forEach { entry ->
                    val isCurrentUser = entry.name == userProfile.name
                    val badgeColor = when (entry.rank) {
                        1 -> Color(0xFFFFD700)
                        2 -> Color(0xFFC0C0C0)
                        3 -> Color(0xFFCD7F32)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isCurrentUser) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.width(32.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (entry.rank <= 3) {
                                        Text(
                                            text = when (entry.rank) {
                                                1 -> "🥇"
                                                2 -> "🥈"
                                                3 -> "🥉"
                                                else -> "#${entry.rank}"
                                            },
                                            fontSize = 16.sp
                                        )
                                    } else {
                                        Text(
                                            text = "#${entry.rank}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = badgeColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = entry.name,
                                            fontWeight = if (isCurrentUser) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (entry.league.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = when (entry.league) {
                                                    "Master" -> Color(0xFFFFD700).copy(alpha = 0.2f)
                                                    "Elite" -> Color(0xFF9C27B0).copy(alpha = 0.15f)
                                                    "Scholar" -> Color(0xFF2196F3).copy(alpha = 0.15f)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            ) {
                                                Text(
                                                    text = entry.league,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (entry.league) {
                                                        "Master" -> Color(0xFFB78103)
                                                        "Elite" -> Color(0xFF7B1FA2)
                                                        "Scholar" -> Color(0xFF1976D2)
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${entry.qualifyingTestsCount} Tests • ${entry.streak}d Streak",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "${entry.score} pts",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 🏆 LEADERBOARD TAB
// ==========================================
@Composable
fun LeaderboardTabScreen(viewModel: NEETViewModel) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val attempts by viewModel.attemptHistory.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val selectedExamFilter by viewModel.leaderboardExamFilter.collectAsStateWithLifecycle()

    LaunchedEffect(selectedExamFilter, userProfile.targetExam) {
        val exam = if (selectedExamFilter.isNotBlank()) selectedExamFilter else userProfile.targetExam.ifBlank { "NEET" }
        viewModel.fetchLeaderboard(exam)
        viewModel.fetchUserRank(exam)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Segmented Control (Tabs)
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("🏆 Leaderboard", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("📊 My Progress", fontWeight = FontWeight.Bold) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (selectedTabIndex == 0) {
            val leaderboard by viewModel.leaderboard.collectAsStateWithLifecycle()
            MiniLeaderboardCard(selectedLanguage, leaderboard, userProfile, viewModel)
        } else {
            MyProgressContent(selectedLanguage, attempts, viewModel)
        }
    }
}

@Composable
fun MyProgressContent(selectedLanguage: AppLanguage, attempts: List<Com.ak.testseries.data.AttemptEntity>, viewModel: NEETViewModel) {
    val testsList by viewModel.testsList.collectAsStateWithLifecycle()
    val totalAttempts = attempts.size
    val uniqueTestsAttempted = attempts.distinctBy { it.testId }.size
    val avgScore = if (attempts.isEmpty()) 0 else (attempts.sumOf { it.score }.toFloat() / attempts.size).toInt()
    val maxPossibleAvg = if (attempts.isEmpty()) 720f else (attempts.sumOf { it.maxScore }.toFloat() / attempts.size)
    val progressRatio = if (maxPossibleAvg > 0) (avgScore / maxPossibleAvg) else 0f
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringRes("Total Attempts"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$totalAttempts", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
            }
            
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringRes("Tests Attempted"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$uniqueTestsAttempted", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringRes("Average Score"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                // Circular UI placeholder
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 12.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$avgScore", fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Text("/ ${maxPossibleAvg.toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
        val targetExam = userProfile.targetExam
        
        val totalChapters = remember(targetExam) {
            Com.ak.testseries.data.SyllabusData.getTotalChaptersCountForExam(targetExam).coerceAtLeast(1)
        }
        val attemptedTestIds = remember(attempts) { attempts.map { it.testId }.toSet() }
        val attemptedTests = remember(testsList, attemptedTestIds) { testsList.filter { it.id in attemptedTestIds } }
        val attemptedChapters = remember(attemptedTests) { attemptedTests.map { it.chapter }.filter { it.isNotBlank() && it != "All" }.distinct() }
        
        val completedChaptersCount = if (attemptedChapters.isNotEmpty()) {
            attemptedChapters.size
        } else {
            if (uniqueTestsAttempted > 0) minOf(uniqueTestsAttempted, totalChapters) else 0
        }
        
        val syllabusProgress = if (totalChapters > 0) completedChaptersCount.toFloat() / totalChapters.toFloat() else 0f
        val syllabusProgressPercent = (syllabusProgress * 100).toInt()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (selectedLanguage == AppLanguage.HINDI) "NCERT पाठ्यक्रम प्रगति" else "NCERT Syllabus Progress", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                // Real-time progress chart
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { syllabusProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.secondary,
                        strokeWidth = 12.dp,
                        trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$syllabusProgressPercent%", fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("$completedChaptersCount / $totalChapters Ch", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ScoreTrendChart(selectedLanguage, attempts)
    }
}

// ==========================================
// 📈 SCORE TREND CHART (COMPOSE CANVAS)
// ==========================================
@Composable
fun ScoreTrendChart(selectedLanguage: AppLanguage, attempts: List<Com.ak.testseries.data.AttemptEntity>) {
    // Take the last 10 attempts
    val recentAttempts = attempts.sortedBy { it.dateTaken }.takeLast(10)
    
    if (recentAttempts.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.ShowChart, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), 
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                BilingualText(
                    englishText = "No test attempts yet",
                    hindiText = "कोई टेस्ट का प्रयास नहीं किया गया",
                    language = selectedLanguage,
                    englishFontSize = 16.sp,
                    hindiFontSize = 16.sp,
                    englishFontWeight = FontWeight.Bold,
                    hindiFontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                BilingualText(
                    englishText = "Attempt a test to see your score progress trend!",
                    hindiText = "प्रगति प्रवृत्ति देखने के लिए टेस्ट शुरू करें!",
                    language = selectedLanguage,
                    englishFontSize = 12.sp,
                    hindiFontSize = 12.sp,
                    englishColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    hindiColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                BilingualText(
                    englishText = "Score Trend (Last 10 Tests)",
                    hindiText = "स्कोर ट्रेंड (पिछले 10 टेस्ट)",
                    language = selectedLanguage,
                    englishFontSize = 16.sp,
                    hindiFontSize = 16.sp,
                    englishFontWeight = FontWeight.Bold,
                    hindiFontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                val primaryColor = MaterialTheme.colorScheme.primary
                
                val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                val strokeColor = MaterialTheme.colorScheme.primary
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                    val width = size.width
                    val height = size.height
                    
                    val xStep = width / (recentAttempts.size - 1).coerceAtLeast(1).toFloat()
                    
                    // Draw grid lines
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = height - (height * (i.toFloat() / gridLines))
                        drawLine(
                            color = gridColor,
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(width, y),
                            strokeWidth = 2f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    }

                    val path = androidx.compose.ui.graphics.Path()
                    val points = mutableListOf<androidx.compose.ui.geometry.Offset>()
                    
                    recentAttempts.forEachIndexed { index, attempt ->
                        val x = if (recentAttempts.size == 1) width / 2f else index * xStep
                        val maxScoreVal = if (attempt.maxScore > 0) attempt.maxScore.toFloat() else 720f
                        val normalizedScore = (attempt.score.toFloat() / maxScoreVal).coerceIn(0f, 1f)
                        val y = height - (normalizedScore * height)
                        points.add(androidx.compose.ui.geometry.Offset(x, y))
                        
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    
                    // Draw the line if multiple points
                    if (recentAttempts.size > 1) {
                        drawPath(
                            path = path,
                            color = primaryColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 6f, 
                                cap = androidx.compose.ui.graphics.StrokeCap.Round, 
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                    
                    // Draw points
                    points.forEach { point ->
                        drawCircle(
                            color = primaryColor,
                            radius = 12f,
                            center = point
                        )
                        drawCircle(
                            color = androidx.compose.ui.graphics.Color.White,
                            radius = 6f,
                            center = point
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUsersAnalyticsContent(viewModel: NEETViewModel) {
    LaunchedEffect(Unit) {
        viewModel.fetchAdminUsers()
    }
    
    val users by viewModel.adminUsers.collectAsStateWithLifecycle()
    val totalUsers = users.size
    val bannedCount = users.count { it.isBanned }
    val totalTestsAttemptedSum = users.sumOf { it.testsAttempted.toIntOrNull() ?: 0 }
    val averagePlatformScore = if (users.isNotEmpty()) {
        (users.sumOf { it.avgScore.toIntOrNull() ?: 0 }.toFloat() / users.size).toInt()
    } else {
        0
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(stringRes("Users & Analytics"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        // Global Analytics Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnalyticsCard(
                title = "Total Users",
                value = totalUsers.toString(),
                modifier = Modifier.weight(1f)
            )
            AnalyticsCard(
                title = "Suspended Users",
                value = bannedCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnalyticsCard(
                title = "Total Tests Attempted",
                value = totalTestsAttemptedSum.toString(),
                modifier = Modifier.weight(1f)
            )
            AnalyticsCard(
                title = "Average Platform Score",
                value = "$averagePlatformScore / 720",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringRes("Student Data"), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (users.isEmpty()) {
            Text(stringRes("No registered users found in Firestore database."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            // Student Data Layout
            users.forEach { student ->
                StudentProfileCard(
                    student = student,
                    onBanToggle = { isBanned ->
                        viewModel.toggleUserBan(student.id, isBanned)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun AnalyticsCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StudentProfileCard(student: StudentData, onBanToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (student.isBanned) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) 
                             else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(student.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (student.isBanned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Text(student.email, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringRes("Tests Attempted"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${student.testsAttempted}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringRes("Average Score"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${student.avgScore}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(if (student.isBanned) "User is Suspended" else "Active User", fontSize = 11.sp, modifier = Modifier.weight(1f), color = if (student.isBanned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(
                    checked = student.isBanned,
                    onCheckedChange = onBanToggle,
                    modifier = Modifier,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.error,
                        checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                    )
                )
            }
        }
    }
}
@Composable
fun PreTestModals(
    step: Int,
    test: Com.ak.testseries.data.TestEntity,
    language: AppLanguage,
    targetExam: String = "NEET",
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    var localLanguage by remember(language) { mutableStateOf(language) }
    val isHindi = localLanguage == AppLanguage.HINDI

    if (step == 1) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onCancel,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isHindi) "टेस्ट दिशानिर्देश" else "Test Guidelines", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { localLanguage = if (isHindi) AppLanguage.ENGLISH else AppLanguage.HINDI }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Translate, contentDescription = "Translate")
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(if (isHindi) "पाठ्यक्रम" else "Syllabus", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(test.description, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val duration = if (test.durationMinutes > 0) test.durationMinutes else test.questionsCount
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Timer, contentDescription = "Timer", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isHindi) "परीक्षा अवधि: $duration मिनट" else "Test Duration: $duration Minutes", fontWeight = FontWeight.Bold)
                    }
                    Text(if (isHindi) "परीक्षा के नियम और शर्तें" else "Exam Rules & Conditions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text(
                        if (isHindi) "1. स्थिर इंटरनेट कनेक्शन सुनिश्चित करें।\n2. टाइमर समाप्त होने पर परीक्षा स्वतः सबमिट हो जाएगी।" else "1. Ensure a stable internet connection.\n2. The exam will submit automatically when the timer ends.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val (posMark, negMark) = Com.ak.testseries.exam.ExamScoringEngine.getMarkingScheme(test.targetExam)
                    val formattedPos = Com.ak.testseries.exam.ExamScoringEngine.formatMark(posMark)
                    val formattedNeg = Com.ak.testseries.exam.ExamScoringEngine.formatMark(negMark)
                    val markingSummary = if (negMark <= 0.0) {
                        if (isHindi) "${test.targetExam} मोड: +${formattedPos} अंक (सही), 0 अंक (कोई नेगेटिव नहीं), 0 अंक (छोड़ा गया)"
                        else "${test.targetExam} Mode: +${formattedPos} marks (correct), 0 marks (no penalty), 0 mark (skipped)"
                    } else {
                        if (isHindi) "${test.targetExam} मोड: +${formattedPos} अंक (सही), -${formattedNeg} अंक (गलत), 0 अंक (छोड़ा गया)"
                        else "${test.targetExam} Mode: +${formattedPos} marks (correct), -${formattedNeg} mark (incorrect), 0 mark (skipped)"
                    }
                    
                    Text(if (isHindi) "मार्किंग स्कीम" else "Marking Scheme", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        markingSummary,
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (isHindi) "प्रश्नों की स्थिति के रंग" else "Question Status Colors", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val indicatorSize = Modifier.size(20.dp)
                    val rowMod = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    
                    val correctLegendText = if (isHindi) "सही उत्तर (+$formattedPos अंक)" else "Correct Answer (+$formattedPos Marks)"
                    val incorrectLegendText = if (negMark <= 0.0) {
                        if (isHindi) "गलत उत्तर (0 अंक)" else "Incorrect Answer (0 Marks)"
                    } else {
                        if (isHindi) "गलत उत्तर (-$formattedNeg अंक)" else "Incorrect Answer (-$formattedNeg Marks)"
                    }

                    Row(modifier = rowMod, verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = indicatorSize.background(Color(0xFF4CAF50), CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(correctLegendText, fontSize = 12.sp)
                    }
                    Row(modifier = rowMod, verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = indicatorSize.background(Color(0xFFF44336), CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(incorrectLegendText, fontSize = 12.sp)
                    }
                    Row(modifier = rowMod, verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = indicatorSize.background(Color(0xFFFF9800), CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isHindi) "समीक्षा के लिए चिह्नित (0 अंक)" else "Marked for Review (0 Marks)", fontSize = 12.sp)
                    }
                    Row(modifier = rowMod, verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = indicatorSize.background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isHindi) "छोड़ा गया (0 अंक)" else "Skipped (0 Marks)", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = onNext) {
                    Text(if (isHindi) "मैं सहमत हूँ, आगे बढ़ें" else "I Agree, Next")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onCancel) {
                    Text(if (isHindi) "रद्द करें" else stringRes("Cancel"))
                }
            }
        )
    }
}

@Composable
fun ProfileEditDialog(
    currentName: String,
    currentEmail: String,
    currentPhone: String = "",
    currentState: String,
    currentCity: String,
    currentTargetExam: String,
    currentProfileImageUri: String,
    selectedLanguage: AppLanguage = AppLanguage.ENGLISH,
    onSave: (name: String, email: String, phone: String, state: String, city: String, profileImageUri: String, targetExam: String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }
    var phone by remember { mutableStateOf(currentPhone) }
    var stateName by remember { mutableStateOf(currentState) }
    var cityName by remember { mutableStateOf(currentCity) }
    var targetExam by remember { mutableStateOf(currentTargetExam) }
    var profileImageUri by remember { mutableStateOf(currentProfileImageUri) }
    var stateDropdownExpanded by remember { mutableStateOf(false) }
    var examDropdownExpanded by remember { mutableStateOf(false) }

    val isHindi = selectedLanguage == AppLanguage.HINDI

    val indianStates = listOf(
        "Uttar Pradesh", "Bihar", "Rajasthan (Kota)", "Delhi NCR", "Madhya Pradesh",
        "Haryana", "Punjab", "Maharashtra", "West Bengal", "Gujarat", "Karnataka",
        "Kerala", "Tamil Nadu", "Andhra Pradesh", "Telangana", "Other"
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { 
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val file = java.io.File(context.filesDir, "profile_pic_${System.currentTimeMillis()}.jpg")
                val outputStream = java.io.FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                profileImageUri = android.net.Uri.fromFile(file).toString()
            } catch (e: Exception) {
                e.printStackTrace()
                profileImageUri = it.toString()
            }
        }
    }

    Dialog(
        onDismissRequest = onCancel,
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
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onCancel) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isHindi) "प्रोफ़ाइल संपादित करें" else "Edit Profile",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            Button(
                                onClick = { onSave(name, email, phone, stateName, cityName, profileImageUri, targetExam) },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isHindi) "सहेजें" else "Save",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Hero Header Banner with Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        // Creative gradient background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.05f))
                            )
                        }
                        
                        // Floating Avatar Container
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .size(110.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = CircleShape,
                                border = BorderStroke(4.dp, MaterialTheme.colorScheme.background),
                                shadowElevation = 6.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { launcher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (profileImageUri.isNotEmpty()) {
                                        coil.compose.AsyncImage(
                                            model = profileImageUri,
                                            contentDescription = "Profile Photo",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = name.take(1).uppercase().ifEmpty { "U" },
                                                fontSize = 42.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Edit Badge
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                    .clickable { launcher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddAPhoto,
                                    contentDescription = "Change Image",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Input Form Fields with Beautiful Grouped Cards
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Personal Information Card
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isHindi) "व्यक्तिगत जानकारी" else "Personal Details",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text(if (isHindi) "पूरा नाम" else "Full Name") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text(if (isHindi) "ईमेल पता" else "Email Address") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Email,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text(if (isHindi) "फ़ोन नंबर" else "Phone Number") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Phone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }

                        // 2. Location Details Card
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Map,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isHindi) "स्थान एवं क्षेत्र" else "Location Details",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))


                                // Target Exam Dropdown
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { examDropdownExpanded = true }
                                    ) {
                                        OutlinedTextField(
                                            value = targetExam,
                                            onValueChange = {},
                                            readOnly = true,
                                            enabled = false,
                                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            label = { Text(if (isHindi) "लक्ष्य परीक्षा" else stringRes("Target Exam")) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Assignment,
                                                    contentDescription = null
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.ArrowDropDown,
                                                    contentDescription = "Select Exam"
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = examDropdownExpanded,
                                        onDismissRequest = { examDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    ) {
                                        val allExamsDropdownList = remember {
                                            Com.ak.testseries.exam.UniversalExamRegistry.getAllExams().map { it.shortName }.distinct()
                                        }
                                        allExamsDropdownList.forEach { exam ->
                                            DropdownMenuItem(
                                                text = { Text(exam, fontWeight = FontWeight.Medium) },
                                                onClick = {
                                                    targetExam = exam
                                                    examDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // State Selector Dropdown
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { stateDropdownExpanded = true }
                                    ) {
                                        OutlinedTextField(
                                            value = stateName,
                                            onValueChange = {},
                                            readOnly = true,
                                            enabled = false,
                                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            label = { Text(if (isHindi) "राज्य चुनें" else "Select State") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.HomeWork,
                                                    contentDescription = null
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.ArrowDropDown,
                                                    contentDescription = "Select State"
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = stateDropdownExpanded,
                                        onDismissRequest = { stateDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    ) {
                                        indianStates.forEach { state ->
                                            DropdownMenuItem(
                                                text = { Text(state, fontWeight = FontWeight.Medium) },
                                                onClick = {
                                                    stateName = state
                                                    stateDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = cityName,
                                    onValueChange = { cityName = it },
                                    label = { Text(if (isHindi) "शहर / स्थान" else "City / Town") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Place,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }

                        // 3. Privacy & Security Shield Footer
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Secure",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isHindi) 
                                        "आपका डेटा आपके डिवाइस पर सुरक्षित रूप से संग्रहीत किया जाता है।" 
                                        else "Your profile details are securely saved on your device.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LatestTestsCarousel(tests: List<Com.ak.testseries.data.TestEntity>, viewModel: Com.ak.testseries.viewmodel.NEETViewModel) {
    val latestTests = tests.sortedByDescending { it.timestamp }.take(3)
    if (latestTests.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(stringRes("Latest Tests"), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { latestTests.size })
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val test = latestTests[page]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(test.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
                        Text("${test.subject} • ${test.questionsCount} Qs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.initiateTest(test) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringRes("Let's Start"))
                        }
                    }
                    // Image Placeholder
                    if (!test.imageUrl.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = test.imageUrl,
                            contentDescription = "Test Image",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }
        }
        
        // Dots Indicator
        Row(
            Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(latestTests.size) { iteration ->
                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.LightGray
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(8.dp)
                )
            }
        }
    }
}


@Composable
fun UserNotificationsScreen(viewModel: NEETViewModel) {
    val notifications by viewModel.userNotifications.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    
    val filteredNotifications = notifications.filter { notif ->
        if (notif.targetEmail != null && notif.targetEmail != userProfile.email) return@filter false
        if (notif.targetEmail == null && notif.targetGroup != "All" && notif.targetGroup != "Specific" && notif.targetGroup != userProfile.targetExam && notif.targetGroup != userProfile.classLevel) return@filter false
        true
    }.sortedWith(compareByDescending<UserNotification> { it.isPinned }.thenByDescending { it.timestamp })

    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text(stringRes("Notifications"), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToScreen(AppScreen.DASHBOARD) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {
            if (filteredNotifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringRes("No notifications yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredNotifications.size) { index ->
                        val notification = filteredNotifications[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = notification.linkUrl != null) {
                                    notification.linkUrl?.let { url ->
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = if (notification.isPinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (notification.isPinned) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PushPin, contentDescription = "Pinned", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pinned", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                if (notification.imageUrl != null) {
                                    coil.compose.AsyncImage(
                                        model = notification.imageUrl,
                                        contentDescription = "Notification Image",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                Text(notification.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(notification.message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val date = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(notification.timestamp))
                                Text(date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminNotificationsContent(viewModel: NEETViewModel) {
    var notificationTitle by remember { mutableStateOf("") }
    var notificationMessage by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    var sendVia by remember { mutableStateOf("Push Notification") }
    var targetGroup by remember { mutableStateOf("All") }
    var targetEmail by remember { mutableStateOf("") }
    var isPinned by remember { mutableStateOf(false) }
    
    var editingNotifId by remember { mutableStateOf<String?>(null) }
    var showSuccessSnackbar by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LaunchedEffect(Unit) {
        viewModel.fetchAllUsers()
    }
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    
    val dynamicClasses = allUsers.mapNotNull { it["classLevel"] as? String }.filter { it.isNotBlank() }.distinct()
    val dynamicExams = allUsers.mapNotNull { it["targetExam"] as? String }.filter { it.isNotBlank() }.distinct()
    val allTargetGroups = (listOf("All", "Class 11", "Class 12", "Dropper", "NEET", "JEE") + dynamicClasses + dynamicExams).distinct()
    
    val notifications by viewModel.userNotifications.collectAsStateWithLifecycle()

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            scope.launch {
                isUploading = true
                val uploadedUrl = Com.ak.testseries.utils.FileUploaderUtil.uploadImage(context, it)
                if (uploadedUrl != null) {
                    imageUrl = uploadedUrl
                }
                isUploading = false
            }
        }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 60.dp)) {
        Text(stringRes("Announcements & Notifications"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(if(editingNotifId != null) "Edit Announcement" else stringRes("Broadcast New Announcement"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(stringRes("Send a push notification and update the announcements section."), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notificationTitle,
                    onValueChange = { notificationTitle = it },
                    label = { Text(stringRes("Announcement Title"), fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notificationMessage,
                    onValueChange = { notificationMessage = it },
                    label = { Text(stringRes("Announcement Message"), fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text(stringRes("Image URL (Optional)"), fontSize = 10.sp) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.height(52.dp),
                        enabled = !isUploading,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Upload", modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    label = { Text(stringRes("Link URL (Optional)"), fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Target Group (Filters)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var expandedTargetGroup by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expandedTargetGroup = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(targetGroup, fontSize = 12.sp)
                    }
                    DropdownMenu(expanded = expandedTargetGroup, onDismissRequest = { expandedTargetGroup = false }) {
                        allTargetGroups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group, fontSize = 12.sp) },
                                onClick = { targetGroup = group; expandedTargetGroup = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPinned, onCheckedChange = { isPinned = it })
                    Text("Pin this message to the top", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingNotifId != null) {
                        OutlinedButton(
                            onClick = {
                                editingNotifId = null
                                notificationTitle = ""
                                notificationMessage = ""
                                imageUrl = ""
                                linkUrl = ""
                                isPinned = false
                                targetGroup = "All"
                            },
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("Cancel Edit", fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                if (editingNotifId != null) {
                                    viewModel.updateNotification(
                                        id = editingNotifId!!,
                                        title = notificationTitle,
                                        message = notificationMessage,
                                        imageUrl = imageUrl.takeIf { it.isNotBlank() },
                                        linkUrl = linkUrl.takeIf { it.isNotBlank() },
                                        targetGroup = targetGroup,
                                        targetEmail = null,
                                        isPinned = isPinned
                                    )
                                    editingNotifId = null
                                } else {
                                    viewModel.addNotification(
                                        title = notificationTitle,
                                        message = notificationMessage,
                                        imageUrl = imageUrl.takeIf { it.isNotBlank() },
                                        linkUrl = linkUrl.takeIf { it.isNotBlank() },
                                        targetGroup = targetGroup,
                                        targetEmail = null,
                                        isPinned = isPinned
                                    )
                                }
                                showSuccessSnackbar = true
                                notificationTitle = ""
                                notificationMessage = ""
                                imageUrl = ""
                                linkUrl = ""
                                isPinned = false
                                targetGroup = "All"
                            }
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Send", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if(editingNotifId != null) "Update" else stringRes("Send Notification"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Send Direct Message to User", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                var selectedUserEmail by remember { mutableStateOf("Select a User") }
                var expandedUsers by remember { mutableStateOf(false) }
                
                Text("Select User", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    OutlinedButton(onClick = { expandedUsers = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedUserEmail, fontSize = 12.sp, maxLines = 1)
                    }
                    DropdownMenu(expanded = expandedUsers, onDismissRequest = { expandedUsers = false }) {
                        allUsers.forEach { user ->
                            val userEmail = user["email"] as? String ?: "No Email"
                            val userName = user["name"] as? String ?: "Unknown"
                            DropdownMenuItem(
                                text = { Text("$userName ($userEmail)", fontSize = 12.sp) },
                                onClick = { 
                                    selectedUserEmail = userEmail
                                    expandedUsers = false 
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                var directMessage by remember { mutableStateOf("") }
                var directTitle by remember { mutableStateOf("") }
                
                OutlinedTextField(
                    value = directTitle,
                    onValueChange = { directTitle = it },
                    label = { Text("Direct Message Title", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = directMessage,
                    onValueChange = { directMessage = it },
                    label = { Text("Message Content", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    maxLines = 4
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (selectedUserEmail != "Select a User" && directMessage.isNotBlank()) {
                            viewModel.addNotification(
                                title = directTitle,
                                message = directMessage,
                                targetGroup = "Specific",
                                targetEmail = selectedUserEmail,
                                isPinned = true
                            )
                            directMessage = ""
                            directTitle = ""
                            selectedUserEmail = "Select a User"
                            android.widget.Toast.makeText(context, "Direct message sent!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Please select a user and enter a message.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Direct Message", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Manage Previous Notifications", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        
        notifications.forEach { notif ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(notif.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row {
                            IconButton(onClick = { 
                                notificationTitle = notif.title
                                notificationMessage = notif.message
                                imageUrl = notif.imageUrl ?: ""
                                linkUrl = notif.linkUrl ?: ""
                                isPinned = notif.isPinned
                                targetGroup = notif.targetGroup
                                editingNotifId = notif.id
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.deleteNotification(notif.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(notif.message, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Target: ${if(notif.targetEmail != null) notif.targetEmail else notif.targetGroup} | Pinned: ${notif.isPinned}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
