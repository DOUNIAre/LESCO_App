package com.imad.lesco

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ── Color Constants from Design Spec ──────────────────────────────────────────
val LescoPrimary   = Color(0xFF3CDBC0) // #3CDBC0
val LescoNavy      = Color(0xFF253746) // #253746 (Background ambient)
val LescoSecondary = Color(0xFF135C50) // #135C50 (Forgot Password)
val LescoPlaceholder = Color(0x333CDBC0) // rgba(60, 219, 192, 0.2)
val LescoGlassBg   = Color(0x80253746) // rgba(37, 55, 70, 0.5)

// ── Models ────────────────────────────────────────────────────────────────────
data class RoomUi(val id: Int, val name: String, val roomType: String)
data class DeviceUi(val id: Int, val roomId: Int, val name: String, val type: String, val value: Int)

// ── SHARED BACKGROUND ───────────────────────────────────────────────────────
@Composable
fun LescoBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().blur(53.05.dp)) {
            drawOval(
                color = Color(0xFF253746),
                topLeft = Offset(27.09f * density, -113.74f * density),
                size = androidx.compose.ui.geometry.Size(417.83f * density, 310.97f * density)
            )
            drawOval(
                color = Color(0xFF253746),
                topLeft = Offset(-109.75f * density, 606.06f * density),
                size = androidx.compose.ui.geometry.Size(417.83f * density, 375.13f * density)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF253746).copy(alpha = 0.1f))
        )
    }
}

@Composable
fun ThemedScreen(
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LescoBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            if (onBack != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.return_boutton),
                        contentDescription = "Back",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onBack() }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }
            content()
        }
    }
}


@Composable
fun LescoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) LescoPrimary else Color.Transparent,
            contentColor = if (filled) LescoNavy else LescoPrimary
        ),
        border = if (!filled) BorderStroke(1.dp, LescoPrimary) else null
    ) {
        Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GlassButton(
    text: String,
    textColor: Color,
    containerColor: Color,
    shimmerEnabled: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LescoButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        filled = (containerColor == LescoPrimary),
        enabled = enabled
    )
}

@Composable
fun LescoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var passwordVisible by remember { mutableStateOf(!isPassword) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        cursorBrush = SolidColor(LescoPrimary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
                
                if (isPassword) {
                    val iconRes = if (passwordVisible) R.drawable.see else R.drawable.not_see
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = "Toggle password visibility",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { passwordVisible = !passwordVisible }
                    )
                }
            }
        }
    )
}


@Composable
fun GlassTextField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    shakeOffsetX: Float = 0f,
    hasError: Boolean = false,
    enabled: Boolean = true
) {
    LescoTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = Modifier.offset { IntOffset(shakeOffsetX.roundToInt(), 0) },
        isPassword = isPassword,
        keyboardType = keyboardType
    )
}

@Composable
fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LescoGlassBg, RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
fun GlassDropdownSelector(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                Text(text = selected, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(text = if (expanded) "▲" else "▼", color = LescoPrimary, fontSize = 12.sp)
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(LescoNavy)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = Color.White) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
