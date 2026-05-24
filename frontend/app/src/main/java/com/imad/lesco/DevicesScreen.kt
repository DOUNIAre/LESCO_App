package com.imad.lesco

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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

private fun parseBackendError(errBody: String): String {
    try {
        val detailKey = "\"detail\":"
        if (errBody.contains(detailKey)) {
            val startIndex = errBody.indexOf(detailKey) + detailKey.length
            var content = errBody.substring(startIndex).trim()
            if (content.startsWith("\"")) {
                content = content.substring(1)
            }
            var endIndex = -1
            for (i in content.indices) {
                if (content[i] == '"' && (i == 0 || content[i - 1] != '\\')) {
                    endIndex = i
                    break
                }
            }
            if (endIndex != -1) {
                return content.substring(0, endIndex)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
        }
    } catch (_: Exception) {}
    return errBody
}

@Composable
fun DevicesScreen(onBack: () -> Unit, roomId: Int, onAddDeviceClick: () -> Unit) {
    var devices  by remember { mutableStateOf<List<DeviceResponse>>(emptyList()) }
    var roomName by remember { mutableStateOf("Loading...") }
    var errorMsg by remember { mutableStateOf("") }
    var loading  by remember { mutableStateOf(true) }
    var roomType by remember { mutableStateOf("shared") }
    val scope    = rememberCoroutineScope()

    // Per-device UI state maps
    val statusMap  = remember { mutableStateMapOf<Int, Boolean>() }
    val valueMap   = remember { mutableStateMapOf<Int, String>() }
    val settingMap = remember { mutableStateMapOf<Int, Boolean>() }

    // Conflict resolution dialog state
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictDevice     by remember { mutableStateOf<DeviceResponse?>(null) }
    var applyingResolution by remember { mutableStateOf(false) }

    // Helper: reload devices from server
    fun refreshDevices() {
        scope.launch {
            try {
                val res = RetrofitInstance.api.getDevices(
                    token  = TokenManager.getAuthHeader(),
                    roomId = roomId
                )
                if (res.isSuccessful && res.body() != null) {
                    devices = res.body()!!
                    devices.forEach { d ->
                        statusMap[d.id] = d.status
                        if (d.deviceType.uppercase() in listOf("AC", "HEATER")) {
                            valueMap[d.id] = if (d.value > 0) d.value.toString() else "22"
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(roomId) {
        scope.launch {
            // Load room name
            try {
                val roomsRes = RetrofitInstance.api.getRooms(
                    token   = TokenManager.getAuthHeader(),
                    houseId = SessionManager.houseId
                )
                if (roomsRes.isSuccessful && roomsRes.body() != null) {
                    val rObj = roomsRes.body()!!.find { it.id == roomId }
                    roomName = rObj?.name ?: "Room #$roomId"
                    roomType = rObj?.roomType ?: "shared"
                }
            } catch (_: Exception) {
                roomName = "Room #$roomId"
            }

            // Load devices
            try {
                val res = RetrofitInstance.api.getDevices(
                    token  = TokenManager.getAuthHeader(),
                    roomId = roomId
                )
                if (res.isSuccessful && res.body() != null) {
                    devices = res.body()!!
                    devices.forEach { d ->
                        statusMap[d.id] = d.status
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
                text       = "Devices in $roomName",
                color      = Color.White,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(bottom = 16.dp)
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
                loading       -> Text("Loading...", color = Color.White)
                devices.isEmpty() -> Text("No devices in this room.", color = Color(0xFFBFD6D1))
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(devices) { _, device ->
                        val checked   = statusMap[device.id] ?: false
                        val isHvac    = device.deviceType.uppercase() in listOf("AC", "HEATER")
                        val tempVal   = valueMap[device.id] ?: "22"
                        val isSetting = settingMap[device.id] ?: false
                        val canDelete = SessionManager.isOwner() || (device.createdBy == SessionManager.userId)

                        GlassCard {
                            // ── Device header row (name + Remove button) ──────
                            Row(
                                modifier            = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment   = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        device.name,
                                        color      = Color.White,
                                        fontSize   = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Type: ${device.deviceType}",
                                        color    = Color(0xFFBFD6D1),
                                        fontSize = 12.sp
                                    )
                                    if (isHvac) {
                                        Text(
                                            "Current setpoint: ${device.value}°C",
                                            color    = Color(0xFFBFD6D1),
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
                                                        token    = TokenManager.getAuthHeader(),
                                                        deviceId = device.id
                                                    )
                                                    if (res.isSuccessful) {
                                                        refreshDevices()
                                                    } else {
                                                        errorMsg = "Failed to remove device."
                                                    }
                                                } catch (e: Exception) {
                                                    errorMsg = "Network error."
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0x26FF6B6B),
                                            contentColor   = Color(0xFFFF6B6B)
                                        ),
                                        shape          = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier       = Modifier.height(32.dp)
                                    ) {
                                        Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // ── Toggle ON/OFF ─────────────────────────────────
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(if (checked) "ON" else "OFF", color = Color.White)
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { newVal ->
                                        statusMap[device.id] = newVal
                                        scope.launch {
                                            try {
                                                val res = RetrofitInstance.api.toggleDevice(
                                                    token    = TokenManager.getAuthHeader(),
                                                    deviceId = device.id
                                                )
                                                if (res.isSuccessful) {
                                                    val body = res.body()
                                                    if (body?.status == "conflict_detected") {
                                                        // Revert optimistic toggle, show dialog
                                                        statusMap[device.id] = !newVal
                                                        conflictDevice     = device
                                                        showConflictDialog = true
                                                    }
                                                    // else toggle succeeded, local state already correct
                                                } else {
                                                    val errBody = res.errorBody()?.string() ?: ""
                                                    statusMap[device.id] = !newVal
                                                    if (errBody.contains("Safety") || errBody.contains("Cannot")) {
                                                        errorMsg = "⛔ ${parseBackendError(errBody)}"
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

                            // ── Temperature setpoint — AC / HEATER only ───────
                            if (isHvac && checked) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Set Temperature (16°C – 28°C)",
                                    color      = LescoPrimary,
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value         = tempVal,
                                        onValueChange = { valueMap[device.id] = it },
                                        label         = { Text("°C", color = Color(0xFFBFD6D1)) },
                                        singleLine    = true,
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
                                        text      = if (isSetting) "Setting..." else "Set",
                                        textColor = LescoNavy,
                                        containerColor = LescoPrimary,
                                        enabled   = !isSetting,
                                        modifier  = Modifier.width(80.dp)
                                    ) {
                                        val numVal = tempVal.toIntOrNull()
                                        if (numVal == null || numVal < 16 || numVal > 28) return@GlassButton
                                        settingMap[device.id] = true
                                        scope.launch {
                                            try {
                                                val res = RetrofitInstance.api.setDeviceValue(
                                                    token    = TokenManager.getAuthHeader(),
                                                    deviceId = device.id,
                                                    body     = mapOf("value" to numVal)
                                                )
                                                if (res.isSuccessful) {
                                                    val body   = res.body()
                                                    val status = body?.get("status")?.toString() ?: ""
                                                    if (status == "conflict_detected") {
                                                        // Block the set — show conflict dialog
                                                        conflictDevice     = device
                                                        showConflictDialog = true
                                                    } else {
                                                        // Accepted — refresh to show updated setpoint
                                                        refreshDevices()
                                                    }
                                                } else {
                                                    val errBody = res.errorBody()?.string() ?: ""
                                                    if (errBody.contains("Safety") || errBody.contains("Cannot")) {
                                                        errorMsg = "⛔ ${parseBackendError(errBody)}"
                                                    }
                                                }
                                            } catch (_: Exception) {
                                                errorMsg = "Network error."
                                            }
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

            val isSharedRoom = roomType.uppercase() == "SHARED"
            val isOwner = SessionManager.isOwner()
            val canAddDevice = isOwner || !isSharedRoom

            if (canAddDevice) {
                GlassButton(
                    text           = "Add Device",
                    textColor      = LescoNavy,
                    containerColor = LescoPrimary,
                    onClick        = onAddDeviceClick
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
