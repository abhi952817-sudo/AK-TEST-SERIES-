package Com.ak.testseries.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import Com.ak.testseries.viewmodel.NEETViewModel
import Com.ak.testseries.exam.UniversalExamRegistry

data class OnboardingExamItem(
    val examId: String,
    val examName: String,
    val hindiTitle: String,
    val batchTag: String,
    val emoji: String,
    val pattern: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileOnboardingScreen(
    viewModel: NEETViewModel,
    onComplete: () -> Unit
) {
    val userProfile by viewModel.userProfile.collectAsState()
    
    // Onboarding step: 1 = Personal Details & Photo, 2 = Target Exam Selection (20 Exams)
    var currentStep by remember { mutableIntStateOf(1) }

    var name by remember { mutableStateOf(userProfile.name.ifEmpty { "" }) }
    var email by remember { mutableStateOf(userProfile.email.ifEmpty { "" }) }
    var selectedState by remember { mutableStateOf(userProfile.stateName.ifEmpty { "Uttar Pradesh" }) }
    var city by remember { mutableStateOf(userProfile.cityName.ifEmpty { "Lucknow" }) }
    var profileImageUri by remember { mutableStateOf(userProfile.profileImageUri) }
    var selectedTargetExam by remember { mutableStateOf(userProfile.targetExam.ifEmpty { "NEET UG" }) }
    
    var stateDropdownExpanded by remember { mutableStateOf(false) }
    
    val indianStates = remember {
        listOf(
            "Uttar Pradesh", "Bihar", "Rajasthan (Kota)", "Delhi NCR", "Madhya Pradesh",
            "Haryana", "Punjab", "Maharashtra", "West Bengal", "Gujarat", "Karnataka",
            "Kerala", "Tamil Nadu", "Andhra Pradesh", "Telangana", "Other"
        )
    }
    
    val context = LocalContext.current
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val file = java.io.File(context.filesDir, "profile_pic_${System.currentTimeMillis()}.jpg")
                val outputStream = java.io.FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                profileImageUri = file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                profileImageUri = it.toString()
            }
        }
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            MaterialTheme.colorScheme.background
        )
    )

    // All 23 Registered Exams Structured into 5 Batches with Dynamic Registry Patterns
    val allExamsList = remember {
        listOf(
            // Batch 1: National & Entrance
            OnboardingExamItem("NEET UG", "NEET UG", "चिकित्सा प्रवेश परीक्षा", "B1: नेशनल", "🩺", UniversalExamRegistry.getExamDisplaySubtitleByName("NEET UG"), Color(0xFF10B981)),
            OnboardingExamItem("JEE Main", "JEE Main", "इंजीनियरिंग प्रवेश परीक्षा", "B1: नेशनल", "📐", UniversalExamRegistry.getExamDisplaySubtitleByName("JEE Main"), Color(0xFF3B82F6)),
            OnboardingExamItem("CUET UG", "CUET UG", "विश्वविद्यालय प्रवेश परीक्षा", "B1: नेशनल", "🏛️", UniversalExamRegistry.getExamDisplaySubtitleByName("CUET UG"), Color(0xFF8B5CF6)),
            OnboardingExamItem("CAT", "CAT", "आईआईएम मैनेजमेंट परीक्षा", "B1: नेशनल", "📊", UniversalExamRegistry.getExamDisplaySubtitleByName("CAT"), Color(0xFFEC4899)),
            OnboardingExamItem("UPSC CSE", "UPSC Civil Services", "सिविल सेवा प्रारंभिक परीक्षा", "B1: नेशनल", "🇮🇳", UniversalExamRegistry.getExamDisplaySubtitleByName("UPSC"), Color(0xFF7C3AED)),
            OnboardingExamItem("GATE", "GATE", "ग्रेजुएट एप्टीट्यूड टेस्ट इंजीनियरिंग", "B1: नेशनल", "⚙️", UniversalExamRegistry.getExamDisplaySubtitleByName("GATE"), Color(0xFF2563EB)),
            OnboardingExamItem("NDA", "NDA & NA", "राष्ट्रीय रक्षा अकादमी परीक्षा", "B1: नेशनल", "🎖️", UniversalExamRegistry.getExamDisplaySubtitleByName("NDA"), Color(0xFF059669)),

            // Batch 2: Staff Selection Commission (SSC)
            OnboardingExamItem("SSC CGL", "SSC CGL", "संयुक्त स्नातक स्तरीय परीक्षा", "B2: SSC", "🦁", UniversalExamRegistry.getExamDisplaySubtitleByName("SSC CGL"), Color(0xFFF59E0B)),
            OnboardingExamItem("SSC CHSL", "SSC CHSL", "संयुक्त उच्चतर माध्यमिक परीक्षा", "B2: SSC", "📝", UniversalExamRegistry.getExamDisplaySubtitleByName("SSC CHSL"), Color(0xFF06B6D4)),
            OnboardingExamItem("SSC MTS", "SSC MTS & Havaldar", "मल्टी-टास्किंग स्टाफ", "B2: SSC", "⚡", UniversalExamRegistry.getExamDisplaySubtitleByName("SSC MTS"), Color(0xFFEF4444)),
            OnboardingExamItem("SSC GD", "SSC GD Constable", "केंद्रीय सशस्त्र पुलिस बल", "B2: SSC", "🛡️", UniversalExamRegistry.getExamDisplaySubtitleByName("SSC GD"), Color(0xFF6366F1)),
            OnboardingExamItem("SSC CPO", "SSC CPO", "दिल्ली पुलिस व सीएपीएफ एसआई", "B2: SSC", "👮", UniversalExamRegistry.getExamDisplaySubtitleByName("SSC CPO"), Color(0xFFD97706)),

            // Batch 3: Indian Railways (RRB)
            OnboardingExamItem("RRB NTPC", "RRB NTPC", "रेलवे गैर-तकनीकी पद", "B3: रेलवे", "🚆", UniversalExamRegistry.getExamDisplaySubtitleByName("RRB NTPC"), Color(0xFF059669)),
            OnboardingExamItem("RRB Group D", "RRB Group D", "रेलवे ट्रैक मेंटेनर व पॉइंट्समैन", "B3: रेलवे", "🔨", UniversalExamRegistry.getExamDisplaySubtitleByName("RRB Group D"), Color(0xFFEA580C)),
            OnboardingExamItem("RRB ALP", "RRB ALP", "सहायक लोको पायलट", "B3: रेलवे", "🚂", UniversalExamRegistry.getExamDisplaySubtitleByName("RRB ALP"), Color(0xFF4F46E5)),
            OnboardingExamItem("RRB Technician", "RRB Technician", "रेलवे तकनीशियन परीक्षा", "B3: रेलवे", "🔧", UniversalExamRegistry.getExamDisplaySubtitleByName("RRB Technician"), Color(0xFF64748B)),
            OnboardingExamItem("RRB JE", "RRB JE", "रेलवे जूनियर इंजीनियर", "B3: रेलवे", "⚙️", UniversalExamRegistry.getExamDisplaySubtitleByName("RRB JE"), Color(0xFFE11D48)),

            // Batch 4: Banking & Financial Services
            OnboardingExamItem("SBI PO", "SBI PO", "भारतीय स्टेट बैंक प्रोबेशनरी ऑफिसर", "B4: बैंक", "🏦", UniversalExamRegistry.getExamDisplaySubtitleByName("SBI PO"), Color(0xFF0284C7)),
            OnboardingExamItem("SBI Clerk", "SBI Clerk", "एसबीआई जूनियर एसोसिएट", "B4: बैंक", "💼", UniversalExamRegistry.getExamDisplaySubtitleByName("SBI Clerk"), Color(0xFF0EA5E9)),
            OnboardingExamItem("IBPS PO", "IBPS PO", "आईबीपीएस प्रोबेशनरी ऑफिसर", "B4: बैंक", "🏛️", UniversalExamRegistry.getExamDisplaySubtitleByName("IBPS PO"), Color(0xFF4338CA)),
            OnboardingExamItem("IBPS Clerk", "IBPS Clerk", "आईबीपीएस क्लर्क सीआरपी", "B4: बैंक", "📋", UniversalExamRegistry.getExamDisplaySubtitleByName("IBPS Clerk"), Color(0xFF7C3AED)),

            // Batch 5: Teaching & Other
            OnboardingExamItem("CTET", "CTET", "केंद्रीय शिक्षक पात्रता परीक्षा", "B5: अन्य", "📚", UniversalExamRegistry.getExamDisplaySubtitleByName("CTET"), Color(0xFF0D9488)),
            OnboardingExamItem("UGC NET", "UGC NET", "असिस्टेंट प्रोफेसर व जेआरएफ", "B5: अन्य", "🎓", UniversalExamRegistry.getExamDisplaySubtitleByName("UGC NET"), Color(0xFF7C3AED))
        )
    }

    var selectedBatchCategoryFilter by remember { mutableStateOf("All") }

    val filteredExams = remember(selectedBatchCategoryFilter) {
        if (selectedBatchCategoryFilter == "All") allExamsList
        else allExamsList.filter { it.batchTag == selectedBatchCategoryFilter }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        ) {
            // Top Step Progress Indicator
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentStep == 1) "चरण 1/2: प्रोफ़ाइल जानकारी" else "चरण 2/2: लक्षित परीक्षा चयन",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (currentStep == 1) "50% पूर्ण" else "100% पूर्ण",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (currentStep == 1) 0.5f else 1.0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }
                },
                modifier = Modifier.weight(1f),
                label = "OnboardingStepTransition"
            ) { step ->
                if (step == 1) {
                    // STEP 1: Personal Details & Avatar
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "विद्यार्थी प्रोफ़ाइल तैयार करें",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "अपनी मूल जानकारी दर्ज करें और परीक्षा तैयारी शुरू करें",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                        )

                        // Circular Avatar Picker with Camera Badge & Status
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clickable { imagePickerLauncher.launch("image/*") },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                if (profileImageUri.isNotEmpty()) {
                                    AsyncImage(
                                        model = profileImageUri,
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = "Profile Placeholder",
                                            modifier = Modifier.size(56.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            FloatingActionButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoCamera,
                                    contentDescription = "Add Photo",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Form Inputs Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Full Name / आपका पूरा नाम", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Email Address / ईमेल आईडी", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                // State Selection Dropdown
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { stateDropdownExpanded = true }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedState,
                                            onValueChange = {},
                                            readOnly = true,
                                            enabled = false,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            label = { Text("Select State / राज्य", fontSize = 12.sp) },
                                            leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                                            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Select State") },
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
                                                text = { Text(state, fontSize = 14.sp) },
                                                onClick = {
                                                    selectedState = state
                                                    stateDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = city,
                                    onValueChange = { city = it },
                                    label = { Text("City or District / ज़िला या शहर", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                                    placeholder = { Text("e.g. Lucknow, Kota, Patna, Delhi", fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Next Step Button
                        Button(
                            onClick = {
                                if (name.trim().isNotEmpty() && city.trim().isNotEmpty()) {
                                    currentStep = 2
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            enabled = name.trim().isNotEmpty() && city.trim().isNotEmpty()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "आगे बढ़ें: अपनी परीक्षा चुनें",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    // STEP 2: Dedicated 20 Exam Selector Page (5 Batches)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { currentStep = 1 }) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "अपनी लक्षित परीक्षा चुनें",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "कुल ${allExamsList.size} परीक्षाएँ उपलब्ध हैं • एक चुनें",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Batch Categories Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val categories = listOf(
                                "All" to "सभी (${allExamsList.size})",
                                "B1: नेशनल" to "B1: Nat",
                                "B2: SSC" to "B2: SSC",
                                "B3: रेलवे" to "B3: Rail",
                                "B4: बैंक" to "B4: Bank",
                                "B5: अन्य" to "B5: Misc"
                            )
                            categories.forEach { (catId, catLabel) ->
                                val isSelected = selectedBatchCategoryFilter == catId
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedBatchCategoryFilter = catId }
                                ) {
                                    Text(
                                        text = catLabel,
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 20 Exam Cards List
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(filteredExams) { exam ->
                                val isSelected = selectedTargetExam.equals(exam.examId, ignoreCase = true) ||
                                        selectedTargetExam.equals(exam.examName, ignoreCase = true)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedTargetExam = exam.examId },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) exam.color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.8.dp else 1.dp,
                                        color = if (isSelected) exam.color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(exam.color.copy(alpha = 0.15f), CircleShape)
                                                .border(1.dp, exam.color.copy(alpha = 0.4f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = exam.emoji, fontSize = 22.sp)
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = exam.examName,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = exam.color.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = exam.batchTag,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = exam.color,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = exam.hindiTitle,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = exam.pattern,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = exam.color
                                            )
                                        }

                                        Icon(
                                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isSelected) exam.color else Color.Gray,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Complete Registration Button
                        Button(
                            onClick = {
                                viewModel.setTargetExam(selectedTargetExam)
                                viewModel.saveUserProfile(
                                    name = name.trim(),
                                    email = email.trim(),
                                    stateName = selectedState,
                                    cityName = city.trim(),
                                    profileImageUri = profileImageUri,
                                    targetExam = selectedTargetExam
                                )
                                onComplete()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "पुष्टि करें और डैशबोर्ड पर जाएँ 🚀",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Rounded.RocketLaunch,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
