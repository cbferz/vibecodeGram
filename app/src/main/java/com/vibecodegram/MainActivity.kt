package com.vibecodegram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.viewinterop.AndroidView
import android.os.Build
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.roundToInt
import com.vibecodegram.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL


private fun createMedia3Player(context: Context, token: String?): ExoPlayer {
    val headers = token?.takeIf { it.isNotBlank() }?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
    val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
        .setDefaultRequestProperties(headers)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
}

private data class Palette(val name: String, val background: Color, val surface: Color, val accent: Color, val secondary: Color, val glow: Color)

private val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
private val palettes = listOf(
    Palette("Graphite", Color(0xFF090B10), Color(0xFF191E28), Color(0xFFE6EAF2), Color(0xFF8D9AAA), Color(0xFF52647B)),
    Palette("Midnight", Color(0xFF080D1A), Color(0xFF17243A), Color(0xFF69C9FF), Color(0xFF9BB6D2), Color(0xFF17669A)),
    Palette("Violet", Color(0xFF100B1B), Color(0xFF251936), Color(0xFFC5A0FF), Color(0xFFB8A8CA), Color(0xFF7643B7)),
    Palette("Emerald", Color(0xFF071511), Color(0xFF142820), Color(0xFF68E0B0), Color(0xFF9BBEAE), Color(0xFF207C5C)),
    Palette("Sunset", Color(0xFF1A0D12), Color(0xFF2D1A24), Color(0xFFFF9E8B), Color(0xFFD2AAA4), Color(0xFF9B4057)),
    Palette("Arctic", Color(0xFF091317), Color(0xFF192A30), Color(0xFF83E8E8), Color(0xFFA9C6C9), Color(0xFF287E88))
)
private val accents = listOf(Color(0xFFE6EAF2), Color(0xFF69C9FF), Color(0xFFC5A0FF), Color(0xFF68E0B0), Color(0xFFFF9E8B), Color(0xFFFFD166), Color(0xFFFF79B0), Color(0xFF83E8E8))

private data class Destination(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String)
private data class Chat(val name: String, val message: String, val time: String, val unread: Int, val online: Boolean, val group: Boolean = false, val id: Long = -1L)
private data class LocalChatMessage(val id: Long, val text: String, val time: String, val outgoing: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashDiagnostics()
        super.onCreate(savedInstanceState)
        setContent { vibecodeGramApp(getSharedPreferences("appearance", Context.MODE_PRIVATE)) }
    }

    private fun installCrashDiagnostics() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val report = buildString {
                    appendLine("vibecodeGram crash report")
                    appendLine("Time: ${java.util.Date()}")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android API: ${Build.VERSION.SDK_INT}")
                    appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    appendLine(android.util.Log.getStackTraceString(error))
                }
                openFileOutput("vibecodegram-crash.txt", Context.MODE_PRIVATE).use {
                    it.write(report.toByteArray(Charsets.UTF_8))
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

@Composable
private fun ProfileEditDialog(profile: ApiProfile, theme: Palette, onDismiss: () -> Unit, onSave: (String, String, String, String, String, String) -> Unit) {
    var name by remember(profile.id) { mutableStateOf(profile.displayName) }
    var bio by remember(profile.id) { mutableStateOf(profile.bio) }
    var avatar by remember(profile.id) { mutableStateOf(profile.avatarUrl) }
    var cover by remember(profile.id) { mutableStateOf(profile.coverUrl) }
    var website by remember(profile.id) { mutableStateOf(profile.website) }
    var location by remember(profile.id) { mutableStateOf(profile.location) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.liquidGlass(theme, RoundedCornerShape(24.dp), .96f),
        containerColor = Color.Transparent,
        title = { Text("Редактирование профиля", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(64) }, modifier = Modifier.fillMaxWidth(), label = { Text("Имя") }, singleLine = true)
                OutlinedTextField(bio, { bio = it.take(160) }, modifier = Modifier.fillMaxWidth(), label = { Text("О себе") }, minLines = 2, maxLines = 4)
                OutlinedTextField(avatar, { avatar = it.take(2048) }, modifier = Modifier.fillMaxWidth(), label = { Text("URL аватара") }, singleLine = true)
                OutlinedTextField(cover, { cover = it.take(2048) }, modifier = Modifier.fillMaxWidth(), label = { Text("URL обложки") }, singleLine = true)
                OutlinedTextField(website, { website = it.take(255) }, modifier = Modifier.fillMaxWidth(), label = { Text("Сайт") }, singleLine = true)
                OutlinedTextField(location, { location = it.take(100) }, modifier = Modifier.fillMaxWidth(), label = { Text("Город") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), bio.trim(), avatar.trim(), cover.trim(), website.trim(), location.trim()) }) { Text("Сохранить", color = theme.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = theme.secondary) } }
    )
}

@Composable
private fun vibecodeGramApp(prefs: android.content.SharedPreferences) {
    var page by remember { mutableIntStateOf(0) }
    var paletteIndex by remember { mutableIntStateOf(prefs.getInt("palette", 0).coerceIn(palettes.indices)) }
    var accentIndex by remember { mutableIntStateOf(prefs.getInt("accent", -1)) }
    var glass by remember { mutableFloatStateOf(prefs.getFloat("glass", .72f).coerceIn(.2f, 1f)) }
    var compact by remember { mutableStateOf(prefs.getBoolean("compact", false)) }
    var profileHue by remember { mutableFloatStateOf(prefs.getFloat("profile_hue", 210f).coerceIn(0f, 360f)) }
    var profileSaturation by remember { mutableFloatStateOf(prefs.getFloat("profile_saturation", .72f).coerceIn(0f, 1f)) }
    var profileBrightness by remember { mutableFloatStateOf(prefs.getFloat("profile_brightness", .88f).coerceIn(0f, 1f)) }
    var profileGradient by remember { mutableStateOf(prefs.getBoolean("profile_gradient", true)) }
    var profileEditor by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("Все") }
    var openedChat by remember { mutableStateOf<Chat?>(null) }
    val api = remember { ApiClient(prefs) }
    val appScope = rememberCoroutineScope()
    var authReady by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<ApiUser?>(null) }
    LaunchedEffect(Unit) {
        currentUser = if (api.token != null) runCatching { api.me() }.getOrNull() else null
        if (currentUser == null) api.token = null
        authReady = true
    }
    val base = palettes[paletteIndex]
    val theme = base.copy(accent = if (accentIndex >= 0) accents[accentIndex] else base.accent)
    val backdrop = rememberLayerBackdrop()
    val destinations = listOf(
        Destination("Лента", Icons.Default.Home, "Твоя лента"),
        Destination("Reels", Icons.Default.PlayCircle, "Короткие видео"),
        Destination("Чаты", Icons.Default.ChatBubble, "Сообщения"),
        Destination("Профиль", Icons.Default.Person, "Профиль")
    )
    MaterialTheme(colorScheme = darkColorScheme(primary = theme.accent, background = theme.background, surface = theme.surface, onPrimary = theme.background, onBackground = Color.White, onSurface = Color.White)) {
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
        if (!authReady) {
            Box(Modifier.fillMaxSize().background(theme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
        } else if (currentUser == null) {
            AuthScreen(theme, api) { currentUser = it }
        } else {
        Scaffold(
            containerColor = theme.background,
            bottomBar = {
                LiquidGlassNavigationBar(destinations, page, { page = it }, backdrop, theme, glass,
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp))
            }
        ) { padding ->
            Box(
                Modifier.fillMaxSize().layerBackdrop(backdrop)
                    .background(Brush.verticalGradient(listOf(theme.glow.copy(alpha = .30f), theme.background, theme.background)))
                    .padding(padding)
            ) {
                Column(Modifier.fillMaxSize()) {
                    AppHeader(theme, onSearch = { page = 4 }, onNotifications = { page = 5 }, onSettings = { editor = true })
                    when (page) {
                        0 -> FeedScreen(theme, compact, api, currentUser!!.id)
                        1 -> ReelsScreen(theme, api)
                        2 -> ChatsScreen(theme, search, { search = it }, folder, { folder = it }, compact, onOpenChat = { openedChat = it }, bottomInset = 0.dp, api = api)
                        4 -> SearchScreen(theme, api, onOpenChat = { openedChat = it })
                        5 -> NotificationsScreen(theme, api)
                        else -> ProfileScreen(
                            theme, paletteIndex, profileHue, profileSaturation, profileBrightness, profileGradient,
                            currentUser!!, api, onLogout = { appScope.launch { api.logout(); currentUser = null } },
                            onCustomize = { editor = true },
                            onProfileColors = { profileEditor = true },
                            bottomInset = 0.dp
                        )
                    }
                }
            }
        }
        if (profileEditor) {
            ProfileColorEditor(
                username = currentUser!!.username,
                hue = profileHue, saturation = profileSaturation, brightness = profileBrightness,
                gradient = profileGradient, theme = theme,
                onHue = { profileHue = it; prefs.edit().putFloat("profile_hue", it).apply() },
                onSaturation = { profileSaturation = it; prefs.edit().putFloat("profile_saturation", it).apply() },
                onBrightness = { profileBrightness = it; prefs.edit().putFloat("profile_brightness", it).apply() },
                onGradient = { profileGradient = it; prefs.edit().putBoolean("profile_gradient", it).apply() },
                onClose = { profileEditor = false }
            )
        }
        if (editor) {
            AppearanceEditor(
                paletteIndex, accentIndex, glass, compact, theme,
                onPalette = { paletteIndex = it; prefs.edit().putInt("palette", it).apply() },
                onAccent = { accentIndex = it; prefs.edit().putInt("accent", it).apply() },
                onGlass = { glass = it; prefs.edit().putFloat("glass", it).apply() },
                onCompact = { compact = it; prefs.edit().putBoolean("compact", it).apply() },
                onClose = { editor = false }
            )
        }
        openedChat?.let { ChatRoomDialog(it, theme, api, currentUser?.id ?: -1L) { openedChat = null } }
        }
        }
    }
}

