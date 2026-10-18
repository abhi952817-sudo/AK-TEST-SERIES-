package Com.ak.testseries.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import Com.ak.testseries.stringRes
import Com.ak.testseries.viewmodel.AiGenerationState
import Com.ak.testseries.viewmodel.AppLanguage
import Com.ak.testseries.viewmodel.AppScreen
import Com.ak.testseries.viewmodel.NEETViewModel
import Com.ak.testseries.viewmodel.PdfAnalysisState

val examsList = listOf(
    "NEET UG", "JEE Main", "CUET UG", 
    "SSC CGL", "SSC CHSL", "SSC MTS", "SSC CPO", "SSC GD Constable",
    "SBI PO", "SBI Clerk", "IBPS PO", "IBPS Clerk", 
    "RRB NTPC", "RRB Group D", "RRB ALP", "RRB Technician", "RRB JE",
    "CTET", "UGC NET", "CAT"
)

val subjectsList = listOf(
    "Physics", "Chemistry", "Biology (Botany & Zoology)", "Mathematics", 
    "Quantitative Aptitude", "Reasoning", "English Comprehension", 
    "General Awareness / GK", "General Science", 
    "History", "Geography", "Polity", "Child Development & Pedagogy (CDP)"
)

// Brand Color Palette based exactly on React code
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

val Violet600 = Color(0xFF7C3AED)
val Violet500 = Color(0xFF8B5CF6)
val Violet400 = Color(0xFFA78BFA)
val Violet300 = Color(0xFFC4B5FD)
val Violet200 = Color(0xFFDDD6FE)
val Violet100 = Color(0xFFEDE9FE)
val Violet50 = Color(0xFFF5F3FF)

val Fuchsia600 = Color(0xFFC026D3)
val Fuchsia500 = Color(0xFFD946EF)
val Fuchsia400 = Color(0xFFE879F9)
val Fuchsia100 = Color(0xFFFAE8FF)
val Fuchsia50 = Color(0xFFFDF4FF)

val Emerald400 = Color(0xFF34D399)
val Amber50 = Color(0xFFFFFBEB)
val Amber100 = Color(0xFFFEF3C7)
val Amber200 = Color(0xFFFDE68A)
val Amber800 = Color(0xFF92400E)
val Amber900 = Color(0xFF78350F)

val PublicGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF7C3AED), Color(0xFF9333EA), Color(0xFFC026D3))
)
val PurpleFuchsiaGradient = Brush.horizontalGradient(
    colors = listOf(Violet500, Fuchsia500)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutocompleteInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    placeholder: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredOptions = remember(value, options) {
        if (value.isBlank()) options
        else options.filter { it.contains(value, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { 
                Text(
                    text = label, 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold,
                    color = Violet600
                ) 
            },
            placeholder = { 
                Text(
                    placeholder, 
                    fontSize = 14.sp, 
                    color = Slate300
                ) 
            },
            leadingIcon = if (icon != null) {
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (expanded) Violet500 else Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else null,
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (expanded) Violet500 else Slate400,
                    modifier = Modifier.size(20.dp)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Violet500,
                unfocusedBorderColor = Slate200,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                focusedTextColor = Slate900,
                unfocusedTextColor = Slate800,
                cursorColor = Violet600
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        if (filteredOptions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color.White)
                    .heightIn(max = 240.dp)
            ) {
                filteredOptions.forEach { option ->
                    val isSelected = option.equals(value, ignoreCase = true)
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Violet700Custom() else Slate700
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Violet500,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = Slate800
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

private fun Violet700Custom() = Color(0xFF6D28D9)

@Composable
fun UploadAiTabScreen(viewModel: NEETViewModel) {
    val aiState by viewModel.aiGenState.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    
    val genName by viewModel.genTestName.collectAsStateWithLifecycle()
    val genDesc by viewModel.genDescription.collectAsStateWithLifecycle()
    val genInst by viewModel.genInstitute.collectAsStateWithLifecycle()
    val genBatchStr by viewModel.genBatch.collectAsStateWithLifecycle()
    val genSubjectStr by viewModel.genSubject.collectAsStateWithLifecycle()
    val genTargetExamStr by viewModel.genTargetExam.collectAsStateWithLifecycle()
    val genQuestionsCount by viewModel.genQuestionsCount.collectAsStateWithLifecycle()

    val dailyLimitUsed by viewModel.dailyUploadLimitUsed.collectAsStateWithLifecycle()
    val dailyLimit by viewModel.dailyFreeTestLimit.collectAsStateWithLifecycle()
    val dailyCount by viewModel.dailyGenerationsCount.collectAsStateWithLifecycle()
    val pdfAnalysisState by viewModel.pdfAnalysisState.collectAsStateWithLifecycle()
    val isPublic by viewModel.genIsPublic.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    LaunchedEffect(userProfile.targetExam) {
        if (viewModel.genTargetExam.value.isBlank() || viewModel.genTargetExam.value == "NEET") {
            if (userProfile.targetExam.isNotBlank()) {
                viewModel.genTargetExam.value = userProfile.targetExam
            }
        }
    }

    val genIsLive by viewModel.genIsLive.collectAsStateWithLifecycle()
    val genLiveScheduledTime by viewModel.genLiveScheduledTime.collectAsStateWithLifecycle()
    val genLiveDurationMinutes by viewModel.genLiveDurationMinutes.collectAsStateWithLifecycle()

    var selectedMockFile by remember { mutableStateOf<String?>(null) }
    var pendingPdfUri by remember { mutableStateOf<Uri?>(null) }
    var showCopyrightDialog by remember { mutableStateOf(false) }
    var isCopyrightChecked by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Launcher for Step 1 direct smart auto-routing
    val step1AutoRoutePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingPdfUri = it
            isCopyrightChecked = false
            showCopyrightDialog = true
        }
    }

    // Tracks whether user is on Step 1 (null) or Step 2 ("private" / "public")
    var selectedTestType by remember {
        mutableStateOf<String?>(null)
    }

    val autoRoutedWorkflow by viewModel.autoRoutedWorkflow.collectAsStateWithLifecycle()
    val autoRoutingExplanation by viewModel.autoRoutingExplanation.collectAsStateWithLifecycle()

    // Sync selectedTestType when autoRoutedWorkflow updates
    LaunchedEffect(autoRoutedWorkflow) {
        autoRoutedWorkflow?.let { wf ->
            selectedTestType = wf
        }
    }

    // Intercept back button if in step 2
    BackHandler(enabled = selectedTestType != null) {
        selectedTestType = null
    }

    // Auto-fill Form Fields & Automatically Route Workflow when AI finishes analyzing the test paper
    LaunchedEffect(pdfAnalysisState) {
        if (pdfAnalysisState is PdfAnalysisState.Success) {
            val result = (pdfAnalysisState as PdfAnalysisState.Success).result
            viewModel.genTargetExam.value = result.targetExam
            viewModel.genTestName.value = result.title
            viewModel.genInstitute.value = result.institute
            viewModel.genSubject.value = result.subject
            viewModel.genLanguage.value = result.language
            viewModel.genType.value = result.testType
            viewModel.genQuestionsCount.value = result.questionsCount
            viewModel.genLiveDurationMinutes.value = result.timerMinutes
            viewModel.genBatch.value = result.classLevel
            viewModel.genDescription.value = "Syllabus / Chapter: ${result.chapter}. Extracted by AI (${result.confidence}% confidence)."

            // Automatically route workflow based on detected questions vs raw text:
            // Detected questions -> Private workflow
            // Raw text / notes -> Public workflow
            val targetWorkflow = result.autoRoutedWorkflow
            selectedTestType = targetWorkflow
            val isTargetPublic = (targetWorkflow == "public")
            viewModel.genIsPublic.value = isTargetPublic
            viewModel.autoShareToCommunity.value = isTargetPublic
        }
    }

    // Dynamically derive subjects list and exam specifications for the currently selected target exam
    val currentExamConfig = remember(genTargetExamStr) {
        Com.ak.testseries.exam.UniversalExamRegistry.findExamByNameOrShortName(genTargetExamStr)
    }
    val dynamicSubjectsList = remember(genTargetExamStr) {
        val list = Com.ak.testseries.exam.UniversalExamRegistry.getSubjectsForExamName(genTargetExamStr)
        if (list.isNotEmpty()) list else subjectsList
    }

    // --- NON-DISMISSABLE COPYRIGHT DISCLAIMER DIALOG ---
    if (showCopyrightDialog) {
        Dialog(
            onDismissRequest = { /* Non-dismissable */ },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Violet100, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = Violet600,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = if (selectedLanguage == AppLanguage.HINDI) "कॉपीराइट और बौद्धिक संपदा अस्वीकरण" else "Copyright & IP Disclaimer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = if (selectedLanguage == AppLanguage.HINDI)
                            "इस दस्तावेज़ या PDF को संसाधित करने से पहले, कृपया पुष्टि करें कि आपके पास इस शैक्षिक सामग्री के उपयोग, प्रसंस्करण और विश्लेषण का कानूनी अधिकार या कॉपीराइट स्वामित्व है। आप अनधिकृत या कॉपीराइट सामग्री को बिना अनुमति के वितरित नहीं करने के लिए सहमत हैं।"
                        else
                            "Before processing this document or PDF, please confirm that you own the copyright or have explicit legal authorization to process, analyze, and use this educational material. You agree not to distribute copyrighted content without proper permission.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCopyrightChecked = !isCopyrightChecked }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Checkbox(
                            checked = isCopyrightChecked,
                            onCheckedChange = { isCopyrightChecked = it }
                        )
                        Text(
                            text = if (selectedLanguage == AppLanguage.HINDI)
                                "मैं पुष्टि करता हूँ कि मेरे पास इस PDF के उपयोग का कानूनी अधिकार है और मैं नियमों से सहमत हूँ।"
                            else
                                "I confirm that I own the copyright or have explicit permission to process this document and agree to terms.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                showCopyrightDialog = false
                                pendingPdfUri = null
                                isCopyrightChecked = false
                            }
                        ) {
                            Text(if (selectedLanguage == AppLanguage.HINDI) "रद्द करें" else "Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                pendingPdfUri?.let { uri ->
                                    viewModel.selectedPdfUri.value = uri
                                    selectedMockFile = "Uploaded_Document.pdf"
                                    showCopyrightDialog = false
                                    pendingPdfUri = null
                                    isCopyrightChecked = false
                                    viewModel.analyzePdfWithAI(context, uri)
                                }
                            },
                            enabled = isCopyrightChecked,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Violet600,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(if (selectedLanguage == AppLanguage.HINDI) "PDF संसाधित करें" else "Proceed & Process PDF")
                        }
                    }
                }
            }
        }
    }

    // --- LIVE PROGRESS & RECOVERY MODAL OVERLAY ---
    if (aiState !is AiGenerationState.Idle && aiState !is AiGenerationState.Success) {
        Dialog(onDismissRequest = {}) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (aiState) {
                        is AiGenerationState.ReadingPdf,
                        is AiGenerationState.ExtractingQuestions,
                        is AiGenerationState.ExtractingOptions,
                        is AiGenerationState.ExtractingAnswers -> {
                            LogoLoader()
                            Spacer(modifier = Modifier.height(16.dp))
                            val msg = viewModel.aiGenStatusMessage.collectAsStateWithLifecycle().value
                            Text(msg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center, color = Slate800)
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), 
                                color = Violet600,
                                trackColor = Violet100
                            )
                        }
                        is AiGenerationState.Generating -> {
                            val genState = aiState as AiGenerationState.Generating
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Generating", tint = Violet600, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringRes("Extracting questions..."), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Batch ${genState.batchIndex} of ${genState.totalBatches} (${genState.progressPercent.toInt()}% completed)", fontSize = 13.sp, color = Slate500)
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { genState.progressPercent / 100f }, 
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), 
                                color = Violet600,
                                trackColor = Violet100
                            )
                        }
                        is AiGenerationState.PausedError -> {
                            val errState = aiState as AiGenerationState.PausedError
                            Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringRes("Task Paused"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Error at batch ${errState.batchIndex}:\n${errState.message}", fontSize = 13.sp, color = Slate600, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                OutlinedButton(onClick = { viewModel.generateTestFromPdf(resume = false) }) {
                                    Text(stringRes("Start Over"))
                                }
                                Button(
                                    onClick = { viewModel.generateTestFromPdf(resume = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Violet600)
                                ) {
                                    Text(stringRes("Resume Process"))
                                }
                            }
                        }
                        is AiGenerationState.Error -> {
                            val errState = aiState as AiGenerationState.Error
                            Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringRes("Failed"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(errState.message, fontSize = 13.sp, color = Slate600, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (errState.message.contains("sign in", ignoreCase = true)) {
                                    Button(
                                        onClick = {
                                            viewModel.resetAiGenState()
                                            viewModel.navigateToScreen(AppScreen.LOGIN)
                                        },
                                        modifier = Modifier.padding(end = 8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Violet600)
                                    ) {
                                        Text(stringRes("Sign In"))
                                    }
                                }
                                if (errState.message.contains("limit", ignoreCase = true) || errState.message.contains("ad", ignoreCase = true)) {
                                    Button(
                                        onClick = {
                                            viewModel.showRewardedAdForExtraUpload()
                                        },
                                        modifier = Modifier.padding(end = 8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Violet600)
                                    ) {
                                        Text(if (selectedLanguage == AppLanguage.HINDI) "विज्ञापन देखें (+1 टेस्ट)" else "Watch Ad (+1 Test)")
                                    }
                                }
                                Button(
                                    onClick = {
                                        viewModel.genIsPublic.value = false
                                        viewModel.resetAiGenState()
                                        viewModel.generateTestFromPdf(resume = false)
                                    },
                                    modifier = Modifier.padding(end = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Violet600)
                                ) {
                                    Text(if (selectedLanguage == AppLanguage.HINDI) "निजी रूप से जारी रखें" else "Continue Offline")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.resetAiGenState() }
                                ) {
                                    Text(stringRes("Close"))
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    // Outer Background Box with ambient blurred glows from React template
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        // Decorative Ambient Blurs
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset(x = (-60).dp, y = (-50).dp)
                .background(Violet400.copy(alpha = 0.22f), CircleShape)
                .blur(50.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(260.dp)
                .offset(x = 60.dp, y = 140.dp)
                .background(Fuchsia400.copy(alpha = 0.16f), CircleShape)
                .blur(50.dp)
        )

        // =========================================================
        // STEP 1: TYPE SELECTION SCREEN (Creator Studio)
        // =========================================================
        if (selectedTestType == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    
                    // Creator Studio Pill Tag
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Violet100,
                        border = BorderStroke(1.dp, Violet200)
                    ) {
                        Text(
                            text = "CREATOR STUDIO",
                            color = Violet700Custom(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (selectedLanguage == AppLanguage.HINDI) "नया टेस्ट बनाएं" else "Create New Test",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedLanguage == AppLanguage.HINDI) 
                            "आगे बढ़ने के लिए अपना पसंदीदा मोड चुनें।" 
                        else 
                            "Select your preferred visibility mode to proceed.",
                        fontSize = 15.sp,
                        color = Slate500,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // --- 🔒 CARD 1: PREMIUM PRIVATE VAULT ---
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTestType = "private"
                                viewModel.genIsPublic.value = false
                                viewModel.autoShareToCommunity.value = false
                            },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        border = BorderStroke(1.dp, Slate700.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Subtle radial glow in top right
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(130.dp)
                                    .offset(x = 20.dp, y = (-20).dp)
                                    .background(Slate800, CircleShape)
                                    .blur(30.dp)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Lock Icon in styled container
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Slate800.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                            .border(1.dp, Slate700.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Private Vault",
                                            tint = Slate300,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (selectedLanguage == AppLanguage.HINDI) "प्राइवेट वॉल्ट (Private Vault)" else "Private Vault",
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            letterSpacing = 0.3.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (selectedLanguage == AppLanguage.HINDI) 
                                                "पूरी तरह आपके व्यक्तिगत अभ्यास के लिए। पीडीएफ से टेक्स्ट और चित्र (Images/Diagrams) दोनों निकलेंगे।" 
                                            else 
                                                "Strictly for self-practice. Auto-extracts text and diagrams/images locally.",
                                            fontSize = 13.sp,
                                            color = Slate400,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Slate800.copy(alpha = 0.8f),
                                                border = BorderStroke(1.dp, Slate700.copy(alpha = 0.5f))
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(Emerald400, CircleShape)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = "100% OFFLINE",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Slate300,
                                                        letterSpacing = 0.8.sp
                                                    )
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF0369A1).copy(alpha = 0.3f),
                                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "TEXT + DIAGRAMS",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF7DD3FC),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = {
                                        selectedTestType = "private"
                                        viewModel.genIsPublic.value = false
                                        viewModel.autoShareToCommunity.value = false
                                        step1AutoRoutePicker.launch("application/pdf")
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (selectedLanguage == AppLanguage.HINDI) "पीडीएफ अपलोड करें (टेक्स्ट + चित्र)" else "Upload PDF (Text & Images)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 🌐 CARD 2: PREMIUM PUBLIC COMMUNITY ---
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTestType = "public"
                                viewModel.genIsPublic.value = true
                                viewModel.autoShareToCommunity.value = true
                            },
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PublicGradient)
                        ) {
                            // Top-right glass flare
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(150.dp)
                                    .offset(x = 30.dp, y = (-30).dp)
                                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                                    .blur(30.dp)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Globe Icon with Glass container
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Public,
                                            contentDescription = "Public Community",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (selectedLanguage == AppLanguage.HINDI) "पब्लिक कम्युनिटी (Public Community)" else "Public Community",
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            letterSpacing = 0.3.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (selectedLanguage == AppLanguage.HINDI) 
                                                "हजारों छात्रों के साथ प्रकाशित व साझा करें। टेक्स्ट और चित्र दोनों एक्सट्रैक्ट होंगे।" 
                                            else 
                                                "Publish and share with thousands of aspirants. Extracts text and diagrams.",
                                            fontSize = 13.sp,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.White.copy(alpha = 0.2f),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = Color(0xFFFDE047),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = "EARN BADGES",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        letterSpacing = 0.8.sp
                                                    )
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.White.copy(alpha = 0.2f),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    text = "TEXT + DIAGRAMS",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = {
                                        selectedTestType = "public"
                                        viewModel.genIsPublic.value = true
                                        viewModel.autoShareToCommunity.value = true
                                        step1AutoRoutePicker.launch("application/pdf")
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Violet700Custom()
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            tint = Violet700Custom(),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (selectedLanguage == AppLanguage.HINDI) "पीडीएफ अपलोड करें (टेक्स्ट + चित्र)" else "Upload PDF (Text & Images)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Violet700Custom()
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        } else {
            // =========================================================
            // STEP 2: TEST DETAILS & UPLOAD FORM
            // =========================================================
            val selectedPdfUri by viewModel.selectedPdfUri.collectAsStateWithLifecycle()
            val pdfPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let {
                    pendingPdfUri = it
                    isCopyrightChecked = false
                    showCopyrightDialog = true
                }
            }

            val isPublicMode = selectedTestType == "public"

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header & Styled Back Button
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color.White.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.clickable { selectedTestType = null }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Slate600,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "BACK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate600,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Test Generator ",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate900,
                            letterSpacing = (-0.5).sp
                        )
                        // Gradient AI Pill / Text
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(PurpleFuchsiaGradient, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "AI",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Text(
                        text = if (selectedLanguage == AppLanguage.HINDI) 
                            "प्रश्नों को स्वचालित रूप से निकालने के लिए पीडीएफ अपलोड करें।" 
                        else 
                            "Upload PDF to auto-extract questions.",
                        fontSize = 13.sp,
                        color = Slate500,
                        fontWeight = FontWeight.Medium
                    )

                    // --- ⚡ AUTOMATIC WORKFLOW ROUTING BANNER ---
                    val isCurrentlyPrivate = selectedTestType == "private" || !isPublic
                    val bannerBorderColor = if (isCurrentlyPrivate) Color(0xFF334155) else Color(0xFFFDE68A)
                    val bannerContainerColor = if (isCurrentlyPrivate) Color(0xFF0F172A) else Color(0xFFFFFBEB)
                    val bannerAccentColor = if (isCurrentlyPrivate) Color(0xFF38BDF8) else Color(0xFFD97706)

                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = bannerContainerColor,
                        border = BorderStroke(1.2.dp, bannerBorderColor)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(bannerAccentColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrentlyPrivate) Icons.Default.Lock else Icons.Default.Public,
                                            contentDescription = null,
                                            tint = bannerAccentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isCurrentlyPrivate) "AUTO-ROUTED: PRIVATE WORKFLOW" else "AUTO-ROUTED: PUBLIC WORKFLOW",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp,
                                                color = bannerAccentColor,
                                                letterSpacing = 0.5.sp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = bannerAccentColor.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "AUTO-ROUTED",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = bannerAccentColor,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = autoRoutingExplanation ?: if (isCurrentlyPrivate) 
                                                "PDF contains detected questions (MCQs). Automatically routed to Private Vault for self-practice."
                                            else 
                                                "PDF contains raw text/study notes without questions. Automatically routed to Public Community.",
                                            fontSize = 11.sp,
                                            color = if (isCurrentlyPrivate) Color(0xFF94A3B8) else Color(0xFF92400E),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isCurrentlyPrivate) "Want to share with community?" else "Prefer to keep it confidential?",
                                    fontSize = 10.sp,
                                    color = if (isCurrentlyPrivate) Color(0xFF64748B) else Color(0xFFB45309)
                                )
                                TextButton(
                                    onClick = {
                                        val newType = if (isCurrentlyPrivate) "public" else "private"
                                        selectedTestType = newType
                                        viewModel.genIsPublic.value = (newType == "public")
                                        viewModel.autoShareToCommunity.value = (newType == "public")
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isCurrentlyPrivate) "Switch to Public 🌐" else "Switch to Private 🔒",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = bannerAccentColor
                                    )
                                }
                            }
                        }
                    }
                }

                // Mode Indicator Banner (Public vs Private)
                item {
                    if (isPublicMode) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Violet50,
                            border = BorderStroke(1.dp, Violet100),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Violet100),
                                    shadowElevation = 1.dp
                                ) {
                                    Box(modifier = Modifier.padding(8.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.Public,
                                            contentDescription = null,
                                            tint = Violet600,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (selectedLanguage == AppLanguage.HINDI) "कम्युनिटी पोस्ट" else "Community Post",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Violet700Custom()
                                    )
                                    Text(
                                        text = if (selectedLanguage == AppLanguage.HINDI) "यह टेस्ट सार्वजनिक रूप से प्रकाशित होगा।" else "This test will be published publicly.",
                                        fontSize = 12.sp,
                                        color = Violet500,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Slate100,
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Slate200),
                                    shadowElevation = 1.dp
                                ) {
                                    Box(modifier = Modifier.padding(8.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Slate700,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (selectedLanguage == AppLanguage.HINDI) "प्राइवेट वॉल्ट" else "Private Vault",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )
                                    Text(
                                        text = if (selectedLanguage == AppLanguage.HINDI) "टेस्ट सभी से छिपा रहेगा।" else "Test remains hidden from everyone.",
                                        fontSize = 12.sp,
                                        color = Slate500,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Daily Upload Quota Banner
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Violet100.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, Violet200.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = "Limit", tint = Violet600, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (selectedLanguage == AppLanguage.HINDI) "अपलोड सीमा: आज $dailyCount / $dailyLimit" else "Upload Limit: $dailyCount / $dailyLimit today", 
                                fontWeight = FontWeight.Bold, 
                                color = Violet700Custom(), 
                                fontSize = 12.sp
                            )
                            if (dailyLimitUsed) {
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = { viewModel.showRewardedAdForExtraUpload() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (selectedLanguage == AppLanguage.HINDI) "+1 जोड़ें (Ad)" else "+1 Free (Ad)",
                                        color = Violet700Custom(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Advanced Upload Area (With Violet/Fuchsia Glow & Dashed Border)
                item {
                    val dashStroke = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { pdfPickerLauncher.launch("application/pdf") }
                            .background(Color.White.copy(alpha = 0.9f))
                            .drawBehind {
                                drawRoundRect(
                                    color = Violet300,
                                    style = Stroke(width = 3f, pathEffect = dashStroke),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                                )
                            }
                            .padding(vertical = 26.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .background(Violet100, RoundedCornerShape(18.dp))
                                    .border(1.dp, Violet200, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload, 
                                    contentDescription = "Upload", 
                                    modifier = Modifier.size(28.dp), 
                                    tint = Violet600
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (selectedLanguage == AppLanguage.HINDI) "पीडीएफ फ़ाइल चुनें" else "Select PDF File", 
                                fontSize = 15.sp, 
                                color = Violet700Custom(), 
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (selectedLanguage == AppLanguage.HINDI) "एआई विवरणों का पता लगाएगा" else "AI will auto-detect questions", 
                                    fontSize = 12.sp, 
                                    color = Slate500, 
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Fuchsia500,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                    
                    if (selectedMockFile != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Selected: $selectedMockFile", 
                            fontSize = 12.sp, 
                            color = Violet600, 
                            fontWeight = FontWeight.Bold, 
                            textAlign = TextAlign.Center, 
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (selectedPdfUri != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { viewModel.analyzePdfWithAI(context, selectedPdfUri!!) },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Violet100, contentColor = Violet700Custom())
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedLanguage == AppLanguage.HINDI) "टेस्ट डिटेल ऑटो फिल करें (दोबारा प्रयास)" else "Auto Fill Test Details (Retry)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    when (pdfAnalysisState) {
                        is PdfAnalysisState.ExtractingText -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Violet600, trackColor = Violet100)
                            Text(
                                text = if (selectedLanguage == AppLanguage.HINDI) "पीडीएफ से टेक्स्ट और चित्र (Images/Diagrams) निकाले जा रहे हैं..." else "Extracting text and diagram images locally...",
                                color = Violet600, 
                                fontSize = 12.sp, 
                                textAlign = TextAlign.Center, 
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is PdfAnalysisState.AnalyzingWithAI -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Fuchsia500, trackColor = Fuchsia100)
                            Text("Analyzing exam pattern & aligning questions...", color = Fuchsia500, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        is PdfAnalysisState.Success -> {
                            val res = (pdfAnalysisState as PdfAnalysisState.Success).result
                            val diagramNote = if (res.extractedDiagramsCount > 0) " • ${res.extractedDiagramsCount} Diagrams/Images Extracted" else ""
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (selectedLanguage == AppLanguage.HINDI) 
                                                "स्वचालित रूप से निकाला गया: ${res.targetExam} (${res.confidence}% विश्वास)" 
                                            else 
                                                "Auto-Extracted: ${res.targetExam} (${res.confidence}% confidence)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D)
                                        )
                                        Text(
                                            text = "${res.subject} • ${res.questionsCount} Qs • ${res.timerMinutes} Mins$diagramNote",
                                            fontSize = 11.sp,
                                            color = Color(0xFF166534)
                                        )
                                    }
                                }
                            }

                            if (res.isQuestionPaperDetected || res.autoRoutedWorkflow == "private") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFEFF6FF),
                                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Private Mode Auto-Redirect",
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = if (selectedLanguage == AppLanguage.HINDI) 
                                                    "⚡ स्ट्रक्चर्ड प्रश्न मिले! API लागत बचाने के लिए वर्कफ़्लो प्राइवेट मोड में ऑटो-रीडायरेक्ट हुआ" 
                                                else 
                                                    "⚡ Structured Questions Detected! Workflow auto-redirected to Private Mode (Saved Gemini API Cost)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E40AF)
                                            )
                                            if (res.routingReason.isNotBlank()) {
                                                Text(
                                                    text = res.routingReason,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF1D4ED8)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }

                // ⏰ Live Schedule Toggle Card (Styled like React template)
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, Slate200),
                        shadowElevation = 1.dp
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
                                            .background(Slate100, RoundedCornerShape(12.dp))
                                            .border(1.dp, Slate200, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = Slate700,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (selectedLanguage == AppLanguage.HINDI) "लाइव शेड्यूल" else "Live Schedule",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Slate900
                                        )
                                        Text(
                                            text = if (selectedLanguage == AppLanguage.HINDI) "छात्रों के लिए समय निर्धारित करें" else "Set a specific time for students",
                                            fontSize = 11.sp,
                                            color = Slate500,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Custom Switch styled as gradient toggle
                                Box(
                                    modifier = Modifier
                                        .width(52.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (genIsLive) PurpleFuchsiaGradient else Brush.linearGradient(listOf(Slate200, Slate200)))
                                        .clickable { viewModel.genIsLive.value = !genIsLive }
                                        .padding(3.dp),
                                    contentAlignment = if (genIsLive) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(Color.White, CircleShape)
                                            .shadow(2.dp, CircleShape)
                                    )
                                }
                            }

                            AnimatedVisibility(visible = genIsLive) {
                                Column {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider(color = Slate200)
                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = genLiveScheduledTime,
                                        onValueChange = { viewModel.genLiveScheduledTime.value = it },
                                        label = { Text(if (selectedLanguage == AppLanguage.HINDI) "निर्धारित समय" else "Scheduled Date & Time", color = Violet600, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.fillMaxWidth(),
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Star, contentDescription = null, tint = Violet500)
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Violet500,
                                            unfocusedBorderColor = Slate200
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = genLiveDurationMinutes.toString(),
                                            onValueChange = { 
                                                val mins = it.toIntOrNull() ?: 180
                                                viewModel.genLiveDurationMinutes.value = mins
                                            },
                                            label = { Text(if (selectedLanguage == AppLanguage.HINDI) "अवधि (मिनट)" else stringRes("Duration (Mins)"), color = Violet600, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            modifier = Modifier.weight(1f),
                                            leadingIcon = {
                                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Violet500)
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Violet500,
                                                unfocusedBorderColor = Slate200
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            singleLine = true
                                        )

                                        Surface(
                                            modifier = Modifier.weight(1.5f),
                                            shape = RoundedCornerShape(14.dp),
                                            color = Violet50,
                                            border = BorderStroke(1.dp, Violet100)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = if (selectedLanguage == AppLanguage.HINDI) "लाइव शेड्यूल सक्रिय" else "Live Schedule Active",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = Violet700Custom()
                                                )
                                                Text(
                                                    text = if (selectedLanguage == AppLanguage.HINDI) "रीयल-टाइम उल्टी गिनती व रैंकिंग" else "Syncs in real-time on Home & Leaderboard",
                                                    fontSize = 9.sp,
                                                    color = Violet500,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Test Details Form Card with Floated Label pill
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White.copy(alpha = 0.95f),
                            border = BorderStroke(1.dp, Slate200),
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                OutlinedTextField(
                                    value = genName,
                                    onValueChange = { viewModel.genTestName.value = it },
                                    label = { Text(if (selectedLanguage == AppLanguage.HINDI) "टेस्ट शीर्षक / नाम" else "Test Title / Name", color = Violet600, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    placeholder = { Text("Custom AI Test", fontSize = 14.sp, color = Slate300) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Violet500,
                                        unfocusedBorderColor = Slate200,
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                
                                OutlinedTextField(
                                    value = genInst,
                                    onValueChange = { viewModel.genInstitute.value = it },
                                    label = { Text(if (selectedLanguage == AppLanguage.HINDI) "संस्थान/स्रोत का नाम" else "Institute / Source Name", color = Violet600, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    placeholder = { Text("Previous Year Paper", fontSize = 14.sp, color = Slate300) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Violet500,
                                        unfocusedBorderColor = Slate200,
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                // AUTOCOMPLETE FOR TARGET EXAM (With Trophy Icon)
                                AutocompleteInput(
                                    label = if (selectedLanguage == AppLanguage.HINDI) "लक्षित परीक्षा (Target Exam)" else "Target Exam",
                                    value = genTargetExamStr,
                                    onValueChange = { selectedExamName ->
                                        viewModel.genTargetExam.value = selectedExamName
                                        val subjects = Com.ak.testseries.exam.UniversalExamRegistry.getSubjectsForExamName(selectedExamName)
                                        if (subjects.isNotEmpty()) {
                                            viewModel.genSubject.value = subjects.first()
                                        }
                                        val defaultQs = Com.ak.testseries.exam.UniversalExamRegistry.getDefaultQuestionsCountForExam(selectedExamName)
                                        viewModel.genQuestionsCount.value = defaultQs
                                        val defaultDuration = Com.ak.testseries.exam.UniversalExamRegistry.getDefaultDurationMinutesForExam(selectedExamName)
                                        viewModel.genLiveDurationMinutes.value = defaultDuration
                                    },
                                    options = examsList,
                                    placeholder = "Select or type exam (e.g. NEET UG, JEE Main, SSC CGL)...",
                                    icon = Icons.Default.EmojiEvents
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                // AUTOCOMPLETE FOR SUBJECT (Dynamically filtered for chosen exam)
                                AutocompleteInput(
                                    label = if (selectedLanguage == AppLanguage.HINDI) "विषय (Subject for $genTargetExamStr)" else "Subject ($genTargetExamStr)",
                                    value = genSubjectStr,
                                    onValueChange = { viewModel.genSubject.value = it },
                                    options = dynamicSubjectsList,
                                    placeholder = "Select or type subject (e.g. ${dynamicSubjectsList.take(2).joinToString(", ")})...",
                                    icon = Icons.Default.Book
                                )

                                // Exam Pattern & Marking Scheme Chip
                                if (currentExamConfig != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val marking = Com.ak.testseries.exam.UniversalExamRegistry.getMarkingSchemeForExam(genTargetExamStr)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Violet50,
                                        border = BorderStroke(1.dp, Violet200.copy(alpha = 0.6f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = Violet600,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "${currentExamConfig.examName} (${currentExamConfig.category})",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Violet700Custom()
                                                    )
                                                }
                                                Text(
                                                    text = "+${marking.first} / -${marking.second} Marks",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Violet600
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            // Visual difficulty distribution bar
                                            Row(
                                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                            ) {
                                                Box(modifier = Modifier.weight(currentExamConfig.difficultyRatio.easyPercent.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(Color(0xFF4CAF50)))
                                                Box(modifier = Modifier.weight(currentExamConfig.difficultyRatio.mediumPercent.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(Color(0xFFFFA000)))
                                                Box(modifier = Modifier.weight(currentExamConfig.difficultyRatio.hardPercent.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(Color(0xFFE53935)))
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "🎯 Difficulty: ${currentExamConfig.difficultyRatio.easyPercent}% Easy • ${currentExamConfig.difficultyRatio.mediumPercent}% Med • ${currentExamConfig.difficultyRatio.hardPercent}% Hard",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Slate600
                                                )
                                                Text(
                                                    text = "${currentExamConfig.supportedQuestionTypes.size} Formats",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Violet600
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                items(currentExamConfig.supportedQuestionTypes) { qType ->
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Violet50,
                                                        border = BorderStroke(0.5.dp, Violet200)
                                                    ) {
                                                        Text(
                                                            text = qType.displayName,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = Violet700Custom()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                
                                // Questions count & Batch/Class
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    var questionsDropdownExpanded by remember { mutableStateOf(false) }
                                    @OptIn(ExperimentalMaterial3Api::class)
                                    ExposedDropdownMenuBox(
                                        expanded = questionsDropdownExpanded,
                                        onExpandedChange = { questionsDropdownExpanded = !questionsDropdownExpanded },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        OutlinedTextField(
                                            value = if (genQuestionsCount <= 0) "Auto Detect" else "$genQuestionsCount Qs",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(if (selectedLanguage == AppLanguage.HINDI) "प्रश्नों की संख्या" else "No. of Questions", fontSize = 11.sp, color = Violet600, fontWeight = FontWeight.Bold) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = questionsDropdownExpanded) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Violet500,
                                                unfocusedBorderColor = Slate200,
                                                focusedContainerColor = Color.White,
                                                unfocusedContainerColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                                            singleLine = true
                                        )
                                        ExposedDropdownMenu(
                                            expanded = questionsDropdownExpanded,
                                            onDismissRequest = { questionsDropdownExpanded = false },
                                            modifier = Modifier.background(Color.White)
                                        ) {
                                            val commonCounts = remember(currentExamConfig) {
                                                val defaultCount = currentExamConfig?.let {
                                                    Com.ak.testseries.exam.UniversalExamRegistry.getDefaultQuestionsCountForExam(it.examName)
                                                } ?: 180
                                                (listOf(0, 10, 15, 20, 25, 30, 45, 50, 75, 90, 100, 120, 150, 180, 200) + defaultCount).distinct().sorted()
                                            }
                                            commonCounts.forEach { count ->
                                                DropdownMenuItem(
                                                    text = { Text(if (count == 0) "Auto Detect" else "$count Questions", fontSize = 13.sp, color = Slate800) },
                                                    onClick = {
                                                        viewModel.genQuestionsCount.value = count
                                                        questionsDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    
                                    OutlinedTextField(
                                        value = genBatchStr,
                                        onValueChange = { viewModel.genBatch.value = it },
                                        label = { Text(if (selectedLanguage == AppLanguage.HINDI) "बैच/कक्षा" else "Batch / Class", fontSize = 11.sp, color = Violet600, fontWeight = FontWeight.Bold) },
                                        placeholder = { Text("e.g. Dropper", fontSize = 14.sp, color = Slate300) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Violet500,
                                            unfocusedBorderColor = Slate200,
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedTextField(
                                    value = genDesc,
                                    onValueChange = { viewModel.genDescription.value = it },
                                    label = { Text(if (selectedLanguage == AppLanguage.HINDI) "विवरण / पाठ्यक्रम" else "Description / Syllabus", color = Violet600, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Violet500,
                                        unfocusedBorderColor = Slate200,
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )
                            }
                        }

                        // Floating "TEST DETAILS" pill
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate200),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 24.dp, y = 0.dp)
                        ) {
                            Text(
                                text = "TEST DETAILS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Violet600,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Mode-specific Banner (Public Amber vs Private Slate)
                item {
                    if (isPublicMode) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Amber50,
                            border = BorderStroke(1.dp, Amber200.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp), 
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome, 
                                    contentDescription = "Sparkles", 
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Amber200
                                    ) {
                                        Text(
                                            text = "AI AUTO-TRANSLATE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Amber900,
                                            letterSpacing = 0.5.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Test will be auto-generated in BOTH English & Hindi for all students in the community.", 
                                        fontSize = 12.sp, 
                                        color = Amber800, 
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Slate100,
                            border = BorderStroke(1.dp, Slate200),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = Slate600,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (selectedLanguage == AppLanguage.HINDI) 
                                        "यह टेस्ट आपके प्राइवेट वॉल्ट में सुरक्षित रहेगा। इसे कोई और नहीं देख पाएगा।" 
                                    else 
                                        "This test will be saved to your private vault. No one else will see it.",
                                    fontSize = 12.sp,
                                    color = Slate600,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // Submit Button with Vibrant Gradient Styling
                item {
                    val isSuccess = aiState is AiGenerationState.Success
                    val buttonBrush = if (isSuccess) {
                        Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
                    } else {
                        PurpleFuchsiaGradient
                    }

                    Button(
                        onClick = { 
                            if (isSuccess) viewModel.open180QuestionsForPreview() 
                            else viewModel.generateTestFromPdf() 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(6.dp, RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(buttonBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isSuccess) Icons.Rounded.Visibility else Icons.Default.AutoAwesome, 
                                    contentDescription = null, 
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isSuccess) {
                                        if (selectedLanguage == AppLanguage.HINDI) "$genQuestionsCount प्रश्न खोलें व देखें 👁️" else "Open All $genQuestionsCount Questions 👁️"
                                    } else {
                                        if (selectedLanguage == AppLanguage.HINDI) "$genTargetExamStr टेस्ट जनरेट करें ✨" else "Generate $genTargetExamStr Test ✨"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
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
