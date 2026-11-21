package com.rin.video

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

data class LibItem(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val isAudio: Boolean,
    val added: Long,
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("rin_video", Context.MODE_PRIVATE)
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()
    var font: Int
        get() = sp.getInt("font", -2)
        set(v) = sp.edit().putInt("font", v).apply()
}

object MediaUtil {
    const val VIDEO_DIR = "Movies/Rin-video"
    const val AUDIO_DIR = "Music/Rin-video"

    fun sanitize(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().take(80).ifBlank { "video" }

    fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        else -> "video/mp4"
    }

    /** Копирует файл в общую галерею (Movies/Rin-video или Music/Rin-video). */
    fun save(ctx: Context, file: File, baseName: String, audio: Boolean): Uri {
        val ext = file.extension.ifBlank { if (audio) "mp3" else "mp4" }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${sanitize(baseName)}.$ext")
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(ext))
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (audio) AUDIO_DIR else VIDEO_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (audio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(collection, values) ?: error("Не удалось создать файл в галерее")
        try {
            resolver.openOutputStream(uri)!!.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    fun query(ctx: Context): List<LibItem> {
        val out = mutableListOf<LibItem>()
        fun q(collection: Uri, dir: String, audio: Boolean) {
            val proj = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.DATE_ADDED,
            )
            ctx.contentResolver.query(
                collection, proj,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$dir/%"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    out += LibItem(
                        uri = ContentUris.withAppendedId(collection, c.getLong(0)),
                        name = c.getString(1) ?: "",
                        durationMs = c.getLong(2),
                        isAudio = audio,
                        added = c.getLong(3),
                    )
                }
            }
        }
        q(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), VIDEO_DIR, false)
        q(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), AUDIO_DIR, true)
        return out.sortedByDescending { it.added }
    }

    fun displayName(ctx: Context, uri: Uri): String {
        return try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "аудио"
        } catch (_: Exception) {
            "аудио"
        }
    }

    fun share(ctx: Context, uri: Uri, audio: Boolean) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (audio) "audio/*" else "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, if (audio) "Поделиться аудио" else "Поделиться видео"))
    }

    fun formatTime(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
