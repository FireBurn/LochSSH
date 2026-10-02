package uk.co.fireburn.lochssh.ssh

internal object KnockSequence {
    enum class Protocol { TCP, UDP }
    data class Step(val port: Int, val protocol: Protocol)

    fun parse(text: String): List<Step> {
        if (text.isBlank()) return emptyList()
        require(text.length <= 512) { "Port sequence is too long" }
        val parts = text.split(',')
        require(parts.size <= 32) { "Use at most 32 ports" }
        return parts.map { part ->
            val fields = part.trim().split(':')
            require(fields.size in 1..2) { "Use port, port:tcp or port:udp" }
            val number = fields[0].trim()
            require(number.isNotEmpty() && number.all { it in '0'..'9' }) { "Enter a port number" }
            val port = number.toIntOrNull()
            require(port != null && port in 1..65535) { "Ports must be between 1 and 65535" }
            val protocol = if (fields.size == 1) Protocol.TCP else when (fields[1].trim().lowercase()) {
                "tcp" -> Protocol.TCP
                "udp" -> Protocol.UDP
                else -> throw IllegalArgumentException("Use tcp or udp")
            }
            Step(port, protocol)
        }
    }
}
