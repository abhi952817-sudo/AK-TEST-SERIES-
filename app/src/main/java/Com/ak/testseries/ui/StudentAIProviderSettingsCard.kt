package Com.ak.testseries.ui

import Com.ak.testseries.ai.StudentAIProviderClient
import Com.ak.testseries.utils.StudentAIKeyStore
import Com.ak.testseries.utils.StudentAIUsageStore
import Com.ak.testseries.utils.StudentAIUsageSnapshot
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Student-owned provider settings. Admin accounts are intentionally excluded:
 * Admin credentials are managed server-side and must never be entered here.
 */
@Composable
fun StudentAIProviderSettingsCard(
    uid: String,
    isHindi: Boolean,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    if (isAdmin) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val providers = StudentAIKeyStore.Provider.values().toList()
    var selectedProvider by remember { mutableStateOf(StudentAIKeyStore.Provider.GEMINI) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf("") }
    var availableModels by remember { mutableStateOf(emptyList<String>()) }
    var isConnected by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var usageSnapshot by remember { mutableStateOf(StudentAIUsageSnapshot(0L, null, null, null)) }

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            val preferred = withContext(Dispatchers.IO) {
                StudentAIKeyStore.preferredProvider(context, uid)
            }
            if (preferred != null) selectedProvider = preferred
        }
    }

    LaunchedEffect(uid, selectedProvider) {
        apiKeyInput = ""
        availableModels = emptyList()
        statusMessage = null
        if (uid.isBlank()) {
            isConnected = false
            modelInput = ""
        } else {
            val stored = withContext(Dispatchers.IO) {
                Triple(
                    StudentAIKeyStore.isConfigured(context, uid, selectedProvider),
                    StudentAIKeyStore.preferredModel(context, uid, selectedProvider),
                    StudentAIUsageStore.snapshot(context, uid, selectedProvider)
                )
            }
            isConnected = stored.first
            modelInput = stored.second.orEmpty()
            usageSnapshot = stored.third
        }
    }

    val providerName = when (selectedProvider) {
        StudentAIKeyStore.Provider.GEMINI -> "Google Gemini"
        StudentAIKeyStore.Provider.OPENAI -> "OpenAI"
        StudentAIKeyStore.Provider.GROQ -> "Groq"
        StudentAIKeyStore.Provider.ANTHROPIC -> "Anthropic Claude"
        StudentAIKeyStore.Provider.CEREBRAS -> "Cerebras"
    }

    Card(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (isHindi) "आपके AI Providers" else "Your AI Providers",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (isHindi)
                    "अपनी API key जोड़ें। Key इसी डिवाइस पर Android Keystore से encrypt होगी और सीधे चुने हुए provider को भेजी जाएगी।"
                else
                    "Add your own API key. It is encrypted on this device with Android Keystore and sent directly to the selected provider.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(onClick = { providerMenuExpanded = true }, enabled = !isBusy) {
                Text(if (isHindi) "Provider: $providerName  ▾" else "Provider: $providerName  ▾")
            }
            DropdownMenu(
                expanded = providerMenuExpanded,
                onDismissRequest = { providerMenuExpanded = false }
            ) {
                providers.forEach { provider ->
                    val label = when (provider) {
                        StudentAIKeyStore.Provider.GEMINI -> "Google Gemini"
                        StudentAIKeyStore.Provider.OPENAI -> "OpenAI"
                        StudentAIKeyStore.Provider.GROQ -> "Groq"
                        StudentAIKeyStore.Provider.ANTHROPIC -> "Anthropic Claude"
                        StudentAIKeyStore.Provider.CEREBRAS -> "Cerebras"
                    }
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            selectedProvider = provider
                            providerMenuExpanded = false
                        }
                    )
                }
            }

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text(if (isHindi) "API Key" else "API Key") },
                placeholder = { Text(if (isHindi) "अपनी key paste करें" else "Paste your provider key") },
                enabled = !isBusy
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !isBusy && uid.isNotBlank() && apiKeyInput.isNotBlank(),
                    onClick = {
                        isBusy = true
                        statusMessage = null
                        scope.launch {
                            try {
                                if (FirebaseAuth.getInstance().currentUser?.uid != uid) {
                                    throw IllegalStateException("The signed-in account changed. Reopen Settings and try again.")
                                }
                                val models = StudentAIProviderClient.verifyApiKey(selectedProvider, apiKeyInput)
                                if (FirebaseAuth.getInstance().currentUser?.uid != uid) {
                                    throw IllegalStateException("The signed-in account changed. The key was not saved.")
                                }
                                withContext(Dispatchers.IO) {
                                    StudentAIKeyStore.save(context, uid, selectedProvider, apiKeyInput)
                                    StudentAIKeyStore.savePreferredModel(context, uid, selectedProvider, models.first())
                                }
                                availableModels = models
                                modelInput = models.firstOrNull().orEmpty()
                                isConnected = true
                                usageSnapshot = withContext(Dispatchers.IO) {
                                    StudentAIUsageStore.snapshot(context, uid, selectedProvider)
                                }
                                apiKeyInput = ""
                                statusMessage = if (isHindi)
                                    "Key सत्यापित और सुरक्षित रूप से सेव हो गई। उपलब्ध मॉडल: ${models.size}"
                                else
                                    "Key verified and saved securely. Available models: ${models.size}"
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                statusMessage = error.message ?: if (isHindi) "Key सत्यापित नहीं हुई।" else "Key verification failed."
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                ) {
                    Text(if (isBusy) (if (isHindi) "जाँच जारी..." else "Checking...") else (if (isHindi) "Verify & Save" else "Verify & Save"))
                }
                if (isConnected) {
                    TextButton(
                        enabled = !isBusy,
                        onClick = {
                            scope.launch {
                                if (FirebaseAuth.getInstance().currentUser?.uid != uid) return@launch
                                withContext(Dispatchers.IO) {
                                    StudentAIKeyStore.clear(context, uid, selectedProvider)
                                }
                                isConnected = false
                                availableModels = emptyList()
                                modelInput = ""
                                usageSnapshot = StudentAIUsageSnapshot(0L, null, null, null)
                                statusMessage = if (isHindi) "Provider key हटा दी गई।" else "Provider key removed."
                            }
                        }
                    ) { Text(if (isHindi) "हटाएँ" else "Disconnect") }
                }
            }

            if (isConnected) {
                Text(
                    if (isHindi) "स्थिति: सुरक्षित रूप से जुड़ा हुआ" else "Status: securely connected",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    if (isHindi)
                        "सफल अनुरोध: ${usageSnapshot.successfulRequests} • पिछला उपयोग: इनपुट ${usageSnapshot.lastInputTokens ?: "—"}, आउटपुट ${usageSnapshot.lastOutputTokens ?: "—"}, कुल ${usageSnapshot.lastTotalTokens ?: "—"} tokens"
                    else
                        "Successful requests: ${usageSnapshot.successfulRequests} • Last usage: input ${usageSnapshot.lastInputTokens ?: "—"}, output ${usageSnapshot.lastOutputTokens ?: "—"}, total ${usageSnapshot.lastTotalTokens ?: "—"} tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (availableModels.isEmpty()) {
                    TextButton(
                        enabled = !isBusy,
                        onClick = {
                            scope.launch {
                                isBusy = true
                                try {
                                    if (FirebaseAuth.getInstance().currentUser?.uid != uid) {
                                        throw IllegalStateException("The signed-in account changed. Reopen Settings and try again.")
                                    }
                                    val savedKey = withContext(Dispatchers.IO) {
                                        StudentAIKeyStore.read(context, uid, selectedProvider)
                                    } ?: throw IllegalStateException("The saved provider key is unavailable. Reconnect it.")
                                    val models = StudentAIProviderClient.verifyApiKey(selectedProvider, savedKey)
                                    if (FirebaseAuth.getInstance().currentUser?.uid != uid) {
                                        throw IllegalStateException("The signed-in account changed. Model settings were not saved.")
                                    }
                                    availableModels = models
                                    if (modelInput.isBlank()) {
                                        modelInput = models.first()
                                        withContext(Dispatchers.IO) {
                                            StudentAIKeyStore.savePreferredModel(context, uid, selectedProvider, modelInput)
                                        }
                                    }
                                    statusMessage = if (isHindi) "मॉडल सूची अपडेट हो गई।" else "Model catalogue refreshed."
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    statusMessage = error.message ?: "Could not load provider models."
                                } finally {
                                    isBusy = false
                                }
                            }
                        }
                    ) { Text(if (isHindi) "सेव key से मॉडल लोड करें" else "Load models using saved key") }
                }
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (isHindi) "Model ID" else "Model ID") },
                    placeholder = { Text(if (isHindi) "मॉडल चुनें या ID लिखें" else "Choose a model or enter its ID") },
                    enabled = !isBusy
                )
                if (availableModels.isNotEmpty()) {
                    TextButton(onClick = { modelMenuExpanded = true }) {
                        Text(if (isHindi) "उपलब्ध मॉडल देखें (${availableModels.size})" else "Browse available models (${availableModels.size})")
                    }
                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    modelInput = model
                                    modelMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                TextButton(
                    enabled = modelInput.isNotBlank() && !isBusy,
                    onClick = {
                        scope.launch {
                            if (FirebaseAuth.getInstance().currentUser?.uid != uid) return@launch
                            withContext(Dispatchers.IO) {
                                StudentAIKeyStore.savePreferredModel(context, uid, selectedProvider, modelInput)
                            }
                            statusMessage = if (isHindi) "डिफ़ॉल्ट मॉडल सेव हो गया।" else "Preferred model saved."
                        }
                    }
                ) { Text(if (isHindi) "मॉडल सेव करें" else "Save model") }
                Text(
                    if (isHindi)
                        "मॉडल ID अभी इस स्क्रीन में चुनी जाती है; अगला चरण इसे सभी Student AI सुविधाओं से जोड़ना है।"
                    else
                        "Model selection is available here; the next integration phase connects this provider to Student AI features.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uid.isBlank()) {
                Text(
                    if (isHindi) "API key जोड़ने के लिए पहले साइन इन करें।" else "Sign in before adding a personal API key.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isBusy) {
                Spacer(modifier = Modifier.height(2.dp))
                androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
