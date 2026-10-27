package com.example.logobuilder.core

/** `hexdump -C` style rows with absolute offsets. Rows are 16 bytes. */
object HexDump {
    const val BYTES_PER_ROW = 16
    private const val HEX = "0123456789abcdef"

    fun rowCount(size: Int): Int = if (size <= 0) 0 else (size + BYTES_PER_ROW - 1) / BYTES_PER_ROW

    fun rowIndexForOffset(offset: Long): Int = (offset / BYTES_PER_ROW).toInt()

    /** Formats row [rowIndex]; throws IllegalArgumentException when outside the data. */
    fun formatRow(b: ByteArray, rowIndex: Int): String {
        require(rowIndex in 0 until rowCount(b.size)) { "row $rowIndex out of range" }
        val start = rowIndex * BYTES_PER_ROW
        val sb = StringBuilder(80)
        for (shift in 28 downTo 0 step 4) sb.append(HEX[(start ushr shift) and 0xF])
        sb.append("  ")
        for (j in 0 until BYTES_PER_ROW) {
            if (j == 8) sb.append(' ')
            if (start + j < b.size) {
                val v = b[start + j].toInt() and 0xFF
                sb.append(HEX[v ushr 4]).append(HEX[v and 0xF]).append(' ')
            } else sb.append("   ")
        }
        sb.append(" |")
        for (j in 0 until BYTES_PER_ROW) {
            if (start + j >= b.size) break
            val c = b[start + j].toInt() and 0xFF
            sb.append(if (c in 32..126) c.toChar() else '.')
        }
        sb.append('|')
        return sb.toString()
    }

    fun dump(b: ByteArray, maxRows: Int = Int.MAX_VALUE): String =
        (0 until minOf(rowCount(b.size), maxRows)).joinToString("\n") { formatRow(b, it) }
}
