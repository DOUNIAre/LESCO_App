package com.imad.lesco

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun RecommendationsScreen(onBack: () -> Unit) {
    var recommendation by remember { mutableStateOf<RecommendationResponse?>(null) }
    var feedbackSent   by remember { mutableStateOf<Boolean?>(null) }
    var errorMsg       by remember { mutableStateOf("") }
    var loading        by remember { mutableStateOf(true) }
    val scope          = rememberCoroutineScope()

    var simTemp by remember { mutableStateOf("38") }
    var simWeather by remember { mutableStateOf("Sunny") }
    var isSimulating by remember { mutableStateOf(false) }
    var simStatusMsg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val res = RetrofitInstance.api.getRecommendation(
                    token   = TokenManager.getAuthHeader(),
                    houseId = SessionManager.houseId
                )
                if (res.isSuccessful && res.body() != null) {
                    recommendation = res.body()
                } else {
                    errorMsg = "Could not load recommendation (${res.code()})."
                }
            } catch (e: Exception) {
                errorMsg = "Network error: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun sendFeedback(accepted: Boolean) {
        val rec = recommendation ?: return
        scope.launch {
            try {
                RetrofitInstance.api.submitFeedback(
                    FeedbackRequest(
                        recommendation_id = rec.id,   // ← uses real id now
                        user_id           = SessionManager.userId,
                        response          = accepted
                    )
                )
                feedbackSent = accepted
            } catch (_: Exception) {}
        }
    }

    ThemedScreen(onBack = onBack) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

            Text(
                text      = "AI Recommendation",
                color     = Color.White,
                fontSize  = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            when {
                loading -> Text("Loading...", color = Color.White)

                errorMsg.isNotEmpty() -> Text(
                    errorMsg,
                    color    = Color(0xFFFF4444),
                    fontSize = 13.sp
                )

                recommendation == null -> Text(
                    "No recommendation available.",
                    color = Color(0xFFBFD6D1)
                )

                else -> {
                    val rec = recommendation!!

                    GlassCard {
                        // ── Main recommendation text ──
                        Text(
                            text       = rec.content,          // ← was rec.action (WRONG)
                            color      = Color.White,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // ── Proposed value ──
                        Text(
                            "Suggested value: ${rec.proposedValue}",
                            color    = LescoPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // ── Confidence ──
                        Text(
                            "Confidence: ${"%.0f".format(rec.confidenceScore * 100)}%",   // ← was rec.confidence
                            color    = Color(0xFFBFD6D1),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // ── Reason ──
                        Text(
                            "Reason: ${rec.reason}",
                            color    = Color(0xFFBFD6D1),
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Feedback buttons ──
                    when (feedbackSent) {
                        null -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassButton(
                                text           = "✓  Accept",
                                textColor      = LescoNavy,
                                containerColor = LescoPrimary,
                                shimmerEnabled = true
                            ) { sendFeedback(true) }

                            GlassButton(
                                text           = "✗  Decline",
                                textColor      = LescoPrimary,
                                containerColor = Color(0x4D3CDBC0)
                            ) { sendFeedback(false) }
                        }
                        true  -> Text(
                            "✓ Recommendation accepted",
                            color    = LescoPrimary,
                            fontSize = 14.sp
                        )
                        false -> Text(
                            "✗ Recommendation declined",
                            color    = Color(0xFFFF4444),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text      = "AI Simulation Sandbox",
                color     = Color.White,
                fontSize  = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            
            GlassCard {
                Text(
                    text = "Push a simulated extreme environmental state to test the PPO AI logic live on this house.",
                    color = Color(0xFFBFD6D1),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                GlassTextField(
                    value = simTemp,
                    placeholder = "Temperature (°C)",
                    onValueChange = { simTemp = it },
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                GlassTextField(
                    value = simWeather,
                    placeholder = "Weather Description (e.g. Sunny, Snowing)",
                    onValueChange = { simWeather = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                GlassButton(
                    text = if (isSimulating) "Simulating..." else "Simulate & Trigger AI",
                    textColor = LescoNavy,
                    containerColor = LescoPrimary,
                    enabled = !isSimulating && simTemp.isNotBlank() && simWeather.isNotBlank()
                ) {
                    val tempInt = simTemp.toIntOrNull()
                    if (tempInt == null) {
                        simStatusMsg = "Temperature must be a valid integer"
                        return@GlassButton
                    }
                    
                    scope.launch {
                        isSimulating = true
                        simStatusMsg = ""
                        try {
                            val res = RetrofitInstance.api.pushEnvironmentData(
                                houseId = SessionManager.houseId,
                                temp = tempInt,
                                weatherDesc = simWeather.trim()
                            )
                            if (res.isSuccessful && res.body() != null) {
                                val body = res.body()!!
                                val recMsg = body["recommendation"] as? String
                                simStatusMsg = if (recMsg != null) {
                                    "Triggered! AI generated new tip: $recMsg"
                                } else {
                                    "Triggered! Environment saved successfully."
                                }
                                
                                // Refresh current recommendation display
                                loading = true
                                val recRes = RetrofitInstance.api.getRecommendation(
                                    token   = TokenManager.getAuthHeader(),
                                    houseId = SessionManager.houseId
                                )
                                if (recRes.isSuccessful && recRes.body() != null) {
                                    recommendation = recRes.body()
                                }
                            } else {
                                simStatusMsg = "Failed to trigger: ${res.code()}"
                            }
                        } catch (e: Exception) {
                            simStatusMsg = "Simulation error: ${e.message}"
                        } finally {
                            isSimulating = false
                            loading = false
                        }
                    }
                }
                
                if (simStatusMsg.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(simStatusMsg, color = LescoPrimary, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


