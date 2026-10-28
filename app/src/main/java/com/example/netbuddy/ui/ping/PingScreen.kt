package com.example.netbuddy.ui.ping

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netbuddy.*
import com.example.netbuddy.network.PingSample

@Composable
fun PingScreen(model: PingViewModel, onBack: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("icmp_ping_settings", Context.MODE_PRIVATE) }
    var host by rememberSaveable { mutableStateOf("8.8.8.8") }
    var count by rememberSaveable { mutableStateOf(prefs.getInt("count", 0).toString()) }
    var interval by rememberSaveable { mutableStateOf(prefs.getInt("interval", 1).toString()) }
    var timeout by rememberSaveable { mutableStateOf(prefs.getInt("timeout", 2).toString()) }
    var payload by rememberSaveable { mutableStateOf(prefs.getInt("payload", 56).toString()) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val replyList = rememberLazyListState()
    val liveRowCount = if (state.traceLines.isNotEmpty()) state.traceLines.size else state.samples.size
    LaunchedEffect(liveRowCount, state.running, state.tracing) {
        if (liveRowCount > 0 && (state.running || state.tracing)) replyList.animateScrollToItem(0)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Glyph("back", "Back") }
            Column(Modifier.weight(1f)) {
                Text("ICMP Ping", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Test connectivity and measure latency", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { settings = true }, enabled = !state.running && !state.tracing) { Glyph("settings", "Ping settings", tint = MaterialTheme.colorScheme.primary) }
        }

        Panel {
            OutlinedTextField(value = host, onValueChange = { host = it }, enabled = !state.running && !state.tracing,
                label = { Text("Host / IP address") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                trailingIcon = { if (host.isNotEmpty()) IconButton(onClick = { host = "" }) { Glyph("close", "Clear destination") } })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(enabled = !state.running && !state.tracing && host.isNotBlank(), onClick = { model.trace(host) },
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) {
                    Glyph("tools", tint = MaterialTheme.colorScheme.primary, size = 19.dp); Spacer(Modifier.width(7.dp)); Text("Traceroute")
                }
                Button(onClick = { if (state.running || state.tracing) model.stop() else model.start(host, count, interval, timeout, payload) },
                    enabled = host.isNotBlank() || state.running || state.tracing,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) {
                    Glyph(if (state.running || state.tracing) "close" else "play", tint = MaterialTheme.colorScheme.onPrimary, size = 18.dp)
                    Spacer(Modifier.width(7.dp)); Text(if (state.running || state.tracing) "Stop" else "Ping")
                }
            }
        }

        if (state.running || state.tracing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.error != null) Text(state.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Live Latency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("ICMP echo reply (ms)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LatencyGraph(state.samples.takeLast(60), Modifier.fillMaxWidth().height(120.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PingMetric("Sent", state.totals.sent.toString())
                PingMetric("Received", state.totals.received.toString())
                PingMetric("Lost", "${state.totals.sent - state.totals.received} (${state.totals.lossPercent?.let { "%.0f%%".format(it) } ?: "0%"})")
                PingMetric("Min", state.totals.minimum?.let { "${it.toInt()} ms" } ?: "—")
                PingMetric("Avg", state.totals.average?.let { "${it.toInt()} ms" } ?: "—")
                PingMetric("Max", state.totals.maximum?.let { "${it.toInt()} ms" } ?: "—")
            }
            if (state.totals.hasUpperBound) Text("Some RTT values are upper bounds.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Panel(Modifier.fillMaxWidth().weight(1f, fill = true)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.traceLines.isNotEmpty()) "Traceroute" else "Ping Replies", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PingStatusPill(if (state.tracing) "Tracing" else state.status, state.running || state.tracing)
                IconButton(enabled = state.samples.isNotEmpty(), onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ICMP Ping", state.report()))
                    Toast.makeText(context, "Ping report copied", Toast.LENGTH_SHORT).show()
                }) { Glyph("copy", "Copy ping report", size = 19.dp) }
            }
            if (liveRowCount == 0) Text("Start a ping or traceroute to see live output.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (state.traceLines.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().weight(1f), state = replyList, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(state.traceLines.reversed(), key = { it }) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                }
            } else LazyColumn(Modifier.fillMaxWidth().weight(1f), state = replyList, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(state.samples.takeLast(200).reversed(), key = { it.sequence }) { sample ->
                    PingReplyRow(sample, state.target, state.options.payloadBytes + 8)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                }
            }
        }
        Text("ICMP or TTL probes may be filtered by Android, a firewall, or the destination. No reply does not prove a host is offline.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Ping settings") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PingField("Count (0 = unlimited)", count) { count = it; it.toIntOrNull()?.takeIf { n -> n in 0..1000 }?.let { n -> prefs.edit().putInt("count", n).apply() } }
            PingField("Interval (1–10 seconds)", interval) { interval = it; it.toIntOrNull()?.takeIf { n -> n in 1..10 }?.let { n -> prefs.edit().putInt("interval", n).apply() } }
            PingField("Timeout (1–10 seconds)", timeout) { timeout = it; it.toIntOrNull()?.takeIf { n -> n in 1..10 }?.let { n -> prefs.edit().putInt("timeout", n).apply() } }
            PingField("Payload (0–1472 bytes)", payload) { payload = it; it.toIntOrNull()?.takeIf { n -> n in 0..1472 }?.let { n -> prefs.edit().putInt("payload", n).apply() } }
        } }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Done") } })
}

