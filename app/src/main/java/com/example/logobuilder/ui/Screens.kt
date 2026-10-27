package com.example.logobuilder.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.logobuilder.core.HexDump
import com.example.logobuilder.export.ExportNames
import com.example.logobuilder.format.FormatRegistry
import com.example.logobuilder.format.Identification
import com.example.logobuilder.state.*
import kotlinx.coroutines.launch

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy = state.busy != null
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::openFile) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::loadReplacement) }
    val copyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(vm::exportCopy) }
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::exportZip) }

    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (busy) BusyBar(state.busy!!, vm::cancel)
            state.message?.let { MessageBanner(it, vm::dismissMessage) }
            val loaded = state.loaded
            if (loaded == null) {
                HomeScreen(state.recents, busy, { fileLauncher.launch(arrayOf("*/*")) }, { vm.openFile(android.net.Uri.parse(it)) }, vm::clearRecents)
            } else {
                BackHandler(enabled = !busy) { vm.closeFile() }
                InspectScreen(
                    file = loaded, replacement = state.replacement, busy = busy,
                    onClose = vm::closeFile,
                    onSaveCopy = { copyLauncher.launch(ExportNames.copyName(loaded.name)) },
                    onSaveZip = { zipLauncher.launch(ExportNames.zipName(loaded.name)) },
                    onPickImage = { imageLauncher.launch(arrayOf("image/*")) }
                )
            }
        }
    }
}

@Composable
private fun BusyBar(kind: Busy, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(when (kind) {
                Busy.IMPORTING -> "Membaca & menghitung hash…"
                Busy.EXPORTING -> "Menulis & memverifikasi…"
                Busy.DECODING_IMAGE -> "Mendekode gambar…"
            }, Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Batal") }
        }
    }
}

@Composable
private fun MessageBanner(m: Message, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg = when (m.kind) {
        MessageKind.ERROR -> colors.errorContainer
        MessageKind.WARNING -> colors.tertiaryContainer
        MessageKind.INFO -> colors.secondaryContainer
    }
    Surface(color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(m.text, Modifier.weight(1f).padding(vertical = 8.dp))
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    }
}

@Composable
private fun HomeScreen(recents: List<RecentProject>, busy: Boolean, onPick: () -> Unit, onOpenRecent: (String) -> Unit, onClear: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("LogoBuilder Android", style = MaterialTheme.typography.headlineSmall)
        Text("Inspeksi file logo (read-only). Tidak ada penulisan partisi.")
        Button(onClick = onPick, enabled = !busy) { Text("Impor logo.bin") }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Terakhir dibuka", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (recents.isNotEmpty()) TextButton(onClick = onClear, enabled = !busy) { Text("Hapus") }
        }
        if (recents.isEmpty()) Text("Belum ada.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recents) { r ->
                Card(Modifier.fillMaxWidth().clickable(enabled = !busy) { onOpenRecent(r.uri) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.name, style = MaterialTheme.typography.titleSmall)
                        Text("${r.size} bytes · ${r.sha256.take(16)}…", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private val TABS = listOf("Ringkasan", "Hex", "Format", "Ekspor")

@Composable
private fun InspectScreen(
    file: LoadedFile, replacement: ReplacementImage?, busy: Boolean,
    onClose: () -> Unit, onSaveCopy: () -> Unit, onSaveZip: () -> Unit, onPickImage: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(file.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
            TextButton(onClick = onClose, enabled = !busy) { Text("Tutup") }
        }
        TabRow(selectedTabIndex = tab) {
            TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> Scroll { SummaryTab(file) }
                1 -> HexTab(file.bytes)
                2 -> Scroll { FormatTab(file) }
                else -> Scroll { ExportTab(replacement, busy, onSaveCopy, onSaveZip, onPickImage) }
            }
        }
    }
}

@Composable
private fun Scroll(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)

@Composable
private fun SummaryTab(file: LoadedFile) {
    Text("Ukuran: ${file.report.size} bytes (${"%.2f".format(file.report.size / 1024.0)} KiB)")
    Text("SHA-256:")
    Text(file.report.sha256, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    Text("Signature:", style = MaterialTheme.typography.titleSmall)
    if (file.report.signatures.isEmpty()) Text("Tidak ada signature umum yang dikenali.")
    file.report.signatures.forEach { s ->
        Text("• ${s.name}${if (!s.verified) " (belum diverifikasi)" else ""}")
        s.note?.let { Text(it, fontSize = 12.sp) }
    }
}

@Composable
private fun FormatTab(file: LoadedFile) {
    when (val id = file.identification) {
        is Identification.Supported -> Text("Format: ${id.format.displayName}")
        is Identification.Unsupported -> {
            Text("Format belum didukung", style = MaterialTheme.typography.titleSmall)
            Text(id.reason)
        }
    }
    val names = FormatRegistry.default.supportedNames
    Text("Format terverifikasi: " + if (names.isEmpty()) "belum ada (butuh sampel + spesifikasi; lihat format-spec/notes.md)" else names.joinToString())
    Text("Gambar terekstrak: tidak ada. Tidak ada gambar yang ditampilkan karena tidak ada parser terverifikasi.")
}

@Composable
private fun ExportTab(replacement: ReplacementImage?, busy: Boolean, onSaveCopy: () -> Unit, onSaveZip: () -> Unit, onPickImage: () -> Unit) {
    Text("Ekspor selalu ke tujuan baru pilihanmu dan diverifikasi dengan hash.", fontSize = 13.sp)
    Button(onClick = onSaveCopy, enabled = !busy) { Text("Simpan salinan (tidak dimodifikasi)") }
    Button(onClick = onSaveZip, enabled = !busy) { Text("Ekspor ZIP (file + report.txt)") }
    HorizontalDivider()
    Text("Gambar pengganti", style = MaterialTheme.typography.titleSmall)
    OutlinedButton(onClick = onPickImage, enabled = !busy) { Text("Pilih gambar") }
    replacement?.let { r ->
        Text("${r.name}: ${r.width}×${r.height} px")
        val bmp = remember(r) {
            Bitmap.createBitmap(r.previewArgb, r.previewWidth, r.previewHeight, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
        Image(bmp, contentDescription = "Pratinjau gambar pengganti", modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp))
    }
    Text(
        "Membangun logo.bin dengan gambar pengganti belum didukung: belum ada format yang terverifikasi, " +
            "jadi tidak ada file hasil rebuild yang dibuat.",
        fontSize = 12.sp
    )
}

@Composable
private fun HexTab(bytes: ByteArray) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jump by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = jump,
                onValueChange = { v -> jump = v.filter { it.isLetterOrDigit() }.take(8); error = null },
                label = { Text("Offset (hex)") }, singleLine = true, isError = error != null,
                supportingText = { error?.let { Text(it) } }, modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val off = jump.toLongOrNull(16)
                if (off == null || off < 0 || off >= bytes.size) error = "Rentang 0–${"%x".format(bytes.size - 1)}"
                else scope.launch { listState.scrollToItem(HexDump.rowIndexForOffset(off)) }
            }) { Text("Lompat") }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            items(count = HexDump.rowCount(bytes.size)) { i ->
                Text(HexDump.formatRow(bytes, i), fontFamily = FontFamily.Monospace, fontSize = 8.sp, softWrap = false)
            }
        }
    }
}
