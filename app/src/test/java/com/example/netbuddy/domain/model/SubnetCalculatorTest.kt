package com.example.netbuddy.domain.model

import org.junit.Assert.*
import org.junit.Test

class SubnetCalculatorTest {
    @Test fun ordinarySubnet() {
        val result = SubnetCalculator.calculate("192.168.1.130", 24)
        assertEquals("255.255.255.0", result.mask)
        assertEquals("192.168.1.0", result.network)
        assertEquals("192.168.1.255", result.broadcast)
        assertEquals("192.168.1.0", result.firstAddress)
        assertEquals("192.168.1.255", result.lastAddress)
        assertEquals("192.168.1.1", result.firstUsable)
        assertEquals("192.168.1.254", result.lastUsable)
        assertEquals(256L, result.totalAddresses)
        assertEquals(254L, result.usableHosts)
    }

    @Test fun zeroPrefixDoesNotOverflow() {
        val result = SubnetCalculator.calculate("255.255.255.255", 0)
        assertEquals("0.0.0.0", result.mask)
        assertEquals("0.0.0.0", result.network)
        assertEquals("255.255.255.255", result.broadcast)
        assertEquals("0.0.0.1", result.firstUsable)
        assertEquals("255.255.255.254", result.lastUsable)
        assertEquals(4294967296L, result.totalAddresses)
        assertEquals(4294967294L, result.usableHosts)
    }

    @Test fun pointToPointUsesBothAddresses() {
        val result = SubnetCalculator.calculate("10.0.0.5", 31)
        assertEquals("255.255.255.254", result.mask)
        assertEquals("10.0.0.4", result.network)
        assertEquals("10.0.0.5", result.broadcast)
        assertEquals("10.0.0.4", result.firstUsable)
        assertEquals("10.0.0.5", result.lastUsable)
        assertEquals(2L, result.usableHosts)
        assertTrue(result.note.contains("no directed broadcast"))
    }

    @Test fun hostRouteHasOneAddress() {
        val result = SubnetCalculator.calculate("255.255.255.255", 32)
        assertEquals("255.255.255.255", result.mask)
        assertEquals(result.address, result.network)
        assertEquals(result.address, result.broadcast)
        assertEquals(result.address, result.firstUsable)
        assertEquals(result.address, result.lastUsable)
        assertEquals(1L, result.totalAddresses)
        assertEquals(1L, result.usableHosts)
    }

    @Test fun nonOctetBoundary() {
        val result = SubnetCalculator.calculate("172.16.19.200", 20)
        assertEquals("255.255.240.0", result.mask)
        assertEquals("172.16.16.0", result.network)
        assertEquals("172.16.31.255", result.broadcast)
        assertEquals(4094L, result.usableHosts)
    }

    @Test fun rejectsInvalidAddresses() {
        listOf("", "1.2.3", "1.2.3.4.5", "256.1.1.1", "-1.2.3.4", "a.2.3.4",
            "1..3.4", "1.2.3.", "01.2.3.4", "1.2.3.4/24", "1. 2.3.4",
            "999999999999.1.1.1").forEach { input ->
            assertThrows("Input: $input", IllegalArgumentException::class.java) {
                SubnetCalculator.calculate(input, 24)
            }
        }
    }

    @Test fun rejectsInvalidPrefixes() {
        listOf(-1, 33, Int.MIN_VALUE, Int.MAX_VALUE).forEach { prefix ->
            assertThrows(IllegalArgumentException::class.java) {
                SubnetCalculator.calculate("192.168.1.1", prefix)
            }
        }
    }

    @Test fun validatesBoundaryAddressesAndWhitespace() {
        assertEquals(0L, SubnetCalculator.parseAddress("0.0.0.0"))
        assertEquals(4294967295L, SubnetCalculator.parseAddress("255.255.255.255"))
        assertEquals("10.0.0.1", SubnetCalculator.calculate(" 10.0.0.1 ", 8).address)
    }

    @Test fun everyPrefixHasCorrectSizeAndContainsInput() {
        for (prefix in 0..32) {
            val result = SubnetCalculator.calculate("203.0.113.93", prefix)
            val first = SubnetCalculator.parseAddress(result.network)
            val last = SubnetCalculator.parseAddress(result.broadcast)
            val ip = SubnetCalculator.parseAddress(result.address)
            val expectedSize = 1L shl (32 - prefix)
            assertEquals(expectedSize, result.totalAddresses)
            assertEquals(expectedSize, last - first + 1)
            assertEquals(0L, first % expectedSize)
            assertTrue(ip in first..last)
            assertEquals(if (prefix >= 31) expectedSize else expectedSize - 2, result.usableHosts)
        }
    }

    @Test fun reportContainsCalculatedValues() {
        val report = SubnetCalculator.calculate("192.168.1.10", 24).report()
        assertTrue(report.contains("192.168.1.10/24"))
        assertTrue(report.contains("Usable hosts: 254"))
    }
}
