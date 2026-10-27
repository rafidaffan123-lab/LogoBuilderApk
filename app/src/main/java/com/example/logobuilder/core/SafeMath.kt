package com.example.logobuilder.core

/** Overflow-safe arithmetic and range checks for parsing untrusted binary data. */
object SafeMath {
    /** Returns `offset + length` if [offset, offset+length) lies inside [0, total], else null. */
    fun checkedRange(offset: Long, length: Long, total: Long): Long? {
        if (offset < 0 || length < 0 || total < 0) return null
        if (offset > total) return null
        if (length > total - offset) return null
        return offset + length
    }

    fun checkedMul(a: Long, b: Long): Long? =
        try { Math.multiplyExact(a, b) } catch (e: ArithmeticException) { null }

    fun checkedAdd(a: Long, b: Long): Long? =
        try { Math.addExact(a, b) } catch (e: ArithmeticException) { null }
}
