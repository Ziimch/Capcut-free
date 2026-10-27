package com.freeeditor

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d.%d".format(s / 60, s % 60, (ms % 1000) / 100) }

private fun probeDurationMs(ctx: android.content.Context, uri: Uri): Long = runCatching {
    val r = MediaMetadataRetriever()
    r.setDataSource(ctx, uri)
    val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
    r.release(); d
}.getOrDefault(0L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(initial: Project, onChange: (Project) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var project by remember { mutableStateOf(initial) }
    val undo = remember { mutableStateListOf<List<Clip>>() }
    val redo = remember { mutableStateListOf<List<Clip>>() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var playheadMs by remember { mutableStateOf(0L) }
    var zoom by remember { mutableStateOf(60f) } // dp per second
    var exportProgress by remember { mutableStateOf<Int?>(null) }
    val exporter = remember { Exporter(ctx) }

    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { exporter.cancel(); player.release() } }

    fun commit(clips: List<Clip>) {
        undo.add(project.clips); redo.clear()
        project = project.copy(clips = clips); onChange(project)
    }
    val total = project.clips.sumOf { it.durationMs }

    // Keep the preview in sync with the project model.
    LaunchedEffect(project.clips) {
        val idx = player.currentMediaItemIndex; val pos = player.currentPosition
        if (project.clips.isEmpty()) { player.clearMediaItems(); return@LaunchedEffect }
        player.setMediaItems(project.clips.map {
            MediaItem.Builder().setUri(Uri.parse(it.uri)).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(it.inMs).setEndPositionMs(it.outMs).build()).build()
        })
        player.prepare()
        player.seekTo(idx.coerceIn(0, project.clips.size - 1), pos)
    }
    LaunchedEffect(Unit) {
        while (true) {
            if (project.clips.isNotEmpty()) {
                val i = player.currentMediaItemIndex.coerceIn(0, project.clips.size - 1)
                playheadMs = project.clips.take(i).sumOf { it.durationMs } + player.currentPosition.coerceAtLeast(0)
            } else playheadMs = 0
            delay(50)
        }
    }
    fun seekGlobal(ms: Long) {
        var acc = 0L
        project.clips.forEachIndexed { i, c ->
            if (ms < acc + c.durationMs || i == project.clips.lastIndex) {
                player.seekTo(i, (ms - acc).coerceIn(0, c.durationMs)); return
            }
            acc += c.durationMs
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val added = uris.mapNotNull { u ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val d = probeDurationMs(ctx, u)
            if (d > 0) Clip(uri = u.toString(), inMs = 0, outMs = d, sourceMs = d) else null
        }
        if (added.isEmpty() && uris.isNotEmpty()) Toast.makeText(ctx, "Could not read that media", Toast.LENGTH_SHORT).show()
        else if (added.isNotEmpty()) commit(project.clips + added)
    }

    val selected = project.clips.find { it.id == selectedId }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(project.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Button(enabled = project.clips.isNotEmpty() && exportProgress == null, onClick = {
                exportProgress = 0
                val out = File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "export_${UUID.randomUUID().toString().take(6)}.mp4")
                exporter.start(project.clips, out, { exportProgress = it }) { res ->
                    exportProgress = null
                    res.onSuccess { Toast.makeText(ctx, "Saved: ${it.absolutePath}", Toast.LENGTH_LONG).show() }
                    res.onFailure { Toast.makeText(ctx, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }) { Text("Export") }
            Spacer(Modifier.width(8.dp))
        }
        exportProgress?.let {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.weight(1f))
                TextButton(onClick = { exporter.cancel(); exportProgress = null }) { Text("Cancel") }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(androidx.compose.ui.graphics.Color.Black)) {
            AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = false } }, modifier = Modifier.fillMaxSize())
            if (project.clips.isEmpty()) Text("Tap + to import videos", Modifier.align(Alignment.Center))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            var playing by remember { mutableStateOf(false) }
            DisposableEffect(player) {
                val l = object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                }
                player.addListener(l); onDispose { player.removeListener(l) }
            }
            IconButton(onClick = { if (playing) player.pause() else player.play() }) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
            }
            Text("${fmt(playheadMs)} / ${fmt(total)}", style = MaterialTheme.typography.bodySmall)
            Slider(value = playheadMs.toFloat(), onValueChange = { seekGlobal(it.toLong()) },
                valueRange = 0f..maxOf(total, 1L).toFloat(), modifier = Modifier.weight(1f).padding(start = 8.dp))
        }
        // Timeline (single video track; multitrack is a planned stage)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp)) {
            project.clips.forEach { c ->
                val w = (c.durationMs / 1000f * zoom).dp.coerceAtLeast(24.dp)
                Surface(
                    shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface,
                    border = if (c.id == selectedId) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.width(w).height(56.dp).padding(end = 2.dp).clickable { selectedId = c.id }
                ) { Text(fmt(c.durationMs), Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall) }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Zoom", style = MaterialTheme.typography.labelSmall)
            Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 10f..200f)
        }
        selected?.let { c ->
            var range by remember(c.id, c.inMs, c.outMs) { mutableStateOf(c.inMs.toFloat()..c.outMs.toFloat()) }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Trim: ${fmt(range.start.toLong())} – ${fmt(range.endInclusive.toLong())}", style = MaterialTheme.typography.labelSmall)
                RangeSlider(value = range, onValueChange = { range = it }, valueRange = 0f..c.sourceMs.toFloat(),
                    onValueChangeFinished = {
                        val a = range.start.toLong(); val b = range.endInclusive.toLong()
                        if (b - a >= 200) commit(project.clips.map { if (it.id == c.id) it.copy(inMs = a, outMs = b) else it })
                    })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalIconButton(onClick = { picker.launch(arrayOf("video/*")) }) { Icon(Icons.Default.Add, "Import") }
            FilledTonalIconButton(enabled = undo.isNotEmpty(), onClick = {
                redo.add(project.clips); project = project.copy(clips = undo.removeLast()); onChange(project)
            }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
            FilledTonalIconButton(enabled = redo.isNotEmpty(), onClick = {
                undo.add(project.clips); project = project.copy(clips = redo.removeLast()); onChange(project)
            }) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
            FilledTonalIconButton(onClick = {
                var acc = 0L
                for (c in project.clips) {
                    val off = playheadMs - acc
                    if (off in 200 until c.durationMs - 200) {
                        val a = c.copy(outMs = c.inMs + off)
                        val b = c.copy(id = UUID.randomUUID().toString(), inMs = c.inMs + off)
                        commit(project.clips.flatMap { if (it.id == c.id) listOf(a, b) else listOf(it) }); break
                    }
                    acc += c.durationMs
                }
            }) { Icon(Icons.Default.ContentCut, "Split at playhead") }
            FilledTonalIconButton(enabled = selected != null, onClick = {
                selected?.let { s -> commit(project.clips.flatMap { if (it.id == s.id) listOf(it, it.copy(id = UUID.randomUUID().toString())) else listOf(it) }) }
            }) { Icon(Icons.Default.ContentCopy, "Duplicate") }
            FilledTonalIconButton(enabled = selected != null, onClick = {
                selected?.let { s -> commit(project.clips.filter { it.id != s.id }); selectedId = null }
            }) { Icon(Icons.Default.Delete, "Delete") }
            FilledTonalIconButton(enabled = selected != null && project.clips.indexOf(selected) > 0, onClick = {
                selected?.let { s ->
                    val l = project.clips.toMutableList(); val i = l.indexOf(s); l.removeAt(i); l.add(i - 1, s); commit(l)
                }
            }) { Icon(Icons.Default.ArrowBack, "Move earlier") }
            FilledTonalIconButton(enabled = selected != null && project.clips.indexOf(selected) < project.clips.lastIndex, onClick = {
                selected?.let { s ->
                    val l = project.clips.toMutableList(); val i = l.indexOf(s); l.removeAt(i); l.add(i + 1, s); commit(l)
                }
            }) { Icon(Icons.Default.ArrowForward, "Move later") }
        }
    }
}
