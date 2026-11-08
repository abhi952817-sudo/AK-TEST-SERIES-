package Com.ak.testseries.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import Com.ak.testseries.security.GeminiApiKeyStore
import java.text.DateFormat
import java.util.Date

private const val GEMINI_API_KEYS_URL = "https://aistudio.google.com/app/apikey"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiAiSetupScreen(
    onNavigateBack: () -> Unit,
    userId: String,
    isHindi: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by rememberSaveable { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var lastUpdated by remember { mutableStateOf(0L) }
    var tutorialStep by rememberSaveable { mutableIntStateOf(0) }
    var showDisconnectDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        isConnected = false
        connectionError = null
        if (userId.isNotBlank()) {
            lastUpdated = GeminiApiKeyStore.lastUpdatedAt(context, userId)
            val savedKey = GeminiApiKeyStore.getApiKey(context, userId)
            if (!savedKey.isNullOrBlank()) {
                isVerifying = true
                val verification = GeminiApiKeyStore.verifyApiKey(savedKey)
                isConnected = verification.isSuccess
                if (verification.isFailure) {
                    connectionError = verification.exceptionOrNull()?.message
                }
                isVerifying = false
            } else {
                lastUpdated = 0L
            }
        } else {
            lastUpdated = 0L
        }
    }

    val rotationDue = lastUpdated > 0L && System.currentTimeMillis() - lastUpdated >= 7L * 24L * 60L * 60L * 1000L

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text(if (isHindi) "Gemini डिस्कनेक्ट करें?" else "Disconnect Gemini?") },
            text = { Text(if (isHindi) "इस डिवाइस से आपकी सुरक्षित API Key हट जाएगी।" else "Your encrypted personal API key will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    GeminiApiKeyStore.deleteApiKey(context, userId)
                    isConnected = false
                    lastUpdated = 0L
                    apiKey = ""
                    connectionMessage = if (isHindi) "Gemini डिस्कनेक्ट हो गया।" else "Gemini disconnected."
                    connectionError = null
                    showDisconnectDialog = false
                }) { Text(if (isHindi) "डिस्कनेक्ट" else "Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) { Text(if (isHindi) "रद्द करें" else "Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isHindi) "Gemini AI कनेक्ट करें" else "Connect Gemini AI", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Text(if (isHindi) "अपनी Gemini API Key इस्तेमाल करें" else "Use your own Gemini API key", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Text(
                        if (isHindi) "Google AI Studio में अपनी Key बनाएँ या मौजूदा Project Import करें। फिर Key कॉपी करके नीचे Paste करें।" else "Create a key in Google AI Studio or import an existing project, then copy and paste your key below.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_API_KEYS_URL)))
                            } catch (_: Exception) {
                                connectionError = "Unable to open Google AI Studio on this device."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (isHindi) "Google AI Studio खोलें" else "Open Google AI Studio")
                    }
                    Text(
                        if (isHindi) "नया योग्य खाता: Terms स्वीकार करने के बाद Google Default Project और Key बना सकता है। पुराना Cloud Project हो तो Projects में Import करना होगा।" else "Eligible new accounts may receive a default project and key after accepting Google's terms. Existing Cloud projects must be imported first.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(if (isHindi) "अपनी API Key यहाँ Paste करें" else "Paste your API key here", fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; connectionError = null; connectionMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(if (isHindi) "Gemini API Key" else "Gemini API Key") },
                        placeholder = { Text("Paste key copied from AI Studio") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Button(
                        onClick = {
                            if (userId.isBlank() || FirebaseAuth.getInstance().currentUser?.uid != userId) {
                                connectionError = if (isHindi) "पहले अपने AK TEST SERIES खाते में लॉगिन करें।" else "Sign in to your AK TEST SERIES account first."
                            } else if (apiKey.isBlank()) {
                                connectionError = if (isHindi) "पहले API Key Paste करें।" else "Paste your API key first."
                            } else {
                                scope.launch {
                                    isVerifying = true
                                    connectionError = null
                                    connectionMessage = null
                                    val verification = GeminiApiKeyStore.verifyApiKey(apiKey)
                                    if (verification.isSuccess) {
                                        try {
                                            // Replace only after the new key has passed live verification.
                                            GeminiApiKeyStore.saveApiKey(context, userId, apiKey)
                                            isConnected = true
                                            lastUpdated = GeminiApiKeyStore.lastUpdatedAt(context, userId)
                                            apiKey = ""
                                            connectionMessage = if (isHindi) "Gemini सफलतापूर्वक कनेक्ट हो गया।" else "Gemini connected and verified."
                                        } catch (error: Exception) {
                                            connectionError = error.message ?: "Could not securely save the verified key."
                                        }
                                    } else {
                                        connectionError = verification.exceptionOrNull()?.message ?: "Gemini key verification failed."
                                    }
                                    isVerifying = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isVerifying && apiKey.isNotBlank()
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.size(8.dp))
                            Text(if (isHindi) "जाँच हो रही है..." else "Verifying...")
                        } else {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(if (isHindi) "Connect & Verify" else "Connect & Verify")
                        }
                    }
                    if (connectionMessage != null) {
                        Text(connectionMessage!!, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    if (connectionError != null) {
                        Text(connectionError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    if (isConnected) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(if (isHindi) "आपकी व्यक्तिगत Key इस डिवाइस पर सुरक्षित रूप से सेव है।" else "Your personal key is encrypted on this device.", fontSize = 12.sp)
                        }
                        if (lastUpdated > 0L) {
                            Text(
                                (if (isHindi) "अंतिम अपडेट: " else "Last updated: ") + DateFormat.getDateTimeInstance().format(Date(lastUpdated)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        if (rotationDue) {
                            Text(
                                if (isHindi) "7 दिन हो गए हैं। सुरक्षा के लिए नई Key बनाकर Verify करें।" else "Your key is 7 days old. Rotate it by verifying a replacement key.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(onClick = { showDisconnectDialog = true }) {
                            Text(if (isHindi) "Disconnect और Key हटाएँ" else "Disconnect and remove key")
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isHindi) "कैसे कनेक्ट करें" else "Quick animated guide", fontWeight = FontWeight.Bold)
                    val steps = if (isHindi) listOf(
                        "1. Google AI Studio में Login करें।",
                        "2. नया Default Project मिले तो उसका API Key पेज खोलें; पुराने Project के लिए Import करें।",
                        "3. API Key Copy करें।",
                        "4. वापस आकर यहाँ Paste करें और Connect & Verify दबाएँ।"
                    ) else listOf(
                        "1. Sign in to Google AI Studio.",
                        "2. Open the default project's API Keys page, or import an existing project.",
                        "3. Copy your Gemini API key.",
                        "4. Return here, paste it, and tap Connect & Verify."
                    )
                    AnimatedContent(
                        targetState = tutorialStep,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "GeminiSetupTutorial"
                    ) { step ->
                        Text(
                            steps[step.coerceIn(0, steps.lastIndex)],
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            minLines = 2,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { tutorialStep = (tutorialStep - 1).coerceAtLeast(0) }, enabled = tutorialStep > 0) {
                            Text(if (isHindi) "पिछला" else "Previous")
                        }
                        Text("${tutorialStep + 1} / 4", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        TextButton(onClick = { tutorialStep = (tutorialStep + 1).coerceAtMost(3) }, enabled = tutorialStep < 3) {
                            Text(if (isHindi) "अगला" else "Next")
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    if (isHindi) "आपकी Key Firebase या Admin Panel में नहीं भेजी जाती। यह केवल इस डिवाइस पर encrypted रहती है। फिर भी मोबाइल ऐप में इस्तेमाल की गई Key को compromised/rooted डिवाइस से पूरी तरह सुरक्षित रखने की गारंटी नहीं है।" else "Your key is not uploaded to Firebase or shown to Admins. It is encrypted on this device. However, no API key used by a mobile app can be guaranteed safe on a compromised or rooted device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}
