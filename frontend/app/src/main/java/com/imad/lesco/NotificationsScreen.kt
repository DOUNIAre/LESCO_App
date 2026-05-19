package com.imad.lesco

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val userId = SessionManager.userId

    fun loadNotifications() {
        scope.launch {
            isLoading = true
            errorMsg = ""
            try {
                val res = RetrofitInstance.api.getNotifications(
                    token = TokenManager.getAuthHeader(),
                    userId = userId
                )
                if (res.isSuccessful && res.body() != null) {
                    notifications = res.body()!!.sortedByDescending { it.id }
                } else {
                    errorMsg = "Failed to load notifications."
                }
            } catch (e: Exception) {
                errorMsg = "Network error."
            } finally {
                isLoading = false
            }
        }
    }

    fun markRead(notifId: Int) {
        scope.launch {
            try {
                val res = RetrofitInstance.api.markNotificationRead(
                    token = TokenManager.getAuthHeader(),
                    userId = userId,
                    notifId = notifId
                )
                if (res.isSuccessful) {
                    notifications = notifications.map {
                        if (it.id == notifId) it.copy(isRead = true) else it
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        loadNotifications()
    }

    ThemedScreen(onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(100.dp))
            Text(
                text = "Notifications",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LescoPrimary)
                }
            } else if (errorMsg.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMsg, color = Color(0xFFFF4444), fontSize = 16.sp)
                }
            } else if (notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "You're all caught up!\nNo notifications yet.",
                        color = Color(0xFFBFD6D1),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(notifications) { notif ->
                        val cardBg = if (notif.isRead) LescoGlassBg else LescoGlassBg.copy(alpha = 0.25f)
                        val borderColor = if (notif.isRead) Color.White.copy(alpha = 0.1f) else LescoPrimary.copy(alpha = 0.4f)
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardBg, RoundedCornerShape(20.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                                .clickable { if (!notif.isRead) markRead(notif.id) }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!notif.isRead) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(LescoPrimary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (notif.isRead) "Read" else "New System Alert",
                                        color = if (notif.isRead) Color.White.copy(alpha = 0.5f) else LescoPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                val formattedTime = try {
                                    val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                    val outputFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                    val date = inputFormat.parse(notif.createdAt)
                                    if (date != null) outputFormat.format(date) else notif.createdAt
                                } catch (_: Exception) {
                                    notif.createdAt
                                }
                                
                                Text(
                                    text = formattedTime,
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = notif.message,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (notif.isRead) FontWeight.Normal else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
