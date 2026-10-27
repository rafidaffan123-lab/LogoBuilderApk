package com.example.logobuilder.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Thrown for user-facing import problems (empty, too large, ...). */
class ImportException(message: String) : IOException(message)

object BoundedReader {
    /** Reads the whole stream, failing with [ImportException] once [maxBytes] is exceeded. */
    fun read(input: InputStream, maxBytes: Int, checkCancelled: () -> Unit = {}): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            checkCancelled()
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw ImportException(ImportValidator.tooLargeMessage(maxBytes))
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
