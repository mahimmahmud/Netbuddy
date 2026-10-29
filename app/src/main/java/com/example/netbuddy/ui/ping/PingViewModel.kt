package com.example.netbuddy.ui.ping

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.netbuddy.NetBuddyApplication
import com.example.netbuddy.data.TestRecord
import com.example.netbuddy.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PingState(val running: Boolean = false, val target: String = "", val totals: PingTotals = PingTotals(),
    val samples: List<PingSample> = emptyList(), val status: String = "Ready", val error: String? = null,
    val options: PingOptions = PingOptions(), val tracing: Boolean = false, val traceLines: List<String> = emptyList()) {
    fun report(): String = if (traceLines.isNotEmpty()) {
        "Traceroute · $target\n${if (tracing) "Running" else status}\n" + traceLines.joinToString("\n") +
            (error?.let { "\nError: $it" } ?: "") + "\nA missing hop response can be caused by router filtering."
    } else "ICMP Ping · $target\n$status\nPayload: ${options.payloadBytes} bytes · Interval: ${options.intervalMs / 1000.0}s · Timeout: ${options.timeoutSeconds}s\n" +
        totals.report() + "\n" + samples.joinToString("\n") { it.reportLine(target, options.payloadBytes + 8) } + (error?.let { "\n$it" } ?: "") +
        "\nA missing reply does not prove the destination is offline; ICMP may be filtered.\nOnly the latest 200 sample rows are retained; counters cover the full run."
}

class PingViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as NetBuddyApplication).repository
    private val mutable = MutableStateFlow(PingState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var traceJob: Job? = null
    fun start(raw: String, count: String, interval: String, timeout: String, size: String) {
        if (job?.isCompleted == false) return
        val target: String; val options: PingOptions
        try {
            target = Inputs.host(raw)
            options = PingOptions(count.toIntOrNull() ?: error("Enter a packet count; 0 means continuous."),
                (interval.toIntOrNull() ?: error("Enter an interval in seconds.")).toLong() * 1000,
                timeout.toIntOrNull() ?: error("Enter a timeout in seconds."), size.toIntOrNull() ?: error("Enter payload bytes."))
            options.validate()
        } catch (error: Exception) { mutable.update { it.copy(error = error.message) }; return }
        mutable.value = PingState(true, target, status = "Running", options = options)
        job = viewModelScope.launch {
            try {
                IcmpPing().run(target, options) { sample ->
                    mutable.update { it.copy(totals = it.totals.add(sample), samples = (it.samples + sample).takeLast(200)) }
                }
                mutable.update { it.copy(status = "Completed") }
            } catch (_: CancellationException) {
                mutable.update { it.copy(status = "Stopped") }
            } catch (error: Exception) {
                mutable.update { it.copy(status = if (error is IcmpUnavailable) "ICMP unavailable" else "Error", error = error.message ?: "Ping failed") }
            } finally {
                mutable.update { it.copy(running = false) }
                val result = mutable.value
                if (result.totals.attempts > 0 || result.error != null) withContext(NonCancellable) {
                    try { repo.save(TestRecord(type = "Ping", target = result.target, status = result.status,
                        averageMs = result.totals.average, summary = "${result.totals.received}/${result.totals.sent} ICMP replies · ${result.status}", details = result.report())) }
                    catch (_: Exception) { mutable.update { it.copy(error = (it.error?.plus("\n") ?: "") + "Could not save ping history.") } }
                }
            }
        }
    }
    fun stop() { job?.cancel(); traceJob?.cancel() }
    fun trace(raw: String, maxHops: Int = 20) {
        if (traceJob?.isActive == true || job?.isActive == true) return
        val target = try { Inputs.host(raw) } catch (e: Exception) { mutable.update { it.copy(error=e.message) }; return }
        mutable.update { it.copy(target=target, tracing=true, traceLines=listOf("Tracing route to $target · max $maxHops hops"), error=null) }
        traceJob = viewModelScope.launch {
            var traceStatus = "Completed"
            try {
                val destinations = withContext(Dispatchers.IO) {
                    java.net.InetAddress.getAllByName(target).map { it.hostAddress.substringBefore('%').lowercase() }.toSet()
                }
                val binary = listOf("/system/bin/ping", "/system/xbin/ping").firstOrNull { java.io.File(it).canExecute() }
                    ?: throw IcmpUnavailable("Android ping executable is unavailable; traceroute cannot run on this device.")
                for (ttl in 1..maxHops) {
                    currentCoroutineContext().ensureActive()
                    val hop = withContext(Dispatchers.IO) {
                        val p = ProcessBuilder(binary, "-n", "-c", "1", "-W", "1", "-t", ttl.toString(), target)
                            .redirectErrorStream(true).apply { environment()["LC_ALL"]="C" }.start()
                        val text = withTimeoutOrNull(2500) { async(Dispatchers.IO) { p.inputStream.bufferedReader().use { it.readText().take(2048) } }.await() }
                        if (p.isAlive) p.destroyForcibly()
                        if (Regex("invalid option|unknown option|usage:", RegexOption.IGNORE_CASE).containsMatchIn(text.orEmpty()))
                            throw IcmpUnavailable("This Android ping executable does not support TTL-based traceroute.")
                        val address = Regex("""from ([0-9a-fA-F:.]+?)(?::\s|\s)""", RegexOption.IGNORE_CASE).find(text.orEmpty())?.groupValues?.get(1)
                        val ms = Regex("time[=<]([0-9.,]+)\\s*ms", RegexOption.IGNORE_CASE).find(text.orEmpty())?.groupValues?.get(1)
                        val reached = address != null && address.substringBefore('%').lowercase() in destinations &&
                            !Regex("time to live exceeded|ttl expired", RegexOption.IGNORE_CASE).containsMatchIn(text.orEmpty())
                        val line = when { address == null -> "$ttl   *   Request timed out"; ms != null -> "$ttl   $address   ${ms} ms"; else -> "$ttl   $address   Hop response" }
                        line to reached
                    }
                    mutable.update { it.copy(traceLines=(it.traceLines+hop.first).takeLast(32)) }
                    if (hop.second) {
                        mutable.update { it.copy(traceLines=it.traceLines+"Destination reached at hop $ttl.") }
                        break
                    }
                }
            } catch (e: CancellationException) { traceStatus = "Stopped"; throw e }
            catch (e: Exception) { traceStatus = "Error"; mutable.update { it.copy(error=e.message ?: "Traceroute failed") } }
            finally {
                mutable.update { it.copy(tracing=false, status=traceStatus) }
                val result = mutable.value
                if (result.traceLines.size > 1 || result.error != null) withContext(NonCancellable) {
                    try {
                        val summary = "${(result.traceLines.size - 1).coerceAtLeast(0)} route hops · $traceStatus"
                        repo.save(TestRecord(type="Traceroute", target=target, status=traceStatus, summary=summary,
                            details=result.copy(tracing=false,status=traceStatus).report()))
                    } catch (_: Exception) { mutable.update { it.copy(error=(it.error?.plus("\n") ?: "")+"Could not save traceroute history.") } }
                }
            }
        }
    }
    fun stopTrace() { traceJob?.cancel() }
    fun reset() { if (job?.isCompleted != false) mutable.value = PingState() }
}
