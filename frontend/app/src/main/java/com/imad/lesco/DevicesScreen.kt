package com.imad.lesco

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun DevicesScreen(onBack: () -> Unit, roomId: Int, onAddDeviceClick: () -> Unit) {
    var devices  by remember { mutableStateOf<List<DeviceResponse>>(emptyList()) }
    var roomName by remember { mutableStateOf("Loading...") }
    var errorMsg by remember { mutableStateOf("") }
    var loading  by remember { mutableStateOf(true) }
    val scope    = rememberCoroutineScope()

    // Map local pour refléter le toggle immédiatement en UI
    val statusMap = remember { mutableStateMapOf<Int, Boolean>() }
    // Map to track per-device temperature input values
    val valueMap  = remember { mutableStateMapOf<Int, String>() }
    // Track whether a temperature set action is in progress per device
    val settingMap = remember { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(roomId) {
        scope.launch {
            try {
                val roomsRes = RetrofitInstance.api.getRooms(
                    token = TokenManager.getAuthHeader(),
                    houseId = SessionManager.houseId
                )
                if (roomsRes.isSuccessful && roomsRes.body() != null) {
                    val rObj = roomsRes.body()!!.find { it.id == roomId }
                    roomName = rObj?.name ?: "Room #${roomId}"
                }
            } catch (_: Exception) {
                roomName = "Room #${roomId}"
            }

            try {
                val res = RetrofitInstance.api.getDevices(
                    token  = TokenManager.getAuthHeader(),
                    roomId = roomId
                )
                if (res.isSuccessful && res.body() != null) {
                    devices = res.body()!!
                    devices.forEach { d ->
                        statusMap[d.id] = d.status
                        // Pre-fill temperature with current device value or 22 default
                        if (d.deviceType.uppercase() in listOf("AC", "HEATER")) {
                            valueMap[d.id] = if (d.value > 0) d.value.toString() else "22"
                        }
                    }
                } else {
                    errorMsg = "Could not load devices."
                }
            } catch (e: Exception) {
                errorMsg = "Network error."
            } finally {
                loading = false
            }
        }
    }

    ThemedScreen(onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Devices in $roomName",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            when {
                loading -> Text("Loading...", color = Color.White)
                errorMsg.isNotEmpty() -> Text(errorMsg, color = Color(0xFFFF4444), fontSize = 13.sp)
                devices.isEmpty() -> Text("No devices in this room.", color = Color(0xFFBFD6D1))
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(devices) { _, device ->
                        val checked = statusMap[device.id] ?: false
                        val isHvac  = device.deviceType.uppercase() in listOf("AC", "HEATER")
                        val tempVal = valueMap[device.id] ?: "22"
                        val isSetting = settingMap[device.id] ?: false
                        val canDelete = SessionManager.isOwner() || (device.createdBy == SessionManager.userId)

                        GlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Type: ${device.deviceType}",
                                        color = Color(0xFFBFD6D1),
                                        fontSize = 12.sp
                                    )
                                    if (isHvac) {
                                        Text(
                                            "Current setpoint: ${device.value}°C",
                                            color = Color(0xFFBFD6D1),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                if (canDelete) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    val res = RetrofitInstance.api.deleteDevice(
                                                        token = TokenManager.getAuthHeader(),
                                                        deviceId = device.id
                                                    )
                                                    if (res.isSuccessful) {
                                                        // Refresh devices list
                                                        val refreshRes = RetrofitInstance.api.getDevices(
                                                            token = TokenManager.getAuthHeader(),
                                                            roomId = roomId
                                                        )
                                                        if (refreshRes.isSuccessful && refreshRes.body() != null) {
                                                            devices = refreshRes.body()!!
                                                        }
                                                    } else {
                                                        errorMsg = "Failed to remove device."
                                                    }
                                                } catch (e: Exception) {
                                                    errorMsg = "Network error."
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0x26FF6B6B),
                                        contentColor = Color(0xFFFF6B6B)
                                    ),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = "Remove",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                               }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Toggle ON/OFF row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (checked) "ON" else "OFF", color = Color.White)
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { newVal ->
                                        statusMap[device.id] = newVal
                                        scope.launch {
                                            try {
                                                val res = RetrofitInstance.api.toggleDevice(
                                                    token = TokenManager.getAuthHeader(),
                                                    deviceId = device.id
                                                )
                                                if (!res.isSuccessful) {
                                                    statusMap[device.id] = !newVal
                                                }
                                            } catch (e: Exception) {
                                                statusMap[device.id] = !newVal
                                            }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor   = LescoNavy,
                                        checkedTrackColor   = LescoPrimary,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color(0xFF505E69)
                                    )
                                )
                            }

                            // Temperature row — only for AC / HEATER
                            if (isHvac && checked) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Set Temperature (16°C – 28°C)",
                                    color = LescoPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = tempVal,
                                        onValueChange = { valueMap[device.id] = it },
                                        label = { Text("°C", color = Color(0xFFBFD6D1)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor   = LescoPrimary,
                                            unfocusedBorderColor = Color(0xFF505E69),
                                            focusedTextColor     = Color.White,
                                            unfocusedTextColor   = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    GlassButton(
                                        text = if (isSetting) "Setting..." else "Set",
                                        textColor = LescoNavy,
                                        containerColor = LescoPrimary,
                                        enabled = !isSetting,
                                        modifier = Modifier.width(80.dp)
                                    ) {
                                        val numVal = tempVal.toIntOrNull()
                                        if (numVal == null || numVal < 16 || numVal > 28) return@GlassButton
                                        settingMap[device.id] = true
                                        scope.launch {
                                            try {
                                                RetrofitInstance.api.setDeviceValue(
                                                    token    = TokenManager.getAuthHeader(),
                                                    deviceId = device.id,
                                                    body     = mapOf("value" to numVal)
                                                )
                                            } catch (_: Exception) {}
                                            settingMap[device.id] = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassButton(
                text = "Add Device",
                textColor = LescoNavy,
                containerColor = LescoPrimary,
                onClick = onAddDeviceClick
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
