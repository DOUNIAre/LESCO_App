package com.imad.lesco

import androidx.compose.foundation.clickable
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
fun RoomAssignmentsScreen(onBack: () -> Unit) {
    var rooms            by remember { mutableStateOf<List<RoomResponse>>(emptyList()) }
    var selectedRoomId   by remember { mutableStateOf<Int?>(null) }
    var members          by remember { mutableStateOf<List<HouseMemberResponse>>(emptyList()) }
    var selectedMemberId by remember { mutableStateOf<Int?>(null) }
    
    var statusMsg        by remember { mutableStateOf("") }
    var isSuccess        by remember { mutableStateOf(false) }
    var loading          by remember { mutableStateOf(true) }
    val scope            = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val roomsRes = RetrofitInstance.api.getRooms(
                    token   = TokenManager.getAuthHeader(),
                    houseId = SessionManager.houseId
                )
                val membersRes = RetrofitInstance.api.getHouseMembers(
                    token   = TokenManager.getAuthHeader(),
                    houseId = SessionManager.houseId
                )
                
                if (roomsRes.isSuccessful && roomsRes.body() != null) {
                    rooms = roomsRes.body()!!
                    selectedRoomId = rooms.firstOrNull()?.id
                }
                
                if (membersRes.isSuccessful && membersRes.body() != null) {
                    members = membersRes.body()!!
                    selectedMemberId = members.firstOrNull()?.id
                }
            } catch (_: Exception) {
                statusMsg = "Could not load assignment options."
            } finally {
                loading = false
            }
        }
    }

    ThemedScreen(onBack = onBack) {
        val isOwner = SessionManager.isOwner()
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (!isOwner) {
                GlassCard {
                    Text(
                        "Notice: Assignment configuration is view-only for family members. Only the house owner can assign members.",
                        color = Color(0xFFFFB300),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Sélection de la room
            GlassCard {
                Text("Select Room", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                if (loading) {
                    Text("Loading rooms...", color = Color(0xFFBFD6D1), fontSize = 14.sp)
                } else if (rooms.isEmpty()) {
                    Text("No rooms found.", color = Color(0xFFBFD6D1), fontSize = 14.sp)
                } else {
                    rooms.forEach { room ->
                        val isSelected = room.id == selectedRoomId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedRoomId = room.id }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSelected) "● " else "○ ",
                                color = if (isSelected) LescoPrimary else Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = room.name,
                                color = if (isSelected) LescoPrimary else Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sélection du membre
            GlassCard {
                Text("Select Family Member", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                if (loading) {
                    Text("Loading family members...", color = Color(0xFFBFD6D1), fontSize = 14.sp)
                } else if (members.isEmpty()) {
                    Text("No family members found.", color = Color(0xFFBFD6D1), fontSize = 14.sp)
                } else {
                    members.forEach { member ->
                        val isSelected = member.id == selectedMemberId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMemberId = member.id }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSelected) "● " else "○ ",
                                color = if (isSelected) LescoPrimary else Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Column {
                                Text(
                                    text = member.name,
                                    color = if (isSelected) LescoPrimary else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = "${member.email} (${member.role.uppercase()})",
                                    color = if (isSelected) LescoPrimary.copy(alpha = 0.8f) else Color(0xFFBFD6D1),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            GlassButton(
                text = "Assign Member to Room",
                textColor = LescoNavy,
                containerColor = if (isOwner) LescoPrimary else Color.Gray,
                shimmerEnabled = isOwner,
                enabled = isOwner && selectedRoomId != null && selectedMemberId != null && !loading
            ) {
                val uid = selectedMemberId
                val rid = selectedRoomId
                if (uid == null || rid == null) {
                    statusMsg = "Please select a room and a member."
                    isSuccess = false
                    return@GlassButton
                }
                scope.launch {
                    try {
                        val res = RetrofitInstance.api.assignUserToRoom(
                            token  = TokenManager.getAuthHeader(),
                            roomId = rid,
                            userId = uid
                        )
                        if (res.isSuccessful) {
                            val memberName = members.find { it.id == uid }?.name ?: "Member"
                            val roomName = rooms.find { it.id == rid }?.name ?: "room"
                            statusMsg = "$memberName assigned to $roomName successfully."
                            isSuccess = true
                        } else {
                            statusMsg = "Assignment failed."
                            isSuccess = false
                        }
                    } catch (e: Exception) {
                        statusMsg = "Network error."
                        isSuccess = false
                    }
                }
            }

            if (statusMsg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    statusMsg,
                    color = if (isSuccess) LescoPrimary else Color(0xFFFF4444),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
