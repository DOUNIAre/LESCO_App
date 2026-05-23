package com.imad.lesco

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor



// ── Écran principal ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenRooms: (Int) -> Unit,
    onOpenRecommendations: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAssignment: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenHouseDevices: () -> Unit,
    onOpenNotifications: () -> Unit,
    onLogout: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Appareil sélectionné pour le popup (using real database DeviceResponse now!)
    var selectedDevice by remember { mutableStateOf<DeviceResponse?>(null) }
    var popupActive by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictDevice by remember { mutableStateOf<DeviceResponse?>(null) }

    var tempVal by remember { mutableStateOf("22 °C") }
    var humidVal by remember { mutableStateOf("49 %") }
    var dashboardTime by remember { mutableStateOf(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())) }

    var rooms by remember { mutableStateOf<List<RoomResponse>>(emptyList()) }
    var devices by remember { mutableStateOf<List<DeviceResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val refreshDashboard = {
        scope.launch {
            loading = true
            try {
                // Fetch dynamic weather
                val res = RetrofitInstance.api.getLiveWeather()
                if (res.isSuccessful && res.body() != null) {
                    val weather = res.body()!!
                    tempVal = "${weather.temperature} °C"
                    humidVal = "${weather.humidity} %"
                }
            } catch (_: Exception) {}

            try {
                dashboardTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            } catch (_: Exception) {}

            try {
                if (SessionManager.houseId != -1) {
                    // Fetch rooms
                    val rRes = RetrofitInstance.api.getRooms(
                        token = TokenManager.getAuthHeader(),
                        houseId = SessionManager.houseId
                    )
                    if (rRes.isSuccessful && rRes.body() != null) {
                        rooms = rRes.body()!!
                    } else if (rRes.code() == 403) {
                        SessionManager.houseId = -1
                        SessionManager.role = "member"
                        rooms = emptyList()
                        devices = emptyList()
                    }

                    if (SessionManager.houseId != -1) {
                        // Fetch all house devices
                        val dRes = RetrofitInstance.api.getHouseDevices(
                            token = TokenManager.getAuthHeader(),
                            houseId = SessionManager.houseId
                        )
                        if (dRes.isSuccessful && dRes.body() != null) {
                            devices = dRes.body()!!.map {
                                DeviceResponse(
                                    id = it.id,
                                    name = it.name ?: "Device",
                                    deviceType = it.deviceType,
                                    roomId = it.roomId,
                                    status = it.status,
                                    value = it.value ?: 0
                                )
                            }
                        } else if (dRes.code() == 403) {
                            SessionManager.houseId = -1
                            SessionManager.role = "member"
                            rooms = emptyList()
                            devices = emptyList()
                        }
                    }
                } else {
                    rooms = emptyList()
                    devices = emptyList()
                }
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(SessionManager.houseId) {
        refreshDashboard()
    }


    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.Black, // Rectangle 49: background: #000000
                windowInsets = WindowInsets(0.dp),
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(Modifier.height(49.dp))
                Image(
                    painter = painterResource(id = R.drawable.logo_lesco),
                    contentDescription = "LESCO Logo",
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .height(27.dp)
                        .wrapContentWidth()
                )
                Spacer(Modifier.height(35.dp))
                HorizontalDivider(
                    color = LescoPrimary.copy(alpha = 0.8f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(16.dp))
                
                NavigationDrawerItem(
                    label = { Text("Dashboard", color = Color.White) },
                    icon = { Image(painter = painterResource(id = R.drawable.dashboard), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = LescoGlassBg,
                        unselectedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                val isOwner = SessionManager.isOwner()
                if (isOwner) {
                    NavigationDrawerItem(
                        label = { Text("Rooms Management", color = Color.White) },
                        icon = { Image(painter = painterResource(id = R.drawable.room_management), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close(); onOpenRooms(-1) } },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                NavigationDrawerItem(
                    label = { Text("AI Recommendations", color = Color.White) },
                    icon = { Image(painter = painterResource(id = R.drawable.airecommendations), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onOpenRecommendations() } },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("House Summary", color = Color.White) },
                    icon = { Image(painter = painterResource(id = R.drawable.house_summary), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onOpenSummary() } },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Device History", color = Color.White) },
                    icon = { Image(painter = painterResource(id = R.drawable.device_history), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onOpenHistory() } },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                if (isOwner) {
                    NavigationDrawerItem(
                        label = { Text("Room Assignments", color = Color.White) },
                        icon = { Image(painter = painterResource(id = R.drawable.room_assignment), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close(); onOpenAssignment() } },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                NavigationDrawerItem(
                    label = { Text("My Devices", color = Color.White) },
                    icon = { Image(painter = painterResource(id = R.drawable.my_devices), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onOpenHouseDevices() } },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Profile & Preferences", color = Color.White) },
                    icon = { Image(painter = painterResource(id = R.drawable.profile_and_preferences), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onOpenProfile() } },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Notifications", color = Color.White) },
                    icon = { Image(painter = painterResource(id = R.drawable.notifications), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onOpenNotifications() } },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                
                Spacer(Modifier.weight(1f))
                
                NavigationDrawerItem(
                    label = { Text("Logout", color = Color(0xFFFF6B6B)) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onLogout() } },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(48.dp))
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Fond d'écran
            Image(
                painter = painterResource(id = R.drawable.background4),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Burger menu
            Box(
                modifier = Modifier
                    .padding(start = 20.dp, top = 42.dp)
                    .size(40.dp)
                    .background(LescoGlassBg, RoundedCornerShape(12.dp))
                    .clickable { scope.launch { drawerState.open() } },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    repeat(3) {
                        Box(
                            Modifier
                                .width(25.dp)
                                .height(3.dp)
                                .background(LescoPrimary, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }


            // Notification Bell Badge on top-right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp, top = 42.dp)
                    .size(40.dp)
                    .background(LescoGlassBg, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenNotifications() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = NotificationBellIcon,
                    contentDescription = "Notifications",
                    tint = LescoPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }


            // ── Filtres ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .padding(start = 59.dp, top = 122.dp)
                    .height(30.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterTab(iconRes = R.drawable.temp,     label = tempVal,   smallText = false)
                FilterTab(iconRes = R.drawable.humidite, label = humidVal,    smallText = false)
                FilterTab(
                    iconRes = R.drawable.house,
                    label = dashboardTime,
                    smallText = false,
                    modifier = Modifier.clickable { onOpenProfile() }
                )
            }

            // ── Cartes de pièces ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .padding(start = 20.dp, top = 162.dp, end = 20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(25.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                if (loading) {
                    Text("Loading rooms and devices...", color = Color.White, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                } else if (rooms.isEmpty()) {
                    Spacer(modifier = Modifier.height(50.dp))
                    Text(
                        text = if (SessionManager.houseId == -1) {
                            "You are not assigned to a house.\nPlease join or create a house in profile."
                        } else {
                            "No rooms or devices yet.\nPlease add rooms in Room Management."
                        },
                        color = Color(0xFFBFD6D1),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    )
                } else {
                    rooms.forEach { room ->
                        val roomDevices = devices.filter { it.roomId == room.id }
                        RoomCard(
                            roomName = room.name,
                            roomType = room.roomType,
                            devices  = roomDevices,
                            onDeviceClick = { device ->
                                selectedDevice = device
                                popupActive    = device.status
                            },
                            onRoomClick = { onOpenRooms(room.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Popup device ──────────────────────────────────────────────────
            AnimatedVisibility(
                visible  = selectedDevice != null,
                enter    = fadeIn(tween(300)),
                exit     = fadeOut(tween(250)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedVisibility(
                        visible = selectedDevice != null,
                        enter   = slideInVertically(tween(300)) { it / 6 },
                        exit    = slideOutVertically(tween(250)) { it / 6 }
                    ) {
                        selectedDevice?.let { device ->
                            DevicePopup(
                                device      = device,
                                isActive    = popupActive,
                                onToggle    = {
                                    scope.launch {
                                        try {
                                            val response = RetrofitInstance.api.toggleDevice(
                                                token = TokenManager.getAuthHeader(),
                                                deviceId = device.id
                                            )
                                            if (response.isSuccessful) {
                                                val body = response.body()
                                                if (body != null && body.status == "conflict_detected") {
                                                    conflictDevice = device
                                                    showConflictDialog = true
                                                    popupActive = false
                                                    selectedDevice = null
                                                } else {
                                                    popupActive = !popupActive
                                                    val dRes = RetrofitInstance.api.getHouseDevices(
                                                        token = TokenManager.getAuthHeader(),
                                                        houseId = SessionManager.houseId
                                                    )
                                                    if (dRes.isSuccessful && dRes.body() != null) {
                                                        devices = dRes.body()!!.map {
                                                            DeviceResponse(
                                                                    id = it.id,
                                                                    name = it.name ?: "Device",
                                                                    deviceType = it.deviceType,
                                                                    roomId = it.roomId,
                                                                    status = it.status,
                                                                    value = it.value ?: 0
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }
                                },
                                onDismiss   = { selectedDevice = null },
                                onHistory   = onOpenHistory
                            )
                        }
                    }
                }
            }
            
            if (showConflictDialog && conflictDevice != null) {
                val dev = conflictDevice!!
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showConflictDialog = false },
                    title = { Text("Preference Conflict Detected", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = { 
                        Text(
                            "Multiple family members have registered preferences in this shared room. " +
                            "To prevent discomfort, manual overrides are restricted.\n\n" +
                            "Would you like the LESCO resolver to apply Weighted Median / Majority Voting resolution logic?",
                            color = Color(0xFFBFD6D1)
                        )
                    },
                    confirmButton = {
                        GlassButton(
                            text = "Apply Logic",
                            containerColor = LescoPrimary,
                            textColor = LescoNavy,
                            modifier = Modifier.width(120.dp).height(36.dp),
                            onClick = {
                                scope.launch {
                                    try {
                                        val res = RetrofitInstance.api.applyConflictResolution(
                                            token = TokenManager.getAuthHeader(),
                                            roomId = dev.roomId,
                                            category = dev.deviceType.uppercase()
                                        )
                                        if (res.isSuccessful) {
                                            showConflictDialog = false
                                            // Refresh devices
                                            val dRes = RetrofitInstance.api.getHouseDevices(
                                                token = TokenManager.getAuthHeader(),
                                                houseId = SessionManager.houseId
                                            )
                                            if (dRes.isSuccessful && dRes.body() != null) {
                                                devices = dRes.body()!!.map {
                                                    DeviceResponse(
                                                        id = it.id,
                                                        name = it.name ?: "Device",
                                                        deviceType = it.deviceType,
                                                        roomId = it.roomId,
                                                        status = it.status,
                                                        value = it.value ?: 0
                                                    )
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        )
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showConflictDialog = false }) {
                            Text("Cancel", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF16252C),
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

// ── Popup de détail d'un appareil ─────────────────────────────────────────────
@Composable
private fun DevicePopup(
    device: DeviceResponse,
    isActive: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onHistory: () -> Unit
) {
    val active = isActive
    val label = device.name ?: device.deviceType
    val iconRes = getDeviceIcon(device.deviceType, active)

    Box(
        modifier = Modifier
            .width(259.dp)
            .height(375.dp)
            .background(LescoNavy, RoundedCornerShape(12.dp))
    ) {
        // Bouton fermer (X)
        Image(
            painter            = painterResource(id = R.drawable.xboutton),
            contentDescription = "close",
            modifier           = Modifier
                .size(35.dp)
                .align(Alignment.TopEnd)
                .offset(x = 10.dp, y = (-12).dp)
                .clip(RoundedCornerShape(50))
                .clickable { onDismiss() }
                .padding(4.dp)
                .zIndex(1f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text       = label,
                color      = Color.White,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0x40000000), RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = label,
                        modifier = Modifier
                            .size(72.dp)
                            .padding(12.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .width(68.dp)
                            .height(30.dp)
                            .background(
                                if (isActive) LescoPrimary else Color(0xFF505E69),
                                RoundedCornerShape(70.dp)
                            )
                            .clickable { onToggle() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .size(27.dp)
                                .align(if (isActive) Alignment.CenterEnd else Alignment.CenterStart)
                                .offset(x = if (isActive) (-1).dp else 1.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .border(1.dp, Color(0xFF505E69), RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onHistory() }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text      = "Check History",
                    color     = Color.White,
                    fontSize  = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun FilterTab(
    iconRes: Int,
    label: String,
    smallText: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .wrapContentWidth()
            .height(30.dp)
            .background(LescoGlassBg, RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text       = label,
                color      = Color.White,
                fontSize   = if (smallText) 11.sp else 13.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun RoomCard(
    roomName: String,
    roomType: String,
    devices: List<DeviceResponse>,
    onDeviceClick: (DeviceResponse) -> Unit,
    onRoomClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(LescoGlassBg, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable { onRoomClick() }
    ) {
        val accessIcon = if (roomType.lowercase().trim() == "shared") R.drawable.shared else R.drawable.personal
        Image(
            painter = painterResource(id = accessIcon),
            contentDescription = roomType,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 12.dp)
                .size(22.dp)
        )
        Box(
            modifier = Modifier
                .padding(start = 7.dp, top = 7.dp)
                .height(30.dp)
                .wrapContentWidth()
                .background(Color(0x40000000), RoundedCornerShape(31.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val nameLower = roomName.lowercase()
                val roomIcon = when {
                    nameLower.contains("living") || nameLower.contains("salon") -> R.drawable.living_room
                    nameLower.contains("kitchen") || nameLower.contains("cuisine") -> R.drawable.kitchen
                    nameLower.contains("bed") || nameLower.contains("bedroom") || nameLower.contains("chambre") -> R.drawable.bedroom
                    nameLower.contains("bath") || nameLower.contains("bathroom") || nameLower.contains("douche") || nameLower.contains("toilet") -> R.drawable.bathroom
                    else -> when (roomType.lowercase().trim()) {
                        "living_room" -> R.drawable.living_room
                        "kitchen" -> R.drawable.kitchen
                        "bedroom", "master_bedroom", "kid_bedroom" -> R.drawable.bedroom
                        "bathroom" -> R.drawable.bathroom
                        else -> R.drawable.house
                    }
                }
                Image(
                    painter = painterResource(id = roomIcon),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = roomName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        val col1 = devices.take(2)
        val col2 = devices.drop(2).take(2)
        Row(
            modifier = Modifier
                .padding(start = 7.dp, top = 48.dp, end = 7.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) { col1.forEach { DeviceCell(it, onDeviceClick) } }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) { col2.forEach { DeviceCell(it, onDeviceClick) } }
        }
    }
}

@Composable
private fun DeviceCell(device: DeviceResponse, onClick: (DeviceResponse) -> Unit) {
    val active = device.status
    val label = device.name ?: device.deviceType
    val status = if (active) "ON" else "OFF"
    val iconRes = getDeviceIcon(device.deviceType, active)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(37.dp)
            .background(Color(0x40000000), RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .clickable { onClick(device) }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(text = "$label: $status", color = Color.White, fontSize = 11.sp, maxLines = 1)
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
        else -> if (active) R.drawable.tv_on else R.drawable.tv_off
    }
}

val NotificationBellIcon: ImageVector
    get() = ImageVector.Builder(
        name = "NotificationBell",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color(0xFF5DF3D4)), // LescoPrimary
        strokeLineWidth = 0f
    ) {
        moveTo(12f, 22f)
        curveTo(13.1f, 22f, 14f, 21.1f, 14f, 20f)
        lineTo(10f, 20f)
        curveTo(10f, 21.1f, 10.9f, 22f, 12f, 22f)
        close()
        moveTo(18f, 16f)
        lineTo(18f, 11f)
        curveTo(18f, 7.93f, 15.37f, 5.36f, 12.5f, 4.73f)
        lineTo(12.5f, 4f)
        curveTo(12.5f, 3.17f, 11.83f, 2.5f, 11f, 2.5f)
        curveTo(10.17f, 2.5f, 9.5f, 3.17f, 9.5f, 4f)
        lineTo(9.5f, 4.73f)
        curveTo(6.63f, 5.36f, 4f, 7.92f, 4f, 11f)
        lineTo(4f, 16f)
        lineTo(2f, 18f)
        lineTo(2f, 19f)
        lineTo(20f, 19f)
        lineTo(20f, 18f)
        lineTo(18f, 16f)
        close()
    }.build()

val RefreshIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Refresh",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color(0xFF5DF3D4)), // LescoPrimary
        strokeLineWidth = 0f
    ) {
        moveTo(17.65f, 6.35f)
        curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
        curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
        curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
        curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
        lineTo(17.65f, 14f)
        curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
        curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
        curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
        curveTo(13.66f, 6f, 15.14f, 6.78f, 16.11f, 8f)
        lineTo(13f, 11f)
        lineTo(20f, 11f)
        lineTo(20f, 4f)
        lineTo(17.65f, 6.35f)
        close()
    }.build()