package com.example.logobuilder.core

data class FileReport(val size: Int, val sha256: String, val signatures: List<Signature>)

object ImportValidator {
    fun tooLargeMessage(maxBytes: Int) = "File terlalu besar (batas ${maxBytes / 1024 / 1024} MB)"

    fun exceedsLimit(size: Long, maxBytes: Int) = size > maxBytes

    /** Returns an error message, or null when [size] is acceptable. */
    fun check(size: Long, maxBytes: Int = Inspector.MAX_BYTES): String? = when {
        size <= 0 -> "File kosong"
        size > maxBytes -> tooLargeMessage(maxBytes)
        else -> null
    }
}

object Inspector {
    const val MAX_BYTES = 64 * 1024 * 1024

    fun analyze(bytes: ByteArray) = FileReport(bytes.size, Hashing.sha256Hex(bytes), Signatures.detect(bytes))

    fun reportText(name: String, r: FileReport): String = buildString {
        appendLine("File: $name")
        appendLine("Ukuran: ${r.size} bytes")
        appendLine("SHA-256: ${r.sha256}")
        appendLine("Signature: " + if (r.signatures.isEmpty()) "(tidak dikenal)" else r.signatures.joinToString("; ") { it.name })
        appendLine("Catatan: file ini hanya diinspeksi; format logo.bin belum diparse.")
    }
}
