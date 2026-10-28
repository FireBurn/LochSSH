package uk.co.fireburn.lochssh.mosh

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.Inflater

// An instruction is compressed, split into pieces that fit one packet, and put back together.
internal object MoshFragments {
    private const val HEADER = 10
    const val PIECE = 1100

    fun split(id: Long, instruction: ByteArray): List<ByteArray> {
        val compressed = compress(instruction)
        val pieces = (compressed.size + PIECE - 1).coerceAtLeast(1) / PIECE
        return (0 until pieces).map { index ->
            val chunk = compressed.copyOfRange(index * PIECE, minOf(compressed.size, (index + 1) * PIECE))
            val last = index == pieces - 1
            ByteBuffer.allocate(HEADER + chunk.size).putLong(id)
                .putShort(((if (last) 0x8000 else 0) or index).toShort()).put(chunk).array()
        }
    }

    class Assembler {
        private var id = -1L
        private val pieces = HashMap<Int, ByteArray>()
        private var total = -1

        // Returns the finished instruction bytes once every piece has arrived.
        fun add(packet: ByteArray): ByteArray? {
            if (packet.size < HEADER) return null
            val buffer = ByteBuffer.wrap(packet)
            val fragmentId = buffer.long
            val combined = buffer.short.toInt() and 0xFFFF
            if (fragmentId != id) {
                if (fragmentId < id) return null
                id = fragmentId
                pieces.clear()
                total = -1
            }
            val number = combined and 0x7FFF
            if (combined and 0x8000 != 0) total = number + 1
            pieces[number] = packet.copyOfRange(HEADER, packet.size)
            if (total < 0 || pieces.size < total) return null
            val joined = ByteArrayOutputStream()
            for (i in 0 until total) joined.write(pieces[i] ?: return null)
            id = fragmentId + 1
            pieces.clear()
            total = -1
            return runCatching { decompress(joined.toByteArray()) }.getOrNull()
        }
    }

    fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    fun decompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, count)
            require(out.size() < MAX_INSTRUCTION) { "Instruction too large" }
        }
        inflater.end()
        return out.toByteArray()
    }

    private const val MAX_INSTRUCTION = 8 * 1024 * 1024
}
