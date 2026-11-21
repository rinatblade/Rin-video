package com.rin.video

import android.content.Context
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

data class QualityOption(
    val label: String,
    val height: Int,
    val audioOnly: Boolean,
)

data class MediaMeta(
    val url: String,
    val title: String,
    val thumb: String?,
    val durationSec: Int,
    val uploader: String?,
    val options: List<QualityOption>,
)

object Downloader {

    private fun heightLabel(h: Int): String = when {
        h >= 2160 -> "${h}p · 4K"
        h >= 1440 -> "${h}p · 2K"
        h >= 1080 -> "${h}p · Full HD"
        h >= 720 -> "${h}p · HD"
        else -> "${h}p"
    }

    /** Получает информацию о видео и список доступных вариантов качества (без форматов с водяным знаком). */
    fun fetch(url: String): MediaMeta {
        val req = YoutubeDLRequest(url)
        req.addOption("--no-playlist")
        val info = YoutubeDL.getInstance().getInfo(req)
        val formats = info.formats ?: arrayListOf()

        val heights = formats.filter { f ->
            val vc = f.vcodec ?: ""
            vc.isNotEmpty() && vc != "none" &&
                f.height > 0 &&
                f.formatId != "download" &&
                !(f.formatNote ?: "").contains("watermark", ignoreCase = true)
        }.map { it.height }.distinct().sortedDescending()

        val options = mutableListOf<QualityOption>()
        if (heights.isEmpty()) {
            options += QualityOption("Лучшее доступное", 0, false)
        } else {
            heights.forEach { options += QualityOption(heightLabel(it), it, false) }
        }
        options += QualityOption("Только аудио · MP3", 0, true)

        return MediaMeta(
            url = url,
            title = info.title ?: "Видео",
            thumb = info.thumbnail,
            durationSec = info.duration,
            uploader = info.uploader,
            options = options,
        )
    }

    /**
     * Скачивает видео в выбранном качестве. Формат с водяным знаком (TikTok: format_id "download")
     * исключается фильтром, поэтому в файл он не попадает.
     */
    fun download(
        ctx: Context,
        meta: MediaMeta,
        opt: QualityOption,
        onProgress: (Float) -> Unit,
    ): Uri {
        val dir = File(ctx.cacheDir, "dl/${System.currentTimeMillis()}").apply { mkdirs() }
        val req = YoutubeDLRequest(meta.url)
        req.addOption("--no-playlist")
        req.addOption("--no-mtime")
        req.addOption("-o", File(dir, "video.%(ext)s").absolutePath)

        val noMark = "[format_id!=download]"
        if (opt.audioOnly) {
            req.addOption("-f", "ba$noMark/b$noMark")
            req.addOption("-x")
            req.addOption("--audio-format", "mp3")
        } else {
            val h = opt.height
            val capped = if (h > 0) "[height<=$h]" else ""
            req.addOption(
                "-f",
                "bv*$noMark$capped+ba/b$noMark$capped/bv*$noMark+ba/b$noMark",
            )
            req.addOption("-S", "res,vcodec:h264,acodec:aac")
            req.addOption("--merge-output-format", "mp4")
        }

        YoutubeDL.getInstance().execute(req, null) { progress, _, _ ->
            onProgress((progress / 100f).coerceIn(0f, 1f))
        }

        val allowed = setOf("mp4", "mkv", "webm", "mp3", "m4a", "ogg", "opus")
        val file = dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in allowed }
            ?.maxByOrNull { it.length() }
            ?: error("Файл не был создан")

        val uri = MediaUtil.save(ctx, file, meta.title, opt.audioOnly)
        dir.deleteRecursively()
        return uri
    }
}
