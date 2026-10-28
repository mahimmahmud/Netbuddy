package com.example.netbuddy.terminal

import com.example.netbuddy.network.*
import com.jcraft.jsch.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TerminalTests {
    @Test fun parsesSupportedCommands() {
        assertTrue(CommandParser.parse("help") is TerminalCommand.Help)
        assertTrue(CommandParser.parse("clear") is TerminalCommand.Clear)
        assertEquals(TerminalCommand.Ping("192.168.1.1"), CommandParser.parse("ping 192.168.1.1"))
        assertEquals(TerminalCommand.Ssh("admin", "192.168.88.1"), CommandParser.parse("ssh admin@192.168.88.1"))
        assertEquals(TerminalCommand.Ssh("user", "example.com", 2222), CommandParser.parse("ssh user@example.com 2222"))
        assertEquals(TerminalCommand.Telnet("192.168.88.1", 23), CommandParser.parse("telnet 192.168.88.1 23"))
    }
    @Test fun rejectsInvalidCommandsAndShellInjection() {
        listOf("", "sh", "ping", "ping -c", "ping 1.1.1.1;id", "ping host && id", "ssh host", "ssh @host", "telnet host 0", "telnet host 65536", "help anything").forEach { raw ->
            assertThrows("Rejected: $raw", IllegalArgumentException::class.java) { CommandParser.parse(raw) }
        }
    }
    @Test fun lineTerminalHandlesSplitEscapesCarriageReturnsAndBounds() {
        val display = LineTerminal(maxLines = 3, maxColumns = 20)
        assertEquals("", display.append("\u001b[3"))
        assertEquals("hello", display.append("2mhello"))
        assertEquals("HELLO", display.append("\rHELLO\u001b[0m"))
        assertEquals("x", display.append("\r\u001b[2Kx"))
        assertEquals("y\nz\nw", display.append("\ny\nz\nw"))
        display.clear()
        assertEquals("safe", display.append("\u001b]0;title\u0007safe"))
    }
    @Test fun hostFingerprintKnownVector() {
        assertEquals("SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU", fingerprint(byteArrayOf()))
    }
    @Test fun hostKeysRequireApprovalAndBlockChanges() {
        val key = byteArrayOf(0,0,0,7) + "ssh-rsa".toByteArray() + ByteArray(32) { 1 }
        val repository = FakeKeys()
        var prompts = 0
        val verifier = VerifyingHostKeys(repository) { prompts++; true }
        assertEquals(HostKeyRepository.OK, verifier.check("host", key))
        assertEquals(1, prompts)
        assertEquals(HostKeyRepository.OK, verifier.check("host", key))
        assertEquals(1, prompts)
        val changed = key.copyOf().also { it[it.lastIndex] = 2 }
        assertThrows(SecurityException::class.java) { verifier.check("host", changed) }
        assertEquals(1, prompts)
        val rejected = FakeKeys()
        assertThrows(SecurityException::class.java) { VerifyingHostKeys(rejected) { false }.check("other", key) }
        assertNull(rejected.stored)
    }
}
private class FakeKeys : HostKeyRepository {
    var stored: HostKey? = null
    override fun check(host: String, key: ByteArray): Int = when {
        stored == null -> HostKeyRepository.NOT_INCLUDED
        stored!!.key == java.util.Base64.getEncoder().encodeToString(key) -> HostKeyRepository.OK
        else -> HostKeyRepository.CHANGED
    }
    override fun add(hostkey: HostKey, ui: UserInfo?) { stored = hostkey }
    override fun remove(host: String?, type: String?) { stored = null }
    override fun remove(host: String?, type: String?, key: ByteArray?) { stored = null }
    override fun getKnownHostsRepositoryID() = "test"
    override fun getHostKey(): Array<HostKey> = stored?.let { arrayOf(it) } ?: emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = getHostKey()
}

class IcmpPingTests {
    @Test fun parsesReplyAndRealRtt() {
        val sample = PingOutput.parse(7, PingProcessResult(0, "64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time=12.34 ms\n1 packets transmitted, 1 received"))
        assertEquals(7, sample.sequence); assertTrue(sample.sent); assertTrue(sample.received)
        assertEquals(12.34, sample.rttMs!!, .0001); assertEquals(64, sample.ttl)
    }
    @Test fun parsesLossAndUnavailableSeparately() {
        val sample = PingOutput.parse(1, PingProcessResult(1, "1 packets transmitted, 0 received, 100% packet loss"))
        assertTrue(sample.sent); assertFalse(sample.received); assertNull(sample.rttMs)
        assertThrows(IcmpUnavailable::class.java) { PingOutput.parse(1, PingProcessResult(2, "ping: socket: Operation not permitted")) }
        assertThrows(java.net.UnknownHostException::class.java) { PingOutput.parse(1, PingProcessResult(2, "ping: unknown host bad.invalid")) }
    }
    @Test fun upperBoundsAreLabelled() {
        val sample = PingOutput.parse(1, PingProcessResult(0, "Reply: ttl=64 time<1 ms"))
        assertTrue(sample.upperBound); assertTrue(sample.line().contains("<1.000"))
        assertTrue(PingTotals().add(sample).report().contains("upper bounds"))
    }
    @Test fun statisticsDoNotCountUnsentProbesAsPacketLoss() {
        var totals = PingTotals()
        totals = totals.add(PingSample(1, true, true, 10.0, 64, output = ""))
        totals = totals.add(PingSample(2, true, false, null, null, output = ""))
        totals = totals.add(PingSample(3, false, false, null, null, output = ""))
        assertEquals(3, totals.attempts); assertEquals(2, totals.sent); assertEquals(1, totals.received)
        assertEquals(50.0, totals.lossPercent!!, .001); assertEquals(10.0, totals.average!!, .001)
        assertNull(PingTotals().lossPercent)
    }
    @Test fun boundedRunUsesProvidedEngineAndStopsAtCount() = runBlocking {
        var probes = 0
        val samples = mutableListOf<PingSample>()
        val process = PingProcess { _, _ -> probes++; PingProcessResult(0, "64 bytes from host: ttl=55 time=2.5 ms") }
        IcmpPing(process).run("example.com", PingOptions(count = 1)) { samples += it }
        assertEquals(1, probes); assertEquals(1, samples.size)
    }
    @Test fun continuousRunCanBeCancelled() = runBlocking {
        var probes = 0
        val process = PingProcess { _, _ -> probes++; PingProcessResult(0, "ttl=64 time=1 ms") }
        val task = launch { IcmpPing(process).run("example.com", PingOptions(count = 0)) { } }
        delay(50); task.cancelAndJoin()
        assertTrue(task.isCancelled); assertEquals(1, probes)
    }
    @Test fun rejectsInvalidOptions() {
        listOf(PingOptions(count = -1), PingOptions(count = 1001), PingOptions(intervalMs = 0), PingOptions(timeoutSeconds = 0), PingOptions(payloadBytes = 1473)).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validate() }
        }
    }
}
