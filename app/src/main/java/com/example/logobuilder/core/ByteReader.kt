package com.example.logobuilder.core

/** Bounds-checked reader. Every accessor returns null instead of throwing when out of range. */
class ByteReader(private val data: ByteArray) {
    val size: Int get() = data.size

    fun u8(offset: Long): Int? {
        SafeMath.checkedRange(offset, 1, data.size.toLong()) ?: return null
        return data[offset.toInt()].toInt() and 0xFF
    }

    fun u16le(offset: Long): Int? {
        SafeMath.checkedRange(offset, 2, data.size.toLong()) ?: return null
        val o = offset.toInt()
        return (data[o].toInt() and 0xFF) or ((data[o + 1].toInt() and 0xFF) shl 8)
    }

    fun u32le(offset: Long): Long? {
        SafeMath.checkedRange(offset, 4, data.size.toLong()) ?: return null
        val o = offset.toInt()
        var v = 0L
        for (i in 3 downTo 0) v = (v shl 8) or (data[o + i].toLong() and 0xFF)
        return v
    }

    fun u32be(offset: Long): Long? {
        SafeMath.checkedRange(offset, 4, data.size.toLong()) ?: return null
        val o = offset.toInt()
        var v = 0L
        for (i in 0..3) v = (v shl 8) or (data[o + i].toLong() and 0xFF)
        return v
    }

    /** True if the bytes at [offset] equal [expected]. False when out of range. */
    fun matches(offset: Long, expected: ByteArray): Boolean {
        SafeMath.checkedRange(offset, expected.size.toLong(), data.size.toLong()) ?: return false
        val o = offset.toInt()
        for (i in expected.indices) if (data[o + i] != expected[i]) return false
        return true
    }
}