@Composable
private fun AppHeader(theme: Palette, onSearch: () -> Unit, onNotifications: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("vibecodeGram", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = (-.7).sp)
            Text("ТВОЁ ПРОСТРАНСТВО · ТВОИ ПРАВИЛА", color = theme.accent, fontSize = 9.sp, letterSpacing = 1.25.sp, fontWeight = FontWeight.Bold)
        }
        HeaderIcon(Icons.Default.Search, "Поиск", onSearch, theme)
        Spacer(Modifier.width(5.dp))
        HeaderIcon(Icons.Default.NotificationsNone, "Уведомления", onNotifications, theme)
        Spacer(Modifier.width(5.dp))
        HeaderIcon(Icons.Default.Tune, "Оформление", onSettings, theme)
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, action: () -> Unit, theme: Palette) {
    Box(Modifier.size(42.dp).liquidGlass(theme, CircleShape, .78f).clickable { action() }, contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = theme.accent, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun ReelsScreen(theme: Palette, api: ApiClient) {
    var posts by remember { mutableStateOf<List<ApiPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var composer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { posts.size })
    val player = remember(api.token) { createMedia3Player(context, api.token) }
    var isPlaying by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(api.token, refreshKey) {
        loading = true
        error = null
        runCatching { api.reels(0, 20) }
            .onSuccess {
                posts = it.filter { post -> post.contentKind == "reel" && post.videoUrl.isNotBlank() }
                if (posts.isEmpty()) player.stop()
            }
            .onFailure { error = it.message ?: "Не удалось загрузить Reels" }
        loading = false
    }

    LaunchedEffect(pagerState.currentPage, posts.size) {
        if (posts.isNotEmpty() && !loadingMore && pagerState.currentPage >= posts.size - 4) {
            loadingMore = true
            runCatching { api.reels(posts.size, 20) }
                .onSuccess { next ->
                    val unique = next.filter { item -> item.contentKind == "reel" && item.videoUrl.isNotBlank() && posts.none { old -> old.id == item.id } }
                    if (unique.isNotEmpty()) posts = posts + unique
                }
                .onFailure { if (posts.isEmpty()) error = it.message }
            loadingMore = false
        }
    }

    val activePost = posts.getOrNull(pagerState.currentPage)
    LaunchedEffect(activePost?.id) {
        if (activePost == null) {
            player.pause()
        } else {
            val url = BuildConfig.API_BASE_URL.trimEnd('/') + activePost.videoUrl
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.volume = if (muted) 0f else 1f
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    when {
        loading && posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = theme.accent)
        }
        error != null && posts.isEmpty() -> Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.CloudOff, null, tint = theme.secondary, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = theme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = { refreshKey++ }) { Text("Повторить", color = theme.accent) }
        }
        posts.isEmpty() -> Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.VideoLibrary, null, tint = theme.secondary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("Reels пока нет", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Короткие видео публикуются отдельно от видео в обычных постах.", color = theme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp)
            TextButton(onClick = { composer = true }) { Text("Создать Reels", color = theme.accent) }
            TextButton(onClick = { refreshKey++ }) { Text("Обновить", color = theme.secondary) }
        }
        else -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { index -> posts[index].id }) { index ->
                val post = posts[index]
                var liked by remember(post.id) { mutableStateOf(post.liked) }
                var saved by remember(post.id) { mutableStateOf(post.saved) }
                var likes by remember(post.id) { mutableLongStateOf(post.likes) }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (index == pagerState.currentPage) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    this.player = player
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                }
                            },
                            update = { view ->
                                if (view.player !== player) view.player = player
                            },
                            onRelease = { view -> view.player = null }
                        )
                    }
                    Column(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f))))
                            .padding(start = 20.dp, end = 88.dp, top = 48.dp, bottom = 30.dp)
                    ) {
                        Text("@${post.username}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (post.body.isNotBlank()) {
                            Spacer(Modifier.height(7.dp))
                            Text(post.body, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                        if (post.tag.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text("#${post.tag}", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Column(
                        Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IconButton(onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                        }) {
                            Icon(if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, "Воспроизведение", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        IconButton(onClick = { muted = !muted; player.volume = if (muted) 0f else 1f }) {
                            Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, "Звук", tint = Color.White, modifier = Modifier.size(25.dp))
                        }
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { api.toggleLike(post.id) }.onSuccess { result ->
                                    liked = result.first
                                    likes = result.second
                                }
                            }
                        }) {
                            Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Нравится", tint = if (liked) Color(0xFFFF7895) else Color.White, modifier = Modifier.size(27.dp))
                        }
                        Text(likes.toString(), color = Color.White, fontSize = 11.sp)
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { api.setSaved(post.id, !saved) }.onSuccess { saved = !saved }
                            }
                        }) {
                            Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Сохранить", tint = if (saved) theme.accent else Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reels", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                IconButton(onClick = { composer = true }) { Icon(Icons.Default.Add, "Создать Reels", tint = Color.White) }
                IconButton(onClick = { refreshKey++ }) { Icon(Icons.Default.Refresh, "Обновить Reels", tint = Color.White) }
            }
        }
    }

    if (composer) {
        CreatePostDialog(
            theme = theme,
            title = "Новое Reels",
            publishLabel = "Опубликовать Reels",
            requireVideo = true,
            onDismiss = { composer = false },
            onPublish = { body, tag, videoUri ->
                scope.launch {
                    runCatching {
                        api.createPost(body.ifBlank { "Reels" }, tag, videoUri, context, isReel = true)
                        api.reels(0, 20)
                    }.onSuccess { rows ->
                        posts = rows.filter { it.contentKind == "reel" && it.videoUrl.isNotBlank() }
                        composer = false
                        pagerState.scrollToPage(0)
                    }.onFailure { error = it.message ?: "Не удалось опубликовать Reels" }
                }
            }
        )
    }
}

