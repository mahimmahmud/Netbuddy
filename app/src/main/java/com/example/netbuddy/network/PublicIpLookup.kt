package com.example.netbuddy.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class PublicIpInfo(val address: String, val isp: String, val location: String)

/**
 * Uses independent IP metadata services. A failure at one provider does not
 * hide the public address or prevent trying the next provider.
 */
class PublicIpLookup(client: OkHttpClient) {
    private val client = client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun lookup(): PublicIpInfo = withContext(Dispatchers.IO) {
        val (ipWho, ipApi, ipify) = supervisorScope {
            val whoRequest = async { attempt { fromIpWho() } }
            val apiRequest = async { attempt { fromIpApi() } }
            val ipifyRequest = async { attemptAddress { fromIpify() } }
            Triple(whoRequest.await(), apiRequest.await(), ipifyRequest.await())
        }
        val metadata = listOfNotNull(ipWho, ipApi)
        val address = metadata.firstNotNullOfOrNull { result -> result.address.takeUnless { it.isUnavailable() } }
            ?: ipify
        val isp = metadata.firstNotNullOfOrNull { result -> result.isp.takeUnless { it.isUnavailable() } }
        val location = metadata.firstNotNullOfOrNull { result -> result.location.takeUnless { it.isUnavailable() } }
        address?.let { PublicIpInfo(it, isp ?: "Unavailable", location ?: "Unavailable") }
            ?: throw IOException("Public IP lookup failed")
    }

    private suspend fun attempt(block: suspend () -> PublicIpInfo): PublicIpInfo? = try {
        block()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }

    private suspend fun attemptAddress(block: suspend () -> String): String? = try {
        block()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }

    private suspend fun fromIpWho(): PublicIpInfo {
        val json = getJson("https://ipwho.is/")
        if (!json.optBoolean("success")) throw IOException("IP lookup provider reported failure")
        val connection = json.optJSONObject("connection")
        return PublicIpInfo(
            address = json.optString("ip").cleanOrUnavailable(),
            isp = connection?.optString("isp").cleanOrUnavailable(),
            location = listOf("city", "region", "country")
                .mapNotNull { json.optString(it).cleanOrNull() }
                .distinct().joinToString(", ").ifBlank { "Unavailable" }
        )
    }

    private suspend fun fromIpApi(): PublicIpInfo {
        val json = getJson("https://ipapi.co/json/")
        if (json.optBoolean("error")) throw IOException(json.optString("reason", "IP lookup provider reported failure"))
        return PublicIpInfo(
            address = json.optString("ip").cleanOrUnavailable(),
            isp = json.optString("org").cleanOrUnavailable(),
            location = listOf("city", "region", "country_name")
                .mapNotNull { json.optString(it).cleanOrNull() }
                .distinct().joinToString(", ").ifBlank { "Unavailable" }
        )
    }

    private suspend fun fromIpify(): String {
        val json = getJson("https://api.ipify.org?format=json")
        return json.optString("ip").cleanOrNull() ?: throw IOException("Public IP was not returned")
    }

    private suspend fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).header("User-Agent", "NetBuddy/1.3").build()
        return client.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) throw IOException("IP information provider returned HTTP ${response.code}")
            JSONObject(response.body?.string() ?: throw IOException("Empty IP information response"))
        }
    }

    private fun String?.cleanOrUnavailable(): String = cleanOrNull() ?: "Unavailable"
    private fun String?.cleanOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "null" && !it.equals("unavailable", true) }
    private fun String.isUnavailable(): Boolean = equals("Unavailable", ignoreCase = true)
}