@Composable private fun PingStatusPill(status: String, running: Boolean) {
    val color = when {
        running -> Success
        status == "Completed" -> Success
        status == "Stopped" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            Text(
                text = if (running) "Running..." else status,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable private fun PingReplyRow(sample: PingSample, target: String, defaultBytes: Int) {
    val output = sample.output
    val bytes = Regex("(\\d+) bytes from", RegexOption.IGNORE_CASE).find(output)?.groupValues?.get(1)?.toIntOrNull() ?: defaultBytes
    val from = Regex("""from ([0-9a-fA-F:.]+?)(?::\s|\s)""", RegexOption.IGNORE_CASE).find(output)?.groupValues?.get(1) ?: target
    val latencyColor = when {
        !sample.received -> MaterialTheme.colorScheme.error
        (sample.rttMs ?: 0.0) < 50 -> Success
        (sample.rttMs ?: 0.0) < 100 -> androidx.compose.ui.graphics.Color(0xFFFFC857)
        else -> MaterialTheme.colorScheme.error
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("#${sample.sequence}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = if (sample.received) {
                val rttStr = sample.rttMs?.let { "${it.toInt()} ms" } ?: "unavailable"
                "Reply from $from: bytes=$bytes time=$rttStr TTL=${sample.ttl ?: "—"}"
            } else if (sample.sent) {
                "Request timed out"
            } else {
                "Probe not sent"
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = sample.rttMs?.let { "${it.toInt()} ms" } ?: "—",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = latencyColor
        )
    }
}

@Composable private fun PingField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}
@Composable private fun PingMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun LatencyGraph(samples: List<PingSample>, modifier: Modifier = Modifier) {
    val maximum = (samples.mapNotNull { it.rttMs }.maxOrNull() ?: 100.0).coerceAtLeast(100.0)
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(end = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text("${maximum.toInt()}", style = MaterialTheme.typography.labelSmall, color = textColor)
            Text("${(maximum * 0.75).toInt()}", style = MaterialTheme.typography.labelSmall, color = textColor)
            Text("${(maximum * 0.5).toInt()}", style = MaterialTheme.typography.labelSmall, color = textColor)
            Text("${(maximum * 0.25).toInt()}", style = MaterialTheme.typography.labelSmall, color = textColor)
            Text("0", style = MaterialTheme.typography.labelSmall, color = textColor)
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Recent ICMP latency chart" }) {
                    val width = size.width
                    val height = size.height

                    for (i in 0..4) {
                        val y = height * (i / 4f)
                        drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
                    }

                    for (i in 0..6) {
                        val x = width * (i / 6f)
                        drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
                    }

                    if (samples.isNotEmpty()) {
                        val points = mutableListOf<Offset>()
                        samples.forEachIndexed { index, sample ->
                            val x = if (samples.size <= 1) width / 2 else index * width / (samples.size - 1)
                            val value = sample.rttMs
                            if (value != null) {
                                val y = height - (value.toFloat() / maximum.toFloat() * (height - 8f)) - 4f
                                points.add(Offset(x, y))
                            }
                        }

                        if (points.size >= 2) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(points.first().x, height)
                                points.forEach { lineTo(it.x, it.y) }
                                lineTo(points.last().x, height)
                                close()
                            }
                            drawPath(
                                path = path,
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.35f), primaryColor.copy(alpha = 0.02f)),
                                    startY = 0f,
                                    endY = height
                                )
                            )

                            for (i in 0 until points.size - 1) {
                                drawLine(primaryColor, points[i], points[i + 1], strokeWidth = 3f)
                            }
                        }

                        samples.forEachIndexed { index, sample ->
                            val x = if (samples.size <= 1) width / 2 else index * width / (samples.size - 1)
                            val value = sample.rttMs
                            if (value != null) {
                                val y = height - (value.toFloat() / maximum.toFloat() * (height - 8f)) - 4f
                                drawCircle(primaryColor, radius = 3f, center = Offset(x, y))
                            } else {
                                drawLine(errorColor, Offset(x, height - 10f), Offset(x, height), strokeWidth = 2f)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("0s", "10s", "20s", "30s", "40s", "50s", "60s").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall, color = textColor)
                }
            }
        }
    }
}
