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
fun HouseSummaryScreen(onBack: () -> Unit) {
    var summary  by remember { mutableStateOf<HouseSummaryResponse?>(null) }
    var errorMsg by remember { mutableStateOf("") }
    var loading  by remember { mutableStateOf(true) }
    val scope    = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val res = RetrofitInstance.api.getHouseSummary(
                    token   = TokenManager.getAuthHeader(),
                    houseId = SessionManager.houseId
                )
                if (res.isSuccessful && res.body() != null) {
                    summary = res.body()
                } else {
                    errorMsg = "Could not load summary."
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
            summary == null -> Text("No data.", color = Color(0xFFBFD6D1))
            else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                GlassCard {
                    Text(
                        "Total energy saved: ${"%.1f".format(summary!!.totalEnergySaved)} kWh",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                summary!!.rooms.forEach { room ->
                    GlassCard {
                        Text(room.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            "Active devices: ${room.activeDevicesCount}",
                            color = Color(0xFFBFD6D1),
                            fontSize = 14.sp
                        )
                        Text(
                            "Energy saved: ${"%.1f".format(room.energySavedKwh)} kWh",
                            color = Color(0xFFBFD6D1),
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}
