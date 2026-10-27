package com.example.logobuilder.core

import java.io.InputStream
import java.security.MessageDigest

object Hashing {
    private const val HEX = "0123456789abcdef"

    fun sha256Hex(bytes: ByteArray): String = toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** Streaming hash; [checkCancelled] runs once per chunk. */
    fun sha256Hex(input: InputStream, checkCancelled: () -> Unit = {}): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            checkCancelled()
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return toHex(md.digest())
    }

    private fun toHex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) {
            val v = x.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }
}
