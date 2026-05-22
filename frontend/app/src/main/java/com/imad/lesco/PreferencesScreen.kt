package com.imad.lesco

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun PreferencesScreen(onBack: () -> Unit) {
    var category by remember { mutableStateOf("TEMPERATURE") }
    var valueInput by remember { mutableStateOf("22") }
    var contextInput by remember { mutableStateOf("HOME") }
    var lightValueSelected by remember { mutableStateOf("ON") }
    
    var preferences by remember { mutableStateOf<List<PreferenceResponse>>(emptyList()) }
    var statusMsg by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadPreferences() {
        scope.launch {
            try {
                val res = RetrofitInstance.api.getPreferences(TokenManager.getAuthHeader())
                if (res.isSuccessful && res.body() != null) {
                    preferences = res.body()!!
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        loadPreferences()
    }

    ThemedScreen(onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Set Personal Preference", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))



            // Custom Dropdown for Category
            GlassDropdownSelector(
                label = "Category",
                selected = category,
                options = listOf("TEMPERATURE", "LIGHT", "BRIGHTNESS", "AC", "HEATER", "FAN", "TV"),
                onSelected = { 
                    category = it 
                    if (it == "LIGHT" || it == "TV") {
                        valueInput = if (lightValueSelected == "ON") "1" else "0"
                    } else if (it == "TEMPERATURE" || it == "AC" || it == "HEATER") {
                        valueInput = "22"
                    } else {
                        valueInput = ""
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Conditional Value Input based on Category
            val isBinaryCategory = category == "LIGHT" || category == "TV"
            if (isBinaryCategory) {
                GlassDropdownSelector(
                    label = "$category Preference",
                    selected = lightValueSelected,
                    options = listOf("ON", "OFF"),
                    onSelected = {
                        lightValueSelected = it
                        valueInput = if (it == "ON") "1" else "0"
                    }
                )
            } else {
                GlassTextField(
                    value = valueInput,
                    placeholder = if (category == "TEMPERATURE" || category == "AC" || category == "HEATER") "Desired Temperature (e.g. 22)" else "Desired Value (e.g. 50)",
                    onValueChange = { valueInput = it },
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Custom Dropdown for Context
            GlassDropdownSelector(
                label = "Context",
                selected = contextInput,
                options = listOf("HOME", "AWAY", "SLEEPING", "STUDYING"),
                onSelected = { contextInput = it }
            )
            Spacer(modifier = Modifier.height(32.dp))

            GlassButton(
                text = if (isLoading) "Saving..." else "Save Preference",
                textColor = LescoNavy,
                containerColor = LescoPrimary,
                enabled = !isLoading && category.isNotBlank() && valueInput.isNotBlank()
            ) {
                val numVal = valueInput.toIntOrNull()
                if (numVal == null) {
                     statusMsg = "Value must be numeric."
                     isSuccess = false
                     return@GlassButton
                }

                val isTempCategory = category == "TEMPERATURE" || category == "AC" || category == "HEATER"
                if (isTempCategory && (numVal < 16 || numVal > 28)) {
                     statusMsg = "Temperature must be between 16°C and 28°C."
                     isSuccess = false
                     return@GlassButton
                }

                if (category == "BRIGHTNESS" && (numVal < 0 || numVal > 100)) {
                     statusMsg = "Brightness must be between 0% and 100%."
                     isSuccess = false
                     return@GlassButton
                }
                
                scope.launch {
                    isLoading = true
                    statusMsg = ""
                    try {
                        val res = RetrofitInstance.api.setPreference(
                            token = TokenManager.getAuthHeader(),
                            preference = PreferenceRequest(
                                userId = SessionManager.userId,
                                category = category.trim(),
                                value = numVal,
                                context = contextInput.trim()
                            )
                        )
                        if (res.isSuccessful) {
                            statusMsg = "Preference saved successfully!"
                            isSuccess = true
                            loadPreferences() // reload after successfully saving
                        } else {
                            statusMsg = "Failed to save preference."
                            isSuccess = false
                        }
                    } catch (e: Exception) {
                        statusMsg = "Network error."
                        isSuccess = false
                    } finally {
                        isLoading = false
                    }
                }
            }

            if (statusMsg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(statusMsg, color = if(isSuccess) LescoPrimary else Color(0xFFFF4444), fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "Your Preferences",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (preferences.isEmpty()) {
                Text(
                    text = "No preferences defined yet.",
                    color = Color(0xFFBFD6D1),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
            } else {
                preferences.forEach { pref ->
                    val isPrefBinary = pref.category.uppercase() == "LIGHT" || pref.category.uppercase() == "TV"
                    val isPrefTemp = pref.category.uppercase() == "TEMPERATURE" || pref.category.uppercase() == "AC" || pref.category.uppercase() == "HEATER"
                    val displayValue = if (isPrefBinary) {
                        if (pref.value == 1) "ON" else "OFF"
                    } else if (isPrefTemp) {
                        "${pref.value} °C"
                    } else {
                        "${pref.value}"
                    }
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(pref.category, color = LescoPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Context: ${pref.context}", color = Color.White, fontSize = 15.sp)
                            }
                            Text(
                                text = displayValue,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


