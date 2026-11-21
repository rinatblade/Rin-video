package com.rin.video

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {
    private val sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { RinRoot(sharedText) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            sharedText.value = i.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}

class DlState {
    var url by mutableStateOf("")
    var loading by mutableStateOf(false)
    var meta by mutableStateOf<MediaMeta?>(null)
    var selected by mutableIntStateOf(0)
    var progress by mutableFloatStateOf(0f)
    var downloading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var result by mutableStateOf<Uri?>(null)
    var resultAudio by mutableStateOf(false)
}

fun extractUrl(text: String): String? =
    Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', '!')

fun friendly(e: Throwable): String {
    val m = e.message ?: ""
    return when {
        m.contains("Unsupported URL", true) -> "Эта ссылка не поддерживается. Поддерживаются TikTok, Instagram и YouTube."
        m.contains("login", true) || m.contains("Sign in", true) || m.contains("private", true) ->
            "Видео закрытое или требует входа в аккаунт."
        m.contains("429") -> "Сервис временно ограничил запросы. Попробуйте чуть позже."
        m.contains("Unable to resolve host", true) || m.contains("network", true) -> "Нет подключения к интернету."
        else -> "Не удалось обработать видео: " + m.lineSequence().lastOrNull { it.isNotBlank() }.orEmpty().take(180)
    }
}

@Composable
fun RinRoot(shared: MutableState<String?>) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val fonts = remember { loadFonts(ctx) }
    var themeIdx by remember { mutableIntStateOf(prefs.theme.coerceIn(0, RinThemes.lastIndex)) }
    var fontIdx by remember {
        val saved = prefs.font
        mutableIntStateOf(
            when {
                saved == -2 -> fonts.indexOfFirst { it.file == "Rubik.ttf" }.let { if (it < 0) -1 else it }
                else -> saved.coerceIn(-1, fonts.lastIndex)
            },
        )
    }
    val theme = RinThemes[themeIdx]
    val family = remember(fontIdx, fonts) {
        fonts.getOrNull(fontIdx)?.let { familyFor(ctx, it) } ?: FontFamily.Default
    }

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !theme.dark
            c.isAppearanceLightNavigationBars = !theme.dark
        }
    }

    RinMaterialTheme(theme, family) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(theme.bg1, theme.bg2))),
        ) {
            AppBody(
                shared = shared,
                fonts = fonts,
                themeIdx = themeIdx,
                fontIdx = fontIdx,
                onTheme = { themeIdx = it; prefs.theme = it },
                onFont = { fontIdx = it; prefs.font = it },
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun AppBody(
    shared: MutableState<String?>,
    fonts: List<RinFont>,
    themeIdx: Int,
    fontIdx: Int,
    onTheme: (Int) -> Unit,
    onFont: (Int) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editUri by remember { mutableStateOf<Uri?>(null) }
    var libVersion by remember { mutableIntStateOf(0) }
    val dl = remember { DlState() }
    val ctx = LocalContext.current

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) editUri = uri
    }

    BackHandler(enabled = editUri != null) { editUri = null }

    val editing = editUri
    if (editing != null) {
        EditorScreen(
            source = editing,
            onClose = { editUri = null },
            onSaved = { libVersion++ },
        )
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
                val items = listOf(
                    Triple("Загрузка", Icons.Rounded.FileDownload, 0),
                    Triple("Мои видео", Icons.Rounded.VideoLibrary, 1),
                    Triple("Стиль", Icons.Rounded.Palette, 2),
                )
                items.forEach { (label, icon, idx) ->
                    NavigationBarItem(
                        selected = tab == idx,
                        onClick = { tab = idx },
                        icon = { Icon(icon, label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> DownloadTab(
                    dl = dl,
                    shared = shared,
                    onEdit = { editUri = it },
                    onPickFromGallery = { pickVideo.launch("video/*") },
                    onDownloaded = { libVersion++ },
                )
                1 -> LibraryTab(
                    version = libVersion,
                    onEdit = { editUri = it },
                    onChanged = { libVersion++ },
                )
                else -> StyleTab(fonts, themeIdx, fontIdx, onTheme, onFont)
            }
        }
    }
}

@Composable
fun LogoBadge(size: Int = 52) {
    Image(
        painterResource(R.drawable.ic_launcher_art),
        null,
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.28f).dp)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun DownloadTab(
    dl: DlState,
    shared: MutableState<String?>,
    onEdit: (Uri) -> Unit,
    onPickFromGallery: () -> Unit,
    onDownloaded: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val engineReady by Engine.ready.collectAsState()
    val engineErr by Engine.error.collectAsState()

    LaunchedEffect(shared.value) {
        shared.value?.let { t ->
            extractUrl(t)?.let { dl.url = it }
            shared.value = null
        }
    }

    fun analyze() {
        val u = extractUrl(dl.url)
        if (u == null) {
            dl.error = "Вставьте ссылку на видео (TikTok, Instagram или YouTube)"
            return
        }
        dl.url = u
        dl.loading = true
        dl.error = null
        dl.meta = null
        dl.result = null
        scope.launch {
            try {
                withTimeout(90_000) { Engine.ready.first { it } }
                val m = withContext(Dispatchers.IO) { Downloader.fetch(u) }
                dl.meta = m
                dl.selected = 0
            } catch (e: Throwable) {
                dl.error = friendly(e)
            } finally {
                dl.loading = false
            }
        }
    }

    fun download() {
        val m = dl.meta ?: return
        val opt = m.options[dl.selected]
        dl.downloading = true
        dl.progress = 0f
        dl.error = null
        dl.result = null
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    Downloader.download(ctx, m, opt) { dl.progress = it }
                }
                dl.result = uri
                dl.resultAudio = opt.audioOnly
                onDownloaded()
            } catch (e: Throwable) {
                dl.error = friendly(e)
            } finally {
                dl.downloading = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LogoBadge()
            Column {
                Text("Rin-video", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Скачивай без водяных знаков", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!engineReady) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (engineErr == null) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("   Готовлю движок загрузки…")
                    } else {
                        Text("Ошибка запуска: $engineErr", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        SectionCard("Ссылка на видео") {
            OutlinedTextField(
                value = dl.url,
                onValueChange = { dl.url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val t = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                        if (!t.isNullOrBlank()) dl.url = t
                    }) { Icon(Icons.Rounded.ContentPaste, "Вставить") }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TikTok", "Instagram", "YouTube").forEach { name ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(name, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Button(
                onClick = { analyze() },
                enabled = !dl.loading && !dl.downloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (dl.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Text("  Ищу видео…")
                } else {
                    Icon(Icons.Rounded.Search, null)
                    Text("  Найти видео")
                }
            }
        }

        dl.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        dl.meta?.let { meta ->
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(
                            model = meta.thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 110.dp, height = 78.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(meta.title, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            val sub = listOfNotNull(
                                meta.uploader,
                                if (meta.durationSec > 0) MediaUtil.formatTime(meta.durationSec * 1000L) else null,
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Выберите качество", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    meta.options.forEachIndexed { i, opt ->
                        val sel = dl.selected == i
                        Surface(
                            onClick = { if (!dl.downloading) dl.selected = i },
                            shape = RoundedCornerShape(16.dp),
                            color = if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            border = BorderStroke(1.5.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(if (opt.audioOnly) Icons.Rounded.AudioFile else Icons.Rounded.VideoFile, null, tint = MaterialTheme.colorScheme.primary)
                                Text("  " + opt.label, Modifier.weight(1f), fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                if (sel) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (dl.downloading) {
                        LinearProgressIndicator(progress = { dl.progress }, modifier = Modifier.fillMaxWidth())
                        Text("Скачиваю… ${(dl.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { download() },
                        enabled = !dl.downloading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.FileDownload, null)
                        Text("  Скачать")
                    }
                }
            }
        }

        dl.result?.let { uri ->
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Готово! Сохранено в галерею без водяного знака ✓", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { MediaUtil.share(ctx, uri, dl.resultAudio) }, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Share, null)
                            Text("  Поделиться")
                        }
                        if (!dl.resultAudio) {
                            OutlinedButton(onClick = { onEdit(uri) }, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Rounded.ContentCut, null)
                                Text("  Редактор")
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onPickFromGallery,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.ContentCut, null)
            Text("  Редактировать видео из галереи")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun LibraryTab(version: Int, onEdit: (Uri) -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<LibItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<LibItem?>(null) }
    var playing by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(version) {
        items = withContext(Dispatchers.IO) { MediaUtil.query(ctx) }
        loaded = true
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(40.dp))
        Text("Мои видео", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (loaded && items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Здесь появятся скачанные\nи отредактированные видео", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.uri.toString() }) { item ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val thumb by produceState<Bitmap?>(null, item.uri) {
                            value = withContext(Dispatchers.IO) {
                                runCatching { ctx.contentResolver.loadThumbnail(item.uri, Size(300, 300), null) }.getOrNull()
                            }
                        }
                        Box(
                            Modifier
                                .size(78.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            val b = thumb
                            if (b != null) {
                                Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(if (item.isAudio) Icons.Rounded.AudioFile else Icons.Rounded.VideoFile, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            if (item.durationMs > 0) Text(MediaUtil.formatTime(item.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row {
                                IconButton(onClick = { playing = item.uri }) { Icon(Icons.Rounded.PlayArrow, "Смотреть") }
                                IconButton(onClick = { MediaUtil.share(ctx, item.uri, item.isAudio) }) { Icon(Icons.Rounded.Share, "Поделиться") }
                                if (!item.isAudio) IconButton(onClick = { onEdit(item.uri) }) { Icon(Icons.Rounded.ContentCut, "Редактировать") }
                                IconButton(onClick = { toDelete = item }) { Icon(Icons.Rounded.Delete, "Удалить") }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить?") },
            text = { Text(item.name) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        ctx.contentResolver.delete(item.uri, null, null)
                    } catch (_: Exception) {
                    }
                    toDelete = null
                    onChanged()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
    playing?.let { PlayerDialog(it) { playing = null } }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerDialog(uri: Uri, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column {
                AndroidView(
                    factory = { c -> PlayerView(c).apply { this.player = player } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Закрыть") }
            }
        }
    }
}

@Composable
fun StyleTab(
    fonts: List<RinFont>,
    themeIdx: Int,
    fontIdx: Int,
    onTheme: (Int) -> Unit,
    onFont: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Стиль", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Тема (${RinThemes.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        RinThemes.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { col, th ->
                    val idx = row * 2 + col
                    val sel = idx == themeIdx
                    Surface(
                        onClick = { onTheme(idx) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(if (sel) 3.dp else 1.dp, if (sel) th.primary else Color.Gray.copy(alpha = 0.35f)),
                    ) {
                        Column(
                            Modifier
                                .background(Brush.linearGradient(listOf(th.bg1, th.bg2)))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(th.primary, th.accent, th.surface).forEach {
                                    Box(Modifier.size(20.dp).clip(CircleShape).background(it))
                                }
                            }
                            Text(th.name, color = th.text, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Шрифт (${fonts.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        FontRow("Системный шрифт", FontFamily.Default, fontIdx == -1) { onFont(-1) }
        fonts.forEachIndexed { i, f ->
            val fam = remember(f) { familyFor(ctx, f) }
            FontRow(f.name, fam, fontIdx == i) { onFont(i) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun FontRow(name: String, family: FontFamily, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Привет, Rin-video! Аа Бб 123", fontFamily = family, fontSize = 18.sp)
                Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