@Composable
private fun StoriesStrip(theme: Palette, api: ApiClient, currentUserId: Long) {
    var stories by remember { mutableStateOf<List<ApiStory>>(emptyList()) }
    var create by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ApiStory?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(api.token, refreshKey) {
        runCatching { api.stories() }.onSuccess { stories = it }
    }
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.width(68.dp).clickable { create = true },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(theme.surface).border(1.5.dp, theme.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, "Добавить историю", tint = theme.accent, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(5.dp))
                Text("Моя история", color = theme.secondary, fontSize = 10.sp, maxLines = 1)
            }
            stories.distinctBy { it.authorId }.forEach { story ->
                Column(
                    Modifier.width(68.dp).clickable {
                        selected = story
                        scope.launch { runCatching { api.viewStory(story.id) } }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(58.dp).border(2.dp, if (story.viewed) theme.secondary.copy(alpha = .45f) else theme.accent, CircleShape).padding(3.dp)
                            .clip(CircleShape).background(theme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(story.displayName.ifBlank { story.username }.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(story.username, color = theme.secondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (create) CreateStoryDialog(theme, api, onDismiss = { create = false }, onCreated = { create = false; refreshKey++ })
    selected?.let { story -> StoryViewerDialog(story, theme, api, currentUserId, onDismiss = { selected = null }, onDeleted = { selected = null; refreshKey++ }) }
}

@Composable
private fun CreateStoryDialog(theme: Palette, api: ApiClient, onDismiss: () -> Unit, onCreated: () -> Unit) {
    var caption by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(24) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selectedUri = it }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.liquidGlass(theme, RoundedCornerShape(24.dp), .96f),
        containerColor = Color.Transparent,
        title = { Text("Новая история", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*", "video/mp4")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp))
                    Text(if (selectedUri == null) "Выбрать фото или видео" else "Медиа выбрано · заменить")
                }
                OutlinedTextField(caption, { caption = it.take(500) }, modifier = Modifier.fillMaxWidth(), label = { Text("Подпись") }, maxLines = 3)
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Исчезнет через $duration ч")
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(5, 12, 24, 48).forEach { hours ->
                            DropdownMenuItem(text = { Text("$hours часов") }, onClick = { duration = hours; expanded = false })
                        }
                    }
                }
                Text("История автоматически исчезнет через выбранное время.", color = theme.secondary, fontSize = 11.sp)
                error?.let { Text(it, color = Color(0xFFFF8294), fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = selectedUri != null && !busy, onClick = {
                val uri = selectedUri ?: return@TextButton
                busy = true; error = null
                scope.launch {
                    runCatching { api.createStory(uri, caption.trim(), duration, context) }
                        .onSuccess { onCreated() }
                        .onFailure { error = it.message ?: "Не удалось опубликовать историю"; busy = false }
                }
            }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Опубликовать", color = theme.accent) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена", color = theme.secondary) } }
    )
}

@Composable
private fun StoryViewerDialog(story: ApiStory, theme: Palette, api: ApiClient, currentUserId: Long, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    var deleteBusy by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var playerError by remember(story.id) { mutableStateOf<String?>(null) }
    var isBuffering by remember(story.id) { mutableStateOf(story.mediaType == "video/mp4") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val storyBackdrop = rememberLayerBackdrop()
    val mediaUrl = BuildConfig.API_BASE_URL.trimEnd('/') + story.mediaUrl
    val isVideo = story.mediaType.equals("video/mp4", ignoreCase = true)
    val storyPlayer = remember(story.id, api.token) {
        createMedia3Player(context, api.token).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 1f
        }
    }
    val bitmap by produceState<Bitmap?>(initialValue = null, story.id, story.mediaType) {
        if (story.mediaType.startsWith("image/")) {
            value = withContext(Dispatchers.IO) {
                val connection = (URL(mediaUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 20000
                    api.token?.let { setRequestProperty("Authorization", "Bearer $it") }
                }
                try {
                    if (connection.responseCode in 200..299) connection.inputStream.use { BitmapFactory.decodeStream(it) } else null
                } catch (_: Exception) { null } finally { connection.disconnect() }
            }
        }
    }

    DisposableEffect(storyPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }
            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Сервер отклонил запрос видео."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Нет соединения с сервером."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Формат видео не поддерживается."
                    else -> "Не удалось загрузить видео (${error.errorCodeName})."
                }
            }
        }
        storyPlayer.addListener(listener)
        onDispose {
            storyPlayer.removeListener(listener)
            storyPlayer.release()
        }
    }

    LaunchedEffect(story.id, mediaUrl, isVideo) {
        if (isVideo) {
            playerError = null
            isBuffering = true
            storyPlayer.setMediaItem(MediaItem.fromUri(mediaUrl))
            storyPlayer.prepare()
            storyPlayer.playWhenReady = true
        } else {
            storyPlayer.pause()
            storyPlayer.clearMediaItems()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides storyBackdrop) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(.88f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
            ) {
                if (isVideo) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize().layerBackdrop(storyBackdrop),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = storyPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                setKeepContentOnPlayerReset(true)
                            }
                        },
                        update = { view ->
                            if (view.player !== storyPlayer) view.player = storyPlayer
                        },
                        onRelease = { view -> view.player = null }
                    )
                    if (isBuffering && playerError == null) {
                        Box(Modifier.align(Alignment.Center).size(54.dp).liquidGlass(theme, CircleShape, .9f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(25.dp), color = Color.White, strokeWidth = 2.dp)
                        }
                    }
                    playerError?.let { message ->
                        Column(
                            Modifier.align(Alignment.Center).padding(24.dp)
                                .liquidGlass(theme, RoundedCornerShape(20.dp), .98f)
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CloudOff, null, tint = Color.White, modifier = Modifier.size(30.dp))
                            Text(message, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            TextButton(onClick = {
                                playerError = null
                                isBuffering = true
                                storyPlayer.seekTo(0)
                                storyPlayer.prepare()
                                storyPlayer.playWhenReady = true
                            }) { Text("Повторить", color = theme.accent) }
                        }
                    }
                } else if (bitmap != null) {
                    Image(bitmap!!.asImageBitmap(), contentDescription = "История", modifier = Modifier.fillMaxSize().layerBackdrop(storyBackdrop), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.accent)
                    }
                }

                Column(
                    Modifier.align(Alignment.TopStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .64f), Color.Black.copy(alpha = .12f), Color.Transparent)))
                        .padding(start = 14.dp, top = 16.dp, end = 116.dp, bottom = 22.dp)
                ) {
                    Text("@\${story.username}", color = Color.White, fontWeight = FontWeight.Bold)
                    if (story.caption.isNotBlank()) Text(story.caption, color = Color.White, fontSize = 13.sp)
                }

                Row(
                    Modifier.align(Alignment.TopEnd).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (story.authorId == currentUserId) {
                        Box {
                            Box(
                                Modifier.size(44.dp).liquidGlass(theme, CircleShape, 1f)
                                    .clickable(enabled = !deleteBusy) { overflowExpanded = true },
                                contentAlignment = Alignment.Center
                            ) {
                                if (deleteBusy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Color.White)
                                else Icon(Icons.Default.MoreVert, "Дополнительные действия", tint = Color.White, modifier = Modifier.size(21.dp))
                            }
                            DropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false },
                                modifier = Modifier.liquidGlass(theme, RoundedCornerShape(16.dp), .98f),
                                containerColor = Color.Transparent
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Удалить историю", color = Color(0xFFFF8294)) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFFF8294)) },
                                    enabled = !deleteBusy,
                                    onClick = {
                                        overflowExpanded = false
                                        confirmDelete = true
                                    }
                                )
                            }
                        }
                    }
                    Box(
                        Modifier.size(44.dp).liquidGlass(theme, CircleShape, 1f)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, "Закрыть историю", tint = Color.White, modifier = Modifier.size(21.dp))
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleteBusy) confirmDelete = false },
            modifier = Modifier.liquidGlass(theme, RoundedCornerShape(24.dp), .98f),
            containerColor = Color.Transparent,
            title = { Text("Удалить историю?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Эта история будет удалена без возможности восстановления.", color = theme.secondary) },
            confirmButton = {
                TextButton(enabled = !deleteBusy, onClick = {
                    deleteBusy = true
                    scope.launch {
                        runCatching { api.deleteStory(story.id) }
                            .onSuccess { onDeleted() }
                            .onFailure { deleteBusy = false; confirmDelete = false }
                    }
                }) {
                    if (deleteBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = theme.accent)
                    else Text("Удалить", color = Color(0xFFFF8294))
                }
            },
            dismissButton = { TextButton(enabled = !deleteBusy, onClick = { confirmDelete = false }) { Text("Отмена", color = theme.secondary) } }
        )
    }
}
@Composable
private fun SearchScreen(theme: Palette, api: ApiClient, onOpenChat: (Chat) -> Unit) {
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<ApiUser>>(emptyList()) }
    var posts by remember { mutableStateOf<List<ApiPost>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length < 2) { users = emptyList(); posts = emptyList(); loading = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        loading = true; error = null
        val userResult = runCatching { api.searchUsers(term) }
        val postResult = runCatching { api.searchPosts(term) }
        users = userResult.getOrDefault(emptyList())
        posts = postResult.getOrDefault(emptyList())
        if (userResult.isFailure && postResult.isFailure) error = userResult.exceptionOrNull()?.message ?: "Поиск не удался"
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        Text("Поиск", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(80) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Люди, публикации, теги") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        when {
            loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = theme.accent) }
            error != null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(error!!, color = Color(0xFFFF8294)) }
            query.trim().length < 2 -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Введи минимум 2 символа.", color = theme.secondary) }
            users.isEmpty() && posts.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Ничего не найдено.", color = theme.secondary) }
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (users.isNotEmpty()) item { Text("Пользователи", color = theme.accent, fontWeight = FontWeight.Bold) }
                items(users, key = { "u${it.id}" }) { user ->
                    var followed by remember(user.id) { mutableStateOf(false) }
                    GlassCard(theme) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(user.displayName.ifBlank { user.username }.take(1), theme.accent, 42)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).clickable {
                                scope.launch {
                                    runCatching { api.createDirectConversation(user.id) }.onSuccess { id ->
                                        onOpenChat(Chat(user.displayName.ifBlank { user.username }, "@${user.username}", "", 0, false, false, id))
                                    }
                                }
                            }) {
                                Text(user.displayName.ifBlank { user.username }, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("@${user.username}", color = theme.secondary, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                scope.launch { runCatching { api.followUser(user.id, !followed) }.onSuccess { followed = !followed } }
                            }) { Text(if (followed) "Вы подписаны" else "Подписаться", color = theme.accent, fontSize = 10.sp) }
                        }
                    }
                }
                if (posts.isNotEmpty()) item { Text("Публикации", color = theme.accent, fontWeight = FontWeight.Bold) }
                items(posts, key = { "p${it.id}" }) { post ->
                    FeedPostCard(post, theme, false, api,
                        onLike = { scope.launch { runCatching { api.toggleLike(post.id) } } },
                        onSave = { scope.launch { runCatching { api.setSaved(post.id, !post.saved) } } },
                        onHide = {
                            scope.launch {
                                runCatching { api.hidePost(post.id) }.onSuccess {
                                    posts = posts.filterNot { it.id == post.id }
                                }
                            }
                        },
                        onBlock = {
                            scope.launch {
                                runCatching { api.blockUser(post.authorId) }.onSuccess {
                                    posts = posts.filterNot { it.authorId == post.authorId }
                                }
                            }
                        })
                }
            }
        }
    }
}

