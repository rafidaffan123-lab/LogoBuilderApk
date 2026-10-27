package com.example.logobuilder.state

import com.example.logobuilder.core.FileReport
import com.example.logobuilder.format.Identification

enum class Busy { IMPORTING, EXPORTING, DECODING_IMAGE }
enum class MessageKind { INFO, WARNING, ERROR }
class Message(val kind: MessageKind, val text: String)

class LoadedFile(
    val uri: String,
    val name: String,
    val bytes: ByteArray,
    val report: FileReport,
    val identification: Identification
)

/** Downscaled preview plus the true dimensions of the user's replacement image. */
class ReplacementImage(
    val name: String,
    val width: Int,
    val height: Int,
    val previewWidth: Int,
    val previewHeight: Int,
    val previewArgb: IntArray
)

data class RecentProject(val uri: String, val name: String, val size: Long, val sha256: String, val openedAt: Long)

data class UiState(
    val busy: Busy? = null,
    val loaded: LoadedFile? = null,
    val replacement: ReplacementImage? = null,
    val message: Message? = null,
    val recents: List<RecentProject> = emptyList()
)

// Pure transitions (unit-tested). A failed import must never leave the previous file on screen.
fun UiState.started(b: Busy) = copy(busy = b, message = null)
fun UiState.importSucceeded(f: LoadedFile) =
    copy(busy = null, loaded = f, replacement = null, message = Message(MessageKind.INFO, "File dibaca: ${f.name}"))
fun UiState.importFailed(text: String) =
    copy(busy = null, loaded = null, replacement = null, message = Message(MessageKind.ERROR, text))
fun UiState.exportSucceeded(text: String) = copy(busy = null, message = Message(MessageKind.INFO, text))
fun UiState.exportFailed(text: String) = copy(busy = null, message = Message(MessageKind.ERROR, text))
fun UiState.replacementLoaded(r: ReplacementImage) =
    copy(busy = null, replacement = r, message = Message(MessageKind.INFO, "Gambar pengganti dimuat: ${r.name}"))
fun UiState.replacementFailed(text: String) = copy(busy = null, replacement = null, message = Message(MessageKind.ERROR, text))
fun UiState.cancelled() = copy(busy = null, message = Message(MessageKind.WARNING, "Operasi dibatalkan"))
fun UiState.closed() = copy(busy = null, loaded = null, replacement = null, message = null)
