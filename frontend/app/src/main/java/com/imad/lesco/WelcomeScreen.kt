package com.imad.lesco

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay


@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "welcome"
    ) {
        composable("welcome") {
            WelcomeScreen(
                onLoginClick  = { navController.navigate("login") },
                onSignUpClick = { navController.navigate("signup") }
            )
        }
        composable("login") {
            LoginScreen(
                onBackClick           = { navController.popBackStack() },
                onLoginSuccess        = {
                    if (SessionManager.houseId != -1) {
                        navController.navigate("dashboard") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    } else {
                        navController.navigate("house_setup")
                    }
                },
                onSignUpClick         = { navController.navigate("signup") },
                onForgotPasswordClick = { navController.navigate("forgot_password") }
            )
        }
        composable("forgot_password") {
            ForgotPasswordScreen(
                onBackClick       = { navController.popBackStack() },
                onPasswordChanged = { navController.navigate("login") }
            )
        }
        composable("signup") {
            SignUpScreen(
                onBackClick     = { navController.popBackStack() },
                onSignUpSuccess = { navController.navigate("house_setup?firstTime=true") },
                onLoginClick    = { navController.navigate("login") }
            )
        }
        composable("house_setup?firstTime={firstTime}") { backStack ->
            val firstTime = backStack.arguments?.getString("firstTime")?.toBoolean() ?: false
            HouseSetupScreen(
                onContinue = {
                    if (firstTime) {
                        navController.navigate("preferences?firstTime=true") {
                            popUpTo("welcome") { inclusive = false }
                        }
                    } else {
                        navController.navigate("dashboard") {
                            popUpTo("welcome") { inclusive = false }
                        }
                    }
                },
                onJoinedAsMember = {
                    // Member just joined via invite code → must set up preferences first
                    navController.navigate("preferences?firstTime=true") {
                        popUpTo("welcome") { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("dashboard") {
            DashboardScreen(
                onOpenRooms           = { roomId ->
                    if (roomId == -1) {
                        navController.navigate("rooms")
                    } else {
                        navController.navigate("devices/$roomId")
                    }
                },
                onOpenRecommendations = { navController.navigate("recommendations") },
                onOpenSummary         = { navController.navigate("summary") },
                onOpenHistory         = { navController.navigate("history") },
                onOpenAssignment      = { navController.navigate("assignments") },
                onOpenProfile         = { navController.navigate("profile") },
                onOpenHouseDevices    = { navController.navigate("house_devices") },
                onOpenNotifications   = { navController.navigate("notifications") },
                onLogout = {
                    TokenManager.clearToken()
                    SessionManager.clear()
                    navController.navigate("welcome") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }
        composable("house_devices") {
            val houseId = if (SessionManager.houseId != -1) SessionManager.houseId else 1
            HouseDevicesScreen(onBack = { navController.popBackStack() }, houseId = houseId)
        }
        composable("rooms") {
            RoomsScreen(
                onBack           = { navController.popBackStack() },
                onRoomSelected   = { roomId -> navController.navigate("devices/$roomId") },
                onAddRoomClick   = { navController.navigate("add_room") }
            )
        }
        composable("add_room") {
            AddRoomScreen(
                onBack = { navController.popBackStack() },
                onRoomAdded = { navController.popBackStack() }
            )
        }
        // ── CHANGEMENT ICI : on passe le roomId au DevicesScreen ──
        composable("devices/{roomId}") { backStack ->
            val roomId = backStack.arguments?.getString("roomId")?.toIntOrNull() ?: -1
            DevicesScreen(
                onBack = { navController.popBackStack() },
                roomId = roomId,
                onAddDeviceClick = { navController.navigate("add_device/$roomId") }
            )
        }
        composable("add_device/{roomId}") { backStack ->
            val roomId = backStack.arguments?.getString("roomId")?.toIntOrNull() ?: -1
            AddDeviceScreen(
                roomId = roomId,
                onBack = { navController.popBackStack() },
                onDeviceAdded = { navController.popBackStack() }
            )
        }
        composable("recommendations") {
            RecommendationsScreen(onBack = { navController.popBackStack() })
        }
        composable("summary") {
            HouseSummaryScreen(onBack = { navController.popBackStack() })
        }
        composable("history") {
            DeviceHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable("assignments") {
            RoomAssignmentsScreen(onBack = { navController.popBackStack() })
        }
        composable("profile") {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onPreferencesClick = { navController.navigate("preferences") },
                onHouseSetupClick = { navController.navigate("house_setup") }
            )
        }
        composable("preferences?firstTime={firstTime}") { backStack ->
            val firstTime = backStack.arguments?.getString("firstTime")?.toBoolean() ?: false
            PreferencesScreen(
                onBack = { navController.popBackStack() },
                firstTime = firstTime,
                onProceed = {
                    navController.navigate("dashboard") {
                        popUpTo("welcome") { inclusive = false }
                    }
                }
            )
        }
        composable("notifications") {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }
    }
}


// ─── Typing Animation ────────────────────────────────────────────────────────
// Retourne le texte progressivement, lettre par lettre, avec un délai par caractère
@Composable
fun rememberTypingText(fullText: String, delayPerChar: Long = 40L): String {
    var displayed by remember { mutableStateOf("") }
    LaunchedEffect(fullText) {
        displayed = ""
        for (i in fullText.indices) {
            delay(delayPerChar)
            displayed = fullText.substring(0, i + 1)
        }
    }
    return displayed
}

// ─── Welcome Screen ───────────────────────────────────────────────────────────
@Composable
fun WelcomeScreen(onLoginClick: () -> Unit, onSignUpClick: () -> Unit) {

    // Texte complet du tagline (sans couleurs, on les applique après)
    val fullLine1 = "for an Optimized Comfort"
    val fullLine2 = "and a Reduced Consumption."

    val typedLine1 = rememberTypingText(fullLine1, delayPerChar = 45L)
    // La ligne 2 commence à s'afficher seulement quand la ligne 1 est terminée
    val startLine2 = typedLine1.length == fullLine1.length
    val typedLine2 = if (startLine2) rememberTypingText(fullLine2, delayPerChar = 45L) else ""

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LescoBackground()
        
        Image(
            painter = painterResource(id = R.drawable.tape1),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentScale = ContentScale.FillWidth
        )
        Image(
            painter = painterResource(id = R.drawable.tape2),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentScale = ContentScale.FillWidth
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(200.dp))
            Image(
                painter = painterResource(id = R.drawable.logo_lesco),
                contentDescription = "Lesco Logo",
                modifier = Modifier.size(width = 245.dp, height = 135.dp)
            )
            Spacer(modifier = Modifier.height(100.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Welcome to",
                    color = Color.White,
                    fontSize = 23.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(6.dp))
                Image(
                    painter = painterResource(id = R.drawable.mini_logo),
                    contentDescription = "Lesco mini",
                    modifier = Modifier
                        .size(width = 55.dp, height = 30.dp)
                        .offset(y = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // ── Tagline avec typing animation ──
            // On colorie "Comfort" et "Consumption." en turquoise au fur et à mesure
            Text(
                text = buildAnnotatedString {
                    // Ligne 1 : "for an Optimized Comfort"
                    val breakWord1 = "for an Optimized "
                    val colored1   = "Comfort"

                    if (typedLine1.length <= breakWord1.length) {
                        withStyle(SpanStyle(color = Color.White)) { append(typedLine1) }
                    } else {
                        withStyle(SpanStyle(color = Color.White)) { append(breakWord1) }
                        val rest1 = typedLine1.substring(breakWord1.length)
                        withStyle(SpanStyle(color = LescoPrimary)) { append(rest1) }
                    }

                    // Ligne 2 : "\nand a Reduced Consumption."
                    if (typedLine2.isNotEmpty()) {
                        val breakWord2 = "\nand a Reduced "
                        val colored2   = "Consumption"
                        val line2WithBreak = "\n" + typedLine2

                        if (line2WithBreak.length <= breakWord2.length) {
                            withStyle(SpanStyle(color = Color.White)) { append(line2WithBreak) }
                        } else {
                            withStyle(SpanStyle(color = Color.White)) {
                                append(breakWord2)
                            }
                            val rest2 = typedLine2.substring(
                                (breakWord2.length - 1).coerceAtMost(typedLine2.length)
                            )
                            withStyle(SpanStyle(color = LescoPrimary)) { append(rest2) }
                        }
                    }
                },
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 27.sp,
                modifier = Modifier
                    .width(210.dp)
                    .heightIn(min = 54.dp) // évite le layout jump quand ligne 2 apparaît
            )

            Spacer(modifier = Modifier.height(120.dp))

            // Bouton Login
            LescoButton(
                text = "Login",
                onClick = onLoginClick,
                filled = false,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Bouton Sign up
            LescoButton(
                text = "Sign up",
                onClick = onSignUpClick,
                filled = true,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
    }
}