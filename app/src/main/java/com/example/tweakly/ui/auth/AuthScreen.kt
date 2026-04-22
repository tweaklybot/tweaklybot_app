package com.example.tweakly.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onSuccess: () -> Unit,
    onGuest: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val focusMgr = LocalFocusManager.current

    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) { if (state.success) onSuccess() }

    // Google Sign-In launcher
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            runCatching { task.result?.let { vm.signInWithGoogle(it) } }
        }
    }

    fun launchGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("YOUR_WEB_CLIENT_ID_HERE") // замените на реальный
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        client.signOut().addOnCompleteListener { googleLauncher.launch(client.signInIntent) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            // Logo
            Box(
                Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PhotoLibrary, null,
                    Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(20.dp))
            Text("Tweakly", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text("Умная галерея с облачной синхронизацией",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)

            Spacer(Modifier.height(36.dp))

            // Tab switch
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(4.dp)) {
                    listOf("Вход" to true, "Регистрация" to false).forEach { (label, isLoginTab) ->
                        val selected = isLogin == isLoginTab
                        Surface(
                            onClick = { isLogin = isLoginTab; vm.clearError() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(label, Modifier.padding(vertical = 10.dp),
                                textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Email field
            OutlinedTextField(
                value = email, onValueChange = { email = it; vm.clearError() },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusMgr.moveFocus(FocusDirection.Down) }),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            // Password field
            OutlinedTextField(
                value = password, onValueChange = { password = it; vm.clearError() },
                label = { Text("Пароль") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton({ showPass = !showPass }) {
                        Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusMgr.clearFocus()
                    if (isLogin) vm.signInEmail(email, password) else vm.registerEmail(email, password)
                }),
                singleLine = true
            )

            // Error
            AnimatedVisibility(state.error != null) {
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Primary button
            Button(
                onClick = { if (isLogin) vm.signInEmail(email, password) else vm.registerEmail(email, password) },
                Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !state.isLoading
            ) {
                AnimatedContent(state.isLoading, label = "btn") { loading ->
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.5.dp)
                    else Text(if (isLogin) "Войти" else "Создать аккаунт", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Divider
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text("  или  ", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            // Google button
            OutlinedButton(
                onClick = { launchGoogle() },
                Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !state.isLoading
            ) {
                Icon(Icons.Default.AccountCircle, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Войти через Google", fontSize = 15.sp)
            }

            Spacer(Modifier.height(12.dp))

            // Guest button
            TextButton(onClick = onGuest, Modifier.fillMaxWidth()) {
                Text("Продолжить без аккаунта", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
