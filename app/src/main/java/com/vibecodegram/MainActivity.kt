package com.vibecodegram

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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


@Composable
private fun GlassPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    theme: Palette,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(135)) + scaleIn(
                initialScale = .72f,
                transformOrigin = TransformOrigin(1f, .5f),
                animationSpec = tween(190)
            )
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 224.dp)
                    .liquidGlass(theme, RoundedCornerShape(20.dp), .88f)
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                content = content
            )
        }
    }
}

@Composable
private fun GlassMenuItem(
    icon: ImageVector,
    label: String,
    theme: Palette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(theme, RoundedCornerShape(13.dp), .62f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(11.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
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
private data class Post(val author: String, val handle: String, val time: String, val text: String, val tag: String, val likes: Int)
private data class LocalChatMessage(val id: Long, val text: String, val time: String, val outgoing: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { vibecodeGramApp(getSharedPreferences("appearance", Context.MODE_PRIVATE)) }
    }
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
                    AppHeader(theme, onSearch = { page = 1 }, onSettings = { editor = true })
                    when (page) {
                        0 -> FeedScreen(theme, compact, 0.dp, prefs)
                        1 -> ChatsScreen(theme, search, { search = it }, folder, { folder = it }, compact, onOpenChat = { openedChat = it }, bottomInset = 0.dp, api = api)
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
private fun AppHeader(theme: Palette, onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("vibecodeGram", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = (-.7).sp)
            Text("ТВОЁ ПРОСТРАНСТВО · ТВОИ ПРАВИЛА", color = theme.accent, fontSize = 9.sp, letterSpacing = 1.25.sp, fontWeight = FontWeight.Bold)
        }
        HeaderIcon(Icons.Default.Search, "Поиск", onSearch, theme)
        Spacer(Modifier.width(9.dp))
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
private fun FeedScreen(theme: Palette, compact: Boolean, bottomInset: androidx.compose.ui.unit.Dp, prefs: android.content.SharedPreferences) {
    Box(Modifier.fillMaxSize().padding(20.dp),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(Icons.Default.DynamicFeed,null,tint=theme.secondary,modifier=Modifier.size(44.dp))
            Spacer(Modifier.height(12.dp))
            Text("Пока нет публикаций",color=Color.White,fontWeight=FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChatsScreen(theme: Palette, search: String, onSearch: (String) -> Unit, folder: String, onFolder: (String) -> Unit, compact: Boolean, onOpenChat: (Chat) -> Unit, bottomInset: androidx.compose.ui.unit.Dp, api: ApiClient) {
    var chats by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(api.token) {
        loading=true; error=null
        runCatching { api.conversations() }
            .onSuccess { rows -> chats=rows.map { Chat(it.title.ifBlank { "Диалог #${it.id}" },it.lastMessage.ifBlank { "Пока нет сообщений" },"",0,false,it.kind=="group",it.id) } }
            .onFailure { error=it.message ?: "Не удалось загрузить чаты" }
        loading=false
    }
    OutlinedTextField(value=search,onValueChange=onSearch,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),placeholder={Text("Поиск по чатам")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color.White.copy(alpha=.12f),focusedBorderColor=theme.accent,unfocusedTextColor=Color.White,focusedTextColor=Color.White))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        listOf("Все","Личные","Группы").forEach { label ->
            FilterChip(selected=folder==label,onClick={onFolder(label)},label={Text(label)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=theme.accent.copy(alpha=.20f),selectedLabelColor=theme.accent))
        }
    }
    when {
        loading -> Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){CircularProgressIndicator(color=theme.accent)}
        error!=null -> Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(error!!,color=theme.secondary,textAlign=androidx.compose.ui.text.style.TextAlign.Center);TextButton(onClick={}){Text("Повтори загрузку",color=theme.accent)}}
        else -> LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=4.dp,bottom=12.dp+bottomInset),verticalArrangement=Arrangement.spacedBy(if(compact)5.dp else 9.dp)) {
            val filtered=chats.filter{it.name.contains(search,true)||it.message.contains(search,true)}.filter{folder=="Все"||(folder=="Группы"&&it.group)||(folder=="Личные"&&!it.group)}
            if(filtered.isEmpty()) item { Text("Чатов пока нет. Начни диалог, когда появится поиск пользователей.",color=theme.secondary,modifier=Modifier.fillMaxWidth().padding(28.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center) }
            items(filtered,key={it.id}) { chat ->
                GlassCard(theme,compact,Modifier.clickable{onOpenChat(chat)}) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Avatar(chat.name.take(1),theme.accent,48);Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)){Text(chat.name,color=Color.White,fontWeight=FontWeight.SemiBold);Text(chat.message,color=theme.secondary,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
                        Icon(Icons.Default.ChevronRight,null,tint=theme.secondary)
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
private fun VideosScreen(theme: Palette, bottomInset: androidx.compose.ui.unit.Dp) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 18.dp + bottomInset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { GlassCard(theme) {
            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(theme.glow, theme.surface, theme.accent.copy(alpha = .24f)))), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayCircle, null, tint = theme.accent, modifier = Modifier.size(52.dp))
                    Text("Видео и клипы", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Тут появится твоя видеолента", color = theme.secondary, fontSize = 12.sp)
                }
            }
        } }
        item { Text("Вертикальные видео, подборки и авторские каналы — отдельное пространство для просмотра.", color = theme.secondary, fontSize = 14.sp, lineHeight = 21.sp) }
    }
}

