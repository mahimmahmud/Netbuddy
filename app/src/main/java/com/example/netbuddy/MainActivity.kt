package com.example.netbuddy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.app.Activity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.netbuddy.ui.ping.PingScreen
import com.example.netbuddy.ui.ping.PingViewModel
import com.example.netbuddy.terminal.TerminalScreen
import com.example.netbuddy.terminal.TerminalViewModel
import androidx.navigation.compose.*
import com.example.netbuddy.data.TestRecord
import com.example.netbuddy.network.NetworkState
import com.example.netbuddy.ui.subnet.SubnetScreen
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NetBuddyApp() }
    }
}
private val tools = listOf("subnet" to "IPv4 Subnet Calculator", "tcp" to "TCP Latency Test",
    "dns" to "DNS Lookup", "http" to "Internet Connectivity Checker", "ping" to "ICMP Ping", "terminal" to "Network Terminal",
    "smart" to "Smart Troubleshooting", "targets" to "Saved Targets", "export" to "Report Export", "compare" to "Network Comparison", "speed" to "Speed Test")
private val tabs = listOf("home" to "Home", "tools" to "Tools", "history" to "History", "guide" to "Guide", "about" to "About")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetBuddyApp(model: NetBuddyModel = viewModel(), ping: PingViewModel = viewModel(), terminal: TerminalViewModel = viewModel()) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, terminal, ping) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) terminal.foreground()
            if (event == Lifecycle.Event.ON_STOP && (context as? Activity)?.isChangingConfigurations != true) {
                terminal.background(); ping.stop()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val prefs = remember { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var theme by rememberSaveable { mutableStateOf(prefs.getString("theme", "System") ?: "System") }
    val dark = when (theme) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF39D1CD), onPrimary = Color(0xFF002F32),
        background = Color(0xFF0A192B), onBackground = Color(0xFFEBF2FA),
        surface = Color(0xFF142B43), onSurface = Color(0xFFEBF2FA),
        surfaceVariant = Color(0xFF1D354F), onSurfaceVariant = Color(0xFFAABED4),
        outline = Color(0xFF607A95), outlineVariant = Color(0xFF29425D),
        secondaryContainer = Color(0xFF173D49), onSecondaryContainer = Color(0xFF66DFD9),
        error = Color(0xFFFFACAD))
        else lightColorScheme(primary = Teal, onPrimary = Color.White,
            background = Color(0xFFF5F8FC), onBackground = Color(0xFF10233E),
            surface = Color.White, onSurface = Color(0xFF10233E),
            surfaceVariant = Color(0xFFEDF3F9), onSurfaceVariant = Color(0xFF5B708A),
            outline = Color(0xFF8295AA), outlineVariant = Color(0xFFE4ECF4),
            secondaryContainer = Color(0xFFE0F5F5), onSecondaryContainer = Color(0xFF007D83))
    val view = LocalView.current
    SideEffect {
        (context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val network by model.network.collectAsStateWithLifecycle()
    val publicIp by model.publicIp.collectAsStateWithLifecycle()
    val history by model.history.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); model.message.value = null } }
    MaterialTheme(colorScheme = colors, shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(22.dp))) {
        Scaffold(topBar = {
            if (route != "home" && route != "ping") TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                title = { Text(if (route == "history") "Test History" else ((tabs + tools).find { it.first == route }?.second ?: "Report"),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (route !in tabs.map { it.first }) IconButton(onClick = { nav.popBackStack() }) { Glyph("back", "Back") } })
        }, bottomBar = {
            if (route != "ping") Surface(color = colors.surface, shadowElevation = 6.dp) {
                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) { tabs.forEach { (key, title) ->
                    NavigationBarItem(selected = route == key || (key == "tools" && route in tools.map { it.first }),
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = colors.primary, selectedTextColor = colors.primary,
                            indicatorColor = colors.primary.copy(alpha = .1f), unselectedIconColor = colors.onSurfaceVariant, unselectedTextColor = colors.onSurfaceVariant),
                        onClick = { nav.navigate(key) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Glyph(key, tint = if (route == key || (key == "tools" && route in tools.map { it.first })) colors.primary else colors.onSurfaceVariant, size = 23.dp) },
                        label = { Text(title, style = MaterialTheme.typography.labelSmall) })
                } }
            }
        }) { padding ->
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                composable("home") {
                    Dashboard(network, publicIp,
                        openSettings = { nav.navigate("about") }, refreshPublicIp = model::refreshPublicIp)
                }
                composable("tools") { Page {
                    Text("Find the right diagnostic", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    ToolCards { nav.navigate(it) }
                    Panel { Text("Real measurements", fontWeight = FontWeight.SemiBold)
                        Text("DNS, TCP and HTTPS tests use your current connection. The subnet calculator works offline.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
                } }
                composable("ping") { PingScreen(ping) { nav.popBackStack() } }
                composable("terminal") { TerminalScreen(terminal) }
                composable("subnet") { SubnetScreen(onSave = model::saveSubnet) }
                composable("tcp") { DiagnosticPage("TCP", state, network, model) }
                composable("dns") { DiagnosticPage("DNS", state, network, model) }
                composable("http") { DiagnosticPage("HTTPS", state, network, model) }
                composable("smart") { SmartTroubleshootPage { nav.navigate(it) } }
                composable("targets") { SavedTargetsPage(model) { nav.navigate(it) } }
                composable("export") { ReportExportPage(history) }
                composable("compare") { NetworkComparePage(network, publicIp.info?.address ?: "Unavailable", model) }
                composable("speed") { SpeedTestPage() }
                composable("history") { HistoryPage(history, model) { nav.navigate("detail/$it") } }
                composable("detail/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull()
                    val record = history.find { it.id == id }
                    Page {
                        if (record == null) Text("Report unavailable or deleted.") else {
                            Text("${record.type} · ${record.status}", style = MaterialTheme.typography.headlineSmall)
                            Text(DateFormat.getDateTimeInstance().format(Date(record.time)))
                            Text(record.target); Text(record.details)
                            CopyButton(record.details)
                            OutlinedButton(onClick = { model.delete(record.id); nav.popBackStack() }) { Text("Delete this report") }
                        }
                    }
                }
                composable("guide") { GuidePage(network, state) { nav.navigate(it) } }
                composable("about") { Page {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.brandmark), "NetBuddy logo", tint = Color.Unspecified, modifier = Modifier.size(60.dp))
                        Column { Text("NetBuddy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Network Troubleshooting Assistant", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                    }
                    Panel {
                        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("System", "Light", "Dark").forEach { option ->
                            FilterChip(selected = theme == option, onClick = { theme = option; prefs.edit().putString("theme", option).apply() }, label = { Text(option) })
                        } }
                        Text("Light cards or a deep navy workspace. Your choice is saved on this device.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Panel {
                        Text("Your privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("No account, analytics or NetBuddy backend. Reports stay on this device until deleted. Targets and IP addresses may appear in copied reports. App backups are disabled. The Home screen requests public IP from ipify.org and ISP/location metadata from ipwho.is or ipapi.co; these services receive your public IP. The app does not request device location permission.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    Panel {
                        Text("How diagnostics work", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("DNS uses Google Public DNS over HTTPS (dns.google). Domain queries are sent to that service. TCP hostnames use its A-record lookup. A TCP test makes five connections to one selected port.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Text("HTTPS endpoints are configurable; the default is https://www.google.com/generate_204. Redirects are reported without following them. One endpoint failure does not prove the internet is down.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Text("TCP setup timing is not ICMP ping or bandwidth. The separate ICMP Ping screen uses Android’s ping executable and reports platform restrictions. No reply does not prove a host is offline. Android validation does not guarantee every endpoint is reachable.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    Panel {
                        Text("Terminal & ICMP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("SSH password authentication verifies saved host keys. New fingerprints require approval; changed keys are blocked. Telnet is intentionally unencrypted. Remote transcripts and passwords are not saved in the database. Ping reports are stored locally. Terminal connections and ICMP monitoring stop when the app is backgrounded.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    Text("University project · Kotlin · Compose · MVVM · Room", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                } }
            }
        }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
private fun Dashboard(network: NetworkState, publicIp: PublicIpState, openSettings: () -> Unit, refreshPublicIp: () -> Unit) {
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.brandmark), "NetBuddy", tint = Color.Unspecified, modifier = Modifier.size(64.dp))
            Column(Modifier.weight(1f)) {
                Row { Text("Net", fontWeight = FontWeight.Bold, fontSize = 30.sp); Text("Buddy", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 30.sp) }
                Text("Your network, explained", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = openSettings) { Glyph("settings", "Settings") }
        }
        NetworkOverviewCard(network)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeInfoCard("Local IP", network.ipv4, "This device", "wifi", Modifier.weight(1f))
            HomeInfoCard("Public IP", publicIp.info?.address ?: when {
                publicIp.loading -> "Looking up…"
                publicIp.error != null -> "Unavailable"
                else -> "Waiting for internet"
            }, "Internet address", "about", Modifier.weight(1f), onRefresh = refreshPublicIp, loading = publicIp.loading)
        }
        HomeInfoCard("ISP information", publicIp.info?.isp ?: publicIp.error ?: if (publicIp.loading) "Looking up…" else "Unavailable", "Provided by your public IP network", "ethernet")
        HomeInfoCard("IP location", publicIp.info?.location ?: publicIp.error ?: if (publicIp.loading) "Looking up…" else "Unavailable", "Approximate location from public IP", "about")
        Text("IP-based location is approximate. Public IP data uses ipify.org with metadata fallbacks.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NetworkOverviewCard(network: NetworkState) {
    val wifi = network.type == "Wi-Fi"
    val dbm = network.wifiSignalDbm
    val quality = when {
        !wifi -> "Wi-Fi not active"
        dbm == null -> "Signal unavailable"
        dbm >= -70 -> "Good"
        else -> "Poor"
    }
    val qualityColor = if (quality == "Good") Success else if (quality == "Poor") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBubble(if (wifi) "wifi" else if (network.connected) "cellular" else "close", size = 54.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (network.connected) "${network.type} connected" else "No active connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (network.validated) "Internet validated" else "Internet not validated", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(quality, color = qualityColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (wifi && dbm != null) Text("$dbm dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HomeInfoCard(title: String, value: String, subtitle: String, icon: String,
                         modifier: Modifier = Modifier, onRefresh: (() -> Unit)? = null, loading: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon, size = 38.dp)
                Spacer(Modifier.weight(1f))
                if (onRefresh != null) IconButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.size(40.dp)) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Glyph("refresh", "Refresh $title", size = 18.dp)
                }
            }
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToolCards(open: (String) -> Unit) {
    val descriptions = listOf("Calculate network ranges and details", "Test TCP connection timing to a host", "Query A and AAAA domain records", "Check HTTPS endpoint reachability", "Monitor real ICMP replies and latency", "Connect with SSH, Telnet or ping", "Guided checks for common connection problems", "Keep frequently tested hosts on this device", "Share or save local diagnostic reports", "Compare this connection with a saved snapshot", "Measure approximate download and upload speed")
    val names = tools.map { it.second }
    BoxWithConstraints {
        val columns = if (maxWidth < 280.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.4f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tools.indices.toList().chunked(columns).forEach { group ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    group.forEach { index ->
                        val route = tools[index].first
                        Card(onClick = { open(route) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.fillMaxWidth().heightIn(min = 156.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    IconBubble(route); Spacer(Modifier.weight(1f)); Glyph("arrow", size = 16.dp)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(names[index], style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(descriptions[index], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SmartTroubleshootPage(open:(String)->Unit) {
    var choice by rememberSaveable { mutableStateOf("Website will not open") }
    Page { Text("Choose the problem and follow the suggested checks. Results use the existing DNS, TCP and HTTPS tools; advice is indicative.", color=MaterialTheme.colorScheme.onSurfaceVariant)
        listOf("Internet is slow", "Website will not open", "No internet access").forEach { FilterChip(selected=choice==it,onClick={choice=it},label={Text(it)}) }
        Panel { Text("Suggested sequence", style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            val routes=if(choice=="Website will not open") listOf("dns","tcp","http") else listOf("http","dns","tcp")
            Text(routes.mapIndexed { i,r -> "${i+1}. ${when(r){"dns"->"Check domain DNS records";"tcp"->"Check TCP port 443";else->"Check secure internet endpoint"}}" }.joinToString("\n"))
            Button(onClick={open(routes.first())},modifier=Modifier.fillMaxWidth()){Text("Start guided checks")}
            Text("Run each suggested tool in order, then compare the results. A failed single check does not prove the whole internet is down.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun SavedTargetsPage(model:NetBuddyModel,open:(String)->Unit) {
    val context= LocalContext.current; val prefs=remember { context.getSharedPreferences("saved_targets",Context.MODE_PRIVATE) }
    var host by rememberSaveable { mutableStateOf("") }; var items by remember { mutableStateOf(prefs.getStringSet("items", emptySet())?.toList().orEmpty()) }
    Page { Text("Save hostnames or IP addresses locally for quick TCP checks.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(host,{host=it},label={Text("Host or IP")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={ val v=host.trim(); if(v.isNotBlank()){items=(items+v).distinct();prefs.edit().putStringSet("items",items.toSet()).apply();host=""} },enabled=host.isNotBlank()){Text("Save target")}
        items.forEach { value -> Panel { Text(value,fontWeight=FontWeight.Bold); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={model.tcp(value,"443");open("tcp")}){Text("TCP 443")};TextButton(onClick={items=items-value;prefs.edit().putStringSet("items",items.toSet()).apply()}){Text("Remove")}} } }
    }
}
@Composable private fun ReportExportPage(records:List<TestRecord>) {
    val context=LocalContext.current
    Page { Text("Export locally saved diagnostics as text through Android's share sheet.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${records.size} reports available")
        Button(enabled=records.isNotEmpty(),onClick={val body=records.joinToString("\n\n---\n\n"){ "${it.type} · ${it.status}\n${it.target}\n${it.details}" }; val intent=android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_SUBJECT,"NetBuddy diagnostic reports").putExtra(android.content.Intent.EXTRA_TEXT,body);context.startActivity(android.content.Intent.createChooser(intent,"Export reports"))},modifier=Modifier.fillMaxWidth()){Text("Share / save reports")}
        Text("Terminal credentials and transcripts are not included in report history.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun NetworkComparePage(network:NetworkState,publicIp:String,model:NetBuddyModel) {
    val context=LocalContext.current; val prefs=remember{context.getSharedPreferences("network_compare",Context.MODE_PRIVATE)}
    var baseline by remember{mutableStateOf(prefs.getString("snapshot",null))}
    val current="Connection: ${network.type}\nLocal IP: ${network.ipv4}\nPublic IP: $publicIp\nInternet validated: ${network.validated}\nWi-Fi signal: ${network.wifiSignalDbm ?: "Unavailable"} dBm"
    Page { Text("Capture a baseline, then compare it with the current connection.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Panel { Text("Current snapshot",fontWeight=FontWeight.Bold);Text(current)
            Button(onClick={baseline=current;prefs.edit().putString("snapshot",current).apply()}){Text("Save as baseline")}
        }
        baseline?.let { Panel { Text("Saved baseline comparison",fontWeight=FontWeight.Bold);Text(if(it==current) "No changes detected." else "Connection details differ from the saved baseline.\n\nBaseline:\n$it\n\nCurrent:\n$current") } } ?: Text("No baseline saved yet.")
    }
}
@Composable private fun SpeedTestPage() {
    val context=LocalContext.current
    Page { Text("Open Cloudflare's network speed test. Results depend on server distance, device, and current network load.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Panel { Text("Download, upload, latency and jitter",fontWeight=FontWeight.Bold);Button(onClick={context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://speed.cloudflare.com")))},modifier=Modifier.fillMaxWidth()){Text("Open Speed Test")};Text("This opens the provider's test in your browser; data use and measurements are controlled by that service.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun NetworkCard(network: NetworkState, compact: Boolean = false) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBubble(when(network.type) { "Wi-Fi" -> "wifi"; "Cellular" -> "cellular"; "Ethernet" -> "ethernet"; else -> "dns" }, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (network.connected) "${network.type} connected" else "No active connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val positive = network.validated
                    Glyph(if (positive) "check" else "about", tint = if (positive) Success else MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp)
                    Text(if (positive) "Internet validated by Android" else "Internet not validated", style = MaterialTheme.typography.bodySmall,
                        color = if (positive) Success else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!compact) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Local IPv4", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(network.ipv4, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        network.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}
@Composable
private fun CopyButton(text: String) {
    val context = LocalContext.current
    OutlinedButton(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), onClick = {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("NetBuddy report", text))
        Toast.makeText(context, "Report copied", Toast.LENGTH_SHORT).show()
    }) { Glyph("copy", tint = MaterialTheme.colorScheme.primary, size = 18.dp); Spacer(Modifier.width(8.dp)); Text("Copy result") }
}
@Composable
private fun DiagnosticPage(type: String, state: DiagnosticState, network: NetworkState, model: NetBuddyModel) {
    var target by rememberSaveable(type) { mutableStateOf(if (type == "HTTPS") "https://www.google.com/generate_204" else "example.com") }
    var port by rememberSaveable { mutableStateOf("443") }
    var dnsType by rememberSaveable { mutableStateOf("A") }
    Page {
        Text(when (type) { "TCP" -> "TCP connection timing · Not ICMP ping"; "DNS" -> "A / AAAA records · Google DNS over HTTPS"; else -> "Test a secure endpoint on your connection" },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Glyph(if (network.connected) "wifi" else "close", tint = MaterialTheme.colorScheme.primary, size = 17.dp)
                Text("${network.type} · " + if (network.validated) "Android validated" else "Not validated", style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = target, onValueChange = { target = it }, enabled = !state.running,
                    label = { Text(if (type == "HTTPS") "HTTPS endpoint" else if (type == "DNS") "Domain name" else "Host or IP address") },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (type == "HTTPS") KeyboardType.Uri else KeyboardType.Ascii))
                if (type == "TCP") OutlinedTextField(value = port, onValueChange = { port = it }, enabled = !state.running,
                    label = { Text("Port") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.width(86.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            if (type == "DNS") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("A", "AAAA").forEach { value -> FilterChip(enabled = !state.running, selected = dnsType == value, onClick = { dnsType = value }, label = { Text(value) }) }
            }
            Button(enabled = !state.running, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(12.dp), onClick = {
                when (type) { "TCP" -> model.tcp(target, port); "DNS" -> model.dns(target, dnsType); else -> model.http(target) }
            }) { Glyph("play", tint = MaterialTheme.colorScheme.onPrimary, size = 18.dp); Spacer(Modifier.width(8.dp)); Text("Run test", fontWeight = FontWeight.SemiBold) }
        }
        if (state.running) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = model::stop, modifier = Modifier.fillMaxWidth()) { Text("Stop current ${state.type} test") }
        }
        if (state.type == type && (state.attempts.isNotEmpty() || !state.running)) {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Test results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (state.running) "Running" else "Completed / stopped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(state.target, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (type == "TCP" && state.attempts.isNotEmpty()) {
                    state.attempts.forEach { attempt ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Attempt ${attempt.number}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(attempt.milliseconds?.let { "%.1f ms".format(it) } ?: "Failed", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Glyph(if (attempt.milliseconds != null) "check" else "close", tint = if (attempt.milliseconds != null) Success else MaterialTheme.colorScheme.error, size = 18.dp)
                        }
                        attempt.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                } else Text(state.text, style = MaterialTheme.typography.bodyMedium)
            }
            if (type == "TCP" && !state.running && state.attempts.isNotEmpty()) {
                val successful = state.attempts.mapNotNull { it.milliseconds }
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(if (successful.size == 5) "check" else "about", color = if (successful.size == 5) Success else MaterialTheme.colorScheme.error)
                        Column(Modifier.weight(1f)) {
                            Text("${successful.size}/${state.attempts.size} successful", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (successful.isNotEmpty()) Text("Average %.1f ms".format(successful.average()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(state.quality, positive = state.quality == "Good" || state.quality == "Fair")
                    }
                    if (successful.isNotEmpty()) Text("Min %.1f ms    ·    Max %.1f ms".format(successful.min(), successful.max()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("TCP-only indicator · ${state.attempts.size} attempts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.text.contains("Stopped")) Text("Stopped. Partial results were not saved.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    CopyButton(state.text)
                }
            } else if (!state.running) CopyButton(state.text)
        } else if (!state.running) Panel {
            Text("Ready when you are", fontWeight = FontWeight.SemiBold)
            Text("Enter a destination and run a test. Results are saved locally in History.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(when(type) {
            "TCP" -> "Measures TCP setup time, not ICMP ping or bandwidth. A blocked port does not prove the host is offline. Stop can take up to the 2.5-second connection timeout."
            "DNS" -> "Queries are sent to Google Public DNS. Empty A or AAAA results do not prove a domain is offline."
            else -> "One endpoint's failure does not prove the internet is down. HTTP error status means the server responded. Android validation does not guarantee every endpoint is reachable."
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun HistoryPage(records: List<TestRecord>, model: NetBuddyModel, open: (Long) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your diagnostic reports", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(enabled = records.isNotEmpty(), onClick = { confirm = true }) { Glyph("delete", "Clear all reports") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Ping", "TCP", "DNS", "HTTPS", "Subnet").forEach { type ->
                FilterChip(selected = filter == type, onClick = { filter = type }, label = { Text(type) }, shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
            }
        }
        val filtered = records.filter { filter == "All" || it.type == filter }
        if (filtered.isEmpty()) Panel {
            IconBubble("history")
            Text("No reports yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Run a diagnostic to start your history, or choose another filter.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(filtered, key = { it.id }) { record ->
                Card(onClick = { open(record.id) }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(record.type.lowercase(), size = 42.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(record.type, color = toolColor(record.type), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault()).format(Date(record.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(record.target, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(record.summary, style = MaterialTheme.typography.bodyMedium,
                                color = if (record.status == "Success") Success else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            record.averageMs?.let { Text("Average %.1f ms".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(record.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick = { model.delete(record.id) }, modifier = Modifier.size(48.dp)) { Glyph("delete", "Delete ${record.type} report", size = 18.dp) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete all reports?") },
        text = { Text("This removes all locally saved test history.") },
        confirmButton = { TextButton(onClick = { model.clear(); confirm = false }) { Text("Delete all") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
private data class Guide(val title: String, val observation: String, val explanation: String, val steps: String, val route: String)
@Composable
private fun GuidePage(network: NetworkState, diagnostic: DiagnosticState, open: (String) -> Unit) {
    val guides = listOf(
        Guide("Device disconnected", if (!network.connected) "Observed: no active default network." else "Current default connection: ${network.type}.",
            "Possible cause: airplane mode, disabled Wi-Fi/mobile data, or a disconnected cable.", "1. Check airplane mode.\n2. Enable Wi-Fi or mobile data.\n3. Check cable/router power.\n4. Run an HTTPS test.", "http"),
        Guide("Connected, internet not validated", "Android validation: ${network.validated}; connection: ${network.type}.",
            "Possible cause: captive portal, router upstream issue, or blocked Android validation probes. Lack of validation alone is not proof of an outage.", "1. Open a browser for a sign-in page.\n2. Test two HTTPS endpoints.\n3. Compare another device.\n4. Check the router uplink.", "http"),
        Guide("DNS lookup failed", if (diagnostic.type == "DNS") diagnostic.text else "No DNS result in this session.",
            "Possible cause: misspelled domain, NXDOMAIN, resolver failure, or blocked access to dns.google.", "1. Check the domain spelling.\n2. Compare A and AAAA records.\n3. Try a known domain.\n4. Check DNS-over-HTTPS service reachability.", "dns"),
        Guide("High TCP latency", "Current TCP indicator: ${diagnostic.quality}.",
            "Possible cause: distance, congestion, server load, or radio conditions. TCP timing is not ICMP or bandwidth.", "1. Move closer to the access point.\n2. Pause large downloads.\n3. Compare another host.\n4. Repeat later with the same port.", "tcp"),
        Guide("TCP connection timed out", if (diagnostic.type == "TCP") diagnostic.text else "No TCP test in this session.",
            "Possible cause: filtered port, route failure, or an unavailable service. The host might still be online.", "1. Confirm the service port.\n2. Check the target address.\n3. Compare HTTPS reachability.\n4. Ask the network administrator about firewall policy.", "tcp"),
        Guide("HTTP endpoint error", if (diagnostic.type == "HTTPS") diagnostic.text else "No HTTPS result in this session.",
            "Possible cause: server failure, access restriction, throttling, DNS error, or TLS configuration.", "1. Read the HTTP status/error.\n2. Try another HTTPS endpoint.\n3. Check device date/time for TLS.\n4. Retry later without repeated requests.", "http")
    )
    Page {
        Text("Suggested next steps", style = MaterialTheme.typography.headlineSmall)
        Text("Offline rule-based guide. These are possible explanations, not a diagnosis of the exact cause.")
        guides.forEach { guide -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { IconBubble(guide.route, size = 40.dp); Text(guide.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                Text(guide.observation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(guide.explanation, style = MaterialTheme.typography.bodyMedium)
                Text(guide.steps, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { open(guide.route) }) { Text("Run relevant diagnostic") }
            }
        } }
    }
}
