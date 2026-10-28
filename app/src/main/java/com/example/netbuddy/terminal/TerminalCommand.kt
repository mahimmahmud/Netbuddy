package com.example.netbuddy.terminal

import com.example.netbuddy.network.Inputs

sealed class TerminalCommand {
    object Help : TerminalCommand()
    object Clear : TerminalCommand()
    data class Ping(val host: String) : TerminalCommand()
    data class Ssh(val user: String, val host: String, val port: Int = 22) : TerminalCommand()
    data class Telnet(val host: String, val port: Int = 23) : TerminalCommand()
}

object CommandParser {
    fun parse(raw: String): TerminalCommand {
        val parts = raw.trim().split(Regex("\\s+"))
        require(parts.isNotEmpty() && parts[0].isNotBlank()) { "Enter a command. Type help for examples." }
        return when (parts[0].lowercase()) {
            "help" -> { require(parts.size == 1); TerminalCommand.Help }
            "clear" -> { require(parts.size == 1); TerminalCommand.Clear }
            "ping" -> {
                require(parts.size == 2) { "Usage: ping <IP or hostname>" }
                TerminalCommand.Ping(Inputs.host(parts[1]))
            }
            "ssh" -> {
                require(parts.size in 2..3) { "Usage: ssh user@host [port]" }
                val at = parts[1].lastIndexOf('@')
                require(at > 0 && at < parts[1].lastIndex) { "Usage: ssh user@host [port]" }
                val user = parts[1].substring(0, at)
                require(user.length <= 64 && user.none { it.isISOControl() || it == '@' }) { "Invalid SSH username." }
                TerminalCommand.Ssh(user, Inputs.host(parts[1].substring(at + 1)), port(parts.getOrNull(2), 22))
            }
            "telnet" -> {
                require(parts.size in 2..3) { "Usage: telnet host [port]" }
                TerminalCommand.Telnet(Inputs.host(parts[1]), port(parts.getOrNull(2), 23))
            }
            else -> throw IllegalArgumentException("Unknown local command. Type help. No arbitrary local shell is available.")
        }
    }
    private fun port(value: String?, default: Int): Int {
        val number = if (value == null) default else value.toIntOrNull()
        require(number != null && number in 1..65535) { "Port must be 1–65535." }
        return number
    }
}

// A bounded line-terminal renderer, not a full-screen VT100 emulator.
// Parse escapes incrementally because network reads can split ANSI sequences.
class LineTerminal(private val maxLines: Int = 300, private val maxColumns: Int = 240) {
    private val lines = mutableListOf(StringBuilder())
    private var column = 0
    private var escape = 0
    private val control = StringBuilder()
    @Synchronized fun clear() { lines.clear(); lines.add(StringBuilder()); column = 0; escape = 0; control.clear() }
    @Synchronized fun append(text: String): String {
        for (c in text) {
            when (escape) {
                1 -> { escape = when(c) { '[' -> 2; ']' -> 3; else -> 0 }; control.clear(); continue }
                2 -> {
                    if (c in '@'..'~') {
                        val amount = control.toString().substringBefore(';').toIntOrNull() ?: 1
                        when(c) {
                            'K' -> if (control.toString() == "2") { lines.last().clear(); column = 0 }
                                else if (column < lines.last().length) lines.last().delete(column, lines.last().length)
                            'C' -> column = (column + amount).coerceAtMost(maxColumns)
                            'D' -> column = (column - amount).coerceAtLeast(0)
                            'G' -> column = (amount - 1).coerceIn(0, maxColumns)
                        }
                        escape = 0
                    } else if (control.length < 64) control.append(c) else escape = 0
                    continue
                }
                3 -> { if (c == '\u0007') escape = 0 else if (c == '\u001b') escape = 4; continue }
                4 -> { escape = if (c == '\\') 0 else 3; continue }
            }
            when(c) {
                '\u001b' -> escape = 1
                '\r' -> column = 0
                '\n' -> { lines.add(StringBuilder()); column = 0; if (lines.size > maxLines) lines.removeAt(0) }
                '\b', '\u007f' -> column = (column - 1).coerceAtLeast(0)
                '\t' -> repeat(8 - column % 8) { put(' ') }
                else -> if (!c.isISOControl()) put(c)
            }
        }
        return lines.joinToString("\n")
    }
    private fun put(c: Char) {
        if (column >= maxColumns) { lines.add(StringBuilder()); column = 0; if (lines.size > maxLines) lines.removeAt(0) }
        val line = lines.last()
        while (line.length < column) line.append(' ')
        if (column < line.length) line.setCharAt(column, c) else line.append(c)
        column++
    }
}
