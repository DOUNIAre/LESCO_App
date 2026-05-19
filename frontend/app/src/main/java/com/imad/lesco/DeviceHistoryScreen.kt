package com.imad.lesco

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun DeviceHistoryScreen(onBack: () -> Unit) {
    var history  by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var errorMsg by remember { mutableStateOf("") }
    var loading  by remember { mutableStateOf(true) }
    val scope    = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val res = RetrofitInstance.api.getHouseHistory(
                    token   = TokenManager.getAuthHeader(),
                    houseId = SessionManager.houseId
                )
                if (res.isSuccessful && res.body() != null) {
                    history = res.body()!!
                } else {
                    errorMsg = "Could not load history."
                }
            } catch (e: Exception) {
                errorMsg = "Network error."
            } finally {
                loading = false
            }
        }
    }

    ThemedScreen(onBack = onBack) {
        when {
            loading -> Text("Loading...", color = Color.White)
            errorMsg.isNotEmpty() -> Text(errorMsg, color = Color(0xFFFF4444), fontSize = 13.sp)
            history.isEmpty() -> Text("No history yet.", color = Color(0xFFBFD6D1))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(history) { item ->
                    GlassCard {
                        Text(
                            "Device: ${item.deviceName ?: "#${item.deviceId}"} (${item.roomName ?: "Unknown Room"})  →  ${item.actionType}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            "Value: ${item.newValue}   Origin: ${item.origin}",
                            color = Color(0xFFBFD6D1),
                            fontSize = 13.sp
                        )
                        Text(
                            item.timestamp,
                            color = Color(0xFF7FA3A0),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
