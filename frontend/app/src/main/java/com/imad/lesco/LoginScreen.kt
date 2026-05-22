package com.imad.lesco

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onBackClick: () -> Unit, onLoginSuccess: () -> Unit,onSignUpClick: () -> Unit,onForgotPasswordClick: () -> Unit ){
    var email         by remember { mutableStateOf("") }
    var password      by remember { mutableStateOf("") }
    var isLoading     by remember { mutableStateOf(false) }
    var errorMessage  by remember { mutableStateOf<String?>(null) }
    var emailError    by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    val scope         = rememberCoroutineScope()
    val shakeEmail    = remember { Animatable(0f) }
    val shakePassword = remember { Animatable(0f) }

    suspend fun doShake(anim: Animatable<Float, AnimationVector1D>) {
        for (target in listOf(10f, -10f, 8f, -8f, 5f, -5f, 0f)) {
            anim.animateTo(target, tween(60, easing = LinearEasing))
        }
    }

    fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        return email.matches(emailRegex.toRegex())
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Background ──
        Image(
            painter      = painterResource(id = R.drawable.background2),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier     = Modifier.fillMaxSize()
        )

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
            modifier              = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(218.dp))

            // ── Titre ──
            Text(
                text       = "Log in",
                color      = Color.White,
                fontSize   = 23.sp,
                fontWeight = FontWeight.Normal,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // ── Champ Email ──
            GlassTextField(
                value         = email,
                placeholder   = "Email",
                onValueChange = { email = it; emailError = false; errorMessage = null },
                keyboardType  = KeyboardType.Email,
                shakeOffsetX  = shakeEmail.value,
                hasError      = emailError
            )
            Spacer(modifier = Modifier.height(20.dp))

            // ── Champ Password ──
            GlassTextField(
                value         = password,
                placeholder   = "Password",
                onValueChange = { password = it; passwordError = false; errorMessage = null },
                isPassword    = true,
                shakeOffsetX  = shakePassword.value,
                hasError      = passwordError
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── Forgot password ──
            Text(
                text           = "Forgot password ?",
                color          = Color(0xFF135C50),
                fontSize       = 15.sp,
                textDecoration = TextDecoration.Underline,
                modifier       = Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp)
                    .clickable{ onForgotPasswordClick()}
            )

            // ── Zone d'erreur : hauteur FIXE réservée en permanence ──
            Box(
                modifier          = Modifier
                    .fillMaxWidth()
                    .height(36.dp)          // hauteur fixe → ne décale JAMAIS le bouton
                    .padding(start = 26.dp, top = 8.dp),
                contentAlignment  = Alignment.CenterStart
            ) {
                if (errorMessage != null) {
                    Text(
                        text     = errorMessage!!,
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
                    .height(56.dp),         // même hauteur que GlassButton
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color    = LescoPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    GlassButton(
                        text           = "Log in",
                        textColor      = LescoNavy,
                        containerColor = LescoPrimary,
                        onClick        = {
                            emailError    = email.isBlank()
                            passwordError = password.isBlank()

                            when {
                                email.isBlank() && password.isBlank() -> {
                                    errorMessage = "Please fill in all fields"
                                    scope.launch { doShake(shakeEmail) }
                                }
                                !isValidEmail(email) -> {
                                    emailError   = true
                                    errorMessage = "Invalid email format"
                                    scope.launch { doShake(shakeEmail) }
                                }
                                email.isBlank() -> {
                                    errorMessage = "Email is required"
                                    scope.launch { doShake(shakeEmail) }
                                }
                                password.isBlank() -> {
                                    errorMessage = "Password is required"
                                    scope.launch { doShake(shakePassword) }
                                }
                                else -> {
                                    scope.launch {
                                        isLoading    = true
                                        errorMessage = null
                                        try {
                                            val response = RetrofitInstance.api.login(
                                                email    = email,
                                                password = password
                                            )
                                            if (response.isSuccessful) {
                                                val body = response.body()
                                                if (body != null) {
                                                    // ── Save token ──
                                                    TokenManager.saveToken(body.access_token)
                                                    // ── Save user info into session ──
                                                    SessionManager.userId = body.user.id
                                                    // If user already belongs to a house, pre-load it
                                                    val firstMembership = body.user.memberships.firstOrNull()
                                                    if (firstMembership != null) {
                                                        SessionManager.houseId = firstMembership.houseId
                                                        SessionManager.role    = firstMembership.role
                                                    }
                                                    onLoginSuccess()
                                                } else {
                                                    errorMessage = "Unexpected server response"
                                                }
                                            } else {
                                                emailError    = true
                                                passwordError = true
                                                errorMessage  = when (response.code()) {
                                                    401  -> "Invalid email or password"
                                                    404  -> "User not found"
                                                    else -> "Server error (${response.code()})"
                                                }
                                                scope.launch { doShake(shakeEmail) }
                                                scope.launch { doShake(shakePassword) }
                                            }
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

            // ── Don't have an account ? Sign up ──
            val annotatedText = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)) {
                    append("Don't have an account ? ")
                }
                withStyle(
                    SpanStyle(
                        color          = LescoPrimary,
                        fontSize       = 14.sp,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("Sign up")
                }
            }
            Text(
                text     = annotatedText,
                modifier = Modifier.clickable { onSignUpClick() }
            )

            Spacer(modifier = Modifier.height(133.dp))
        }
    }
}