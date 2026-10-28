package com.example.netbuddy.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class DiagnosticsTest {
    @Test fun validatesHostAndEndpoint() {
        assertEquals("example.com", Inputs.domain("example.com"))
        assertEquals("192.168.1.1", Inputs.host("192.168.1.1"))
        listOf("", "bad host", "https://example.com", "-example.com", "256.1.1.1").forEach {
            assertThrows(IllegalArgumentException::class.java) { Inputs.host(it) }
        }
        assertThrows(IllegalArgumentException::class.java) { Inputs.endpoint("http://example.com") }
        assertThrows(IllegalArgumentException::class.java) { Inputs.endpoint("https://user:pass@example.com") }
        assertEquals("https", Inputs.endpoint("https://example.com").scheme)
    }
    @Test fun dnsSuccessEmptyAndFailure() = runBlocking {
        MockWebServer().use { server ->
            val dns = GoogleDns(OkHttpClient(), server.url("/resolve"))
            server.enqueue(MockResponse().setBody("""{"Status":0,"Answer":[{"type":1,"data":"93.184.216.34"}]}"""))
            assertEquals(listOf("93.184.216.34"), dns.lookup("example.com", "A"))
            server.enqueue(MockResponse().setBody("""{"Status":0,"Answer":[{"type":28,"data":"2001:db8::1"}]}"""))
            assertEquals(listOf("2001:db8::1"), dns.lookup("example.com", "AAAA"))
            server.enqueue(MockResponse().setBody("""{"Status":0}"""))
            assertTrue(dns.lookup("example.com", "A").isEmpty())
            server.enqueue(MockResponse().setBody("""{"Status":3}"""))
            try { dns.lookup("invalid.example", "A"); fail("Expected NXDOMAIN") } catch (_: UnknownHostException) { }
        }
    }
    @Test fun httpStatusIsNotNetworkFailure() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            val result = HttpProbe(OkHttpClient()).check(server.url("/"))
            assertEquals(503, result.code)
            assertFalse(result.successful)
            assertTrue(result.milliseconds >= 0)
        }
    }
    @Test fun connectivityTimeoutIsHandled() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = OkHttpClient.Builder().readTimeout(150, TimeUnit.MILLISECONDS).callTimeout(1, TimeUnit.SECONDS).build()
            try { HttpProbe(client).check(server.url("/")); fail("Expected timeout") }
            catch (error: java.io.IOException) { assertTrue(describeError(error).isNotBlank()) }
            assertTrue(describeError(SocketTimeoutException()).contains("Timeout"))
        }
    }
    @Test fun qualityNeedsAllFiveAttempts() {
        assertEquals("Insufficient Data", quality(listOf(Attempt(1, 20.0))))
        assertEquals("Good", quality((1..5).map { Attempt(it, 20.0) }))
        assertEquals("Fair", quality((1..5).map { Attempt(it, 150.0) }))
        assertEquals("Poor", quality((1..5).map { Attempt(it, 300.0) }))
        assertEquals("Poor", quality((1..4).map { Attempt(it, 20.0) } + Attempt(5, error = "Timeout")))
    }
}
