package com.example.netbuddy.ui.ping

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netbuddy.*
import com.example.netbuddy.network.PingSample
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun PingScreen(model: PingViewModel, onBack: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("icmp_ping_settings", Context.MODE_PRIVATE) }
    val historyPrefs = remember(context) { context.getSharedPreferences("icmp_ping_history", Context.MODE_PRIVATE) }

    val defaultHistory = remember {
        listOf("facebook.com", "faceboo.com", "8.8.8.8", "youtube.com", "192.168.214.1", "192.168.0.1", "192.168.1.1")
    }
    var historyList by remember {
        mutableStateOf(historyPrefs.getString("items", null)?.split(",")?.filter { it.isNotBlank() } ?: defaultHistory)
    }
    var historyExpanded by remember { mutableStateOf(false) }

    var host by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableStateOf(prefs.getInt("count", 0).toString()) }
    var interval by rememberSaveable { mutableStateOf(prefs.getInt("interval", 1).toString()) }
    var timeout by rememberSaveable { mutableStateOf(prefs.getInt("timeout", 2).toString()) }
    var payload by rememberSaveable { mutableStateOf(prefs.getInt("payload", 56).toString()) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var showToastBanner by remember { mutableStateOf(false) }

    val replyList = rememberLazyListState()
    val liveRowCount = if (state.traceLines.isNotEmpty()) state.traceLines.size else state.samples.size

    val addToHistory = { target: String ->
        val clean = target.trim()
        if (clean.isNotBlank()) {
            val updated = (listOf(clean) + historyList.filter { it != clean }).take(15)
            historyList = updated
            historyPrefs.edit().putString("items", updated.joinToString(",")).apply()
        }
    }

    LaunchedEffect(showToastBanner) {
        if (showToastBanner) {
            delay(2200)
            showToastBanner = false
        }
    }

    LaunchedEffect(liveRowCount, state.running, state.tracing) {
        if (liveRowCount > 0 && (state.running || state.tracing)) {
            replyList.animateScrollToItem(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Bar
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Glyph("back", "Back") }
                Column(Modifier.weight(1f)) {
                    Text("ICMP Ping", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Test connectivity and measure latency",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Host Input & Action Card
            Panel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Host Input Field with History Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            enabled = !state.running && !state.tracing,
                            label = { Text("Hostname / IP Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (host.isNotEmpty()) {
                                        IconButton(onClick = { host = "" }) {
                                            Glyph("close", "Clear destination", size = 18.dp)
                                        }
                                    }
                                    IconButton(onClick = { historyExpanded = !historyExpanded }) {
                                        Icon(
                                            imageVector = if (historyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Search history suggestions",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        )

                        // Search History Dropdown Menu
                        DropdownMenu(
                            expanded = historyExpanded,
                            onDismissRequest = { historyExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            historyList.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (suggestion == host) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        host = suggestion
                                        historyExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Ping Settings Button
                    Surface(
                        onClick = { settings = true },
                        enabled = !state.running && !state.tracing,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Glyph("settings", "Ping settings", tint = MaterialTheme.colorScheme.primary, size = 22.dp)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        enabled = !state.running && !state.tracing && host.isNotBlank(),
                        onClick = {
                            addToHistory(host)
                            model.trace(host)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Glyph("tools", tint = MaterialTheme.colorScheme.primary, size = 19.dp)
                        Spacer(Modifier.width(7.dp))
                        Text("Traceroute", color = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = {
                            if (state.running || state.tracing) {
                                model.stop()
                            } else {
                                addToHistory(host)
                                model.start(host, count, interval, timeout, payload)
                            }
                        },
                        enabled = host.isNotBlank() || state.running || state.tracing,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Glyph(
                            if (state.running || state.tracing) "close" else "play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            size = 18.dp
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (state.running || state.tracing) "Stop" else "Ping",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (state.running || state.tracing) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            // Live Latency Card
            Panel(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Live Latency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "ICMP echo reply (ms)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LatencyGraph(
                    samples = state.samples.takeLast(60),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 6 Metrics Row with Vertical Dividers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val metrics = listOf(
                        Triple("Sent", state.totals.sent.toString(), MaterialTheme.colorScheme.onSurface),
                        Triple("Received", state.totals.received.toString(), MaterialTheme.colorScheme.onSurface),
                        Triple("Lost", "${state.totals.sent - state.totals.received} (${state.totals.lossPercent?.roundToInt() ?: 0}%)", Success),
                        Triple("Min", state.totals.minimum?.let { "${it.roundToInt()} ms" } ?: "—", MaterialTheme.colorScheme.onSurface),
                        Triple("Avg", state.totals.average?.let { "${it.roundToInt()} ms" } ?: "—", MaterialTheme.colorScheme.onSurface),
                        Triple("Max", state.totals.maximum?.let { "${it.roundToInt()} ms" } ?: "—", MaterialTheme.colorScheme.onSurface)
                    )

                    metrics.forEachIndexed { index, (label, value, color) ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = color,
                                maxLines = 1
                            )
                        }

                        if (index < metrics.size - 1) {
                            VerticalDivider(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .fillMaxHeight(),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                if (state.totals.hasUpperBound) {
                    Text(
                        "Some RTT values are upper bounds.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Ping Replies Card
            Panel(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (state.traceLines.isNotEmpty()) "Traceroute" else "Ping Replies",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusPill(
                            label = if (state.running) "Running…" else if (state.tracing) "Tracing…" else if (state.status == "Completed") "Completed" else "Stopped",
                            positive = state.running || state.tracing || state.status == "Completed"
                        )

                        IconButton(
                            enabled = state.samples.isNotEmpty() || state.traceLines.isNotEmpty(),
                            onClick = {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("ICMP Ping", state.report()))
                                showToastBanner = true
                                Toast.makeText(context, "Copied to clipboard.", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Glyph("copy", "Copy ping report", size = 19.dp)
                        }
                    }
                }

                if (liveRowCount == 0) {
                    Text(
                        "Start a ping or traceroute to see live output.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (state.traceLines.isNotEmpty()) {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        state = replyList,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.traceLines.reversed(), key = { it }) { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        state = replyList,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.samples.takeLast(200).reversed(), key = { it.sequence }) { sample ->
                            PingReplyRow(sample, state.target, state.options.payloadBytes + 8)
                        }
                    }
                }
            }

            Text(
                "ICMP or TTL probes may be filtered by Android, a firewall, or the destination. No reply does not prove a host is offline.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Animated Copied Toast Banner
        AnimatedVisibility(
            visible = showToastBanner,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF233246),
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color(0xFF384A62))
            ) {
                Text(
                    text = "Copied to clipboard.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }

    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("Ping & Traceroute Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PingField("Count / Probes (0 = unlimited)", count) {
                        count = it
                        it.toIntOrNull()?.takeIf { n -> n in 0..1000 }?.let { n -> prefs.edit().putInt("count", n).apply() }
                    }
                    PingField("Interval (1–10 seconds)", interval) {
                        interval = it
                        it.toIntOrNull()?.takeIf { n -> n in 1..10 }?.let { n -> prefs.edit().putInt("interval", n).apply() }
                    }
                    PingField("Timeout (1–10 seconds)", timeout) {
                        timeout = it
                        it.toIntOrNull()?.takeIf { n -> n in 1..10 }?.let { n -> prefs.edit().putInt("timeout", n).apply() }
                    }
                    PingField("Packet payload size (0–1472 bytes)", payload) {
                        payload = it
                        it.toIntOrNull()?.takeIf { n -> n in 0..1472 }?.let { n -> prefs.edit().putInt("payload", n).apply() }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { settings = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun PingReplyRow(sample: PingSample, target: String, defaultBytes: Int) {
    val reportLine = sample.reportLine(target, defaultBytes)
    val seq = "#${sample.sequence}"

    val bytes = Regex("bytes=(\\d+)").find(reportLine)?.groupValues?.get(1) ?: "$defaultBytes"
    val from = Regex("Reply from ([^:]+)").find(reportLine)?.groupValues?.get(1) ?: target
    val ttl = sample.ttl ?: Regex("TTL=(\\d+)").find(reportLine)?.groupValues?.get(1) ?: "117"
    val timeVal = sample.rttMs?.roundToInt()

    val textLine = if (sample.received) {
        "Reply from $from: bytes=$bytes time=${timeVal ?: 0} ms TTL=$ttl"
    } else if (sample.sent) {
        "Request timed out"
    } else {
        "Probe could not be sent"
    }

    val latencyText = if (sample.received && timeVal != null) "$timeVal ms" else "—"

    val latencyColor = when {
        !sample.received -> MaterialTheme.colorScheme.error
        (sample.rttMs ?: 0.0) < 50 -> Success // Mint Green
        (sample.rttMs ?: 0.0) <= 100 -> Color(0xFFF59E0B) // Gold / Yellow
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = seq,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = textLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = latencyText,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = latencyColor
        )
    }
}

@Composable
private fun PingField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun LatencyGraph(samples: List<PingSample>, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val maximum = (samples.mapNotNull { it.rttMs }.maxOrNull() ?: 100.0).coerceAtLeast(10.0)

    Canvas(
        modifier = modifier.semantics { contentDescription = "Live ICMP latency chart" }
    ) {
        val width = size.width
        val height = size.height

        val leftPadding = 26.dp.toPx()
        val bottomPadding = 16.dp.toPx()

        val chartWidth = width - leftPadding
        val chartHeight = height - bottomPadding

        // Y-axis labels & horizontal dashed grid lines (100, 75, 50, 25, 0)
        val yRatios = listOf(1.0f, 0.75f, 0.5f, 0.25f, 0.0f)
        yRatios.forEach { ratio ->
            val yVal = (maximum * ratio).roundToInt().toString()
            val yPos = chartHeight * (1f - ratio)

            val textLayout = textMeasurer.measure(yVal, labelStyle)
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset((leftPadding - textLayout.size.width - 4.dp.toPx()).coerceAtLeast(0f), yPos - textLayout.size.height / 2f)
            )

            drawLine(
                color = grid,
                start = Offset(leftPadding, yPos),
                end = Offset(width, yPos),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
        }

        // X-axis labels (0s, 10s, 20s, 30s, 40s, 50s, 60s)
        val xLabels = listOf("0s", "10s", "20s", "30s", "40s", "50s", "60s")
        xLabels.forEachIndexed { index, label ->
            val xPos = leftPadding + (index.toFloat() / (xLabels.size - 1)) * chartWidth
            val textLayout = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(xPos - textLayout.size.width / 2f, chartHeight + 2.dp.toPx())
            )

            drawLine(
                color = grid,
                start = Offset(xPos, 0f),
                end = Offset(xPos, chartHeight),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
        }

        // Smooth Cubic Wave Curve & Gradient Fill
        if (samples.isNotEmpty()) {
            val points = mutableListOf<Offset>()
            val stepX = if (samples.size <= 1) chartWidth / 2f else chartWidth / (samples.size - 1)

            samples.forEachIndexed { index, sample ->
                val x = leftPadding + index * stepX
                val rtt = sample.rttMs
                if (rtt != null) {
                    val y = chartHeight - (rtt / maximum * chartHeight).toFloat().coerceIn(0f, chartHeight)
                    points.add(Offset(x, y))
                }
            }

            if (points.isNotEmpty()) {
                val strokePath = Path()
                val fillPath = Path()

                strokePath.moveTo(points.first().x, points.first().y)
                fillPath.moveTo(points.first().x, chartHeight)
                fillPath.lineTo(points.first().x, points.first().y)

                if (points.size == 1) {
                    strokePath.lineTo(points.first().x, points.first().y)
                    fillPath.lineTo(points.first().x, points.first().y)
                } else {
                    for (i in 0 until points.size - 1) {
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val cx = (p1.x + p2.x) / 2f

                        strokePath.cubicTo(cx, p1.y, cx, p2.y, p2.x, p2.y)
                        fillPath.cubicTo(cx, p1.y, cx, p2.y, p2.x, p2.y)
                    }
                }

                fillPath.lineTo(points.last().x, chartHeight)
                fillPath.close()

                // Vertical Gradient Area Fill
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(line.copy(alpha = 0.35f), Color.Transparent),
                        startY = 0f,
                        endY = chartHeight
                    )
                )

                // Neon Line
                drawPath(
                    path = strokePath,
                    color = line,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
