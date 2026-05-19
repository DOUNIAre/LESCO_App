package com.imad.lesco

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
fun AddDeviceScreen(roomId: Int, onBack: () -> Unit, onDeviceAdded: () -> Unit) {
    var deviceName by remember { mutableStateOf("") }
    var deviceType by remember { mutableStateOf("LIGHT") }
    var errorMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ThemedScreen(onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Add a New Device", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(24.dp))

            GlassTextField(
                value = deviceName,
                placeholder = "Device Name (e.g. Main Light)",
                onValueChange = { deviceName = it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Custom dropdown selector for device types recognized by the AI
            GlassDropdownSelector(
                label = "Device Type",
                selected = deviceType,
                options = listOf("LIGHT", "AC", "HEATER", "TV", "FAN", "WINDOW", "CURTAIN", "WASHING_MACHINE", "DISH_WASHER", "FRIDGE"),
                onSelected = { deviceType = it }
            )
            Spacer(modifier = Modifier.height(32.dp))

            GlassButton(
                text = if (isLoading) "Adding..." else "Add Device",
                textColor = LescoNavy,
                containerColor = LescoPrimary,
                enabled = !isLoading && deviceName.isNotBlank() && deviceType.isNotBlank()
            ) {
                scope.launch {
                    isLoading = true
                    errorMsg = ""
                    try {
                        val res = RetrofitInstance.api.addDevice(
                            token = TokenManager.getAuthHeader(),
                            roomId = roomId,
                            request = CreateDeviceRequest(name = deviceName.trim(), device_type = deviceType.trim())
                        )
                        if (res.isSuccessful) {
                            onDeviceAdded()
                        } else {
                            errorMsg = "Failed to add device."
                        }
                    } catch (e: Exception) {
                        errorMsg = "Network error."
                    } finally {
                        isLoading = false
                    }
                }
            }

            if (errorMsg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(errorMsg, color = Color(0xFFFF4444), fontSize = 14.sp)
            }
        }
    }
}


