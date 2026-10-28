package com.example.netbuddy.terminal

import com.jcraft.jsch.*
import org.apache.commons.net.telnet.*
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64

fun fingerprint(key: ByteArray): String = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key))
data class HostPrompt(val host: String, val algorithm: String, val fingerprint: String)

class VerifyingHostKeys(private val repository: HostKeyRepository, private val approve: (HostPrompt) -> Boolean) : HostKeyRepository by repository {
    override fun check(host: String, key: ByteArray): Int {
        return when (val state = repository.check(host, key)) {
            HostKeyRepository.OK -> state
            HostKeyRepository.CHANGED -> throw SecurityException("SSH HOST KEY CHANGED for $host. Connection blocked. Verify with the server administrator before forgetting the saved key.")
            HostKeyRepository.NOT_INCLUDED -> {
                val hostKey = HostKey(host, key)
                if (!approve(HostPrompt(host, hostKey.type, fingerprint(key)))) throw SecurityException("SSH host key was not trusted. No password was sent.")
                repository.add(hostKey, null)
                HostKeyRepository.OK
            }
            else -> throw SecurityException("SSH host key verification failed.")
        }
    }
}

interface RemoteConnection : Closeable {
    val input: InputStream
    val lineEnding: String
    fun send(bytes: ByteArray)
}

class SshConnection(private val command: TerminalCommand.Ssh, private val knownHosts: File,
    private val approve: (HostPrompt) -> Boolean) : RemoteConnection {
    private val jsch = JSch()
    @Volatile private var session: Session? = null
    @Volatile private var channel: ChannelShell? = null
    private var output: OutputStream? = null
    private var source: InputStream? = null
    override val input: InputStream get() = source ?: throw IllegalStateException("SSH channel not open")
    override val lineEnding = "\r"
    fun connect(password: String) {
        knownHosts.parentFile?.mkdirs()
        if (!knownHosts.exists()) knownHosts.createNewFile()
        jsch.setKnownHosts(knownHosts.absolutePath)
        jsch.hostKeyRepository = VerifyingHostKeys(jsch.hostKeyRepository, approve)
        val ssh = jsch.getSession(command.user, command.host, command.port)
        session = ssh
        ssh.setConfig("StrictHostKeyChecking", "yes")
        ssh.setConfig("PreferredAuthentications", "password")
        val secret = password.toByteArray(Charsets.UTF_8)
        ssh.setPassword(secret)
        secret.fill(0)
        ssh.setServerAliveInterval(15000)
        ssh.serverAliveCountMax = 3
        try {
            ssh.connect(10000)
            val shell = ssh.openChannel("shell") as ChannelShell
            channel = shell
            shell.setPty(true)
            shell.setPtyType("dumb", 80, 24, 0, 0)
            source = shell.inputStream
            output = shell.outputStream
            shell.connect(10000)
        } finally { ssh.setPassword(ByteArray(0)) }
    }
    @Synchronized override fun send(bytes: ByteArray) { val stream = output ?: error("SSH is not connected"); stream.write(bytes); stream.flush() }
    override fun close() {
        runCatching { channel?.disconnect() }; runCatching { session?.disconnect() }
        output = null; source = null
    }
}

class TelnetConnection : RemoteConnection {
    private val client = TelnetClient("DUMB")
    override val input: InputStream get() = client.inputStream
    override val lineEnding = "\r\n"
    fun connect(host: String, port: Int) {
        client.connectTimeout = 10000
        client.addOptionHandler(TerminalTypeOptionHandler("DUMB", false, false, true, false))
        client.addOptionHandler(SuppressGAOptionHandler(true, true, true, true))
        client.addOptionHandler(EchoOptionHandler(false, false, false, true))
        client.addOptionHandler(WindowSizeOptionHandler(80, 24, true, false, true, false))
        client.connect(host, port)
    }
    @Synchronized override fun send(bytes: ByteArray) { client.outputStream.write(bytes); client.outputStream.flush() }
    override fun close() { runCatching { client.disconnect() } }
}
