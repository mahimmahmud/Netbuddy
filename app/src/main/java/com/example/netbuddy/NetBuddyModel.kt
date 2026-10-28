package com.example.netbuddy

import android.app.Application
import androidx.lifecycle.*
import com.example.netbuddy.data.*
import com.example.netbuddy.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NetBuddyApplication : Application() {
    val database by lazy { NetBuddyDatabase.create(this) }
    val repository by lazy { HistoryRepository(database.tests()) }
    val monitor by lazy { NetworkMonitor(this) }
    val client by lazy { OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false).build() }
}

data class PublicIpState(
    val loading: Boolean = false,
    val info: PublicIpInfo? = null,
    val error: String? = null
)

data class DiagnosticState(
    val running: Boolean = false,
    val type: String = "",
    val target: String = "",
    val text: String = "No test has been run.",
    val attempts: List<Attempt> = emptyList(),
    val quality: String = "Insufficient Data"
)

class NetBuddyModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NetBuddyApplication
    val network = app.monitor.states.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkState())
    private val publicLookup = PublicIpLookup(app.client)
    private val mutablePublicIp = MutableStateFlow(PublicIpState())
    val publicIp = mutablePublicIp.asStateFlow()
    val history = app.repository.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val mutableState = MutableStateFlow(DiagnosticState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private val dns = GoogleDns(app.client)
    private var lastStart = 0L
    val message = MutableStateFlow<String?>(null)
    private var publicIpJob: Job? = null

    init {
        viewModelScope.launch {
            network.map { state -> if (state.connected && state.validated) state.ipv4 else null }
                .distinctUntilChanged().collectLatest { localAddress ->
                    if (localAddress == null) {
                        publicIpJob?.cancel()
                        mutablePublicIp.value = PublicIpState(error = "Connect to the internet to look up public IP details.")
                    } else loadPublicIp()
                }
        }
    }

    fun refreshPublicIp() {
        if (!network.value.connected) {
            mutablePublicIp.value = PublicIpState(error = "Connect to a network first.")
        } else loadPublicIp()
    }

    private fun loadPublicIp() {
        publicIpJob?.cancel()
        publicIpJob = viewModelScope.launch {
            mutablePublicIp.value = PublicIpState(loading = true)
            try {
                mutablePublicIp.value = PublicIpState(info = publicLookup.lookup())
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                mutablePublicIp.value = PublicIpState(error = "Public IP details are temporarily unavailable.")
            }
        }
    }
    private fun begin(type: String, target: String, action: suspend () -> TestRecord) {
        if (job?.isActive == true) { message.value = "Stop the current test first."; return }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastStart < 2000) { message.value = "Wait a moment before retrying."; return }
        lastStart = now
        mutableState.value = DiagnosticState(true, type, target, "Running…",
            quality = if (type == "TCP") "Insufficient Data" else mutableState.value.quality)
        job = viewModelScope.launch {
            try {
                val record = try { action() } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    val text = describeError(error)
                    TestRecord(type = type, target = target, status = "Error", summary = text, details = text)
                }
                mutableState.value = mutableState.value.copy(running = false, text = record.details)
                try { app.repository.save(record) }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    message.value = "Test completed, but history could not be saved."
                }
            } catch (_: CancellationException) {
                mutableState.value = mutableState.value.copy(running = false,
                    text = mutableState.value.text + "\nStopped. Partial results are not saved.", quality = if (type == "TCP") "Insufficient Data" else mutableState.value.quality)
            }
        }
    }
    fun stop() { job?.cancel() }
    fun tcp(raw: String, portText: String) {
        val host: String; val port: Int
        try {
            host = Inputs.host(raw); port = portText.toIntOrNull() ?: error("Port must be a number.")
            require(port in 1..65535) { "Port must be 1–65535." }
        } catch (error: Exception) { message.value = error.message; return }
        begin("TCP", "$host:$port") {
            val results = TcpProbe(dns).run(host, port) { result ->
                val list = mutableState.value.attempts + result
                mutableState.value = mutableState.value.copy(attempts = list, text = list.joinToString("\n") { it.line() })
            }
            val times = results.mapNotNull { it.milliseconds }
            val summary = "${times.size}/5 successful TCP connections"
            val stats = if (times.isEmpty()) "No successful connections." else
                "Average: %.2f ms\nMinimum: %.2f ms\nMaximum: %.2f ms".format(times.average(), times.min(), times.max())
            val q = quality(results)
            mutableState.value = mutableState.value.copy(quality = q)
            val details = "TCP connection timing (not ICMP ping)\n$host:$port\n" + results.joinToString("\n") { it.line() } +
                "\n$summary\nFailed: ${5-times.size}\n$stats\nQuality: $q\nFailure does not prove the host is offline. Firewalls may block the port."
            TestRecord(type = "TCP", target = "$host:$port", status = if (times.size == 5) "Success" else "Partial / Failed",
                averageMs = times.takeIf { it.isNotEmpty() }?.average(), summary = summary, details = details)
        }
    }
    fun dns(raw: String, recordType: String) {
        val domain = try { Inputs.domain(raw) } catch (error: Exception) { message.value = error.message; return }
        begin("DNS", domain) {
            val addresses = dns.lookup(domain, recordType)
            val text = "Google Public DNS over HTTPS\n$domain · $recordType\n" +
                if (addresses.isEmpty()) "No $recordType records returned. This does not prove the domain is offline." else addresses.joinToString("\n")
            TestRecord(type = "DNS", target = domain, status = if (addresses.isEmpty()) "Empty" else "Success",
                summary = "${addresses.size} $recordType addresses", details = text)
        }
    }
    fun http(raw: String) {
        val url = try { Inputs.endpoint(raw) } catch (error: Exception) { message.value = error.message; return }
        begin("HTTPS", url.toString()) {
            val result = HttpProbe(app.client).check(url)
            val advice = if (result.successful) "Endpoint responded. This is one endpoint, not a complete internet-quality test."
            else "Server responded with an HTTP error. This does not mean the whole internet is down. Try a second endpoint."
            val duration = "%.2f".format(result.milliseconds)
            val text = "$url\nHTTP ${result.code}\nDuration: $duration ms\n$advice\nRedirects are reported, not followed."
            TestRecord(type = "HTTPS", target = url.toString(), status = if (result.successful) "Success" else "HTTP error",
                summary = "HTTP ${result.code}", details = text)
        }
    }
    fun saveSubnet(report: String, target: String) {
        viewModelScope.launch {
            try { app.repository.save(TestRecord(type = "Subnet", target = target, status = "Success", summary = "IPv4 subnet calculated", details = report))
                message.value = "Subnet report saved."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message.value = "Could not save subnet report."
            }
        }
    }
    fun delete(id: Long) = updateHistory { app.repository.delete(id) }
    fun clear() = updateHistory { app.repository.clear() }
    private fun updateHistory(action: suspend () -> Unit) { viewModelScope.launch {
        try { action() } catch (error: Exception) {
            if (error is CancellationException) throw error
            message.value = "History could not be updated."
        }
    } }
}
