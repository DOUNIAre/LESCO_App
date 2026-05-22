package com.imad.lesco

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun HouseDevicesScreen(onBack: () -> Unit, houseId: Int) {
    var devices by remember { mutableStateOf<List<DeviceData>>(emptyList()) }
    var rooms by remember { mutableStateOf<List<RoomResponse>>(emptyList()) }
    var errorMsg by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val statusMap = remember { mutableStateMapOf<Int, Boolean>() }

    // Conflict resolution dialog state
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictDevice     by remember { mutableStateOf<DeviceData?>(null) }
    var applyingResolution by remember { mutableStateOf(false) }

    // Helper: reload devices from server
    fun refreshDevices() {
        scope.launch {
            try {
                val res = RetrofitInstance.api.getHouseDevices(
                    token   = TokenManager.getAuthHeader(),
                    houseId = houseId
                )
                if (res.isSuccessful && res.body() != null) {
                    devices = res.body()!!
                    devices.forEach { statusMap[it.id] = it.status }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(houseId) {
        try {
            val roomsRes = RetrofitInstance.api.getRooms(
                token = TokenManager.getAuthHeader(),
                houseId = houseId
            )
            if (roomsRes.isSuccessful && roomsRes.body() != null) {
                rooms = roomsRes.body()!!
            }

            val res = RetrofitInstance.api.getHouseDevices(
                token = TokenManager.getAuthHeader(),
                houseId = houseId
            )
            if (res.isSuccessful && res.body() != null) {
                devices = res.body()!!
                devices.forEach { statusMap[it.id] = it.status }
            } else {
                errorMsg = "Failed to load house devices."
            }
        } catch (e: Exception) {
            errorMsg = "Network error: ${e.localizedMessage}"
        } finally {
            loading = false
        }
    }

    // ── Conflict Resolution Dialog ────────────────────────────────────────────
    if (showConflictDialog && conflictDevice != null) {
        val dev = conflictDevice!!
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            shape            = RoundedCornerShape(24.dp),
            containerColor   = Color(0xFF16252C),
            title = {
                Text(
                    "⚠️ Preference Conflict",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        "Multiple family members have registered preferences for ${dev.deviceType} in this room.",
                        color    = Color(0xFFBFD6D1),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Manual overrides are blocked to prevent conflicts.\n\n" +
                        "Tap \"Apply Logic\" to let LESCO resolve this using Weighted Median / Majority Voting.",
                        color    = Color(0xFFBFD6D1),
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        applyingResolution = true
                        scope.launch {
                            try {
                                val res = RetrofitInstance.api.applyConflictResolution(
                                    token    = TokenManager.getAuthHeader(),
                                    roomId   = dev.roomId,
                                    category = dev.deviceType.uppercase()
                                )
                                if (res.isSuccessful) {
                                    showConflictDialog = false
                                    conflictDevice     = null
                                    refreshDevices()
                                } else {
                                    val errBody = res.errorBody()?.string() ?: ""
                                    errorMsg = if (errBody.contains("Safety") || errBody.contains("Cannot")) {
                                        "⛔ Safety constraint blocked resolution."
                                    } else {
                                        "Resolution failed. Try again."
                                    }
                                    showConflictDialog = false
                                }
                            } catch (_: Exception) {
                                errorMsg = "Network error during resolution."
                                showConflictDialog = false
                            } finally {
                                applyingResolution = false
                            }
                        }
                    },
                    enabled = !applyingResolution,
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = LescoPrimary,
                        contentColor   = LescoNavy
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (applyingResolution) "Resolving..." else "Apply Logic",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConflictDialog = false }) {
                    Text("Cancel", color = Color(0xFFBFD6D1))
                }
            }
        )
    }

    ThemedScreen(onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "All House Devices",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (errorMsg.isNotEmpty()) {
                Text(
                    errorMsg,
                    color    = Color(0xFFFF4444),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            when {
                loading -> Text("Loading devices...", color = Color.White)
                devices.isEmpty() -> Text("No devices found in this house.", color = Color.Gray)
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(devices) { _, device ->
                            val checked = statusMap[device.id] ?: false
                            GlassCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Device Icon
                                    val iconRes = getDeviceIcon(device.deviceType, checked)
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        val deviceName = device.name ?: device.deviceType.lowercase().replaceFirstChar { it.uppercase() }
                                        val roomName = rooms.find { it.id == device.roomId }?.name ?: "Unknown Room"
                                        
                                        Text(deviceName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("Room: $roomName", color = Color(0xFFBFD6D1), fontSize = 13.sp)
                                        Text(device.deviceType.uppercase(), color = LescoPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Switch(
                                        checked = checked,
                                        onCheckedChange = { newVal ->
                                            // Optimistic update
                                            statusMap[device.id] = newVal
                                            scope.launch {
                                                try {
                                                    val toggleRes = RetrofitInstance.api.toggleDevice(
                                                        token    = TokenManager.getAuthHeader(),
                                                        deviceId = device.id
                                                    )
                                                    if (toggleRes.isSuccessful) {
                                                        val body = toggleRes.body()
                                                        if (body?.status == "conflict_detected") {
                                                            // Revert optimistic toggle, show dialog
                                                            statusMap[device.id] = !newVal
                                                            conflictDevice     = device
                                                            showConflictDialog = true
                                                        }
                                                        // else: toggle succeeded, local state already correct
                                                    } else {
                                                        // HTTP error — revert
                                                        statusMap[device.id] = !newVal
                                                        val errBody = toggleRes.errorBody()?.string() ?: ""
                                                        if (errBody.contains("Safety") || errBody.contains("Cannot")) {
                                                            errorMsg = "⛔ $errBody"
                                                        }
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
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getDeviceIcon(type: String, active: Boolean): Int {
    val t = type.lowercase().trim()
    return when {
        t.contains("ac") || t.contains("air") -> if (active) R.drawable.ac_on else R.drawable.ac_off
        t.contains("light") || t.contains("lamp") -> if (active) R.drawable.lamp_on else R.drawable.lamp_off
        t.contains("tv") -> if (active) R.drawable.tv_on else R.drawable.tv_off
        t.contains("fridge") || t.contains("frigo") || t.contains("refrigerator") -> if (active) R.drawable.fridge_on else R.drawable.fridge_off
        t.contains("dishwasher") -> if (active) R.drawable.dishwasher_on else R.drawable.dishwasher_off
        t.contains("fan") -> if (active) R.drawable.fan_on else R.drawable.fan_off
        t.contains("wash") || t.contains("washing") || t.contains("machine") -> if (active) R.drawable.washingmachine_on else R.drawable.washingmachine_off
        t.contains("curtain") -> if (active) R.drawable.curtain_on else R.drawable.curtain_off
        t.contains("heater") -> if (active) R.drawable.ac_on else R.drawable.ac_off
        else -> if (active) R.drawable.tv_on else R.drawable.tv_off
    }
}
