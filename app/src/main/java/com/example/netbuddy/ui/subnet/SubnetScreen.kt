package com.example.netbuddy.ui.subnet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.netbuddy.Panel
import com.example.netbuddy.Glyph
import com.example.netbuddy.IconBubble

@Composable
fun SubnetScreen(model: SubnetViewModel = viewModel(), onSave: (String, String) -> Unit = { _, _ -> }) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("IPv4 ranges, calculated locally", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBubble("subnet", size = 40.dp)
                Column { Text("Subnet Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Works offline · /0 through /32", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            OutlinedTextField(value = state.address, onValueChange = model::setAddress,
                label = { Text("IPv4 address") }, placeholder = { Text("192.168.1.10") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = state.prefix, onValueChange = model::setPrefix,
                label = { Text("CIDR prefix (0–32)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = model::calculate, modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp)) { Text("Calculate") }
                OutlinedButton(onClick = model::reset, modifier = Modifier.heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp)) { Text("Reset") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        state.result?.let { result ->
            Panel {
                Text("${result.address}/${result.prefix}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Valid IPv4 subnet", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                listOf("Subnet mask" to result.mask, "Network address" to result.network,
                    "Broadcast (bitwise)" to result.broadcast, "First address" to result.firstAddress,
                    "Last address" to result.lastAddress, "First usable" to result.firstUsable,
                    "Last usable" to result.lastUsable, "Total addresses" to result.totalAddresses.toString(),
                    "Usable hosts" to result.usableHosts.toString()).forEach { (label, value) ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(result.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { onSave(result.report(), "${result.address}/${result.prefix}") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Glyph("history", tint = MaterialTheme.colorScheme.onSecondaryContainer, size = 18.dp)
                    Spacer(Modifier.width(8.dp)); Text("Save to history")
                }
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("NetBuddy subnet", result.report()))
                    Toast.makeText(context, "Subnet result copied", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Glyph("copy", tint = MaterialTheme.colorScheme.primary, size = 18.dp)
                    Spacer(Modifier.width(8.dp)); Text("Copy result")
                }
            }
        }
    }
}
