package com.example.logobuilder.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipEntrySpec(val name: String, val bytes: ByteArray)

/** Plain ZIP archive creation. Deliberately independent of any flashing/device logic. */
object ZipExporter {
    fun write(out: OutputStream, entries: List<ZipEntrySpec>, timestampMillis: Long = System.currentTimeMillis()) {
        require(entries.isNotEmpty()) { "ZIP harus berisi minimal satu entri" }
        val seen = HashSet<String>()
        for (e in entries) {
            require(isSafeEntryName(e.name)) { "Nama entri tidak aman: ${e.name}" }
            require(seen.add(e.name)) { "Nama entri duplikat: ${e.name}" }
        }
        ZipOutputStream(out).use { zip ->
            for (e in entries) {
                val ze = ZipEntry(e.name).apply { time = timestampMillis }
                zip.putNextEntry(ze)
                zip.write(e.bytes)
                zip.closeEntry()
            }
        }
    }

    fun isSafeEntryName(n: String): Boolean =
        n.isNotEmpty() && !n.startsWith("/") && !n.contains('\\') && !n.contains('\u0000') &&
            n.split('/').none { it == ".." || it.isEmpty() }
}

object ExportNames {
    /** Strips path separators/control chars; falls back to [fallback]. */
    fun sanitize(name: String, fallback: String = "logo.bin"): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
            .filter { it.code >= 32 && it != ':' && it != '*' && it != '?' && it != '"' && it != '<' && it != '>' && it != '|' }
            .trim().trim('.')
        return cleaned.ifEmpty { fallback }
    }

    /** "logo.bin" -> "logo_copy.bin" */
    fun copyName(name: String): String {
        val n = sanitize(name)
        val dot = n.lastIndexOf('.')
        return if (dot > 0) n.substring(0, dot) + "_copy" + n.substring(dot) else n + "_copy"
    }

    fun zipName(name: String): String {
        val n = sanitize(name)
        val dot = n.lastIndexOf('.')
        return (if (dot > 0) n.substring(0, dot) else n) + ".zip"
    }
}
