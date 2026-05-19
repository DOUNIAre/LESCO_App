package com.imad.lesco

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// ─── SignUpScreen ─────────────────────────────────────────────────────────────
@Composable
fun SignUpScreen(
    onBackClick: () -> Unit,
    onSignUpSuccess: () -> Unit,
    onLoginClick: () -> Unit = {}
) {
    var fullName        by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage    by remember { mutableStateOf("") }
    var isLoading       by remember { mutableStateOf(false) }

    var fullNameError        by remember { mutableStateOf(false) }
    var emailError           by remember { mutableStateOf(false) }
    var passwordError        by remember { mutableStateOf(false) }
    var confirmPasswordError by remember { mutableStateOf(false) }

    val shakeFullName        = remember { Animatable(0f) }
    val shakeEmail           = remember { Animatable(0f) }
    val shakePassword        = remember { Animatable(0f) }
    val shakeConfirmPassword = remember { Animatable(0f) }
    val scope                = rememberCoroutineScope()

    suspend fun doShake(anim: Animatable<Float, AnimationVector1D>) {
        for (target in listOf(10f, -10f, 8f, -8f, 5f, -5f, 0f)) {
            anim.animateTo(target, tween(60, easing = LinearEasing))
        }
    }

    fun isValidEmail(e: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
        return e.matches(emailRegex.toRegex())
    }

    val confirmMismatch = confirmPassword.isNotEmpty()
            && password.isNotEmpty()
            && password != confirmPassword

    Box(modifier = Modifier.fillMaxSize()) {

        LescoBackground()

        // ── Bouton retour ──
        Image(
            painter            = painterResource(id = R.drawable.return_boutton),
            contentDescription = "Retour",
            modifier           = Modifier
                .padding(start = 20.dp, top = 65.dp)
                .size(40.dp)
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onBackClick() }
        )

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(207.dp))

            // ── Titre ──
            Text(
                text       = "Sign up",
                color      = Color.White,
                fontSize   = 23.sp,
                fontWeight = FontWeight.Normal,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // ── Full Name ──
            GlassTextField(
                value         = fullName,
                placeholder   = "Full Name",
                onValueChange = { fullName = it; fullNameError = false; errorMessage = "" },
                shakeOffsetX  = shakeFullName.value,
                hasError      = fullNameError
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── Email ──
            GlassTextField(
                value         = email,
                placeholder   = "Email",
                onValueChange = { email = it; emailError = false; errorMessage = "" },
                keyboardType  = KeyboardType.Email,
                shakeOffsetX  = shakeEmail.value,
                hasError      = emailError
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── Password ──
            GlassTextField(
                value         = password,
                placeholder   = "Password",
                onValueChange = { password = it; passwordError = false; errorMessage = "" },
                isPassword    = true,
                shakeOffsetX  = shakePassword.value,
                hasError      = passwordError
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── Confirm Password ──
            GlassTextField(
                value         = confirmPassword,
                placeholder   = "Confirm Password",
                onValueChange = { confirmPassword = it; confirmPasswordError = false; errorMessage = "" },
                isPassword    = true,
                shakeOffsetX  = shakeConfirmPassword.value,
                hasError      = confirmPasswordError || confirmMismatch
            )

            // ── Zone d'erreur : hauteur FIXE réservée en permanence ──
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .height(36.dp)          // hauteur fixe → ne décale JAMAIS le bouton
                    .padding(start = 26.dp, top = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text     = errorMessage,
                        color    = Color(0xFFFF6B6B),
                        fontSize = 13.sp
                    )
                }
            }

            // ── Spacer flexible : absorbe tout l'espace restant ──
            Spacer(modifier = Modifier.weight(1f))

            // ── Slot bouton/loader : taille FIXE → position toujours identique ──
            Box(
                modifier         = Modifier
                    .width(302.dp)
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color    = LescoPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    GlassButton(
                        text           = "Sign up",
                        textColor      = LescoNavy,
                        containerColor = LescoPrimary,
                        shimmerEnabled = true,
                        onClick        = {
                            val emptyFields = listOf(
                                fullName.isEmpty(),
                                email.isEmpty(),
                                password.isEmpty(),
                                confirmPassword.isEmpty()
                            ).count { it }

                            fun shakeFirstError() {
                                scope.launch {
                                    when {
                                        fullNameError                           -> doShake(shakeFullName)
                                        emailError                              -> doShake(shakeEmail)
                                        passwordError                           -> doShake(shakePassword)
                                        confirmPasswordError || confirmMismatch -> doShake(shakeConfirmPassword)
                                    }
                                }
                            }

                            when {
                                // ── ≥ 2 champs vides ──
                                emptyFields >= 2 -> {
                                    fullNameError        = fullName.isEmpty()
                                    emailError           = email.isEmpty()
                                    passwordError        = password.isEmpty()
                                    confirmPasswordError = confirmPassword.isEmpty()
                                    errorMessage         = "Please fill in all fields"
                                    if(!isValidEmail(email)){
                                        emailError   = true
                                        errorMessage = "Invalid email format"
                                        scope.launch { doShake(shakeEmail) }
                                    }
                                    shakeFirstError()
                                }
                                // ── Email invalide ──
                                !isValidEmail(email) -> {
                                    emailError   = true
                                    errorMessage = "Invalid email format"
                                    scope.launch { doShake(shakeEmail) }
                                }
                                // ── 1 seul champ vide ──
                                emptyFields == 1 -> {
                                    fullNameError        = fullName.isEmpty()
                                    emailError           = email.isEmpty()
                                    passwordError        = password.isEmpty()
                                    confirmPasswordError = confirmPassword.isEmpty()
                                    errorMessage         = when {
                                        passwordError && confirmPassword.length < 6 -> {
                                            confirmPasswordError = true
                                            "Password must be at least 6 characters"
                                        }
                                        else -> "Please fill in all fields"
                                    }
                                    scope.launch {
                                        if (fullName.isEmpty())        doShake(shakeFullName)
                                        if (email.isEmpty())           doShake(shakeEmail)
                                        if (confirmPassword.isEmpty()) doShake(shakeConfirmPassword)
                                    }
                                }
                                // ── Mots de passe différents ──
                                password != confirmPassword -> {
                                    confirmPasswordError = true
                                    errorMessage         = "Passwords do not match"
                                    scope.launch { doShake(shakeConfirmPassword) }
                                }
                                // ── Password trop court ──
                                password.length < 6 -> {
                                    passwordError = true
                                    errorMessage  = "Password must be at least 6 characters"
                                    scope.launch { doShake(shakePassword) }
                                }
                                // ── Tout OK → appel API ──
                                else -> {
                                    isLoading    = true
                                    errorMessage = ""
                                    scope.launch {
                                        try {
                                            // 1. Register
                                            val registerResponse = RetrofitInstance.api.register(
                                                RegisterRequest(
                                                    name     = fullName.trim(),
                                                    email    = email.trim(),
                                                    password = password
                                                )
                                            )
                                            if (!registerResponse.isSuccessful) {
                                                errorMessage = when (registerResponse.code()) {
                                                    400  -> "Email already registered"
                                                    else -> "Error: ${registerResponse.code()}"
                                                }
                                                return@launch
                                            }

                                            // 2. Login automatique
                                            val loginResponse = RetrofitInstance.api.login(
                                                email    = email.trim(),
                                                password = password
                                            )
                                            if (!loginResponse.isSuccessful || loginResponse.body() == null) {
                                                errorMessage = "Registration ok but login failed"
                                                return@launch
                                            }

                                            // 3. Sauvegarder le token
                                            TokenManager.saveToken(loginResponse.body()!!.access_token)

                                            // 4. Récupérer le profil
                                            val profile = RetrofitInstance.api.getMyProfile(TokenManager.getAuthHeader())
                                            if (profile.isSuccessful && profile.body() != null) {
                                                SessionManager.userId = profile.body()!!.id
                                            }

                                            // 5. Naviguer seulement quand tout est prêt
                                            onSignUpSuccess()

                                        } catch (e: Exception) {
                                            errorMessage = "Cannot connect to server"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Already have an account ? Log in ──
            val annotatedText = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)) {
                    append("Already have an account ? ")
                }
                withStyle(
                    SpanStyle(
                        color          = LescoPrimary,
                        fontSize       = 14.sp,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("Log in")
                }
            }
            Text(
                text     = annotatedText,
                modifier = Modifier.clickable { onLoginClick() }
            )

            Spacer(modifier = Modifier.height(133.dp))
        }
    }
}