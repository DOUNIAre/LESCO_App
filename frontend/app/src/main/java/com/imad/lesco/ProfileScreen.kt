package com.imad.lesco

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onBack: () -> Unit, onPreferencesClick: () -> Unit, onHouseSetupClick: () -> Unit) {
    val context = LocalContext.current
    var name    by remember { mutableStateOf("Loading...") }
    var email   by remember { mutableStateOf("") }
    var role    by remember { mutableStateOf(SessionManager.role) }
    var inviteCode by remember { mutableStateOf("Loading...") }
    var errorMsg by remember { mutableStateOf("") }
    val scope   = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                // 1. Get Profile
                val res = RetrofitInstance.api.getMyProfile(TokenManager.getAuthHeader())
                if (res.isSuccessful && res.body() != null) {
                    val profile = res.body()!!
                    name  = profile.name
                    email = profile.email
                }

                // 2. Get House Summary for Invite Code
                val houseId = if (SessionManager.houseId != -1) SessionManager.houseId else 1
                val summaryRes = RetrofitInstance.api.getHouseSummary(TokenManager.getAuthHeader(), houseId)
                if (summaryRes.isSuccessful && summaryRes.body() != null) {
                    inviteCode = summaryRes.body()!!.inviteCode
                }
            } catch (e: Exception) {
                errorMsg = "Network error."
            }
        }
    }

    ThemedScreen(onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Your Profile", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

            GlassCard {
                Text("Name", color = LescoPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Email", color = LescoPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(email, color = Color(0xFFBFD6D1), fontSize = 16.sp)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Role", color = LescoPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(if (role == "owner") "House Owner" else "Family Member", color = Color(0xFFBFD6D1), fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            val isOwner = SessionManager.isOwner()

            if (isOwner) {
                Text("Home Management", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))

                GlassCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Invite Code", color = LescoPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                inviteCode,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            
                            GlassButton(
                                text = "COPY",
                                containerColor = LescoPrimary,
                                textColor = LescoNavy,
                                modifier = Modifier.width(100.dp).height(36.dp),
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("LESCO Invite Code", inviteCode)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        Text(
                            "Share this code with your family members to let them join your smart home.",
                            color = Color(0xFFBFD6D1),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            if (errorMsg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMsg, color = Color(0xFFFF4444), fontSize = 13.sp)
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            GlassButton(
                text = "Preferences",
                textColor = LescoNavy,
                containerColor = LescoPrimary,
                onClick = onPreferencesClick
            )
            Spacer(modifier = Modifier.height(16.dp))

            GlassButton(
                text = "Create or Join Another House",
                textColor = Color.White,
                containerColor = Color.Transparent,
                onClick = onHouseSetupClick,
                modifier = Modifier.fillMaxWidth().border(1.dp, LescoPrimary, RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
