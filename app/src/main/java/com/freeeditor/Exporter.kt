package com.freeeditor

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

/** Real export using Media3 Transformer (MediaCodec, hardware encoder with fallback). Call on main thread. */
class Exporter(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var transformer: Transformer? = null
    private var polling = false

    fun start(clips: List<Clip>, out: File, onProgress: (Int) -> Unit, onDone: (Result<File>) -> Unit) {
        val items = clips.map {
            val mi = MediaItem.Builder().setUri(Uri.parse(it.uri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(it.inMs).setEndPositionMs(it.outMs).build())
                .build()
            EditedMediaItem.Builder(mi).build()
        }
        val composition = Composition.Builder(listOf(EditedMediaItemSequence(items))).build()
        val t = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    polling = false; onDone(Result.success(out))
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    polling = false; out.delete(); onDone(Result.failure(exportException))
                }
            }).build()
        transformer = t
        out.parentFile?.mkdirs(); out.delete()
        t.start(composition, out.absolutePath)
        polling = true
        val holder = ProgressHolder()
        handler.post(object : Runnable {
            override fun run() {
                if (!polling) return
                if (t.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) onProgress(holder.progress)
                handler.postDelayed(this, 300)
            }
        })
    }

    fun cancel() { polling = false; transformer?.cancel() }
}
