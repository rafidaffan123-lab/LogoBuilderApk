package com.example.logobuilder

import com.example.logobuilder.core.Inspector
import com.example.logobuilder.data.RecentCodec
import com.example.logobuilder.export.ExportNames
import com.example.logobuilder.export.ZipEntrySpec
import com.example.logobuilder.export.ZipExporter
import com.example.logobuilder.format.*
import com.example.logobuilder.image.ImageRules
import com.example.logobuilder.state.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

private fun loaded(name: String = "logo.bin", bytes: ByteArray = byteArrayOf(1, 2, 3)) =
    LoadedFile("content://x/$name", name, bytes, Inspector.analyze(bytes), FormatRegistry.default.identify(bytes))

class StateTest {
    @Test fun failedImportClearsStaleFileAndReplacement() {
        val s = UiState(loaded = loaded(), replacement = ReplacementImage("a", 1, 1, 1, 1, intArrayOf(0)))
            .started(Busy.IMPORTING).importFailed("rusak")
        assertNull(s.loaded); assertNull(s.replacement); assertNull(s.busy)
        assertEquals(MessageKind.ERROR, s.message!!.kind)
    }
    @Test fun successfulImportReplacesPreviousFileAndClearsReplacement() {
        val s = UiState(loaded = loaded("old.bin"), replacement = ReplacementImage("a", 1, 1, 1, 1, intArrayOf(0))).importSucceeded(loaded("new.bin"))
        assertEquals("new.bin", s.loaded!!.name); assertNull(s.replacement); assertEquals(MessageKind.INFO, s.message!!.kind)
    }
    @Test fun failedExportIsErrorAndKeepsFile() {
        val f = loaded()
        val s = UiState(loaded = f).started(Busy.EXPORTING).exportFailed("gagal")
        assertSame(f, s.loaded); assertEquals(MessageKind.ERROR, s.message!!.kind); assertNull(s.busy)
    }
    @Test fun cancelClearsBusyWithWarning() {
        val s = UiState().started(Busy.IMPORTING).cancelled()
        assertNull(s.busy); assertEquals(MessageKind.WARNING, s.message!!.kind)
    }
    @Test fun startingClearsOldMessage() = assertNull(UiState(message = Message(MessageKind.ERROR, "x")).started(Busy.EXPORTING).message)
}

class RecentCodecTest {
    private fun p(uri: String, name: String = "n") = RecentProject(uri, name, 5, "h", 1)
    @Test fun roundTripWithSpecialCharsSanitized() {
        val decoded = RecentCodec.decode(RecentCodec.encode(listOf(p("u1", "a\tb\nc"), p("u2"))))
        assertEquals(listOf("u1", "u2"), decoded.map { it.uri }); assertEquals("a b c", decoded[0].name)
    }
    @Test fun addDedupesMovesToFrontAndCaps() {
        var l = emptyList<RecentProject>()
        for (i in 1..15) l = RecentCodec.add(l, p("u$i"))
        assertEquals(RecentCodec.MAX, l.size); assertEquals("u15", l[0].uri)
        l = RecentCodec.add(l, p("u10")); assertEquals("u10", l[0].uri); assertEquals(1, l.count { it.uri == "u10" })
    }
    @Test fun garbageLinesIgnored() = assertTrue(RecentCodec.decode("garbage\n\na\tb\tx\ty\tz").isEmpty())
}

