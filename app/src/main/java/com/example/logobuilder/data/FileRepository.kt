package com.example.logobuilder.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.logobuilder.core.BoundedReader
import com.example.logobuilder.core.Hashing
import com.example.logobuilder.core.ImportValidator
import com.example.logobuilder.state.RecentProject
import java.io.FileNotFoundException
import java.io.IOException

/** All Storage Access Framework I/O. Blocking: call from Dispatchers.IO only. */
class FileRepository(private val resolver: ContentResolver) {

    fun displayName(uri: Uri): String = query(uri) { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && !c.isNull(i)) c.getString(i) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"

    fun declaredSize(uri: Uri): Long? = query(uri) { c ->
        val i = c.getColumnIndex(OpenableColumns.SIZE)
        if (i >= 0 && !c.isNull(i)) c.getLong(i) else null
    }

    private fun <T> query(uri: Uri, block: (android.database.Cursor) -> T?): T? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { if (it.moveToFirst()) block(it) else null }
    } catch (e: Exception) { null }

    fun read(uri: Uri, maxBytes: Int, checkCancelled: () -> Unit): ByteArray {
        declaredSize(uri)?.let { if (ImportValidator.exceedsLimit(it, maxBytes)) throw com.example.logobuilder.core.ImportException(ImportValidator.tooLargeMessage(maxBytes)) }
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException("File tidak bisa dibuka")
        return input.use { BoundedReader.read(it, maxBytes, checkCancelled) }
    }

    fun tryKeepPermission(uri: Uri) {
        try { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (e: Exception) { /* provider does not offer persistable grants; recents may need re-pick */ }
    }

    /**
     * Writes [bytes] to the new destination [uri], then re-reads it and compares SHA-256.
     * Returns the verified hash. On any failure the partial destination is deleted and an exception is thrown.
     */
    fun writeAndVerify(uri: Uri, bytes: ByteArray, checkCancelled: () -> Unit): String {
        val expected = Hashing.sha256Hex(bytes)
        try {
            val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("Tujuan tidak bisa ditulis")
            out.use { it.write(bytes); it.flush() }
            checkCancelled()
            val input = resolver.openInputStream(uri) ?: throw IOException("Tujuan tidak bisa dibaca untuk verifikasi")
            val actual = input.use { Hashing.sha256Hex(it, checkCancelled) }
            if (actual != expected) throw IOException("Verifikasi gagal: hash hasil tulis tidak sama")
            return expected
        } catch (t: Throwable) {
            try { DocumentsContract.deleteDocument(resolver, uri) } catch (e: Exception) {}
            throw t
        }
    }
}

class RecentStore(context: Context) {
    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)
    fun load(): List<RecentProject> = RecentCodec.decode(prefs.getString("v1", "") ?: "")
    fun add(item: RecentProject): List<RecentProject> {
        val list = RecentCodec.add(load(), item)
        prefs.edit().putString("v1", RecentCodec.encode(list)).apply()
        return list
    }
    fun clear(): List<RecentProject> { prefs.edit().remove("v1").apply(); return emptyList() }
}
