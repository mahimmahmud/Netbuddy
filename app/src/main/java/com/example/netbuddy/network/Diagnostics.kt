package com.example.netbuddy.network

import java.net.*
import java.io.IOException
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

object Inputs {
    fun host(raw: String): String {
        val host = raw.trim()
        require(host.isNotBlank() && host.length <= 253) { "Enter an IP address or hostname." }
        require(!host.contains('/') && !host.contains('@') && !host.any { it.isWhitespace() }) { "Enter only a hostname or IP, without a URL/path." }
        if (host.contains(':')) {
            require(host.all { it in "0123456789abcdefABCDEF:." }) { "Invalid IPv6 address." }
            require(InetAddress.getByName(host) is Inet6Address) { "Invalid IPv6 address." }
        } else if (host.all { it.isDigit() || it == '.' }) {
            com.example.netbuddy.domain.model.SubnetCalculator.parseAddress(host)
        } else {
            val ascii = IDN.toASCII(host.removeSuffix("."))
            require(ascii.split('.').all { it.isNotEmpty() && it.length <= 63 && it.first().isLetterOrDigit() && it.last().isLetterOrDigit() && it.all { c -> c.isLetterOrDigit() || c == '-' } }) { "Invalid domain name." }
        }
        return host
    }
    fun domain(raw: String): String {
        val host = host(raw)
        require(!host.contains(':') && !host.all { it.isDigit() || it == '.' }) { "Enter a domain name, not an IP address." }
        return IDN.toASCII(host.removeSuffix("."))
    }
    fun endpoint(raw: String): HttpUrl {
        val url = raw.trim().toHttpUrlOrNull()
        require(url != null && url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty()) { "Enter an HTTPS URL without embedded credentials." }
        return url
    }

}

// A suspended call cancels its underlying HTTP request when Stop is pressed.
suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, result, _ -> result.close() }
        }
    })
}

data class HttpResult(val code: Int, val milliseconds: Double) {
    val successful get() = code in 200..399
}

class HttpProbe(private val client: OkHttpClient) {
    suspend fun check(url: HttpUrl): HttpResult = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        client.newCall(Request.Builder().url(url).header("User-Agent", "NetBuddy/1.0").get().build())
            .awaitResponse().use { HttpResult(it.code, (System.nanoTime() - start) / 1_000_000.0) }
    }
}

interface AddressLookup { suspend fun lookup(domain: String, type: String): List<String> }

class GoogleDns(private val client: OkHttpClient, private val endpoint: HttpUrl = HttpUrl.Builder().scheme("https").host("dns.google").addPathSegment("resolve").build()) : AddressLookup {
    override suspend fun lookup(domain: String, type: String): List<String> = withContext(Dispatchers.IO) {
        require(type == "A" || type == "AAAA") { "Only A and AAAA records are supported." }
        val url = endpoint.newBuilder()
            .addQueryParameter("name", domain).addQueryParameter("type", type).build()
        val response = client.newCall(Request.Builder().url(url).header("Accept", "application/dns-json").build()).awaitResponse()
        response.use {
            if (!it.isSuccessful) throw IOException("DNS service returned HTTP ${it.code}")
            val body = JSONObject(it.body?.string() ?: throw IOException("Empty DNS response"))
            val status = body.optInt("Status", -1)
            if (status == 3) throw UnknownHostException("Domain does not exist (NXDOMAIN)")
            if (status != 0) throw IOException("DNS service status $status")
            val answers = body.optJSONArray("Answer") ?: return@use emptyList()
            val code = if (type == "A") 1 else 28
            (0 until answers.length()).map { answers.getJSONObject(it) }
                .filter { answer -> answer.optInt("type") == code }
                .map { answer -> answer.getString("data") }.distinct()
        }
    }
}

data class Attempt(val number: Int, val milliseconds: Double? = null, val error: String? = null) {
    fun line() = if (milliseconds != null) "#$number: %.2f ms".format(milliseconds) else "#$number: ${error ?: "Failed"}"
}

class TcpProbe(private val dns: AddressLookup) {
    suspend fun run(host: String, port: Int, onAttempt: (Attempt) -> Unit): List<Attempt> {
        require(port in 1..65535) { "Port must be 1–65535." }
        val address = withContext(Dispatchers.IO) {
            if (host.contains(':') || host.all { it.isDigit() || it == '.' }) InetAddress.getByName(host)
            else InetAddress.getByName(dns.lookup(Inputs.domain(host), "A").firstOrNull()
                ?: throw UnknownHostException("No IPv4 address returned for TCP test"))
        }
        val attempts = mutableListOf<Attempt>()
        repeat(5) { index ->
            currentCoroutineContext().ensureActive()
            val attempt = withContext(Dispatchers.IO) {
                Socket().use { socket ->
                    val start = System.nanoTime()
                    try {
                        socket.connect(InetSocketAddress(address, port), 2500)
                        Attempt(index + 1, (System.nanoTime() - start) / 1_000_000.0)
                    } catch (error: IOException) { Attempt(index + 1, error = describeError(error)) }
                }
            }
            currentCoroutineContext().ensureActive()
            attempts += attempt; onAttempt(attempt)
            if (index < 4) delay(400)
        }
        return attempts
    }
}

fun describeError(error: Throwable): String = when (error) {
    is SocketTimeoutException -> "Timeout. Check the route and retry; the endpoint may be filtering requests."
    is UnknownHostException -> "DNS lookup failed. Check spelling and try another domain or DNS service."
    is SSLException -> "TLS error. Check device date/time and the server certificate; do not bypass certificate checks."
    is ConnectException -> "Connection refused/unreachable. Check the service port and firewall; the host may still be online."
    is IllegalArgumentException -> error.message ?: "Invalid input"
    else -> "Request failed: ${error.message ?: error.javaClass.simpleName}. Try another endpoint to compare."
}

fun quality(attempts: List<Attempt>): String {
    if (attempts.size < 5) return "Insufficient Data"
    val good = attempts.mapNotNull { it.milliseconds }
    if (good.isEmpty() || good.size < attempts.size) return "Poor"
    return when { good.average() < 100 -> "Good"; good.average() < 250 -> "Fair"; else -> "Poor" }
}
