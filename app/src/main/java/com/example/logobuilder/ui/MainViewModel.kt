package com.example.logobuilder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.logobuilder.core.ImportException
import com.example.logobuilder.core.ImportValidator
import com.example.logobuilder.core.Inspector
import com.example.logobuilder.data.FileRepository
import com.example.logobuilder.data.RecentStore
import com.example.logobuilder.export.ExportNames
import com.example.logobuilder.export.ZipEntrySpec
import com.example.logobuilder.export.ZipExporter
import com.example.logobuilder.format.FormatRegistry
import com.example.logobuilder.image.ImageDecoder
import com.example.logobuilder.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException

/** Owns UI state. All file I/O and hashing run on Dispatchers.IO; only one operation runs at a time. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FileRepository(app.contentResolver)
    private val recents = RecentStore(app)
    private val _state = MutableStateFlow(UiState(recents = recents.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var job: Job? = null

    private fun launchExclusive(kind: Busy, block: suspend () -> Unit) {
        if (_state.value.busy != null) return // ignore duplicate taps
        _state.update { it.started(kind) }
        job = viewModelScope.launch { block() }
    }

    fun cancel() { job?.cancel() }
    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun closeFile() { if (_state.value.busy == null) _state.update { it.closed() } }
    fun clearRecents() { _state.update { it.copy(recents = recents.clear()) } }

    fun openFile(uri: Uri) = launchExclusive(Busy.IMPORTING) {
        try {
            val file = withContext(Dispatchers.IO) {
                val ctx = currentCoroutineContext()
                repo.tryKeepPermission(uri)
                val name = repo.displayName(uri)
                val bytes = repo.read(uri, Inspector.MAX_BYTES) { ctx[Job]?.ensureActive() }
                ImportValidator.check(bytes.size.toLong())?.let { throw ImportException(it) }
                val report = Inspector.analyze(bytes)
                LoadedFile(uri.toString(), name, bytes, report, FormatRegistry.default.identify(bytes))
            }
            val list = recents.add(RecentProject(file.uri, file.name, file.bytes.size.toLong(), file.report.sha256, System.currentTimeMillis()))
            _state.update { it.importSucceeded(file).copy(recents = list) }
        } catch (e: CancellationException) {
            _state.update { it.cancelled() }; throw e
        } catch (e: Exception) {
            _state.update { it.importFailed(describe(e, "membaca")) }
        } catch (e: OutOfMemoryError) {
            _state.update { it.importFailed("Memori tidak cukup untuk membuka file ini") }
        }
    }

    fun loadReplacement(uri: Uri) = launchExclusive(Busy.DECODING_IMAGE) {
        try {
            val r = withContext(Dispatchers.IO) {
                val ctx = currentCoroutineContext()
                val name = repo.displayName(uri)
                val bytes = repo.read(uri, ImageDecoder.MAX_INPUT_BYTES) { ctx[Job]?.ensureActive() }
                ImageDecoder.decode(name, bytes)
            }
            _state.update { it.replacementLoaded(r) }
        } catch (e: CancellationException) {
            _state.update { it.cancelled() }; throw e
        } catch (e: Exception) {
            _state.update { it.replacementFailed(describe(e, "membaca gambar")) }
        } catch (e: OutOfMemoryError) {
            _state.update { it.replacementFailed("Memori tidak cukup untuk mendekode gambar") }
        }
    }

    /** Saves a byte-identical copy of the loaded file to a NEW destination, then verifies it. */
    fun exportCopy(dest: Uri) {
        val file = _state.value.loaded ?: return
        runExport(dest, file, "Salinan") { file.bytes }
    }

    fun exportZip(dest: Uri) {
        val file = _state.value.loaded ?: return
        runExport(dest, file, "ZIP") {
            val bos = ByteArrayOutputStream()
            ZipExporter.write(
                bos,
                listOf(
                    ZipEntrySpec(ExportNames.sanitize(file.name), file.bytes),
                    ZipEntrySpec("report.txt", Inspector.reportText(file.name, file.report).toByteArray(Charsets.UTF_8))
                )
            )
            bos.toByteArray()
        }
    }

    private fun runExport(dest: Uri, file: LoadedFile, label: String, produce: () -> ByteArray) {
        if (_state.value.busy != null) return
        if (dest.toString() == file.uri) {
            _state.update { it.exportFailed("Tujuan sama dengan file sumber; ekspor dibatalkan agar sumber tidak tertimpa") }
            return
        }
        launchExclusive(Busy.EXPORTING) {
            try {
                val sha = withContext(Dispatchers.IO) {
                    val ctx = currentCoroutineContext()
                    repo.writeAndVerify(dest, produce()) { ctx[Job]?.ensureActive() }
                }
                _state.update { it.exportSucceeded("$label tersimpan & terverifikasi (SHA-256 ${sha.take(12)}…)") }
            } catch (e: CancellationException) {
                _state.update { it.cancelled() }; throw e
            } catch (e: Exception) {
                _state.update { it.exportFailed(describe(e, "menulis")) }
            } catch (e: OutOfMemoryError) {
                _state.update { it.exportFailed("Memori tidak cukup untuk ekspor") }
            }
        }
    }

    private fun describe(e: Exception, verb: String): String = when (e) {
        is ImportException -> e.message ?: "File ditolak"
        is FileNotFoundException -> "File tidak ditemukan atau tidak bisa dibuka"
        is SecurityException -> "Izin akses ditolak; pilih ulang file"
        is IOException -> "Gagal $verb: ${e.message ?: "kesalahan I/O"}"
        else -> "Kesalahan tak terduga: ${e.message ?: e::class.java.simpleName}"
    }
}
