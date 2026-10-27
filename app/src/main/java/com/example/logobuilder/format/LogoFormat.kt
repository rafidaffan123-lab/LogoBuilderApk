package com.example.logobuilder.format

import com.example.logobuilder.core.Signature
import com.example.logobuilder.core.Signatures

/** A decoded image slot. [argb] has width*height entries. */
class LogoImage(val index: Int, val width: Int, val height: Int, val pixelFormat: String, val argb: IntArray)

sealed interface ParseResult {
    class Success(val images: List<LogoImage>) : ParseResult
    class Malformed(val reason: String) : ParseResult
}

sealed interface WriteResult {
    class Success(val bytes: ByteArray) : WriteResult
    class Rejected(val reason: String) : WriteResult
}

/**
 * A *verified* logo format. Implement only with evidence (documented spec + real samples,
 * see format-spec/notes.md). Implementations must validate every offset/length with
 * [com.example.logobuilder.core.SafeMath] and return Malformed rather than throw.
 */
interface LogoFormat {
    val id: String
    val displayName: String
    fun matches(bytes: ByteArray): Boolean
    fun parse(bytes: ByteArray): ParseResult
    /** Rebuild from [original], replacing images by slot index. */
    fun rebuild(original: ByteArray, replacements: Map<Int, LogoImage>): WriteResult
}

sealed interface Identification {
    class Supported(val format: LogoFormat) : Identification
    class Unsupported(val reason: String, val hints: List<Signature>) : Identification
}

class FormatRegistry(private val formats: List<LogoFormat>) {
    val supportedNames: List<String> get() = formats.map { it.displayName }

    fun identify(bytes: ByteArray): Identification {
        val hits = formats.filter { it.matches(bytes) }
        return when (hits.size) {
            1 -> Identification.Supported(hits[0])
            0 -> Identification.Unsupported(
                "Tidak ada parser terverifikasi yang cocok dengan file ini.", Signatures.detect(bytes)
            )
            else -> Identification.Unsupported(
                "Ambigu: beberapa parser cocok (${hits.joinToString { it.id }}). Ditolak demi keamanan.",
                Signatures.detect(bytes)
            )
        }
    }

    companion object {
        /** Intentionally empty: no logo.bin layout has been verified against samples yet. */
        val default = FormatRegistry(emptyList())
    }
}
