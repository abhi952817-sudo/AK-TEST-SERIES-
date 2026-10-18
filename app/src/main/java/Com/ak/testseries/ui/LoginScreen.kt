package Com.ak.testseries.ui

import android.util.Patterns
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import Com.ak.testseries.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onGoogleSignInClick: () -> Unit,
    onSignInClick: (String, String) -> Unit,
    onSignUpClick: (String, String, String) -> Unit,
    onMagicLinkClick: (String) -> Unit = {},
    authError: String? = null,
    clearAuthError: () -> Unit = {}
) {
    var isSignUp by remember { mutableStateOf(false) }
    
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    
    var passwordVisible by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(authError) {
        if (authError != null) {
            snackbarHostState.showSnackbar(authError)
            clearAuthError()
        }
    }
    
    fun validate(): Boolean {
        var isValid = true
        
        if (isSignUp && name.trim().isEmpty()) {
            nameError = "Name cannot be empty"
            isValid = false
        } else {
            nameError = null
        }
        
        if (email.trim().isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "Please enter a valid email"
            isValid = false
        } else {
            emailError = null
        }
        
        if (password.length < 6) {
            passwordError = "Password must be at least 6 characters"
            isValid = false
        } else {
            passwordError = null
        }
        
        return isValid
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.background
        )
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush)
                    .padding(horizontal = 32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                
                // App Logo
                Surface(
                    shape = CircleShape,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(100.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_ak_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .clip(CircleShape)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Title
                AnimatedContent(targetState = isSignUp, label = "title_animation") { signUp ->
                    Text(
                        text = if (signUp) "Create Account" else "Welcome Back",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.5).sp
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                AnimatedContent(targetState = isSignUp, label = "subtitle_animation") { signUp ->
                    Text(
                        text = if (signUp) "Sign up to start your journey." else "Sign in to continue your preparation.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                AnimatedVisibility(visible = isSignUp) {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; nameError = null },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                            isError = nameError != null,
                            supportingText = { if (nameError != null) Text(nameError!!) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // Email Field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; emailError = null },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
                    isError = emailError != null,
                    supportingText = { if (emailError != null) Text(emailError!!) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Password Field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; passwordError = null },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = "Toggle Password Visibility")
                        }
                    },
                    isError = passwordError != null,
                    supportingText = { if (passwordError != null) Text(passwordError!!) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { 
                        if (validate()) {
                            if (isSignUp) onSignUpClick(name, email, password) else onSignInClick(email, password)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(if (isSignUp) "Sign Up" else "Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = {
                        if (email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            onMagicLinkClick(email)
                        } else {
                            emailError = "Please enter a valid email for Magic Link"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Send Magic Link", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
                    Text(" OR ", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 8.dp))
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Google Sign In Button
                Button(
                    onClick = onGoogleSignInClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 1.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(24.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("G", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFEA4335))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Continue with Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { onSignInClick("demo@aktest.com", "123456") },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                    Text("Sign in with Demo Account", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Toggle Sign In / Sign Up
                TextButton(onClick = { isSignUp = !isSignUp }) {
                    Text(
                        text = if (isSignUp) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