@Composable
private fun NotificationsScreen(theme: Palette, api: ApiClient) {
    var notifications by remember { mutableStateOf<List<ApiNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(api.token, refresh) {
        loading = true; error = null
        runCatching { api.markNotificationsRead(); api.notifications() }
            .onSuccess { notifications = it }
            .onFailure { error = it.message ?: "Не удалось загрузить уведомления" }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Уведомления", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Обновить", tint = theme.accent) }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = theme.accent) }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error!!, color = Color(0xFFFF8294)) }
            notifications.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Пока нет уведомлений.", color = theme.secondary) }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notifications, key = { it.id }) { item ->
                    GlassCard(theme) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(item.displayName.ifBlank { item.username }.take(1), theme.accent, 42)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                val action = when (item.kind) { "follow" -> "подписался на тебя"; "comment" -> "прокомментировал твою публикацию"; "like" -> "оценил твою публикацию"; "message" -> "отправил тебе сообщение"; else -> "есть новое событие" }
                                Text("${item.displayName.ifBlank { "@${item.username}" }} $action", color = Color.White, fontSize = 13.sp)
                                Text(item.createdAt.replace("T", " ").take(16), color = theme.secondary, fontSize = 10.sp)
                            }
                            if (!item.read) Box(Modifier.size(8.dp).clip(CircleShape).background(theme.accent))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedScreen(theme: Palette, compact: Boolean, api: ApiClient, currentUserId: Long) {
    var sort by remember { mutableStateOf("latest") }
    var posts by remember { mutableStateOf<List<ApiPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var composer by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(sort, refreshKey, api.token) {
        loading = posts.isEmpty()
        refreshing = posts.isNotEmpty()
        error = null
        runCatching { if (sort == "recommended") api.recommendations() else api.posts(sort) }
            .onSuccess { posts = it }
            .onFailure { error = it.message ?: "Не удалось загрузить ленту" }
        loading = false
        refreshing = false
    }

    Column(Modifier.fillMaxSize()) {
        StoriesStrip(theme, api, currentUserId)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Для тебя" to "latest", "Рекомендации" to "recommended", "Популярное" to "popular", "Видео" to "videos").forEach { (label, value) ->
                FilterChip(
                    selected = sort == value,
                    onClick = { if (sort != value) sort = value },
                    label = { Text(label, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.accent.copy(alpha = .22f),
                        selectedLabelColor = Color.White,
                        labelColor = theme.secondary
                    )
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { refreshKey++ }, enabled = !refreshing) {
                if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = theme.accent)
                else Icon(Icons.Default.Refresh, "Обновить ленту", tint = theme.secondary)
            }
            IconButton(onClick = { composer = true }) {
                Icon(Icons.Default.AddCircle, "Создать публикацию", tint = theme.accent, modifier = Modifier.size(27.dp))
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
            error != null && posts.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CloudOff, null, tint = theme.secondary, modifier = Modifier.size(38.dp))
                Spacer(Modifier.height(12.dp))
                Text(error!!, color = theme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { refreshKey++ }) { Text("Повторить", color = theme.accent) }
            }
            posts.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.DynamicFeed, null, tint = theme.secondary, modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(12.dp))
                Text("Лента пока пуста", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Опубликуй первую запись — она появится здесь.", color = theme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { composer = true }) { Text("Создать публикацию") }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
            ) {
                items(posts, key = { it.id }) { post ->
                    FeedPostCard(
                        post = post,
                        theme = theme,
                        compact = compact,
                        api = api,
                        onLike = {
                            scope.launch {
                                runCatching { api.toggleLike(post.id) }.onSuccess { result ->
                                    posts = posts.map { if (it.id == post.id) it.copy(liked = result.first, likes = result.second) else it }
                                }.onFailure { error = it.message ?: "Не удалось поставить отметку" }
                            }
                        },
                        onSave = {
                            scope.launch {
                                runCatching { api.setSaved(post.id, !post.saved) }.onSuccess {
                                    posts = posts.map { if (it.id == post.id) it.copy(saved = !it.saved) else it }
                                }.onFailure { error = it.message ?: "Не удалось изменить сохранение" }
                            }
                        },
                        onHide = {
                            scope.launch {
                                runCatching { api.hidePost(post.id) }.onSuccess {
                                    posts = posts.filterNot { it.id == post.id }
                                }.onFailure { error = it.message ?: "Не удалось скрыть публикацию" }
                            }
                        },
                        onBlock = {
                            scope.launch {
                                runCatching { api.blockUser(post.authorId) }.onSuccess {
                                    posts = posts.filterNot { it.authorId == post.authorId }
                                }.onFailure { error = it.message ?: "Не удалось заблокировать автора" }
                            }
                        }
                    )
                }
                if (error != null) item { Text(error!!, color = Color(0xFFFF8294), fontSize = 11.sp) }
            }
        }
    }

    if (composer) {
        CreatePostDialog(theme = theme, onDismiss = { composer = false }, onPublish = { body, tag, videoUri ->
            scope.launch {
                runCatching { api.createPost(body, tag, videoUri, context); api.posts(sort) }
                    .onSuccess { posts = it; composer = false }
                    .onFailure { error = it.message ?: "Не удалось опубликовать запись" }
            }
        })
    }
}

@Composable
private fun PostVideoPlayer(post: ApiPost, theme: Palette, api: ApiClient) {
    var playing by remember(post.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val videoUrl = BuildConfig.API_BASE_URL.trimEnd('/') + post.videoUrl
    if (!playing) {
        Box(
            Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF080B11)).clickable { playing = true },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayCircleFilled, null, tint = theme.accent, modifier = Modifier.size(54.dp))
                Spacer(Modifier.height(7.dp))
                Text("Воспроизвести видео", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("MP4 · ${(post.mediaSize / (1024 * 1024)).coerceAtLeast(1)} МБ", color = theme.secondary, fontSize = 10.sp)
            }
        }
    } else {
        val player = remember(post.id) { createMedia3Player(context, api.token) }
        DisposableEffect(player) {
            onDispose { player.release() }
        }
        LaunchedEffect(player, videoUrl) {
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.setMediaItem(MediaItem.fromUri(videoUrl))
            player.prepare()
            player.playWhenReady = true
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(18.dp)),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    keepScreenOn = true
                }
            },
            update = { view -> if (view.player !== player) view.player = player },
            onRelease = { view -> view.player = null }
        )
    }
}