@Composable
private fun ExploreScreen(theme: Palette, search: String, onSearch: (String) -> Unit, bottomInset: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        OutlinedTextField(value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Найти темы и авторов") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp))
        Text("Открывай новое", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 18.dp))
        val topics = listOf("Технологии" to "12,4 тыс. публикаций", "Музыка" to "8,2 тыс. публикаций", "Фотография" to "5,7 тыс. публикаций", "Игры" to "4,9 тыс. публикаций", "Дизайн" to "3,1 тыс. публикаций")
        LazyColumn(contentPadding = PaddingValues(bottom = bottomInset + 9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(topics.filter { it.first.contains(search, true) }) { topic ->
                GlassCard(theme) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(theme.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Tag, null, tint = theme.accent) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text("#${topic.first}", color = Color.White, fontWeight = FontWeight.SemiBold); Text(topic.second, color = theme.secondary, fontSize = 11.sp) }
                        Icon(Icons.Default.ChevronRight, null, tint = theme.secondary)
                    }
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
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProfileStat("0", "Публикации", theme)
                ProfileStat("0", "Подписчики", theme)
                ProfileStat("0", "Подписки", theme)
            }
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
            Row(Modifier.fillMaxWidth().clickable { onLogout() }.padding(vertical = 12.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.Logout,null,tint=Color(0xFFFF8294),modifier=Modifier.size(19.dp))
                Spacer(Modifier.width(12.dp))
                Text("Выйти из аккаунта",color=Color(0xFFFF8294),modifier=Modifier.weight(1f),fontSize=14.sp)
            }
            Divider(color = Color.White.copy(alpha = .08f))
            SettingsRow(Icons.Default.Lock, "Конфиденциальность", theme)
            Divider(color = Color.White.copy(alpha = .08f))
            SettingsRow(Icons.Default.Notifications, "Уведомления", theme)
            Divider(color = Color.White.copy(alpha = .08f))
            SettingsRow(Icons.Default.HelpOutline, "Помощь и поддержка", theme)
        }
    }
}

@Composable
private fun ProfileColorEditor(
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
                        Column { Text("Твой профиль", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("@your_username", color = Color.White.copy(alpha = .8f), fontSize = 12.sp) }
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
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, theme: Palette) {
    Row(Modifier.fillMaxWidth().clickable { }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = theme.accent, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Icon(Icons.Default.ChevronRight, null, tint = theme.secondary)
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
            Color.White.copy(alpha = .15f * intensity),
            theme.surface.copy(alpha = .58f),
            theme.background.copy(alpha = .48f),
            theme.surface.copy(alpha = .66f)
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
                if (Build.VERSION.SDK_INT >= 33) {
                    lens((17f * intensity).dp.toPx(), (17f * intensity).dp.toPx(), depthEffect = true)
                }
            },
            onDrawSurface = { drawRect(glassFill) }
        )
    } else {
        base.background(glassFill)
    }
    return surface
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .38f), Color.White.copy(alpha = .08f), theme.accent.copy(alpha = .16f))), shape)
        .border(.6.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .20f), Color.Transparent)), shape)
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
    var register by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.glow.copy(alpha=.28f), theme.background, theme.background))).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().liquidGlass(theme, RoundedCornerShape(28.dp), .92f).padding(22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("vibecodeGram", color=Color.White, fontSize=29.sp, fontWeight=FontWeight.Black)
            Text(if(register) "Создай аккаунт" else "С возвращением", color=theme.accent, fontSize=19.sp, fontWeight=FontWeight.Bold)
            Text("Аккаунт и переписки синхронизируются через сервер.", color=theme.secondary, fontSize=12.sp)
            if(register) OutlinedTextField(displayName,{displayName=it},modifier=Modifier.fillMaxWidth(),label={Text("Имя")},singleLine=true)
            OutlinedTextField(username,{username=it.lowercase().filter { c -> c.isLetterOrDigit() || c=='_' }},modifier=Modifier.fillMaxWidth(),label={Text("Логин")},singleLine=true)
            OutlinedTextField(password,{password=it},modifier=Modifier.fillMaxWidth(),label={Text("Пароль")},singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
            if(error!=null) Text(error!!,color=Color(0xFFFF8294),fontSize=12.sp)
            Button(onClick={
                error=null
                if(username.length !in 3..32 || password.length !in 10..72 || (register && displayName.length>64)) {
                    error="Логин: 3–32 символа. Пароль: минимум 10 символов."
                } else {
                    busy=true
                    scope.launch {
                        runCatching { api.authenticate(username,password,displayName,register) }
                            .onSuccess { onAuthenticated(it.user) }
                            .onFailure { error=it.message ?: "Не удалось выполнить запрос" }
                        busy=false
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
        }
    }
}
