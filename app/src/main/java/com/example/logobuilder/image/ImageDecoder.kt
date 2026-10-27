package com.example.logobuilder.image

import android.graphics.BitmapFactory
import com.example.logobuilder.state.ReplacementImage
import java.io.IOException

/** Decodes a user-selected image: validates size from the header first, then decodes a bounded preview. */
object ImageDecoder {
    const val MAX_INPUT_BYTES = 32 * 1024 * 1024
    private const val PREVIEW_MAX = 1024

    fun decode(name: String, bytes: ByteArray): ReplacementImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) throw IOException("Bukan gambar yang dapat didekode (PNG/JPG/BMP/WebP/GIF)")
        ImageRules.validateDimensions(w, h)?.let { throw IOException(it) }
        val opts = BitmapFactory.Options().apply { inSampleSize = ImageRules.previewSampleSize(w, h, PREVIEW_MAX) }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IOException("Gagal mendekode gambar")
        try {
            val pw = bmp.width
            val ph = bmp.height
            val px = IntArray(pw * ph)
            bmp.getPixels(px, 0, pw, 0, 0, pw, ph)
            return ReplacementImage(name, w, h, pw, ph, px)
        } finally {
            bmp.recycle()
        }
    }
}
