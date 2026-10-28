package uk.co.fireburn.lochssh.mosh

import java.io.ByteArrayOutputStream

// The state-sync instruction both sides send; diff carries the change between two numbered states.
internal data class Instruction(
    val oldNum: Long,
    val newNum: Long,
    val ackNum: Long,
    val throwawayNum: Long,
    val diff: ByteArray
) {
    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        Protobuf.uint(out, 1, PROTOCOL_VERSION.toLong())
        Protobuf.uint(out, 2, oldNum)
        Protobuf.uint(out, 3, newNum)
        Protobuf.uint(out, 4, ackNum)
        Protobuf.uint(out, 5, throwawayNum)
        if (diff.isNotEmpty()) Protobuf.bytes(out, 6, diff)
        return out.toByteArray()
    }

    companion object {
        const val PROTOCOL_VERSION = 2
        const val SHUTDOWN = -1L

        fun decode(data: ByteArray): Instruction? {
            var version = 0L
            var old = 0L
            var new = 0L
            var ack = 0L
            var throwaway = 0L
            var diff = ByteArray(0)
            for (field in Protobuf.read(data)) when (field.number) {
                1 -> version = field.number64
                2 -> old = field.number64
                3 -> new = field.number64
                4 -> ack = field.number64
                5 -> throwaway = field.number64
                6 -> diff = field.data
            }
            return if (version == PROTOCOL_VERSION.toLong()) Instruction(old, new, ack, throwaway, diff) else null
        }
    }
}

internal object MoshMessages {
    // The bytes the server wants shown, from the host message inside an instruction's diff.
    fun hostBytes(diff: ByteArray): List<ByteArray> = Protobuf.read(diff)
        .filter { it.number == 1 }
        .flatMap { instruction ->
            Protobuf.read(instruction.data).filter { it.number == 2 }.flatMap { hostBytes ->
                Protobuf.read(hostBytes.data).filter { it.number == 4 }.map { it.data }
            }
        }

    fun keystroke(keys: ByteArray): ByteArray = userInstruction(2) { Protobuf.bytes(it, 4, keys) }

    fun resize(columns: Int, rows: Int): ByteArray = userInstruction(3) {
        Protobuf.uint(it, 5, columns.toLong())
        Protobuf.uint(it, 6, rows.toLong())
    }

    // The user message the client sends: each event is one repeated instruction.
    fun userMessage(events: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        events.forEach { Protobuf.bytes(out, 1, it) }
        return out.toByteArray()
    }

    private fun userInstruction(extension: Int, body: (ByteArrayOutputStream) -> Unit): ByteArray {
        val inner = ByteArrayOutputStream()
        body(inner)
        val out = ByteArrayOutputStream()
        Protobuf.bytes(out, extension, inner.toByteArray())
        return out.toByteArray()
    }
}
