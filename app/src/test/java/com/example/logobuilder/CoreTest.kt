package com.example.logobuilder

import com.example.logobuilder.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class HashingTest {
    // Standard FIPS 180-4 test vectors, independent of the implementation.
    @Test fun empty() = assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Hashing.sha256Hex(ByteArray(0)))
    @Test fun abc() = assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hashing.sha256Hex("abc".toByteArray()))
    @Test fun streamingEqualsOneShot() {
        val data = ByteArray(200_000) { (it * 31).toByte() }
        assertEquals(Hashing.sha256Hex(data), Hashing.sha256Hex(ByteArrayInputStream(data)))
    }
}

class HexDumpTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52)
    @Test fun fullRowMatchesHexdumpC() =
        assertEquals("00000000  89 50 4e 47 0d 0a 1a 0a  00 00 00 0d 49 48 44 52  |.PNG........IHDR|", HexDump.formatRow(png, 0))
    @Test fun partialLastRowIsPadded() {
        val data = ByteArray(17) { 0x41 }
        assertEquals(2, HexDump.rowCount(17))
        assertEquals("00000010  41" + " ".repeat(3 * 15 + 2) + " |A|", HexDump.formatRow(data, 1))
    }
    @Test fun offsetsAreAbsolute() {
        val data = ByteArray(0x120)
        assertTrue(HexDump.formatRow(data, 0x11).startsWith("00000110  "))
        assertEquals(0x11, HexDump.rowIndexForOffset(0x11F))
    }
    @Test fun emptyHasNoRows() = assertEquals(0, HexDump.rowCount(0))
    @Test(expected = IllegalArgumentException::class) fun outOfRangeRowThrows() { HexDump.formatRow(ByteArray(16), 1) }
}

class SignaturesTest {
    private fun names(b: ByteArray) = Signatures.detect(b).map { it.name }
    @Test fun png() = assertEquals(listOf("PNG"), names(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
    @Test fun pngPrefixOnlyIsRejected() = assertTrue(names(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)).isEmpty())
    @Test fun jpeg() = assertEquals(listOf("JPEG"), names(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
    @Test fun zip() = assertEquals(listOf("ZIP"), names(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0)))
    @Test fun bmpNeedsPlausibleDibHeader() {
        val ok = ByteArray(30); ok[0] = 0x42; ok[1] = 0x4D; ok[14] = 40
        assertEquals(listOf("BMP"), names(ok))
        val bad = ByteArray(30); bad[0] = 0x42; bad[1] = 0x4D; bad[14] = 7
        assertTrue(names(bad).isEmpty())
        assertTrue(names(byteArrayOf(0x42, 0x4D)).isEmpty())
    }
    @Test fun androidBoot() = assertEquals(1, names("ANDROID!xxxx".toByteArray()).size)
    @Test fun mtkHintIsMarkedUnverified() {
        val s = Signatures.detect(byteArrayOf(0x88.toByte(), 0x16, 0x88.toByte(), 0x58, 0, 0))
        assertEquals(1, s.size); assertFalse(s[0].verified)
    }
    @Test fun emptyAndShortInputsDoNotCrash() {
        assertTrue(names(ByteArray(0)).isEmpty()); assertTrue(names(byteArrayOf(1)).isEmpty())
    }
}

class SafeMathTest {
    @Test fun rangeAccepted() { assertEquals(10L, SafeMath.checkedRange(4, 6, 10)); assertEquals(10L, SafeMath.checkedRange(10, 0, 10)) }
    @Test fun rangeRejected() {
        assertNull(SafeMath.checkedRange(-1, 1, 10)); assertNull(SafeMath.checkedRange(4, 7, 10))
        assertNull(SafeMath.checkedRange(11, 0, 10)); assertNull(SafeMath.checkedRange(Long.MAX_VALUE, 1, 10))
        assertNull(SafeMath.checkedRange(1, Long.MAX_VALUE, 10))
    }
    @Test fun overflowDetected() { assertNull(SafeMath.checkedMul(Long.MAX_VALUE, 2)); assertNull(SafeMath.checkedAdd(Long.MAX_VALUE, 1)); assertEquals(6L, SafeMath.checkedMul(2, 3)) }
}

class ByteReaderTest {
    private val r = ByteReader(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0xFF.toByte()))
    @Test fun littleAndBigEndian() { assertEquals(0x04030201L, r.u32le(0)); assertEquals(0x01020304L, r.u32be(0)); assertEquals(0x0201, r.u16le(0)) }
    @Test fun noSignExtension() = assertEquals(0xFF, r.u8(4))
    @Test fun outOfRangeIsNull() { assertNull(r.u32le(2)); assertNull(r.u8(5)); assertNull(r.u8(-1)); assertNull(r.u16le(Long.MAX_VALUE)) }
    @Test fun highBitU32() = assertEquals(0xFFFFFFFFL, ByteReader(ByteArray(4) { 0xFF.toByte() }).u32le(0))
}

class BoundedReaderTest {
    @Test fun readsWithinLimit() = assertEquals(100, BoundedReader.read(ByteArrayInputStream(ByteArray(100)), 100).size)
    @Test(expected = ImportException::class) fun rejectsOverLimit() { BoundedReader.read(ByteArrayInputStream(ByteArray(101)), 100) }
    @Test fun cancellationCallbackInvoked() {
        var calls = 0
        BoundedReader.read(ByteArrayInputStream(ByteArray(200_000)), 1_000_000) { calls++ }
        assertTrue(calls >= 3)
    }
    @Test fun importValidator() {
        assertEquals("File kosong", ImportValidator.check(0))
        assertNull(ImportValidator.check(1))
        assertNotNull(ImportValidator.check(Inspector.MAX_BYTES + 1L))
        assertNull(ImportValidator.check(Inspector.MAX_BYTES.toLong()))
    }
}

class InspectorTest {
    @Test fun analyzeReportsSizeHashSignature() {
        val r = Inspector.analyze("abc".toByteArray())
        assertEquals(3, r.size)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", r.sha256)
        assertTrue(r.signatures.isEmpty())
    }
}
