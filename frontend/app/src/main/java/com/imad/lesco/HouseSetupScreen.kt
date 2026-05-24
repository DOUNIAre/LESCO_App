package com.imad.lesco

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun HouseSetupScreen(onContinue: () -> Unit, onJoinedAsMember: () -> Unit = onContinue, onBack: (() -> Unit)? = null) {
    var houseName   by remember { mutableStateOf("") }
    var inviteCode  by remember { mutableStateOf("") }
    var errorMsg    by remember { mutableStateOf("") }
    var isLoading   by remember { mutableStateOf(false) }
    val scope       = rememberCoroutineScope()

    ThemedScreen(onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 220.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Create or join your house", color = Color(0xFFBFD6D1), fontSize = 20.sp)
                Spacer(modifier = Modifier.height(14.dp))

                GlassTextField(houseName, "House Name", { houseName = it })
                Spacer(modifier = Modifier.height(12.dp))

                GlassButton(
                    text = if (isLoading) "..." else "Create House",
                    textColor = LescoPrimary,
                    containerColor = Color(0x4D3CDBC0),
                    enabled = !isLoading,
                    onClick = {
                        if (houseName.isBlank()) {
                            errorMsg = "Enter a house name"
                            return@GlassButton
                        }
                        scope.launch {
                            isLoading = true
                            errorMsg = ""
                            try {
                                val res = RetrofitInstance.api.createHouse(
                                    token = TokenManager.getAuthHeader(),
                                    request = CreateHouseRequest(name = houseName)
                                )
                                if (res.isSuccessful && res.body() != null) {
                                    val house = res.body()!!
                                    SessionManager.houseId = house.id
                                    SessionManager.role = "owner"
                                    onContinue()
                                } else {
                                    errorMsg = "Could not create house. Try again."
                                }
                            } catch (e: Exception) {
                                errorMsg = "Network error: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(26.dp))
                Text("or join with invite code", color = Color(0xFFBFD6D1), fontSize = 20.sp)
                Spacer(modifier = Modifier.height(12.dp))

                GlassTextField(inviteCode, "Invite Code", { inviteCode = it })
                Spacer(modifier = Modifier.height(12.dp))

                GlassButton(
                    text = if (isLoading) "..." else "Join House",
                    textColor = LescoNavy,
                    containerColor = LescoPrimary,
                    enabled = !isLoading,
                    shimmerEnabled = true,
                    onClick = {
                        if (inviteCode.isBlank()) {
                            errorMsg = "Enter an invite code"
                            return@GlassButton
                        }
                        scope.launch {
                            isLoading = true
                            errorMsg = ""
                            try {
                                val res = RetrofitInstance.api.joinHouse(
                                    request = JoinHouseRequest(
                                        invite_code = inviteCode.trim().uppercase(),
                                        user_id     = SessionManager.userId
                                    )
                                )
                                if (res.isSuccessful && res.body() != null) {
                                    // ── Save house_id from the join response ──
                                    SessionManager.houseId = res.body()!!.houseId
                                    SessionManager.role    = "member"
                                    onJoinedAsMember()  // → redirect to preferences setup
                                } else {
                                    errorMsg = "Invalid invite code. Try again."
                                }
                            } catch (e: Exception) {
                                errorMsg = "Network error: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )

                if (errorMsg.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(errorMsg, color = Color(0xFFFF4444), fontSize = 13.sp)
                }
            }
        }
    }
}
