package com.rin.video

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.MutableStateFlow

/** Состояние движка загрузки (yt-dlp + ffmpeg). */
object Engine {
    val ready = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
}

class RinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread {
            var ok = false
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                Engine.ready.value = true
                ok = true
            } catch (e: Throwable) {
                Engine.error.value = e.message ?: "Не удалось запустить движок загрузки"
            }
            if (ok) {
                // обновляем yt-dlp: сайты часто меняются, свежая версия важна для TikTok/Instagram/YouTube
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(this)
                } catch (_: Throwable) {
                }
            }
        }.start()
    }
}
