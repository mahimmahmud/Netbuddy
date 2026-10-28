package com.example.netbuddy.network

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

data class PingOptions(val count: Int = 10, val intervalMs: Long = 1000, val timeoutSeconds: Int = 2, val payloadBytes: Int = 56) {
    fun validate() {
        require(count == 0 || count in 1..1000) { "Count must be 1–1000, or 0 for continuous." }
        require(intervalMs in 1000..10000) { "Interval must be 1–10 seconds." }
        require(timeoutSeconds in 1..10) { "Timeout must be 1–10 seconds." }
        require(payloadBytes in 0..1472) { "Payload must be 0–1472 bytes." }
    }
}
data class PingSample(val sequence: Int, val sent: Boolean, val received: Boolean, val rttMs: Double?, val ttl: Int?, val upperBound: Boolean = false, val output: String) {
    fun line(): String = "#$sequence " + when {
        received -> "Reply" + (rttMs?.let { " · ${if (upperBound) "<" else ""}%.3f ms".format(it) } ?: " · RTT unavailable") + (ttl?.let { " · TTL $it" } ?: "")
        sent -> "No ICMP reply / timeout"
        else -> "Probe could not be sent"
    }
}
data class PingTotals(val attempts: Int = 0, val sent: Int = 0, val received: Int = 0,
    val timed: Int = 0, val sum: Double = 0.0, val minimum: Double? = null, val maximum: Double? = null, val hasUpperBound: Boolean = false) {
    val average get() = if (timed == 0) null else sum / timed
    val lossPercent get() = if (sent == 0) null else 100.0 * (sent - received) / sent
    fun add(sample: PingSample): PingTotals = copy(attempts = attempts + 1, sent = sent + if (sample.sent) 1 else 0,
        received = received + if (sample.received) 1 else 0, timed = timed + if (sample.rttMs != null) 1 else 0,
        sum = sum + (sample.rttMs ?: 0.0), minimum = sample.rttMs?.let { minOf(minimum ?: it, it) } ?: minimum,
        maximum = sample.rttMs?.let { maxOf(maximum ?: it, it) } ?: maximum, hasUpperBound = hasUpperBound || sample.upperBound)
    fun report(): String = "Attempts: $attempts\nSent: $sent\nReceived: $received\nLost: ${sent - received}\nPacket loss: ${lossPercent?.let { "%.1f%%".format(it) } ?: "Unavailable"}\n" +
        "Min / Avg / Max: " + listOf(minimum, average, maximum).joinToString(" / ") { it?.let { value -> "%.3f ms".format(value) } ?: "—" } +
        if (hasUpperBound) "\nAggregates use reported upper bounds for replies marked <." else ""
}
class IcmpUnavailable(message: String) : java.io.IOException(message)

data class PingProcessResult(val code: Int, val output: String, val killed: Boolean = false)
fun interface PingProcess { suspend fun execute(target: String, options: PingOptions): PingProcessResult }

object PingOutput {
    private val rtt = Regex("time\\s*([=<])\\s*([0-9]+(?:[.,][0-9]+)?)\\s*ms", RegexOption.IGNORE_CASE)
    private val transmitted = Regex("(\\d+) packets? transmitted", RegexOption.IGNORE_CASE)
    fun parse(sequence: Int, result: PingProcessResult): PingSample {
        val text = result.output
        if (Regex("permission denied|operation not permitted|not found|invalid option|unknown option|invalid argument|unrecognized option|usage:|operation not supported", RegexOption.IGNORE_CASE).containsMatchIn(text))
            throw IcmpUnavailable("ICMP ping is unavailable or restricted on this device. No TCP result has been substituted.\n$text")
        if (Regex("unknown host|bad address|name or service not known|temporary failure in name resolution", RegexOption.IGNORE_CASE).containsMatchIn(text))
            throw java.net.UnknownHostException("Could not resolve the ping target.\n$text")
        val timing = rtt.find(text)
        val received = result.code == 0 || timing != null
        val sent = received || (transmitted.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0) > 0
        return PingSample(sequence, sent, received, timing?.groupValues?.get(2)?.replace(',', '.')?.toDoubleOrNull(),
            Regex("ttl[= ](\\d+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull(),
            timing?.groupValues?.get(1) == "<", text.take(4096))
    }
}

class AndroidPingProcess : PingProcess {
    override suspend fun execute(target: String, options: PingOptions): PingProcessResult = withContext(Dispatchers.IO) { coroutineScope {
        val binary = listOf("/system/bin/ping", "/system/xbin/ping").firstOrNull { File(it).canExecute() }
            ?: throw IcmpUnavailable("Android's ping executable is not available. Use the separately labelled TCP test instead.")
        val args = mutableListOf(binary)
        if (target.contains(':')) args += "-6"
        args += listOf("-c", "1", "-W", options.timeoutSeconds.toString(), "-w", (options.timeoutSeconds + 1).toString(), "-s", options.payloadBytes.toString(), target)
        val process = try { ProcessBuilder(args).redirectErrorStream(true).apply { environment()["LC_ALL"] = "C"; environment()["LANG"] = "C" }.start() }
        catch (error: java.io.IOException) { throw IcmpUnavailable("Could not start Android ICMP ping: ${error.message}. No TCP result has been substituted.") }
        val reader = async(Dispatchers.IO) { process.inputStream.bufferedReader().use { it.readText().take(8192) } }
        try {
            val deadline = System.nanoTime() + (options.timeoutSeconds + 3L) * 1_000_000_000L
            while (process.isAlive && System.nanoTime() < deadline) { ensureActive(); delay(50) }
            val killed = process.isAlive
            if (killed) process.destroyForcibly()
            withContext(Dispatchers.IO) { process.waitFor(1, TimeUnit.SECONDS) }
            val text = withTimeout(2000) { reader.await() }
            PingProcessResult(if (process.isAlive) -1 else process.exitValue(), text, killed)
        } finally {
            if (process.isAlive) process.destroyForcibly()
            reader.cancel()
        }
    } }
}

class IcmpPing(private val process: PingProcess = AndroidPingProcess()) {
    suspend fun run(raw: String, options: PingOptions, onSample: (PingSample) -> Unit) {
        val target = Inputs.host(raw)
        options.validate()
        var sequence = 0
        while (options.count == 0 || sequence < options.count) {
            currentCoroutineContext().ensureActive()
            val start = System.nanoTime()
            val result = process.execute(target, options)
            currentCoroutineContext().ensureActive()
            val sample = PingOutput.parse(++sequence, result)
            onSample(sample)
            if (!sample.sent) throw java.io.IOException("No ICMP probe was confirmed sent.\n${sample.output}")
            if (options.count == 0 || sequence < options.count) {
                val elapsed = (System.nanoTime() - start) / 1_000_000
                delay((options.intervalMs - elapsed).coerceAtLeast(0))
            }
        }
    }
}
