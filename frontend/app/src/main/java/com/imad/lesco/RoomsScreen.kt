package com.imad.lesco

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
fun RoomsScreen(onBack: () -> Unit, onRoomSelected: (Int) -> Unit, onAddRoomClick: () -> Unit) {
    var rooms    by remember { mutableStateOf<List<RoomResponse>>(emptyList()) }
    var errorMsg by remember { mutableStateOf("") }
    var loading  by remember { mutableStateOf(true) }
    val scope    = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val res = RetrofitInstance.api.getRooms(
                    token   = TokenManager.getAuthHeader(),
                    houseId = SessionManager.houseId
                )
                if (res.isSuccessful && res.body() != null) {
                    rooms = res.body()!!
                } else {
                    errorMsg = "Could not load rooms."
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
            when {
                loading -> Text("Loading...", color = Color.White)
                errorMsg.isNotEmpty() -> Text(errorMsg, color = Color(0xFFFF4444), fontSize = 13.sp)
                rooms.isEmpty() -> Text("No rooms yet.", color = Color(0xFFBFD6D1))
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(rooms) { index, room ->
                        GlassCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRoomSelected(room.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Room category icon
                                val nameLower = room.name.lowercase()
                                val roomIcon = when {
                                    nameLower.contains("living") || nameLower.contains("salon") -> R.drawable.living_room
                                    nameLower.contains("kitchen") || nameLower.contains("cuisine") -> R.drawable.kitchen
                                    nameLower.contains("bed") || nameLower.contains("bedroom") || nameLower.contains("chambre") -> R.drawable.bedroom
                                    nameLower.contains("bath") || nameLower.contains("bathroom") || nameLower.contains("douche") || nameLower.contains("toilet") -> R.drawable.bathroom
                                    else -> R.drawable.house
                                }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = roomIcon),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Room #${index + 1}: ${room.name}",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Access: ${room.roomType.uppercase()}",
                                        color = Color(0xFFBFD6D1),
                                        fontSize = 13.sp
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // Shared / Personal badge icon
                                val accessIcon = if (room.roomType.lowercase().trim() == "shared") R.drawable.shared else R.drawable.personal
                                Image(
                                    painter = painterResource(id = accessIcon),
                                    contentDescription = room.roomType,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }

            }
            
            if (SessionManager.isOwner()) {
                GlassButton(
                    text = "Add Room",
                    textColor = LescoNavy,
                    containerColor = LescoPrimary,
                    onClick = onAddRoomClick
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

