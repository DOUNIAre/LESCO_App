package com.imad.lesco

import androidx.compose.foundation.layout.*
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
fun AddRoomScreen(onBack: () -> Unit, onRoomAdded: () -> Unit) {
    var roomName by remember { mutableStateOf("") }
    var roomPhysicalType by remember { mutableStateOf("LIVING_ROOM") }
    var roomAccessMode by remember { mutableStateOf("SHARED") }
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
            Text("Add a New Room", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(24.dp))

            GlassTextField(
                value = roomName,
                placeholder = "Room Name (e.g. Master)",
                onValueChange = { roomName = it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            GlassDropdownSelector(
                label = "Room Category",
                selected = roomPhysicalType,
                options = listOf("LIVING_ROOM", "KITCHEN", "BEDROOM", "BATHROOM"),
                onSelected = { roomPhysicalType = it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            GlassDropdownSelector(
                label = "Access Mode",
                selected = roomAccessMode,
                options = listOf("SHARED", "PERSONAL"),
                onSelected = { roomAccessMode = it }
            )
            Spacer(modifier = Modifier.height(32.dp))

            GlassButton(
                text = if (isLoading) "Adding..." else "Add Room",
                textColor = LescoNavy,
                containerColor = LescoPrimary,
                enabled = !isLoading && roomName.isNotBlank()
            ) {
                scope.launch {
                    isLoading = true
                    errorMsg = ""
                    try {
                        val formattedType = roomPhysicalType.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
                        val finalName = "${roomName.trim()} ($formattedType)"
                        
                        val res = RetrofitInstance.api.createRoom(
                            token = TokenManager.getAuthHeader(),
                            houseId = SessionManager.houseId,
                            request = CreateRoomRequest(
                                name = finalName,
                                room_type = roomAccessMode.lowercase().trim()
                            )
                        )
                        if (res.isSuccessful) {
                            onRoomAdded()
                        } else {
                            errorMsg = "Failed to add room."
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
