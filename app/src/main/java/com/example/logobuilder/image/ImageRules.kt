package com.example.logobuilder.image

import com.example.logobuilder.core.SafeMath

/** Pure image helpers (no Android types) so they can be unit-tested on the JVM. */
object ImageRules {
    const val MAX_DIMENSION = 4096
    const val MAX_PIXELS = 4096L * 4096L

    /** Returns an error message, or null when the size is acceptable. */
    fun validateDimensions(width: Int, height: Int): String? = when {
        width <= 0 || height <= 0 -> "Dimensi gambar tidak valid ($width×$height)"
        width > MAX_DIMENSION || height > MAX_DIMENSION ->
            "Gambar terlalu besar ($width×$height); maksimum $MAX_DIMENSION piksel per sisi"
        width.toLong() * height > MAX_PIXELS -> "Jumlah piksel terlalu besar ($width×$height)"
        else -> null
    }

    /** Power-of-two decode sample size so that the longest side is <= [target]. */
    fun previewSampleSize(width: Int, height: Int, target: Int): Int {
        var s = 1
        val longest = maxOf(width, height)
        while (longest / s > target) s *= 2
        return s
    }

    fun rawSize(width: Int, height: Int, bytesPerPixel: Int): Long? {
        if (width <= 0 || height <= 0 || bytesPerPixel <= 0) return null
        val px = SafeMath.checkedMul(width.toLong(), height.toLong()) ?: return null
        return SafeMath.checkedMul(px, bytesPerPixel.toLong())
    }

    fun argbToRgb565(argb: Int): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return ((r shr 3) shl 11) or ((g shr 2) shl 5) or (b shr 3)
    }

    fun rgb565ToArgb(v: Int): Int {
        val r5 = (v shr 11) and 0x1F
        val g6 = (v shr 5) and 0x3F
        val b5 = v and 0x1F
        val r = (r5 shl 3) or (r5 shr 2)
        val g = (g6 shl 2) or (g6 shr 4)
        val b = (b5 shl 3) or (b5 shr 2)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