@Composable
private fun FeedPostCard(
    post: ApiPost,
    theme: Palette,
    compact: Boolean,
    api: ApiClient,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onHide: () -> Unit,
    onBlock: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var reportDialog by remember { mutableStateOf(false) }
    var reportStatus by remember { mutableStateOf<String?>(null) }
    val reportScope = rememberCoroutineScope()
    GlassCard(theme, compact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.displayName.ifBlank { post.username }.take(1), theme.accent, 44)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(post.displayName.ifBlank { post.username }, color = Color.White, fontWeight = FontWeight.Bold)
                Text("@${post.username} · ${post.createdAt.replace("T", " ").take(16)}", color = theme.secondary, fontSize = 11.sp)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreHoriz, "Действия с публикацией", tint = theme.secondary)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.liquidGlass(theme, RoundedCornerShape(18.dp), .96f).widthIn(min = 190.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Скрыть публикацию", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, null, tint = theme.secondary) },
                        onClick = { menuExpanded = false; onHide() }
                    )
                    DropdownMenuItem(
                        text = { Text("Пожаловаться", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.Flag, null, tint = theme.secondary) },
                        onClick = { menuExpanded = false; reportDialog = true }
                    )
                    if (onBlock != null) {
                        DropdownMenuItem(
                            text = { Text("Заблокировать автора", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Block, null, tint = theme.secondary) },
                            onClick = { menuExpanded = false; onBlock() }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        if (post.body.isNotBlank()) {
            Text(post.body, color = Color(0xFFE8EBF1), fontSize = 15.sp, lineHeight = 23.sp)
        }
        if (post.videoUrl.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            PostVideoPlayer(post, theme, api)
        }
        if (post.tag.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = theme.accent.copy(alpha = .13f),
                shape = RoundedCornerShape(11.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .12f))
            ) {
                Text("#${post.tag}", color = theme.accent, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLike, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(if (post.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (post.liked) Color(0xFFFF7895) else theme.secondary, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text(post.likes.toString(), color = theme.secondary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSave, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(if (post.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, tint = theme.secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (post.saved) "Сохранено" else "Сохранить", color = theme.secondary, fontSize = 12.sp)
            }
        }
        reportStatus?.let { Text(it, color = theme.secondary, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
    if (reportDialog) {
        AlertDialog(
            onDismissRequest = { reportDialog = false },
            modifier = Modifier.liquidGlass(theme, RoundedCornerShape(24.dp), .96f),
            containerColor = Color.Transparent,
            title = { Text("Пожаловаться на публикацию", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Выбери причину:", color = theme.secondary)
                    listOf(
                        "Спам" to "spam",
                        "Оскорбления или травля" to "harassment",
                        "Насилие" to "violence",
                        "Дезинформация" to "misinformation",
                        "Нарушение авторских прав" to "copyright",
                        "Другое" to "other"
                    ).forEach { (label, reason) ->
                        TextButton(
                            onClick = {
                                reportDialog = false
                                reportScope.launch {
                                    runCatching { api.reportPost(post.id, reason) }
                                        .onSuccess { reportStatus = "Жалоба отправлена" }
                                        .onFailure { reportStatus = it.message ?: "Не удалось отправить жалобу" }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label, color = Color.White) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { reportDialog = false }) { Text("Отмена", color = theme.accent) }
            }
        )
    }
}

@Composable
private fun CreatePostDialog(
    theme: Palette,
    title: String = "Новая публикация",
    publishLabel: String = "Опубликовать",
    requireVideo: Boolean = false,
    onDismiss: () -> Unit,
    onPublish: (String, String, Uri?) -> Unit
) {
    var body by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedVideo = uri
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.liquidGlass(theme, RoundedCornerShape(26.dp), .94f),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = body,
                    onValueChange = { if (it.length <= 5000) body = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Чем хочешь поделиться?") },
                    minLines = 4,
                    maxLines = 7
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it.removePrefix("#").take(32) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Тег, например Дизайн") },
                    singleLine = true
                )
                OutlinedButton(
                    onClick = { videoPicker.launch(arrayOf("video/mp4")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedVideo == null) "Прикрепить видео MP4" else "Видео прикреплено · заменить")
                }
                if (selectedVideo != null) {
                    Text("Видео выбрано. Ограничение сервера — 50 МБ.", color = theme.accent, fontSize = 11.sp)
                }
                Text("${body.length}/5000", color = theme.secondary, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = (if (requireVideo) selectedVideo != null else body.isNotBlank() || selectedVideo != null) && !busy,
                onClick = { busy = true; onPublish(body.trim(), tag.trim(), selectedVideo) }
            ) { Text(publishLabel, color = theme.accent) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена", color = theme.secondary) } }
    )
}

@Composable
private fun ChatsScreen(theme: Palette, search: String, onSearch: (String) -> Unit, folder: String, onFolder: (String) -> Unit, compact: Boolean, onOpenChat: (Chat) -> Unit, bottomInset: androidx.compose.ui.unit.Dp, api: ApiClient) {
    var chats by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var newChat by remember { mutableStateOf(false) }
    LaunchedEffect(api.token, refreshKey) {
        loading = true
        error = null
        runCatching { api.conversations() }
            .onSuccess { rows -> chats = rows.map { Chat(it.title.ifBlank { "Диалог #${it.id}" }, it.lastMessage.ifBlank { "Пока нет сообщений" }, "", 0, false, it.kind == "group", it.id) } }
            .onFailure { error = it.message ?: "Не удалось загрузить чаты" }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Сообщения", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { newChat = true }) { Icon(Icons.Default.Edit, "Новый диалог", tint = theme.accent) }
        }
        OutlinedTextField(
            value = search, onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Поиск по чатам") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.White.copy(alpha = .12f), focusedBorderColor = theme.accent, unfocusedTextColor = Color.White, focusedTextColor = Color.White)
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Все", "Личные", "Группы").forEach { label ->
                FilterChip(selected = folder == label, onClick = { onFolder(label) }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.accent.copy(alpha = .20f), selectedLabelColor = theme.accent))
            }
        }
        when {
            loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = theme.accent) }
            error != null -> Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(error!!, color = theme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { refreshKey++ }) { Text("Повторить", color = theme.accent) }
            }
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp + bottomInset), verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 9.dp)) {
                val filtered = chats.filter { it.name.contains(search, true) || it.message.contains(search, true) }.filter { folder == "Все" || (folder == "Группы" && it.group) || (folder == "Личные" && !it.group) }
                if (filtered.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ChatBubbleOutline, null, tint = theme.secondary, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Диалогов пока нет", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Нажми на кнопку создания и найди пользователя по логину или имени.", color = theme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp)
                    }
                }
                items(filtered, key = { it.id }) { chat ->
                    GlassCard(theme, compact, Modifier.clickable { onOpenChat(chat) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(chat.name.take(1), theme.accent, 48); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(chat.name, color = Color.White, fontWeight = FontWeight.SemiBold); Text(chat.message, color = theme.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Icon(Icons.Default.ChevronRight, null, tint = theme.secondary)
                        }
                    }
                }
            }
        }
    }
    if (newChat) NewChatDialog(theme, api, onDismiss = { newChat = false }, onCreated = { chat ->
        newChat = false
        refreshKey++
        onOpenChat(chat)
    })
}

@Composable
private fun NewChatDialog(theme: Palette, api: ApiClient, onDismiss: () -> Unit, onCreated: (Chat) -> Unit) {
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<ApiUser>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var busyUser by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query) {
        if (query.trim().length < 2) { users = emptyList(); loading = false; return@LaunchedEffect }
        loading = true
        error = null
        kotlinx.coroutines.delay(250)
        runCatching { api.searchUsers(query.trim()) }
            .onSuccess { users = it }
            .onFailure { error = it.message ?: "Не удалось найти пользователей" }
        loading = false
    }
    Dialog(onDismissRequest = { if (busyUser == null) onDismiss() }) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.78f).liquidGlass(theme, RoundedCornerShape(24.dp), .94f), color = Color.Transparent, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Новый диалог", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, enabled = busyUser == null) { Icon(Icons.Default.Close, "Закрыть", tint = theme.secondary) }
                }
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Имя или логин (от 2 символов)") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                Spacer(Modifier.height(8.dp))
                if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = theme.accent) }
                else if (error != null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(error!!, color = Color(0xFFFF8294), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                else if (query.trim().length < 2) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Введи минимум 2 символа для поиска.", color = theme.secondary) }
                else if (users.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Пользователи не найдены.", color = theme.secondary) }
                else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(users, key = { it.id }) { user ->
                        GlassCard(theme, modifier = Modifier.clickable(enabled = busyUser == null) {
                            busyUser = user.id
                            scope.launch {
                                runCatching { api.createDirectConversation(user.id) }
                                    .onSuccess { id -> onCreated(Chat(user.displayName.ifBlank { user.username }, "@${user.username}", "", 0, false, false, id)) }
                                    .onFailure { error = it.message ?: "Не удалось создать диалог" }
                                busyUser = null
                            }
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(user.displayName.ifBlank { user.username }.take(1), theme.accent, 42)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) { Text(user.displayName.ifBlank { user.username }, color = Color.White, fontWeight = FontWeight.SemiBold); Text("@${user.username}", color = theme.secondary, fontSize = 11.sp) }
                                if (busyUser == user.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = theme.accent)
                                else Icon(Icons.Default.Chat, null, tint = theme.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRoomDialog(chat: Chat, theme: Palette, api: ApiClient, currentUserId: Long, onClose: () -> Unit) {
    var messages by remember(chat.id) { mutableStateOf<List<ApiMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    suspend fun refresh() {
        runCatching { api.messages(chat.id) }.onSuccess { messages=it;error=null }.onFailure { error=it.message ?: "Не удалось загрузить сообщения" }
        loading=false
    }
    LaunchedEffect(chat.id) { refresh() }
    Dialog(onDismissRequest=onClose) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.88f).liquidGlass(theme,RoundedCornerShape(24.dp),.94f),shape=RoundedCornerShape(24.dp),color=Color.Transparent,tonalElevation=0.dp) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically){Avatar(chat.name.take(1),theme.accent,42);Spacer(Modifier.width(10.dp));Text(chat.name,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));IconButton(onClick=onClose){Icon(Icons.Default.Close,"Закрыть",tint=theme.secondary)}}
                Divider(color=Color.White.copy(alpha=.1f))
                if(loading) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator(color=theme.accent)}
                else LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(messages.isEmpty()) item { Text("Сообщений пока нет. Напиши первым.",color=theme.secondary,modifier=Modifier.fillMaxWidth().padding(24.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center) }
                    items(messages,key={it.id}) { m ->
                        val outgoing=m.senderId==currentUserId
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=if(outgoing)Arrangement.End else Arrangement.Start) {
                            Column(Modifier.widthIn(max=280.dp).liquidGlass(theme,RoundedCornerShape(16.dp),if(outgoing).58f else .82f).padding(12.dp)) {
                                if(!outgoing) Text(m.username,color=theme.accent,fontSize=10.sp,fontWeight=FontWeight.Bold)
                                Text(m.body,color=Color.White,fontSize=14.sp,lineHeight=19.sp)
                                Text(m.createdAt.replace("T"," ").take(16),color=theme.secondary,fontSize=9.sp,modifier=Modifier.align(Alignment.End))
                            }
                        }
                    }
                }
                if(error!=null) Text(error!!,color=Color(0xFFFF8294),fontSize=11.sp)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    OutlinedTextField(value=draft,onValueChange={draft=it},modifier=Modifier.weight(1f),placeholder={Text("Сообщение…")},shape=RoundedCornerShape(18.dp),maxLines=4)
                    IconButton(enabled=draft.isNotBlank()&&!sending,onClick={
                        val body=draft.trim();sending=true
                        scope.launch { runCatching { api.sendMessage(chat.id,body) }.onSuccess { draft="";refresh() }.onFailure { error=it.message ?: "Не удалось отправить сообщение" };sending=false }
                    }) { if(sending) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=theme.accent) else Icon(Icons.Default.Send,"Отправить",tint=theme.accent) }
                }
            }
        }
    }
}


