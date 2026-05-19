package com.imad.lesco

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler

// ─── ForgotPasswordScreen ─────────────────────────────────────────────────────
@Composable
fun ForgotPasswordScreen(
    onBackClick: () -> Unit,
    onPasswordChanged: () -> Unit = {}
) {
    // ── Step 1 : email ──
    var email         by remember { mutableStateOf("") }
    var emailError    by remember { mutableStateOf(false) }
    var isLoadingStep1 by remember { mutableStateOf(false) }
    var errorMessage  by remember { mutableStateOf<String?>(null) }

    // ── Step 2 : code banner + code field ──
    var showCodeBanner by remember { mutableStateOf(false) }
    var verificationCode by remember { mutableStateOf("") }
    var codeError     by remember { mutableStateOf(false) }
    var isLoadingCode by remember { mutableStateOf(false) }

    // ── Step 3 : new password fields ──
    var showPasswordStep by remember { mutableStateOf(false) }
    var newPassword      by remember { mutableStateOf("") }
    var confirmPassword  by remember { mutableStateOf("") }
    var newPasswordError by remember { mutableStateOf(false) }
    var confirmPasswordError by remember { mutableStateOf(false) }
    var isLoadingChange  by remember { mutableStateOf(false) }

    BackHandler(enabled = showCodeBanner) {
        showCodeBanner = false
    }

    val scope         = rememberCoroutineScope()
    val shakeEmail    = remember { Animatable(0f) }
    val shakeCode     = remember { Animatable(0f) }
    val shakeNewPwd   = remember { Animatable(0f) }
    val shakeConfPwd  = remember { Animatable(0f) }

    suspend fun doShake(anim: Animatable<Float, AnimationVector1D>) {
        for (target in listOf(10f, -10f, 8f, -8f, 5f, -5f, 0f)) {
            anim.animateTo(target, tween(60, easing = LinearEasing))
        }
    }

    fun isValidEmail(e: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
        return e.matches(emailRegex.toRegex())
    }

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
                .clickable(enabled = !showCodeBanner) { onBackClick() }
        )

        // ── Formulaire (Step 1 ou Step 3) ── EN PREMIER = en dessous
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (showCodeBanner) 0.3f else 1f)
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(218.dp))

                // ── Titre ──
                Text(
                    text       = "Please Enter Your Email",
                    color      = Color.White,
                    fontSize   = 27.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ══════════════════════════════════════════════
                // STEP 1 — Email + bouton Confirm
                // ══════════════════════════════════════════════
                if (!showPasswordStep) {

                    // ── Champ Email ──
                    GlassTextField(
                        value         = email,
                        placeholder   = "Email",
                        onValueChange = { email = it; emailError = false; errorMessage = null },
                        keyboardType  = KeyboardType.Email,
                        shakeOffsetX  = shakeEmail.value,
                        hasError      = emailError,
                        enabled       = !showCodeBanner
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Zone d'erreur fixe ──
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .padding(start = 26.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        if (errorMessage != null) {
                            Text(
                                text     = errorMessage!!,
                                color    = Color(0xFFFF6B6B),
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // ── Bouton Confirm ──
                    Box(
                        modifier         = Modifier
                            .width(302.dp)

                            .height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingStep1) {
                            CircularProgressIndicator(
                                color    = LescoPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            GlassButton(
                                text           = "Confirm",
                                textColor      = LescoNavy,
                                containerColor = LescoPrimary,
                                enabled        = !showCodeBanner,
                                onClick        = {
                                    errorMessage = null
                                    when {
                                        email.isBlank() -> {
                                            emailError   = true
                                            errorMessage = "Email is required"
                                            scope.launch { doShake(shakeEmail) }
                                        }
                                        !isValidEmail(email) -> {
                                            emailError   = true
                                            errorMessage = "Invalid email format"
                                            scope.launch { doShake(shakeEmail) }
                                        }
                                        else -> {
                                            scope.launch {
                                                isLoadingStep1 = true
                                                try {
                                                    val response = RetrofitInstance.api.forgotPassword(email)
                                                    if (response.isSuccessful) {
                                                        showCodeBanner = true
                                                    } else {
                                                        errorMessage = "User not found"
                                                        emailError = true
                                                        doShake(shakeEmail)
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = "Cannot connect to server"
                                                } finally {
                                                    isLoadingStep1 = false
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // ══════════════════════════════════════════════
                    // STEP 3 — New password + Confirm password
                    // ══════════════════════════════════════════════
                } else {

                    // ── Champ New Password ──
                    GlassTextField(
                        value         = newPassword,
                        placeholder   = "New password",
                        onValueChange = {
                            newPassword = it; newPasswordError = false; errorMessage = null
                        },
                        isPassword    = true,
                        shakeOffsetX  = shakeNewPwd.value,
                        hasError      = newPasswordError,
                        enabled       = !showCodeBanner
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Champ Confirm Password ──
                    GlassTextField(
                        value         = confirmPassword,
                        placeholder   = "Confirm password",
                        onValueChange = {
                            confirmPassword = it; confirmPasswordError = false; errorMessage = null
                        },
                        isPassword    = true,
                        shakeOffsetX  = shakeConfPwd.value,
                        hasError      = confirmPasswordError,
                        enabled       = !showCodeBanner
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Zone d'erreur fixe ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .padding(start = 26.dp, top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (errorMessage != null) {
                            Text(
                                text     = errorMessage!!,
                                color    = Color(0xFFFF6B6B),
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // ── Bouton Change password ──
                    Box(
                        modifier         = Modifier
                            .width(302.dp)

                            .height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingChange) {
                            CircularProgressIndicator(
                                color    = LescoPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            GlassButton(
                                text           = "Change password",
                                textColor      = LescoNavy,
                                containerColor = LescoPrimary,
                                enabled        = !showCodeBanner,
                                onClick        = {
                                    errorMessage        = null
                                    newPasswordError    = newPassword.isBlank()
                                    confirmPasswordError = confirmPassword.isBlank()

                                    when {
                                        newPassword.isBlank() && confirmPassword.isBlank() -> {
                                            errorMessage = "Please fill in all fields"
                                            scope.launch { doShake(shakeNewPwd) }
                                        }
                                        newPassword.isBlank() -> {
                                            errorMessage = "New password is required"
                                            scope.launch { doShake(shakeNewPwd) }
                                        }
                                        newPassword.length < 6 -> {
                                            newPasswordError = true
                                            errorMessage     = "Password must be at least 6 characters"
                                            scope.launch { doShake(shakeNewPwd) }
                                        }
                                        confirmPassword.isBlank() -> {
                                            errorMessage = "Please confirm your password"
                                            scope.launch { doShake(shakeConfPwd) }
                                        }
                                        newPassword != confirmPassword -> {
                                            confirmPasswordError = true
                                            errorMessage        = "Passwords do not match"
                                            scope.launch { doShake(shakeConfPwd) }
                                        }
                                        else -> {
                                            scope.launch {
                                                isLoadingChange = true
                                                try {
                                                    val response = RetrofitInstance.api.resetPassword(email, verificationCode, newPassword)
                                                    if (response.isSuccessful) {
                                                        onPasswordChanged()
                                                    } else {
                                                        errorMessage = "Reset failed. Try again."
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = "Cannot connect to server"
                                                } finally {
                                                    isLoadingChange = false
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(170.dp))
            }
        }

        // ── Popup centrale (code envoyé) ── EN DERNIER = par-dessus tout
        AnimatedVisibility(
            visible  = showCodeBanner,
            enter    = fadeIn(tween(300)),
            exit     = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = showCodeBanner,
                    enter   = slideInVertically(tween(300)) { it / 6 },
                    exit    = slideOutVertically(tween(250)) { it / 6 }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .background(Color(0xFF1A2B38), RoundedCornerShape(12.dp))
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter            = painterResource(id = R.drawable.xboutton),
                            contentDescription = "close",
                            modifier           = Modifier
                                .size(35.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = (10).dp, y = (-15).dp)
                                .clip(RoundedCornerShape(50))
                                //.background(Color(0xFF253746))
                                .clickable {
                                    showCodeBanner = false
                                }
                                .padding(4.dp)
                                .zIndex(1f)                       // garantit qu'il est par-dessus
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A2B38), RoundedCornerShape(7.dp))
                                .padding(horizontal = 16.dp, vertical = 20.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                                Text(
                                    text       = "Verification code sent !",
                                    color      = Color.White,
                                    fontSize   = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text       = "A code has been sent to\n$email",
                                    color      = Color(0x64748B).copy(alpha = 0.80f),
                                    fontSize   = 13.sp,
                                    lineHeight = 17.sp,
                                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                // ── Champ code de vérification ──
                                GlassTextField(
                                    value         = verificationCode,
                                    placeholder   = "xxx-xxx",
                                    onValueChange = {
                                        if (it.length <= 6) {
                                            verificationCode = it; codeError = false; errorMessage = null
                                        }
                                    },
                                    keyboardType  = KeyboardType.Number,
                                    shakeOffsetX  = shakeCode.value,
                                    hasError      = codeError,
                                    enabled       = true
                                )

                                if (errorMessage != null && showCodeBanner) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text     = errorMessage!!,
                                        color    = Color(0xFFFF6B6B),
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // ── Bouton Confirm code ──
                                Box(
                                    modifier         = Modifier
                                        .width(220.dp)
                                        .height(50.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingCode) {
                                        CircularProgressIndicator(
                                            color    = LescoPrimary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    } else {
                                        GlassButton(
                                            text           = "Confirm code",
                                            textColor      = LescoNavy,
                                            containerColor = LescoPrimary,
                                            enabled        = true,
                                            onClick        = {
                                                if (verificationCode.isBlank()) {
                                                    codeError    = true
                                                    errorMessage = "Please enter the verification code"
                                                    scope.launch { doShake(shakeCode) }
                                                    return@GlassButton
                                                }
                                                scope.launch {
                                                    isLoadingCode = true
                                                    errorMessage  = null
                                                    try {
                                                        val response = RetrofitInstance.api.verifyEmail(email, verificationCode)
                                                        if (response.isSuccessful) {
                                                            showCodeBanner   = false
                                                            showPasswordStep = true
                                                        } else {
                                                            errorMessage = "Invalid verification code"
                                                            codeError = true
                                                            doShake(shakeCode)
                                                        }
                                                    } catch (e: Exception) {
                                                        errorMessage = "Cannot connect to server"
                                                    } finally {
                                                        isLoadingCode = false
                                                    }
                                                }
                                            }
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
}