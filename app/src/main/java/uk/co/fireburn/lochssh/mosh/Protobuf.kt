package uk.co.fireburn.lochssh.mosh

import java.io.ByteArrayOutputStream

// Only what the Mosh messages need: varints and length-delimited fields.
internal object Protobuf {
    const val VARINT = 0
    const val LENGTH_DELIMITED = 2

    fun varint(out: ByteArrayOutputStream, value: Long) {
        var rest = value
        while (rest and 0x7FL.inv() != 0L) {
            out.write(((rest and 0x7F) or 0x80).toInt())
            rest = rest ushr 7
        }
        out.write(rest.toInt())
    }

    fun uint(out: ByteArrayOutputStream, field: Int, value: Long) {
        varint(out, (field.toLong() shl 3) or VARINT.toLong())
        varint(out, value)
    }

    fun bytes(out: ByteArrayOutputStream, field: Int, value: ByteArray) {
        varint(out, (field.toLong() shl 3) or LENGTH_DELIMITED.toLong())
        varint(out, value.size.toLong())
        out.write(value)
    }

    class Field(val number: Int, val wire: Int, val number64: Long, val data: ByteArray)

    fun read(data: ByteArray): List<Field> {
        val fields = ArrayList<Field>()
        var pos = 0
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(pos < data.size && shift < 70) { "Damaged message" }
                val byte = data[pos++].toInt()
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
        }
        while (pos < data.size) {
            val key = varint()
            val number = (key ushr 3).toInt()
            when (val wire = (key and 7).toInt()) {
                VARINT -> fields.add(Field(number, wire, varint(), ByteArray(0)))
                LENGTH_DELIMITED -> {
                    val length = varint().toInt()
                    require(length >= 0 && pos + length <= data.size) { "Damaged message" }
                    fields.add(Field(number, wire, 0, data.copyOfRange(pos, pos + length)))
                    pos += length
                }
                1 -> { require(pos + 8 <= data.size); pos += 8 }
                5 -> { require(pos + 4 <= data.size); pos += 4 }
                else -> throw IllegalArgumentException("Unknown field type $wire")
            }
        }
        return fields
    }
}
