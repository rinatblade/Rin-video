package com.rin.video

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun EditorScreen(source: Uri, onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(source))
            prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var duration by remember { mutableLongStateOf(0L) }
    var rangeStart by remember { mutableFloatStateOf(0f) }
    var rangeEnd by remember { mutableFloatStateOf(1f) }
    var mute by remember { mutableStateOf(false) }
    var origVol by remember { mutableFloatStateOf(1f) }
    var music by remember { mutableStateOf<Uri?>(null) }
    var musicName by remember { mutableStateOf("") }
    var musicVol by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableIntStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var doneUri by remember { mutableStateOf<Uri?>(null) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && duration == 0L) {
                    duration = player.duration.coerceAtLeast(0L)
                    rangeStart = 0f
                    rangeEnd = duration.toFloat()
                }
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    // предпросмотр играет только выбранный отрезок
    LaunchedEffect(player) {
        while (true) {
            delay(80)
            if (player.isPlaying && duration > 0 && player.currentPosition >= rangeEnd.toLong()) {
                player.pause()
                player.seekTo(rangeStart.toLong())
            }
        }
    }
    LaunchedEffect(mute, origVol) { player.volume = if (mute) 0f else origVol.coerceAtMost(1f) }

    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            music = uri
            musicName = MediaUtil.displayName(ctx, uri)
        }
    }

    fun save() {
        if (duration <= 0L) return
        if (rangeEnd - rangeStart < 500f) {
            error = "Отрезок слишком короткий — минимум 0,5 секунды"
            return
        }
        player.pause()
        exporting = true
        progress = 0f
        error = null
        scope.launch {
            try {
                val out = File(ctx.cacheDir, "edit_${System.currentTimeMillis()}.mp4")
                val full = rangeEnd >= duration - 50f
                VideoEditor.export(
                    ctx,
                    EditParams(
                        input = source,
                        startMs = rangeStart.toLong(),
                        endMs = if (full) null else rangeEnd.toLong(),
                        mute = mute,
                        music = music,
                        originalVolume = origVol,
                        musicVolume = musicVol,
                        rotation = rotation,
                        flip = flip,
                    ),
                    out,
                ) { progress = it }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val uri = withContext(Dispatchers.IO) {
                    MediaUtil.save(ctx, out, "Rin_edit_$stamp", false).also { out.delete() }
                }
                doneUri = uri
                onSaved()
            } catch (e: Throwable) {
                error = "Не удалось сохранить: ${e.message ?: "ошибка"}"
            } finally {
                exporting = false
            }
        }
    }

    Column(
        Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            Text("Редактор видео", style = MaterialTheme.typography.titleLarge)
        }

        AndroidView(
            factory = { c -> PlayerView(c).apply { this.player = player } },
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        )

        SectionCard("Обрезка") {
            val maxV = if (duration > 0) duration.toFloat() else 1f
            RangeSlider(
                value = rangeStart..rangeEnd,
                onValueChange = { r ->
                    if (r.start != rangeStart) player.seekTo(r.start.toLong())
                    else if (r.endInclusive != rangeEnd) player.seekTo((r.endInclusive - 1500f).coerceAtLeast(r.start).toLong())
                    rangeStart = r.start
                    rangeEnd = r.endInclusive
                },
                valueRange = 0f..maxV,
                enabled = duration > 0 && !exporting,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("с ${MediaUtil.formatTime(rangeStart.toLong())}")
                Text("итого ${MediaUtil.formatTime((rangeEnd - rangeStart).toLong())}")
                Text("до ${MediaUtil.formatTime(rangeEnd.toLong())}")
            }
        }

        SectionCard("Звук") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (mute) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, null)
                Text("  Убрать оригинальный звук", Modifier.weight(1f))
                Switch(checked = mute, onCheckedChange = { mute = it }, enabled = !exporting)
            }
            if (!mute) {
                Text("Громкость оригинала: ${(origVol * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = origVol, onValueChange = { origVol = it }, valueRange = 0f..2f, enabled = !exporting)
            }
            OutlinedButton(onClick = { pickMusic.launch(arrayOf("audio/*")) }, enabled = !exporting) {
                Icon(Icons.Rounded.AudioFile, null)
                Text("  " + if (music == null) "Наложить свой звук" else "Заменить музыку")
            }
            if (music != null) {
                Text(musicName, style = MaterialTheme.typography.bodySmall)
                Text("Громкость музыки: ${(musicVol * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = musicVol, onValueChange = { musicVol = it }, valueRange = 0f..2f, enabled = !exporting)
                TextButton(onClick = { music = null; musicName = "" }, enabled = !exporting) { Text("Убрать музыку") }
            }
        }

        SectionCard("Картинка") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { rotation = (rotation + 90) % 360 }, enabled = !exporting) {
                    Icon(Icons.Rounded.RotateRight, null)
                    Text("  Повернуть ($rotation°)")
                }
                FilterChip(
                    selected = flip,
                    onClick = { flip = !flip },
                    label = { Text("Зеркало") },
                    leadingIcon = { Icon(Icons.Rounded.Flip, null) },
                    enabled = !exporting,
                )
            }
        }

        if (exporting) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("Сохраняю… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = { save() },
            enabled = !exporting && duration > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Rounded.Save, null)
            Text("  Сохранить видео")
        }
        Spacer(Modifier.height(24.dp))
    }

    doneUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { doneUri = null },
            title = { Text("Готово!") },
            text = { Text("Видео сохранено в галерею (папка Movies/Rin-video) и в раздел «Мои видео».") },
            confirmButton = {
                TextButton(onClick = { MediaUtil.share(ctx, uri, false) }) { Text("Поделиться") }
            },
            dismissButton = {
                TextButton(onClick = { doneUri = null; onClose() }) { Text("Закрыть") }
            },
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
