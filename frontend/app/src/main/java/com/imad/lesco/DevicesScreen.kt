package com.imad.lesco

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
fun DevicesScreen(onBack: () -> Unit, roomId: Int, onAddDeviceClick: () -> Unit) {
    var devices  by remember { mutableStateOf<List<DeviceResponse>>(emptyList()) }
    var roomName by remember { mutableStateOf("Loading...") }
    var errorMsg by remember { mutableStateOf("") }
    var loading  by remember { mutableStateOf(true) }
    val scope    = rememberCoroutineScope()

    // Map local pour refléter le toggle immédiatement en UI
    val statusMap = remember { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(roomId) {
        scope.launch {
            try {
                // Fetch rooms list to find the actual name of this room
                val roomsRes = RetrofitInstance.api.getRooms(
                    token = TokenManager.getAuthHeader(),
                    houseId = SessionManager.houseId
                )
                if (roomsRes.isSuccessful && roomsRes.body() != null) {
                    val rObj = roomsRes.body()!!.find { it.id == roomId }
                    if (rObj != null) {
                        roomName = rObj.name
                    } else {
                        roomName = "Room #${roomId}"
                    }
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
                    devices.forEach { statusMap[it.id] = it.status }
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
                    itemsIndexed(devices) { index, device ->
                        val checked = statusMap[device.id] ?: false
                        GlassCard {
                            Text(device.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Room: $roomName",
                                color = Color(0xFFBFD6D1),
                                fontSize = 13.sp
                            )
                            Text(
                                "Type: ${device.deviceType}  |  Value: ${device.value}",
                                color = Color(0xFFBFD6D1),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))


                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (checked) "ON" else "OFF", color = Color.White)
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { newVal ->
                                        // Optimistic update
                                        statusMap[device.id] = newVal
                                        scope.launch {
                                            try {
                                                val res = RetrofitInstance.api.toggleDevice(
                                                    token = TokenManager.getAuthHeader(),
                                                    deviceId = device.id
                                                )
                                                if (!res.isSuccessful) {
                                                    // Rollback si erreur
                                                    statusMap[device.id] = !newVal
                                                }
                                            } catch (e: Exception) {
                                                statusMap[device.id] = !newVal
                                            }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = LescoNavy,
                                        checkedTrackColor = LescoPrimary,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color(0xFF505E69)
                                    )
                                )
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
