package com.example.netbuddy.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import com.example.netbuddy.Glyph

@Composable
fun TerminalScreen(model: TerminalViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var input by remember(state.connected, state.destination) { mutableStateOf("") }
    var password by remember(state.passwordRequest) { mutableStateOf("") }
    var forget by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(scroll) { snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }
    LaunchedEffect(state.passwordRequest) { if (state.passwordRequest == null) password = "" }
    fun send() {
        if (input.isNotBlank() || state.connected) {
            val value = input; input = ""; model.submit(value)
        }
    }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (state.connected) "${state.protocol} connected" else if (state.busy) if (state.stopping) "Stopping…" else "${state.protocol} running" else "NetBuddy >", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(state.destination.ifEmpty { "Local commands · Ping / SSH / Telnet" }, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = model::clear) { Glyph("delete", "Clear terminal display") }
            IconButton(enabled = state.busy, onClick = model::stop) { Glyph("close", "Stop or disconnect") }
            IconButton(onClick = { info = true }) { Glyph("about", "Terminal help") }
        }
        if (state.protocol == "Telnet") Text("Telnet is unencrypted. Credentials are sent in cleartext.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF071522), RoundedCornerShape(16.dp)).padding(14.dp)) {
            SelectionContainer {
                Text(state.output.ifEmpty { "NetBuddy >" }, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    lineHeight = 18.sp, color = Color(0xFFC8EBDD), modifier = Modifier.fillMaxSize().verticalScroll(scroll).horizontalScroll(rememberScrollState()))
            }
        }
        if (state.connected) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Ctrl+C" to "\u0003", "Tab" to "\t", "↑" to "\u001b[A", "↓" to "\u001b[B", "←" to "\u001b[D", "→" to "\u001b[C", "Esc" to "\u001b").forEach { (label, bytes) ->
                    OutlinedButton(enabled = !state.stopping, onClick = { val data = input + bytes; input = ""; model.sendKey(data) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(10.dp)) { Text(label, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.secretInput, onCheckedChange = model::secretInput)
                Text("Hide input (password prompts)", style = MaterialTheme.typography.labelSmall)
            }
        } else if (!state.busy) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("help" to "help", "Ping" to "ping 192.168.1.1", "SSH" to "ssh admin@192.168.88.1", "Telnet" to "telnet 192.168.88.1 23").forEach { (label, command) ->
                SuggestionChip(onClick = { input = command }, label = { Text(label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { if (it.length <= 4096) input = it },
                enabled = (!state.busy || state.connected) && !state.stopping,
                label = { Text(if (state.connected) "Remote input" else "NetBuddy command") },
                visualTransformation = if (state.secretInput) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = if (state.secretInput) KeyboardType.Password else KeyboardType.Ascii, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }))
            FilledIconButton(enabled = (!state.busy || state.connected) && !state.stopping, onClick = { send() }, modifier = Modifier.size(48.dp)) { Glyph("play", "Send command", tint = MaterialTheme.colorScheme.onPrimary) }
        }
    }
    state.passwordRequest?.let { command ->
        AlertDialog(onDismissRequest = { password = ""; model.cancelPassword() },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text("SSH password") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${command.user}@${command.host}:${command.port}")
                Text("Password is used for this connection only. Verify the host fingerprint before trusting a new server.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = password, onValueChange = { if (it.length <= 1024) password = it }, singleLine = true,
                    label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            } },
            confirmButton = { TextButton(onClick = { val secret = password; password = ""; model.connectSsh(secret) }) { Text("Connect") } },
            dismissButton = { TextButton(onClick = { password = ""; model.cancelPassword() }) { Text("Cancel") } })
    }
    state.trust?.let { prompt ->
        AlertDialog(onDismissRequest = { model.trust(false) }, title = { Text("Verify SSH host key") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(prompt.host); Text(prompt.algorithm, fontWeight = FontWeight.SemiBold)
                SelectionContainer { Text(prompt.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                Text("Compare this fingerprint with a trusted value from the server administrator. Trust only if it matches. Changed saved keys are blocked automatically.", style = MaterialTheme.typography.bodyMedium)
            } },
            confirmButton = { TextButton(onClick = { model.trust(true) }) { Text("Trust and connect") } },
            dismissButton = { TextButton(onClick = { model.trust(false) }) { Text("Reject") } })
    }
    if (info) AlertDialog(onDismissRequest = { info = false }, title = { Text("Network terminal") },
        text = { Text("Local commands: help, clear, ping host, ssh user@host [port], telnet host [port]. After connecting, typed input and special keys go to the remote device. Use Disconnect to return locally.\n\nThis is a line-oriented console with basic ANSI cleanup, not a full Linux/VT100 emulator. Full-screen editors are not supported. SSH uses password authentication only, with saved host-key verification. Telnet is unencrypted.\n\nPasswords are not saved. Remote transcripts stay only in a bounded memory buffer and are cleared when the app is backgrounded. Sessions close and ping stops when the app is backgrounded. This app does not elevate privileges.") },
        confirmButton = { TextButton(onClick = { info = false }) { Text("Got it") } },
        dismissButton = { TextButton(enabled = !state.busy, onClick = { info = false; forget = true }) { Text("Saved SSH keys") } })
    if (forget) AlertDialog(onDismissRequest = { forget = false }, title = { Text("Forget saved SSH host keys?") },
        text = { Text("Only do this after verifying a legitimate key change with the administrator. All saved public host keys will be removed; each server will require fingerprint verification again. Passwords are not stored here.") },
        confirmButton = { TextButton(onClick = { forget = false; model.forgetKeys() }) { Text("Forget keys") } },
        dismissButton = { TextButton(onClick = { forget = false }) { Text("Cancel") } })
}
