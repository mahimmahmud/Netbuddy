package com.example.netbuddy.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IcmpDeviceTest {
    @Test fun loopbackReturnsIcmpReplyOrExplicitPlatformRestriction() = runBlocking {
        val samples = mutableListOf<PingSample>()
        try {
            IcmpPing().run("127.0.0.1", PingOptions(count = 1)) { samples += it }
            assertEquals(1, samples.size)
            assertTrue("Loopback should reply when ICMP execution is supported", samples.single().received)
        } catch (error: IcmpUnavailable) {
            assertTrue("A restricted platform must give a readable explanation", error.message?.isNotBlank() == true)
            assertTrue(samples.isEmpty())
        }
    }
}