@Composable
private fun ProfileScreen(
    theme: Palette,
    paletteIndex: Int,
    profileHue: Float,
    profileSaturation: Float,
    profileBrightness: Float,
    profileGradient: Boolean,
    user: ApiUser,
    api: ApiClient,
    onLogout: () -> Unit,
    onCustomize: () -> Unit,
    onProfileColors: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp
) {
    val profileColor = Color.hsv(profileHue, profileSaturation, profileBrightness)
    val profileDeep = Color.hsv((profileHue + 34f) % 360f, (profileSaturation * .92f).coerceIn(0f, 1f), (profileBrightness * .43f).coerceIn(.08f, 1f))
    val profileBrush = if (profileGradient) Brush.linearGradient(listOf(profileColor, profileDeep, profileColor.copy(alpha = .72f))) else Brush.linearGradient(listOf(profileColor, profileColor))
    var savedPosts by remember { mutableStateOf<List<ApiPost>>(emptyList()) }
    var savedLoading by remember { mutableStateOf(true) }
    var savedError by remember { mutableStateOf<String?>(null) }
    var hiddenPosts by remember { mutableStateOf<List<ApiPost>>(emptyList()) }
    var hiddenLoading by remember { mutableStateOf(true) }
    var hiddenError by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<ApiProfile?>(null) }
    var blockedUsers by remember { mutableStateOf<List<ApiBlockedUser>>(emptyList()) }
    var editProfile by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }
    val savedScope = rememberCoroutineScope()
    LaunchedEffect(api.token, user.id) {
        runCatching { api.profile(user.id) }.onSuccess { profile = it }
        savedLoading = true
        hiddenLoading = true
        savedError = null
        hiddenError = null
        runCatching { api.posts("saved") }
            .onSuccess { savedPosts = it }
            .onFailure { savedError = it.message ?: "Не удалось загрузить избранное" }
        runCatching { api.posts("hidden") }
            .onSuccess { hiddenPosts = it }
            .onFailure { hiddenError = it.message ?: "Не удалось загрузить скрытые публикации" }
        runCatching { api.blockedUsers() }.onSuccess { blockedUsers = it }
        savedLoading = false
        hiddenLoading = false
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 18.dp + bottomInset), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(profileColor.copy(alpha = .18f), theme.surface.copy(alpha = .96f))))
                .border(1.dp, profileColor.copy(alpha = .42f), RoundedCornerShape(28.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(20.dp)).background(profileBrush),
                contentAlignment = Alignment.Center
            ) {
                Text("vibecodeGram", color = Color.White.copy(alpha = .92f), fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = (-.7).sp)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.size(94.dp).clip(CircleShape).background(profileBrush).border(3.dp, theme.surface, CircleShape), contentAlignment = Alignment.Center) {
                Text("V", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            Text(user.displayName, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("@"+user.username, color = theme.secondary, fontSize = 13.sp)
            profile?.let { data ->
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(data.posts.toString(), color = Color.White, fontWeight = FontWeight.Bold); Text("публикаций", color = theme.secondary, fontSize = 10.sp) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(data.followers.toString(), color = Color.White, fontWeight = FontWeight.Bold); Text("подписчиков", color = theme.secondary, fontSize = 10.sp) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(data.following.toString(), color = Color.White, fontWeight = FontWeight.Bold); Text("подписок", color = theme.secondary, fontSize = 10.sp) }
                }
                if (data.bio.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(data.bio, color = Color(0xFFE8EBF1), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                if (data.location.isNotBlank()) Text(data.location, color = theme.secondary, fontSize = 11.sp)
                if (data.website.isNotBlank()) Text(data.website, color = theme.accent, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { editProfile = true }, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Редактировать профиль")
                }
            }
            profileError?.let { Text(it, color = Color(0xFFFF8294), fontSize = 11.sp) }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onProfileColors,
                border = androidx.compose.foundation.BorderStroke(1.dp, profileColor.copy(alpha = .75f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = profileColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.ColorLens, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Цвет профиля")
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(theme) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bookmark, null, tint = theme.accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Избранное", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Твои сохранённые публикации", color = theme.secondary, fontSize = 11.sp)
                }
                IconButton(onClick = {
                    savedScope.launch {
                        savedLoading = true
                        savedError = null
                        runCatching { api.posts("saved") }
                            .onSuccess { savedPosts = it }
                            .onFailure { savedError = it.message ?: "Не удалось обновить избранное" }
                        savedLoading = false
                    }
                }) { Icon(Icons.Default.Refresh, "Обновить избранное", tint = theme.accent) }
            }
            when {
                savedLoading -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = theme.accent)
                }
                savedError != null -> Text(savedError!!, color = Color(0xFFFF8294), fontSize = 12.sp)
                savedPosts.isEmpty() -> Text("Здесь будут публикации, которые ты сохранишь в ленте.", color = theme.secondary, fontSize = 12.sp)
                else -> Text("Сохранено: ${savedPosts.size}", color = theme.secondary, fontSize = 11.sp)
            }
        }
        savedPosts.forEach { savedPost ->
            Spacer(Modifier.height(8.dp))
            FeedPostCard(
                post = savedPost,
                theme = theme,
                compact = false,
                api = api,
                onLike = {
                    savedScope.launch {
                        runCatching { api.toggleLike(savedPost.id) }.onSuccess { result ->
                            savedPosts = savedPosts.map { if (it.id == savedPost.id) it.copy(liked = result.first, likes = result.second) else it }
                        }.onFailure { savedError = it.message ?: "Не удалось поставить отметку" }
                    }
                },
                onSave = {
                    savedScope.launch {
                        runCatching { api.setSaved(savedPost.id, false) }.onSuccess {
                            savedPosts = savedPosts.filterNot { it.id == savedPost.id }
                        }.onFailure { savedError = it.message ?: "Не удалось убрать из избранного" }
                    }
                },
                onHide = {
                    savedScope.launch {
                        runCatching { api.hidePost(savedPost.id) }.onSuccess {
                            savedPosts = savedPosts.filterNot { it.id == savedPost.id }
                        }.onFailure { savedError = it.message ?: "Не удалось скрыть публикацию" }
                    }
                },
                onBlock = {
                    savedScope.launch {
                        runCatching { api.blockUser(savedPost.authorId) }.onSuccess {
                            savedPosts = savedPosts.filterNot { it.authorId == savedPost.authorId }
                        }.onFailure { savedError = it.message ?: "Не удалось заблокировать автора" }
                    }
                }
            )
        }
        Spacer(Modifier.height(14.dp))
        GlassCard(theme) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VisibilityOff, null, tint = theme.accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Скрытые публикации", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Посты, которые ты убрал из своей ленты", color = theme.secondary, fontSize = 11.sp)
                }
                IconButton(onClick = {
                    savedScope.launch {
                        hiddenLoading = true
                        hiddenError = null
                        runCatching { api.posts("hidden") }
                            .onSuccess { hiddenPosts = it }
                            .onFailure { hiddenError = it.message ?: "Не удалось обновить скрытые публикации" }
                        hiddenLoading = false
                    }
                }, enabled = !hiddenLoading) {
                    Icon(Icons.Default.Refresh, "Обновить скрытые публикации", tint = theme.accent)
                }
            }
            when {
                hiddenLoading -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = theme.accent)
                }
                hiddenError != null -> Text(hiddenError!!, color = Color(0xFFFF8294), fontSize = 12.sp)
                hiddenPosts.isEmpty() -> Text("Скрытых публикаций пока нет.", color = theme.secondary, fontSize = 12.sp)
                else -> Text("Скрыто: ${hiddenPosts.size}", color = theme.secondary, fontSize = 11.sp)
            }
        }
        hiddenPosts.forEach { hiddenPost ->
            Spacer(Modifier.height(8.dp))
            FeedPostCard(
                post = hiddenPost,
                theme = theme,
                compact = false,
                api = api,
                onLike = {
                    savedScope.launch {
                        runCatching { api.toggleLike(hiddenPost.id) }.onSuccess { result ->
                            hiddenPosts = hiddenPosts.map { if (it.id == hiddenPost.id) it.copy(liked = result.first, likes = result.second) else it }
                        }.onFailure { hiddenError = it.message ?: "Не удалось поставить отметку" }
                    }
                },
                onSave = {
                    savedScope.launch {
                        runCatching { api.setSaved(hiddenPost.id, !hiddenPost.saved) }.onSuccess {
                            hiddenPosts = hiddenPosts.map { if (it.id == hiddenPost.id) it.copy(saved = !it.saved) else it }
                        }.onFailure { hiddenError = it.message ?: "Не удалось изменить сохранение" }
                    }
                },
                onHide = { }
            )
            TextButton(
                onClick = {
                    savedScope.launch {
                        runCatching { api.unhidePost(hiddenPost.id) }.onSuccess {
                            hiddenPosts = hiddenPosts.filterNot { it.id == hiddenPost.id }
                        }.onFailure { hiddenError = it.message ?: "Не удалось вернуть публикацию" }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Visibility, null, tint = theme.accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Вернуть в ленту", color = theme.accent)
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(theme) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, null, tint = theme.accent)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Заблокированные пользователи", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Их публикации и истории скрыты от тебя", color = theme.secondary, fontSize = 11.sp)
                    }
                    IconButton(onClick = {
                        savedScope.launch {
                            runCatching { api.blockedUsers() }.onSuccess { blockedUsers = it }
                        }
                    }) { Icon(Icons.Default.Refresh, "Обновить", tint = theme.accent) }
                }
                if (blockedUsers.isEmpty()) {
                    Text("Список пуст.", color = theme.secondary, fontSize = 12.sp)
                } else {
                    blockedUsers.forEach { blocked ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(blocked.displayName.ifBlank { blocked.username }, color = Color.White, fontSize = 13.sp)
                                Text("@${blocked.username}", color = theme.secondary, fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                savedScope.launch {
                                    runCatching { api.unblockUser(blocked.id) }.onSuccess {
                                        blockedUsers = blockedUsers.filterNot { it.id == blocked.id }
                                    }
                                }
                            }) { Text("Разблокировать", color = theme.accent, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(theme) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, null, tint = theme.accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Оформление приложения", color = Color.White, fontWeight = FontWeight.SemiBold); Text("Тема: ${palettes[paletteIndex].name}", color = theme.secondary, fontSize = 12.sp) }
                TextButton(onClick = onCustomize) { Text("Изменить") }
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(theme) {
            ServerStatusCard(theme)
            Divider(color = Color.White.copy(alpha = .08f))
            SessionManagerCard(theme, api)
            Divider(color = Color.White.copy(alpha = .08f))
            Row(Modifier.fillMaxWidth().clickable { onLogout() }.padding(vertical = 12.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.Logout,null,tint=Color(0xFFFF8294),modifier=Modifier.size(19.dp))
                Spacer(Modifier.width(12.dp))
                Text("Выйти из аккаунта",color=Color(0xFFFF8294),modifier=Modifier.weight(1f),fontSize=14.sp)
            }
            Divider(color = Color.White.copy(alpha = .08f))
        }
    }
    if (editProfile && profile != null) {
        ProfileEditDialog(profile!!, theme, onDismiss = { editProfile = false }, onSave = { displayName, bio, avatar, cover, website, location ->
            savedScope.launch {
                profileError = null
                runCatching { api.updateProfile(displayName, bio, avatar, cover, website, location); api.profile(user.id) }
                    .onSuccess { profile = it; editProfile = false }
                    .onFailure { profileError = it.message ?: "Не удалось сохранить профиль" }
            }
        })
    }
}

@Composable
private fun SessionManagerCard(theme: Palette, api: ApiClient) {
    var expanded by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column {
        Row(Modifier.fillMaxWidth().clickable {
            expanded = !expanded
            if (expanded) {
                loading = true
                scope.launch {
                    runCatching { api.sessions() }.onSuccess { data ->
                        rows = (0 until data.length()).map { i -> data.getJSONObject(i).let { it.getString("id") to it.optBoolean("current") } }
                    }
                    loading = false
                }
            }
        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Devices, null, tint = theme.accent)
            Spacer(Modifier.width(12.dp))
            Text("Активные сеансы", color = Color.White, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = theme.secondary)
        }
        if (expanded) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = theme.accent)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (row.second) "Это устройство" else "Другой сеанс", color = theme.secondary, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    if (!row.second) TextButton(onClick = { scope.launch { runCatching { api.revokeSession(row.first) }; rows = rows.filterNot { it.first == row.first } } }) { Text("Завершить", color = Color(0xFFFF8294)) }
                }
            }
            if (rows.isEmpty() && !loading) Text("Нет активных сеансов", color = theme.secondary, fontSize = 11.sp)
        }
    }
}
@Composable
private fun ProfileColorEditor(
    username: String,
    hue: Float,
    saturation: Float,
    brightness: Float,
    gradient: Boolean,
    theme: Palette,
    onHue: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onGradient: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val color = Color.hsv(hue, saturation, brightness)
    val deep = Color.hsv((hue + 34f) % 360f, saturation, (brightness * .43f).coerceIn(.08f, 1f))
    val presets = listOf(205f, 265f, 325f, 350f, 18f, 42f, 125f, 165f)
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.liquidGlass(theme, RoundedCornerShape(28.dp), .92f),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        title = { Text("Цвет профиля", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 570.dp).verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(20.dp))
                        .background(if (gradient) Brush.linearGradient(listOf(color, deep, color.copy(alpha = .72f))) else Brush.linearGradient(listOf(color, color))),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                            Text("V", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column { Text("Твой профиль", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("@$username", color = Color.White.copy(alpha = .8f), fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("ГОТОВЫЕ ЦВЕТА", color = theme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    presets.forEach { preset ->
                        val presetColor = Color.hsv(preset, .76f, .92f)
                        Box(
                            Modifier.size(31.dp).clip(CircleShape).background(presetColor)
                                .border(if (kotlin.math.abs(hue - preset) < 12f) 3.dp else 1.dp, Color.White.copy(alpha = if (kotlin.math.abs(hue - preset) < 12f) 1f else .25f), CircleShape)
                                .clickable { onHue(preset); onSaturation(.76f); onBrightness(.92f) }
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("ОТТЕНОК · ${hue.roundToInt()}°", color = theme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(value = hue, onValueChange = onHue, valueRange = 0f..360f, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = color, inactiveTrackColor = Color.White.copy(alpha = .16f)))
                Text("НАСЫЩЕННОСТЬ · ${(saturation * 100).roundToInt()}%", color = theme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(value = saturation, onValueChange = onSaturation, valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
                Text("ЯРКОСТЬ · ${(brightness * 100).roundToInt()}%", color = theme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(value = brightness, onValueChange = onBrightness, valueRange = .12f..1f, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Градиент профиля", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Плавный переход между двумя оттенками", color = theme.secondary, fontSize = 11.sp)
                    }
                    Switch(checked = gradient, onCheckedChange = onGradient, colors = SwitchDefaults.colors(checkedThumbColor = theme.background, checkedTrackColor = color))
                }
                Spacer(Modifier.height(8.dp))
                Text("Цвет сохраняется автоматически и применяется к шапке и аватару профиля.", color = theme.secondary, fontSize = 11.sp, lineHeight = 16.sp)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Готово", color = theme.accent) } }
    )
}

@Composable
private fun ProfileStat(value: String, label: String, theme: Palette) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(label, color = theme.secondary, fontSize = 11.sp) }
}

@Composable
private fun ServerStatusCard(theme: Palette) {
    var status by remember { mutableStateOf("Проверяем подключение…") }
    var checking by remember { mutableStateOf(true) }
    var healthy by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        checking = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(BuildConfig.API_BASE_URL.trimEnd('/') + "/healthz").openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                try {
                    if (connection.responseCode == 200) "Сервер доступен" else "Ответ сервера: HTTP ${connection.responseCode}"
                } finally { connection.disconnect() }
            }.getOrElse { "Сервер пока недоступен" }
        }
        status = result
        healthy = result == "Сервер доступен"
        checking = false
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Cloud, null, tint = theme.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Сервер vibecodeGram", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(BuildConfig.API_BASE_URL, color = theme.secondary, fontSize = 10.sp)
            Text(status, color = if (healthy == true) Color(0xFF65D6B0) else theme.secondary, fontSize = 11.sp)
        }
        if (checking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = theme.accent)
        else Icon(if (healthy == true) Icons.Default.CheckCircle else Icons.Default.CloudOff, null, tint = if (healthy == true) Color(0xFF65D6B0) else theme.secondary)
    }
}

@Composable
private fun AppearanceEditor(paletteIndex: Int, accentIndex: Int, glass: Float, compact: Boolean, theme: Palette, onPalette: (Int) -> Unit, onAccent: (Int) -> Unit, onGlass: (Float) -> Unit, onCompact: (Boolean) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.liquidGlass(theme, RoundedCornerShape(28.dp), .92f),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Palette, null, tint = theme.accent); Spacer(Modifier.width(9.dp)); Text("Настройка внешнего вида", color = Color.White, fontSize = 19.sp) } },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState())) {
                Text("ЦВЕТОВАЯ ТЕМА", color = theme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                palettes.forEachIndexed { index, palette ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(if (index == paletteIndex) theme.accent.copy(alpha = .12f) else Color.Transparent).clickable { onPalette(index) }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(palette.background, palette.surface, palette.accent, palette.glow).forEach { color -> Box(Modifier.size(21.dp).clip(CircleShape).background(color)) }
                        }
                        Text(palette.name, color = Color.White, modifier = Modifier.weight(1f).padding(start = 10.dp), fontWeight = if (index == paletteIndex) FontWeight.Bold else FontWeight.Normal)
                        if (index == paletteIndex) Icon(Icons.Default.CheckCircle, null, tint = theme.accent)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("АКЦЕНТНЫЙ ЦВЕТ", color = theme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    accents.forEachIndexed { index, color ->
                        Box(Modifier.size(30.dp).clip(CircleShape).background(color).border(if (accentIndex == index) 3.dp else 0.dp, Color.White, CircleShape).clickable { onAccent(index) }, contentAlignment = Alignment.Center) {
                            if (accentIndex == index) Icon(Icons.Default.Check, null, tint = Color(0xFF101010), modifier = Modifier.size(16.dp))
                        }
                    }
                }
                TextButton(onClick = { onAccent(-1) }) { Text("Цвет по умолчанию для темы", color = theme.accent) }
                Spacer(Modifier.height(7.dp))
                Text("ИНТЕНСИВНОСТЬ LIQUID GLASS · ${(glass * 100).roundToInt()}%", color = theme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                Slider(value = glass, onValueChange = onGlass, valueRange = .2f..1f, colors = SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Компактный интерфейс", color = Color.White, fontSize = 13.sp); Text("Меньше отступов в списках", color = theme.secondary, fontSize = 11.sp) }
                    Switch(checked = compact, onCheckedChange = onCompact, colors = SwitchDefaults.colors(checkedThumbColor = theme.background, checkedTrackColor = theme.accent))
                }
                Spacer(Modifier.height(10.dp))
                GlassCard(theme) {
                    Text("Предпросмотр", color = theme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar("v", theme.accent, 34)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) { Text("vibecodeGram", color = Color.White, fontWeight = FontWeight.SemiBold); Text("Твой стиль — твои правила", color = theme.secondary, fontSize = 11.sp) }
                        Icon(Icons.Default.Favorite, null, tint = theme.accent)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Готово", color = theme.accent) } }
    )
}

@Composable
private fun Avatar(letter: String, accent: Color, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent.copy(alpha = .85f), accent.copy(alpha = .25f)))).border(1.dp, Color.White.copy(alpha = .22f), CircleShape), contentAlignment = Alignment.Center) {
        Text(letter.take(1).uppercase(), color = Color(0xFF10151D), fontSize = (size / 2.5f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun GlassCard(theme: Palette, compact: Boolean = false, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().liquidGlass(theme, RoundedCornerShape(22.dp), .82f).padding(if (compact) 12.dp else 16.dp), content = content)
}

@Composable
private fun Modifier.liquidGlass(theme: Palette, shape: Shape, intensity: Float = .8f): Modifier {
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassFill = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = .24f * intensity),
            theme.surface.copy(alpha = .58f),
            theme.background.copy(alpha = .48f),
            Color.White.copy(alpha = .13f * intensity)
        )
    )
    val gloss = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = .28f * intensity),
            Color.White.copy(alpha = .07f * intensity),
            Color.Transparent
        )
    )
    val base = this.shadow(18.dp, shape, clip = false).clip(shape)
    val surface = if (backdrop != null && Build.VERSION.SDK_INT >= 33) {
        base.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur((18f * intensity).dp.toPx())
                lens((17f * intensity).dp.toPx(), (17f * intensity).dp.toPx(), depthEffect = true)
            },
            onDrawSurface = { drawRect(glassFill) }
        )
    } else {
        // Android 12 and 12L do not support the backdrop lens path used on API 33+.
        // Layered specular gradients preserve a visible glass appearance on those devices.
        base.background(glassFill).background(gloss)
    }
    return surface
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .52f * intensity), Color.White.copy(alpha = .10f), theme.accent.copy(alpha = .22f * intensity))), shape)
        .border(.6.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .26f * intensity), Color.Transparent)), shape)
}

