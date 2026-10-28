package uk.co.fireburn.lochssh.serial

// Line settings for a USB serial adapter, kept in one text column on the host.
data class SerialSettings(
    val baud: Int = 115_200,
    val dataBits: Int = 8,
    val parity: Parity = Parity.NONE,
    val stopBits: StopBits = StopBits.ONE,
    val flow: Flow = Flow.NONE,
    val device: String = ""
) {
    enum class Parity { NONE, ODD, EVEN, MARK, SPACE }
    enum class StopBits { ONE, ONE_AND_HALF, TWO }
    enum class Flow { NONE, RTS_CTS, XON_XOFF }

    init {
        require(baud in MIN_BAUD..MAX_BAUD) { "Baud rate must be between $MIN_BAUD and $MAX_BAUD" }
        require(dataBits in 5..8) { "Data bits must be 5 to 8" }
        require(device.isEmpty() || deviceId.matches(device)) { "Device must look like 0403:6001, or be blank" }
    }

    fun encode(): String = listOf(baud, dataBits, parity, stopBits, flow, device).joinToString(",")

    // The vendor and product numbers, or null to use the first adapter found.
    fun deviceNumbers(): Pair<Int, Int>? = device.takeIf { it.isNotEmpty() }?.split(':')
        ?.let { it[0].toInt(16) to it[1].toInt(16) }

    companion object {
        const val MIN_BAUD = 50
        const val MAX_BAUD = 4_000_000
        private val deviceId = Regex("[0-9a-fA-F]{1,4}:[0-9a-fA-F]{1,4}")

        fun decode(text: String): SerialSettings = runCatching {
            val parts = text.split(',')
            require(parts.size == 6)
            SerialSettings(parts[0].toInt(), parts[1].toInt(), Parity.valueOf(parts[2]),
                StopBits.valueOf(parts[3]), Flow.valueOf(parts[4]), parts[5])
        }.getOrDefault(SerialSettings())
    }
}
