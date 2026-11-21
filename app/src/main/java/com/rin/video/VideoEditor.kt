package com.rin.video

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class EditParams(
    val input: Uri,
    val startMs: Long,
    /** null — до конца видео */
    val endMs: Long?,
    val mute: Boolean,
    val music: Uri?,
    val originalVolume: Float,
    val musicVolume: Float,
    val rotation: Int,
    val flip: Boolean,
)

@androidx.annotation.OptIn(UnstableApi::class)
object VideoEditor {

    private fun volume(v: Float): AudioProcessor = ChannelMixingAudioProcessor().apply {
        putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(v))
        putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(v))
    }

    /** Нужно вызывать из главного потока. */
    suspend fun export(
        ctx: Context,
        p: EditParams,
        out: File,
        onProgress: (Float) -> Unit,
    ) = suspendCancellableCoroutine { cont ->
        val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(p.startMs).apply {
            if (p.endMs != null) setEndPositionMs(p.endMs)
        }.build()
        val videoMediaItem = MediaItem.Builder().setUri(p.input).setClippingConfiguration(clip).build()

        val videoEffects = mutableListOf<Effect>()
        if (p.rotation != 0 || p.flip) {
            videoEffects += ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(p.rotation.toFloat())
                .setScale(if (p.flip) -1f else 1f, 1f)
                .build()
        }
        val audioEffects = mutableListOf<AudioProcessor>()
        if (!p.mute && p.originalVolume != 1f) audioEffects += volume(p.originalVolume)

        val videoItem = EditedMediaItem.Builder(videoMediaItem)
            .setRemoveAudio(p.mute)
            .setEffects(Effects(audioEffects, videoEffects))
            .build()

        val sequences = mutableListOf(EditedMediaItemSequence.Builder(videoItem).build())

        if (p.music != null) {
            val musicEffects = mutableListOf<AudioProcessor>()
            if (p.musicVolume != 1f) musicEffects += volume(p.musicVolume)
            val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(p.music))
                .setRemoveVideo(true)
                .setEffects(Effects(musicEffects, emptyList()))
                .build()
            sequences += EditedMediaItemSequence.Builder(musicItem).setIsLooping(true).build()
        }

        val composition = Composition.Builder(sequences).build()
        val handler = Handler(Looper.getMainLooper())
        val holder = ProgressHolder()
        var finished = false

        lateinit var transformer: Transformer
        val poll = object : Runnable {
            override fun run() {
                if (finished) return
                if (transformer.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                }
                handler.postDelayed(this, 250)
            }
        }

        transformer = Transformer.Builder(ctx)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    handler.removeCallbacks(poll)
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    finished = true
                    handler.removeCallbacks(poll)
                    if (cont.isActive) cont.resumeWithException(exportException)
                }
            })
            .build()

        cont.invokeOnCancellation {
            finished = true
            handler.removeCallbacks(poll)
            try {
                transformer.cancel()
            } catch (_: Exception) {
            }
        }

        if (out.exists()) out.delete()
        transformer.start(composition, out.absolutePath)
        handler.post(poll)
    }
}
