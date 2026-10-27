package com.example.logobuilder.core

/** A recognised byte pattern. [verified] is false for hints not confirmed against samples. */
data class Signature(val name: String, val note: String? = null, val verified: Boolean = true)

/**
 * Generic container/image signatures only. A match says nothing about the layout of a
 * logo.bin payload; that requires a verified [com.example.logobuilder.format.LogoFormat].
 */
object Signatures {
    private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val ANDROID_BOOT = "ANDROID!".toByteArray(Charsets.US_ASCII)
    private val GIF87 = "GIF87a".toByteArray(Charsets.US_ASCII)
    private val GIF89 = "GIF89a".toByteArray(Charsets.US_ASCII)
    private val BMP_DIB_SIZES = setOf(12L, 40L, 52L, 56L, 64L, 108L, 124L)
    private const val MTK_HEADER_MAGIC = 0x58881688L

    fun detect(b: ByteArray): List<Signature> {
        val r = ByteReader(b)
        val out = mutableListOf<Signature>()
        if (r.matches(0, PNG)) out += Signature("PNG")
        if (r.matches(0, byteArrayOf(0x42, 0x4D))) {
            val dib = r.u32le(14)
            if (b.size >= 26 && dib != null && dib in BMP_DIB_SIZES) out += Signature("BMP")
        }
        if (r.matches(0, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))) out += Signature("JPEG")
        if (r.matches(0, GIF87) || r.matches(0, GIF89)) out += Signature("GIF")
        if (r.matches(0, byteArrayOf(0x50, 0x4B, 0x03, 0x04)) ||
            r.matches(0, byteArrayOf(0x50, 0x4B, 0x05, 0x06)) ||
            r.matches(0, byteArrayOf(0x50, 0x4B, 0x07, 0x08))
        ) out += Signature("ZIP")
        if (r.matches(0, byteArrayOf(0x1F, 0x8B.toByte(), 0x08))) out += Signature("GZIP")
        if (r.matches(0, ANDROID_BOOT)) out += Signature("Android boot image (ANDROID!)")
        if (r.u32le(0) == MTK_HEADER_MAGIC) {
            out += Signature(
                "Kemungkinan header partisi MediaTek (magic 0x58881688)",
                note = "Petunjuk saja: berdasarkan pengetahuan umum, belum diverifikasi dengan sampel. " +
                    "Tidak menjelaskan tata letak payload logo.",
                verified = false
            )
        }
        return out
    }
}
