package com.vibecodegram

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class Palette(val name: String, val background: Color, val surface: Color, val accent: Color, val secondary: Color, val glow: Color)
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
private data class Chat(val name: String, val message: String, val time: String, val unread: Int, val online: Boolean, val group: Boolean = false)
private data class Post(val author: String, val handle: String, val time: String, val text: String, val tag: String, val likes: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VibeCodeGramApp(getSharedPreferences("appearance", Context.MODE_PRIVATE)) }
    }
}

@Composable
private fun VibeCodeGramApp(prefs: android.content.SharedPreferences) {
    var page by remember { mutableIntStateOf(0) }
    var paletteIndex by remember { mutableIntStateOf(prefs.getInt("palette", 0).coerceIn(palettes.indices)) }
    var accentIndex by remember { mutableIntStateOf(prefs.getInt("accent", -1)) }
    var glass by remember { mutableFloatStateOf(prefs.getFloat("glass", .72f).coerceIn(.2f, 1f)) }
    var compact by remember { mutableStateOf(prefs.getBoolean("compact", false)) }
    var editor by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("Все") }
    val base = palettes[paletteIndex]
    val theme = base.copy(accent = if (accentIndex >= 0) accents[accentIndex] else base.accent)
    val backdrop = rememberLayerBackdrop()
    val destinations = listOf(
        Destination("Лента", Icons.Default.Home, "Твоя лента"),
        Destination("Чаты", Icons.Default.ChatBubble, "Сообщения"),
        Destination("Видео", Icons.Default.PlayCircle, "Видео"),
        Destination("Обзор", Icons.Default.Explore, "Интересное"),
        Destination("Профиль", Icons.Default.Person, "Профиль")
    )
    MaterialTheme(colorScheme = darkColorScheme(primary = theme.accent, background = theme.background, surface = theme.surface, onPrimary = theme.background, onBackground = Color.White, onSurface = Color.White)) {
        Scaffold(
            containerColor = theme.background,
            bottomBar = {
                LiquidGlassNavigationBar(destinations, page, { page = it }, backdrop, theme, glass,
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp))
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
                        0 -> FeedScreen(theme, compact)
                        1 -> ChatsScreen(theme, search, { search = it }, folder, { folder = it }, compact)
                        2 -> VideosScreen(theme)
                        3 -> ExploreScreen(theme, search, { search = it })
                        else -> ProfileScreen(theme, paletteIndex, onCustomize = { editor = true })
                    }
                }
            }
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
    Box(Modifier.size(42.dp).clip(CircleShape).background(theme.surface.copy(alpha = .88f)).border(1.dp, Color.White.copy(alpha = .12f), CircleShape).clickable { action() }, contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = theme.accent, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun FeedScreen(theme: Palette, compact: Boolean) {
    var selectedFilter by remember { mutableStateOf("Для тебя") }
    var liked by remember { mutableStateOf(setOf<Int>()) }
    val posts = listOf(
        Post("Алекс", "@alexwave", "2 мин", "Иногда лучший апдейт — это просто сделать интерфейс таким, каким давно хотел его видеть.", "Дизайн", 128),
        Post("Mira", "@mira.jpg", "18 мин", "Собрала новый плейлист для ночных поездок. Какой трек у вас сейчас на повторе?", "Музыка", 86),
        Post("vibecodeGram", "@vibecodegram", "1 ч", "Здесь будет твоя лента: публикации, истории, реакции и обсуждения. Настраивай внешний вид под себя.", "Обновления", 54)
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Для тебя", "Подписки", "Популярное", "Медиа").forEach { label ->
            FilterChip(selected = selectedFilter == label, onClick = { selectedFilter = label }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.accent.copy(alpha = .20f), selectedLabelColor = theme.accent))
        }
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        item { GlassCard(theme, compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar("Ты", theme.accent, 42)
                Spacer(Modifier.width(10.dp))
                Text("Что нового?", color = theme.secondary, modifier = Modifier.weight(1f))
                Text("＋", color = theme.accent, fontSize = 24.sp)
            }
        } }
        items(posts) { post ->
            val index = posts.indexOf(post)
            var menu by remember { mutableStateOf(false) }
            GlassCard(theme, compact, Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true })) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(post.author.take(1), theme.accent, 40)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(post.author, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("${post.handle} · ${post.time}", color = theme.secondary, fontSize = 11.sp)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "Ещё", tint = theme.secondary) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Сохранить") }, onClick = { menu = false }, leadingIcon = { Icon(Icons.Default.BookmarkBorder, null) })
                            DropdownMenuItem(text = { Text("Скрыть публикацию") }, onClick = { menu = false }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null) })
                            DropdownMenuItem(text = { Text("Поделиться") }, onClick = { menu = false }, leadingIcon = { Icon(Icons.Default.Share, null) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(post.text, color = Color(0xFFE5EAF1), fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(12.dp))
                Surface(color = theme.accent.copy(alpha = .10f), shape = RoundedCornerShape(8.dp)) {
                    Text("#${post.tag}", color = theme.accent, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { liked = if (index in liked) liked - index else liked + index }) {
                        Icon(if (index in liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (index in liked) Color(0xFFFF718B) else theme.secondary, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text((post.likes + if (index in liked) 1 else 0).toString(), color = theme.secondary)
                    }
                    TextButton(onClick = {}) { Icon(Icons.Default.ChatBubbleOutline, null, tint = theme.secondary, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Ответить", color = theme.secondary) }
                    Spacer(Modifier.weight(1f))
                    Text("•••", color = theme.secondary)
                }
            }
        }
    }
}

@Composable
private fun ChatsScreen(theme: Palette, search: String, onSearch: (String) -> Unit, folder: String, onFolder: (String) -> Unit, compact: Boolean) {
    val chats = listOf(
        Chat("Лера", "Отправила фотографию", "21:14", 2, true),
        Chat("Разработка vibecodeGram", "Алекс: залил новый интерфейс", "20:58", 5, true, true),
        Chat("Миша", "Окей, договорились", "19:42", 0, false),
        Chat("Избранное", "Твои сохранённые сообщения", "Вчера", 0, false),
        Chat("Дизайн-команда", "Новый вариант палитры готов", "Вт", 0, false, true)
    )
    OutlinedTextField(value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("Поиск по чатам и сообщениям") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.White.copy(alpha = .12f), focusedBorderColor = theme.accent, unfocusedTextColor = Color.White, focusedTextColor = Color.White))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf("Все", "Личные", "Группы", "Непрочитанные").forEach { label ->
            FilterChip(selected = folder == label, onClick = { onFolder(label) }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.accent.copy(alpha = .20f), selectedLabelColor = theme.accent))
        }
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 9.dp)) {
        items(chats.filter { it.name.contains(search, true) || it.message.contains(search, true) }.filter { folder == "Все" || folder == "Непрочитанные" && it.unread > 0 || folder == "Группы" && it.group || folder == "Личные" && !it.group }) { chat ->
            var menu by remember { mutableStateOf(false) }
            GlassCard(theme, compact, Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true })) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Avatar(chat.name.take(1), theme.accent, 48)
                        if (chat.online) Box(Modifier.align(Alignment.BottomEnd).size(12.dp).background(Color(0xFF55D69A), CircleShape).border(2.dp, theme.surface, CircleShape))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(chat.message, color = theme.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(chat.time, color = theme.secondary, fontSize = 10.sp)
                        if (chat.unread > 0) Box(Modifier.padding(top = 5.dp).clip(CircleShape).background(theme.accent).padding(horizontal = 7.dp, vertical = 3.dp)) { Text(chat.unread.toString(), color = theme.background, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null, tint = theme.secondary) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Закрепить") }, onClick = { menu = false }, leadingIcon = { Icon(Icons.Default.PushPin, null) })
                            DropdownMenuItem(text = { Text("В архив") }, onClick = { menu = false }, leadingIcon = { Icon(Icons.Default.Archive, null) })
                            DropdownMenuItem(text = { Text("Отключить уведомления") }, onClick = { menu = false }, leadingIcon = { Icon(Icons.Default.NotificationsOff, null) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideosScreen(theme: Palette) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
private fun ExploreScreen(theme: Palette, search: String, onSearch: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        OutlinedTextField(value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Найти темы и авторов") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp))
        Text("Открывай новое", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 18.dp))
        val topics = listOf("Технологии" to "12,4 тыс. публикаций", "Музыка" to "8,2 тыс. публикаций", "Фотография" to "5,7 тыс. публикаций", "Игры" to "4,9 тыс. публикаций", "Дизайн" to "3,1 тыс. публикаций")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
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
private fun ProfileScreen(theme: Palette, paletteIndex: Int, onCustomize: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Avatar("V", theme.accent, 86)
        Spacer(Modifier.height(12.dp))
        Text("Твой профиль", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("@your_username", color = theme.secondary, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ProfileStat("0", "Публикации", theme)
            ProfileStat("0", "Подписчики", theme)
            ProfileStat("0", "Подписки", theme)
        }
        Spacer(Modifier.height(22.dp))
        GlassCard(theme) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, null, tint = theme.accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Оформление", color = Color.White, fontWeight = FontWeight.SemiBold); Text("Тема: ${palettes[paletteIndex].name}", color = theme.secondary, fontSize = 12.sp) }
                TextButton(onClick = onCustomize) { Text("Изменить") }
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(theme) {
            SettingsRow(Icons.Default.BookmarkBorder, "Сохранённое", theme)
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
private fun ProfileStat(value: String, label: String, theme: Palette) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(label, color = theme.secondary, fontSize = 11.sp) }
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
        containerColor = theme.surface,
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
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(theme.surface.copy(alpha = .96f), theme.surface.copy(alpha = .72f)))).border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(22.dp)).padding(if (compact) 12.dp else 16.dp), content = content)
}

@Composable
private fun LiquidGlassNavigationBar(items: List<Destination>, selectedIndex: Int, onSelected: (Int) -> Unit, backdrop: Backdrop, theme: Palette, intensity: Float, modifier: Modifier = Modifier) {
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedIndex) { indicator.animateTo(selectedIndex.toFloat(), spring(dampingRatio = .72f, stiffness = 520f)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.fillMaxWidth(.98f).height(70.dp)
                .drawBackdrop(backdrop = backdrop, shape = { Capsule() }, effects = {
                    vibrancy()
                    blur((4f + 9f * intensity).dp.toPx())
                    lens((10f + 12f * intensity).dp.toPx(), (18f + 10f * intensity).dp.toPx(), depthEffect = true)
                }, onDrawSurface = { drawRect(theme.surface.copy(alpha = .72f)) })
                .border(1.dp, Color.White.copy(alpha = .19f), Capsule()).padding(4.dp)
        ) {
            val tabWidth = maxWidth / items.size
            val tabWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { tabWidth.toPx() }
            Box(Modifier.offset(x = tabWidth * indicator.value).size(width = tabWidth, height = maxHeight).padding(2.dp)
                .drawBackdrop(backdrop = backdrop, shape = { Capsule() }, effects = { vibrancy(); blur(2.dp.toPx()); lens(12.dp.toPx(), 20.dp.toPx(), chromaticAberration = true) },
                    onDrawSurface = { drawRect(theme.accent.copy(alpha = .13f)); drawRect(Brush.verticalGradient(listOf(Color.White.copy(.16f), Color.White.copy(.025f)))) }))
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { index, item ->
                    Column(Modifier.weight(1f).fillMaxHeight().clip(Capsule())
                        .pointerInput(index, tabWidthPx) {
                            detectDragGestures(onDragEnd = { val target = indicator.value.roundToInt().coerceIn(items.indices); scope.launch { indicator.animateTo(target.toFloat(), spring()); onSelected(target) } },
                                onDragCancel = { scope.launch { indicator.animateTo(selectedIndex.toFloat(), spring()) } },
                                onDrag = { change, amount -> change.consume(); val next = (indicator.value + amount.x / tabWidthPx).coerceIn(0f, items.lastIndex.toFloat()); scope.launch { indicator.snapTo(next) } })
                        }.clickable { onSelected(index) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(item.icon, item.label, tint = if (selectedIndex == index) theme.accent else theme.secondary, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(item.label, color = if (selectedIndex == index) Color.White else theme.secondary, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