class ZipExporterTest {
    @Test fun roundTripContents() {
        val bos = ByteArrayOutputStream()
        val payload = ByteArray(1000) { it.toByte() }
        ZipExporter.write(bos, listOf(ZipEntrySpec("logo.bin", payload), ZipEntrySpec("report.txt", "hi".toByteArray())))
        val zin = ZipInputStream(ByteArrayInputStream(bos.toByteArray()))
        val got = LinkedHashMap<String, ByteArray>()
        generateSequence { zin.nextEntry }.forEach { got[it.name] = zin.readBytes() }
        assertEquals(listOf("logo.bin", "report.txt"), got.keys.toList())
        assertArrayEquals(payload, got["logo.bin"]); assertArrayEquals("hi".toByteArray(), got["report.txt"])
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTraversal() { ZipExporter.write(ByteArrayOutputStream(), listOf(ZipEntrySpec("../x", ByteArray(1)))) }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicates() { ZipExporter.write(ByteArrayOutputStream(), listOf(ZipEntrySpec("a", ByteArray(1)), ZipEntrySpec("a", ByteArray(1)))) }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyList() { ZipExporter.write(ByteArrayOutputStream(), emptyList()) }
    @Test fun names() {
        assertEquals("logo_copy.bin", ExportNames.copyName("logo.bin"))
        assertEquals("logo.zip", ExportNames.zipName("logo.bin"))
        assertEquals("evil.bin", ExportNames.sanitize("../../evil.bin"))
        assertEquals("logo.bin", ExportNames.sanitize("///"))
        assertEquals("noext_copy", ExportNames.copyName("noext"))
    }
}

class ImageRulesTest {
    @Test fun dimensions() {
        assertNull(ImageRules.validateDimensions(1080, 1920))
        assertNotNull(ImageRules.validateDimensions(0, 10)); assertNotNull(ImageRules.validateDimensions(-1, 10))
        assertNotNull(ImageRules.validateDimensions(4097, 10)); assertNull(ImageRules.validateDimensions(4096, 4096))
    }
    @Test fun sampleSize() {
        assertEquals(1, ImageRules.previewSampleSize(1024, 768, 1024)); assertEquals(4, ImageRules.previewSampleSize(4000, 3000, 1024))
    }
    @Test fun rawSize() {
        assertEquals(1080L * 1920 * 2, ImageRules.rawSize(1080, 1920, 2)); assertNull(ImageRules.rawSize(0, 1, 2))
    }
    // RGB565 expectations are computed by hand from the definition (5-6-5 bit packing), not from the code.
    @Test fun rgb565Packing() {
        assertEquals(0xF800, ImageRules.argbToRgb565(0xFFFF0000.toInt())); assertEquals(0x07E0, ImageRules.argbToRgb565(0xFF00FF00.toInt()))
        assertEquals(0x001F, ImageRules.argbToRgb565(0xFF0000FF.toInt())); assertEquals(0xFFFF, ImageRules.argbToRgb565(0xFFFFFFFF.toInt()))
        assertEquals(0, ImageRules.argbToRgb565(0xFF000000.toInt()))
    }
    @Test fun rgb565Unpacking() {
        assertEquals(0xFFFF0000.toInt(), ImageRules.rgb565ToArgb(0xF800)); assertEquals(0xFFFFFFFF.toInt(), ImageRules.rgb565ToArgb(0xFFFF))
        assertEquals(0xFF000000.toInt(), ImageRules.rgb565ToArgb(0))
    }
    @Test fun rgb565IsStableUnderRoundTrip() { for (v in 0..0xFFFF step 257) assertEquals(v, ImageRules.argbToRgb565(ImageRules.rgb565ToArgb(v))) }
}

class FormatRegistryTest {
    private class Fake(override val id: String, val hit: Boolean) : LogoFormat {
        override val displayName = id
        override fun matches(bytes: ByteArray) = hit
        override fun parse(bytes: ByteArray): ParseResult = ParseResult.Malformed("n/a")
        override fun rebuild(original: ByteArray, replacements: Map<Int, LogoImage>): WriteResult = WriteResult.Rejected("n/a")
    }
    @Test fun defaultRegistryHasNoVerifiedFormatsAndRejectsEverything() {
        assertTrue(FormatRegistry.default.supportedNames.isEmpty())
        assertTrue(FormatRegistry.default.identify(ByteArray(64)) is Identification.Unsupported)
    }
    @Test fun singleMatchSupported() = assertTrue(FormatRegistry(listOf(Fake("a", true), Fake("b", false))).identify(ByteArray(1)) is Identification.Supported)
    @Test fun ambiguousMatchRejected() = assertTrue(FormatRegistry(listOf(Fake("a", true), Fake("b", true))).identify(ByteArray(1)) is Identification.Unsupported)
}
