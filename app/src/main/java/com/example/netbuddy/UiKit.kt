package com.example.netbuddy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val Teal = Color(0xFF009EA5)
val Success: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < .2f) Color(0xFF71DDA9) else Color(0xFF087842)

@Composable
fun Glyph(name: String, description: String? = null, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, size: Dp = 24.dp) {
    val resource = when(name) {
        "ping" -> R.drawable.ui_ping; "terminal" -> R.drawable.ui_terminal;
        "home" -> R.drawable.ui_home; "tools" -> R.drawable.ui_tools
        "history" -> R.drawable.ui_history; "guide" -> R.drawable.ui_guide
        "about" -> R.drawable.ui_about; "wifi" -> R.drawable.ui_wifi
        "ethernet" -> R.drawable.ui_ethernet; "cellular" -> R.drawable.ui_cellular
        "subnet" -> R.drawable.ui_subnet; "tcp" -> R.drawable.ui_tcp
        "dns" -> R.drawable.ui_dns; "http", "HTTPS" -> R.drawable.ui_http
        "arrow" -> R.drawable.ui_arrow; "check" -> R.drawable.ui_check
        "close" -> R.drawable.ui_close; "copy" -> R.drawable.ui_copy
        "delete" -> R.drawable.ui_delete; "play" -> R.drawable.ui_play
        "back" -> R.drawable.ui_back; "settings" -> R.drawable.ui_settings
        "refresh" -> R.drawable.ui_refresh
        else -> R.drawable.ui_about
    }
    Icon(painterResource(resource), description, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun toolColor(type: String): Color {
    val base = when(type.lowercase()) {
        "ping" -> Color(0xFF168453); "terminal" -> Color(0xFF9A6200);
        "tcp" -> Color(0xFF2478E8); "dns" -> Color(0xFF007ADE)
        "http", "https" -> Color(0xFF7653DB); "subnet" -> Teal
        else -> Teal
    }
    return if (MaterialTheme.colorScheme.background.luminance() < .2f) lerp(base, Color.White, .35f) else base
}

@Composable
fun IconBubble(type: String, color: Color = toolColor(type), size: Dp = 48.dp) {
    Box(Modifier.size(size).background(color.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
        Glyph(type, tint = color, size = size * .52f)
    }
}

@Composable
fun StatusPill(label: String, positive: Boolean = true) {
    val color = when {
        label == "Insufficient Data" -> MaterialTheme.colorScheme.onSurfaceVariant
        label == "Fair" -> if (MaterialTheme.colorScheme.background.luminance() < .2f) Color(0xFFFFCC80) else Color(0xFF8A5400)
        positive -> Success
        else -> MaterialTheme.colorScheme.error
    }
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(50)) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = color,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
