package com.example.netbuddy.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.netbuddy.network.*
import com.example.netbuddy.NetBuddyApplication
import com.example.netbuddy.data.TestRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.InputStreamReader

private const val HELP = "NetBuddy Network Terminal\n\nping 192.168.1.1          4 real ICMP probes\nssh admin@192.168.88.1   SSH password login (port 22)\nssh admin@host 2222      SSH custom port\ntelnet 192.168.88.1 23   Telnet session\nhelp                    Show local commands\nclear                   Clear local display\n\nAfter connecting, input is sent to the remote device. Use Disconnect to return locally. No Linux packages or arbitrary local shell.\n"

data class TerminalState(val output: String = HELP, val busy: Boolean = false, val connected: Boolean = false,
    val protocol: String = "Local", val destination: String = "", val passwordRequest: TerminalCommand.Ssh? = null,
    val trust: HostPrompt? = null, val secretInput: Boolean = false, val stopping: Boolean = false)

class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(TerminalState())
    val state = mutable.asStateFlow()
    private val renderer = LineTerminal().apply { append(HELP) }
    private val repository = (application as NetBuddyApplication).repository
    private val knownHosts = File(application.filesDir, "ssh_known_hosts")
    @Volatile private var connection: RemoteConnection? = null
    @Volatile private var trustDecision: CompletableDeferred<Boolean>? = null
    private val writes = Channel<Pair<RemoteConnection, ByteArray>>(32)
    init {
        viewModelScope.launch(Dispatchers.IO) {
            for ((remote, bytes) in writes) {
                try { if (connection === remote) remote.send(bytes) }
                catch (error: Exception) { print("\nSend failed: ${error.message}\n") }
                finally { bytes.fill(0) }
            }
        }
    }
    private var job: Job? = null
    @Volatile private var hidden = false
    private val displayLock = Any()
    private fun print(text: String) { synchronized(displayLock) { if (hidden) return; val output = renderer.append(text); mutable.update { it.copy(output = output) } } }
    fun clear() { synchronized(displayLock) { renderer.clear(); mutable.update { it.copy(output = "") } } }
    fun submit(raw: String) {
        if (mutable.value.stopping) return
        if (mutable.value.connected) { sendLine(raw); return }
        if (job?.isCompleted == false || mutable.value.passwordRequest != null) return
        try {
            require(raw.length <= 512) { "Command is too long." }
            val command = CommandParser.parse(raw)
            if (command is TerminalCommand.Clear) { clear(); return }
            print("\nNetBuddy > ${raw.trim()}\n")
            when(command) {
                TerminalCommand.Help -> print(HELP)
                TerminalCommand.Clear -> clear()
                is TerminalCommand.Ping -> ping(command.host)
                is TerminalCommand.Ssh -> mutable.update { it.copy(passwordRequest = command) }
                is TerminalCommand.Telnet -> remote("Telnet", "${command.host}:${command.port}") {
                    TelnetConnection().also { connection = it; it.connect(command.host, command.port) }
                }
            }
        } catch (error: Exception) { print("${error.message ?: "Invalid command"}\n") }
    }
    fun cancelPassword() { mutable.update { it.copy(passwordRequest = null) } }
    fun connectSsh(password: String) {
        val command = mutable.value.passwordRequest ?: return
        mutable.update { it.copy(passwordRequest = null) }
        remote("SSH", "${command.user}@${command.host}:${command.port}") {
            SshConnection(command, knownHosts) { prompt ->
                if (hidden || mutable.value.stopping) false else {
                val decision = CompletableDeferred<Boolean>()
                trustDecision = decision
                mutable.update { if (it.stopping) it else it.copy(trust = prompt) }
                if (hidden || mutable.value.stopping) decision.complete(false)
                try { runBlocking { withTimeout(60000) { decision.await() } } }
                finally { trustDecision = null; mutable.update { it.copy(trust = null) } }
                }
            }.also { connection = it; it.connect(password) }
        }
    }
    fun trust(accept: Boolean) { trustDecision?.complete(accept) }
    private fun remote(protocol: String, destination: String, open: () -> RemoteConnection) {
        if (job?.isCompleted == false) return
        mutable.update { it.copy(busy = true, protocol = protocol, destination = destination, stopping = false) }
        if (protocol == "Telnet") print("Warning: Telnet sends credentials and traffic without encryption.\n")
        print("Connecting to $destination…\n")
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                ensureActive()
                val remote = open()
                ensureActive()
                mutable.update { it.copy(connected = true) }
                print("Connected. Remote input is not echoed locally.\n")
                InputStreamReader(remote.input, Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(1024)
                    while (isActive) {
                        val count = reader.read(buffer)
                        ensureActive()
                        if (count < 0) break
                        if (count > 0) {
                            val chunk = String(buffer, 0, count)
                            print(chunk)
                            val tail = mutable.value.output.takeLast(200)
                            if (Regex("(?i)password[^\\n]*:\\s*$").containsMatchIn(tail)) mutable.update { it.copy(secretInput = true) }
                        }
                    }
                }
                print("\nRemote session closed.\n")
            } catch (_: CancellationException) { print("\nSession stopped.\n") }
            catch (error: Exception) { print("\n${error.message ?: "Connection failed"}\n") }
            finally {
                runCatching { connection?.close() }; connection = null
                trustDecision?.complete(false)
                mutable.update { it.copy(busy = false, connected = false, protocol = "Local", destination = "", trust = null, secretInput = false, stopping = false) }
            }
        }
    }
    private fun ping(target: String) {
        mutable.update { it.copy(busy = true, protocol = "ICMP Ping", destination = target) }
        job = viewModelScope.launch {
            var totals = PingTotals()
            val samples = mutableListOf<PingSample>()
            var status = "Completed"
            var failure: String? = null
            try {
                IcmpPing().run(target, PingOptions(count = 4)) { sample ->
                    totals = totals.add(sample); samples += sample; print(sample.line() + "\n")
                }
                print(totals.report() + "\nNo reply may mean ICMP filtering; it is not proof the host is offline.\n")
            } catch (_: CancellationException) { status = "Stopped"; print("Stopped.\n" + totals.report() + "\n") }
            catch (error: Exception) { status = if (error is IcmpUnavailable) "ICMP unavailable" else "Error"; failure = error.message; print("${error.message ?: "Ping failed"}\n") }
            finally {
                if (totals.attempts > 0 || failure != null) withContext(NonCancellable) {
                    try {
                        repository.save(TestRecord(type = "Ping", target = target, status = status, averageMs = totals.average,
                            summary = "${totals.received}/${totals.sent} ICMP replies · Terminal",
                            details = "Terminal ICMP ping · $target\n$status\n${totals.report()}\n" + samples.joinToString("\n") { it.line() } + (failure?.let { "\n$it" } ?: "")))
                    } catch (_: Exception) { print("Could not save ping history.\n") }
                }
                mutable.update { it.copy(busy = false, protocol = "Local", destination = "", stopping = false) }
            }
        }
    }
    fun sendLine(text: String) {
        val remote = connection ?: return
        send((text + remote.lineEnding).toByteArray(Charsets.UTF_8))
        mutable.update { it.copy(secretInput = false) }
    }
    fun sendKey(value: String) = send(value.toByteArray(Charsets.UTF_8))
    private fun send(bytes: ByteArray) {
        val remote = connection
        if (!mutable.value.connected || mutable.value.stopping || remote == null) { bytes.fill(0); return }
        if (writes.trySend(remote to bytes).isFailure) { bytes.fill(0); print("\nOutput queue busy; retry after the pending input is sent.\n") }
    }
    fun secretInput(value: Boolean) { mutable.update { it.copy(secretInput = value) } }
    fun stop() {
        mutable.update { it.copy(passwordRequest = null, stopping = it.busy) }
        trustDecision?.complete(false)
        job?.cancel()
        val remote = connection
        viewModelScope.launch(Dispatchers.IO) { remote?.close() }
    }
    fun foreground() { hidden = false }
    fun background() { hidden = true; stop(); clear() }
    fun forgetKeys() {
        if (job?.isCompleted == false) return
        mutable.update { it.copy(busy = true, protocol = "Key store") }
        job = viewModelScope.launch(Dispatchers.IO) {
            try { if (knownHosts.exists() && !knownHosts.delete()) error("Could not remove saved keys")
                print("Saved SSH host keys removed. Verify fingerprints with the administrator before trusting new keys.\n")
            } catch (error: Exception) { print("${error.message}\n") }
            finally { mutable.update { it.copy(busy = false, protocol = "Local", stopping = false) } }
        }
    }
    override fun onCleared() { hidden = true; clear(); writes.close(); while (true) { val pending = writes.tryReceive().getOrNull() ?: break; pending.second.fill(0) }; trustDecision?.complete(false); val remote = connection; Thread({ runCatching { remote?.close() } }, "netbuddy-session-close").start(); super.onCleared() }
}
