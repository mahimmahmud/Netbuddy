package com.example.netbuddy.domain.model

data class SubnetResult(
    val address: String,
    val prefix: Int,
    val mask: String,
    val network: String,
    val broadcast: String,
    val firstAddress: String,
    val lastAddress: String,
    val firstUsable: String,
    val lastUsable: String,
    val totalAddresses: Long,
    val usableHosts: Long,
    val note: String
) {
    fun report(): String = """
        IPv4: $address/$prefix
        Subnet mask: $mask
        Network address: $network
        Broadcast address (bitwise): $broadcast
        First address: $firstAddress
        Last address: $lastAddress
        First usable: $firstUsable
        Last usable: $lastUsable
        Total addresses: $totalAddresses
        Usable hosts: $usableHosts
        $note
    """.trimIndent()
}

object SubnetCalculator {
    private const val IPV4_MAX = 0xFFFFFFFFL

    fun parseAddress(input: String): Long {
        val parts = input.trim().split('.')
        require(parts.size == 4) { "Enter four IPv4 octets, for example 192.168.1.10." }
        return parts.fold(0L) { value, part ->
            require(part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' }) {
                "Each octet must contain 1–3 decimal digits."
            }
            require(part.length == 1 || part[0] != '0') { "Remove leading zeros from IPv4 octets." }
            val octet = part.toInt()
            require(octet in 0..255) { "Each IPv4 octet must be between 0 and 255." }
            (value shl 8) or octet.toLong()
        }
    }

    private fun format(value: Long): String =
        listOf(24, 16, 8, 0).joinToString(".") { ((value ushr it) and 255L).toString() }

    fun calculate(address: String, prefix: Int): SubnetResult {
        require(prefix in 0..32) { "CIDR prefix must be between 0 and 32." }
        val ip = parseAddress(address)
        // Long retains all 2^32 addresses for /0 without signed Int overflow.
        val total = 1L shl (32 - prefix)
        val mask = (IPV4_MAX shl (32 - prefix)) and IPV4_MAX
        val network = ip and mask
        val last = network + total - 1
        val usable = if (prefix >= 31) total else total - 2
        val firstUsable = if (prefix >= 31) network else network + 1
        val lastUsable = if (prefix >= 31) last else last - 1
        val note = when (prefix) {
            31 -> "RFC 3021 point-to-point semantics: both addresses are usable; no directed broadcast."
            32 -> "Single host route: one address; no directed broadcast."
            else -> "Conventional subnet arithmetic excludes network and broadcast. Reserved-address policies still apply."
        }
        return SubnetResult(format(ip), prefix, format(mask), format(network), format(last),
            format(network), format(last), format(firstUsable), format(lastUsable), total, usable, note)
    }
}