@Composable
private fun LiquidGlassNavigationBar(
    items: List<Destination>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop,
    theme: Palette,
    intensity: Float,
    modifier: Modifier = Modifier
) {
    var shown by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) { shown = selectedIndex }
    val shownState = rememberUpdatedState(shown)
    val selectedTabIndex = remember { { shownState.value } }
    val selectedState = rememberUpdatedState(selectedIndex)
    val onSelectedState = rememberUpdatedState(onSelected)
    val blurs = Build.VERSION.SDK_INT >= 33
    val refracts = Build.VERSION.SDK_INT >= 33

    LiquidBottomTabs(
        selectedTabIndex = selectedTabIndex,
        onTabSelected = { index ->
            shown = index
            if (index != selectedState.value) onSelectedState.value(index)
        },
        backdrop = backdrop,
        tabsCount = items.size,
        modifier = modifier,
        accentColor = theme.accent,
        stiffness = 320f,
        blurs = blurs,
        refracts = refracts,
        intensity = intensity,
        isLightTheme = false,
        containerColor = theme.surface.copy(alpha = if (blurs) .38f + .18f * (1f - intensity) else .96f)
    ) {
        items.forEachIndexed { index, item ->
            LiquidBottomTab(
                onClick = { shown = index },
                modifier = Modifier.semantics { selected = index == shown }
            ) {
                Icon(item.icon, contentDescription = null, tint = theme.secondary, modifier = Modifier.size(21.dp))
                Text(item.label, color = theme.secondary, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}


@Composable
private fun AuthScreen(theme: Palette, api: ApiClient, onAuthenticated: (ApiUser) -> Unit) {
    val context = LocalContext.current
    var register by remember { mutableStateOf(false) }
    var diagnosticsMessage by remember { mutableStateOf<String?>(null) }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val diagnostics = remember { context.getSharedPreferences("vibecodegram-diagnostics", Context.MODE_PRIVATE) }
    fun markAuthStage(stage: String, detail: String = "") {
        diagnostics.edit().putString("auth_stage", stage).putString("auth_detail", detail.take(500))
            .putLong("auth_stage_time", System.currentTimeMillis()).apply()
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.glow.copy(alpha=.28f), theme.background, theme.background))).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().liquidGlass(theme, RoundedCornerShape(28.dp), .92f).padding(22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("vibecodeGram", color=Color.White, fontSize=29.sp, fontWeight=FontWeight.Black)
            Text(if(register) "Создай аккаунт" else "С возвращением", color=theme.accent, fontSize=19.sp, fontWeight=FontWeight.Bold)
            Text("Аккаунт и переписки синхронизируются через сервер.", color=theme.secondary, fontSize=12.sp)
            if(register) OutlinedTextField(displayName,{displayName=it},modifier=Modifier.fillMaxWidth(),label={Text("Имя")},singleLine=true)
            OutlinedTextField(username,{username=it.lowercase().filter { c -> c in 'a'..'z' || c in '0'..'9' || c=='_' }},modifier=Modifier.fillMaxWidth(),label={Text("Логин (латиница, цифры, _)")},singleLine=true)
            OutlinedTextField(password,{password=it},modifier=Modifier.fillMaxWidth(),label={Text("Пароль")},singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
            if(error!=null) Text(error!!,color=Color(0xFFFF8294),fontSize=12.sp)
            Button(onClick={
                error=null
                val login = username.trim().lowercase()
                val passwordBytes = password.toByteArray(Charsets.UTF_8).size
                val displayNameBytes = displayName.trim().toByteArray(Charsets.UTF_8).size
                if (!login.matches(Regex("[a-z0-9_]{3,32}"))) {
                    error="Логин: 3–32 символа, только латиница, цифры и _."
                } else if (passwordBytes !in 10..72) {
                    error="Пароль должен занимать от 10 до 72 байт."
                } else if (register && displayNameBytes > 64) {
                    error="Имя слишком длинное (максимум 64 байта)."
                } else {
                    busy=true
                    markAuthStage(if (register) "register_started" else "login_started")
                    scope.launch {
                        try {
                            markAuthStage("auth_request_sending")
                            val result = api.authenticate(login,password,displayName,register)
                            markAuthStage("auth_api_success", "user_id=${result.user.id}")
                            onAuthenticated(result.user)
                            markAuthStage("auth_state_updated", "user_id=${result.user.id}")
                        } catch (e: Exception) {
                            val message = e.message ?: e.javaClass.simpleName
                            markAuthStage("auth_failed", message)
                            error=message
                        } finally {
                            busy=false
                        }
                    }
                }
            },modifier=Modifier.fillMaxWidth(),enabled=!busy) {
                if(busy) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
                else Text(if(register) "Зарегистрироваться" else "Войти")
            }
            TextButton(onClick={register=!register;error=null},modifier=Modifier.fillMaxWidth()) {
                Text(if(register) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться",color=theme.accent)
            }
            Text("Пароль хранится на сервере только в виде защищённого хеша.",color=theme.secondary,fontSize=10.sp)
            TextButton(onClick = {
                val report = runCatching {
                    context.openFileInput("vibecodegram-crash.txt").bufferedReader().use { it.readText() }
                }.getOrElse {
                    val stage = diagnostics.getString("auth_stage", "не записан")
                    val detail = diagnostics.getString("auth_detail", "")
                    val stageTime = diagnostics.getLong("auth_stage_time", 0L)
                    buildString {
                        appendLine("Аварийный отчёт отсутствует; сохранена диагностика этапа.")
                        appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("Android API: ${Build.VERSION.SDK_INT}")
                        appendLine("Версия: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Последний этап: $stage")
                        if (!detail.isNullOrBlank()) appendLine("Детали: $detail")
                        if (stageTime > 0L) appendLine("Время: ${java.util.Date(stageTime)}")
                    }
                }
                runCatching {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "vibecodeGram — диагностика")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(intent, "Отправить отчёт"))
                }.onFailure { diagnosticsMessage = it.message ?: "Не удалось открыть отправку отчёта" }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Отправить отчёт о сбое", color = theme.accent)
            }
            diagnosticsMessage?.let { Text(it, color = Color(0xFFFF8294), fontSize = 11.sp) }
        }
    }
}
