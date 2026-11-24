package com.vibecodegram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Night = Color(0xFF080B12)
private val Glass = Color(0xB8202938)
private val Accent = Color(0xFF75D7FF)

private data class NavDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VibeCodeGramApp() }
    }
}

@Composable
private fun VibeCodeGramApp() {
    var selected by remember { mutableIntStateOf(0) }
    val backdrop = rememberLayerBackdrop()
    val items = listOf(
        NavDestination("Лента", Icons.Default.Home, "Твоя лента"),
        NavDestination("Чаты", Icons.Default.ChatBubble, "Сообщения"),
        NavDestination("Видео", Icons.Default.PlayCircle, "Видео"),
        NavDestination("Обзор", Icons.Default.Explore, "Интересное"),
        NavDestination("Профиль", Icons.Default.Person, "Профиль")
    )

    MaterialTheme {
        Scaffold(
            containerColor = Night,
            bottomBar = {
                LiquidGlassNavigationBar(
                    items = items,
                    selectedIndex = selected,
                    onSelected = { selected = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF142338), Night, Color(0xFF10131D))
                        )
                    )
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("vibecodeGram", color = Color.White, fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                            Text("Связь без границ", color = Accent, fontSize = 12.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Glass, CircleShape)
                                .border(1.dp, Color.White.copy(alpha = .18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ChatBubble, contentDescription = "Сообщения", tint = Accent)
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                    Text(
                        items[selected].title,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    GlassPanel {
                        Text("Добро пожаловать в vibecodeGram", color = Color.White, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Новый независимый клиент: общение, медиа и социальные функции в одном пространстве.",
                            color = Color(0xFFB9C5D5),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(18.dp))
                        Surface(
                            color = Color(0x332FBAF5),
                            shape = Capsule()
                        ) {
                            Text(
                                "ПЕРВЫЙ ЭТАП · ОСНОВА ПРИЛОЖЕНИЯ",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = Accent,
                                fontSize = 10.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    GlassPanel {
                        Text("Что будет внутри", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 17.sp)
                        Spacer(Modifier.height(10.dp))
                        FeatureLine("Мессенджер", "Диалоги, группы, каналы и звонки")
                        FeatureLine("Социальная лента", "Публикации, истории, реакции и комментарии")
                        FeatureLine("Настройка", "Темы, жесты, интерфейс и расширения")
                        FeatureLine("Liquid Glass", "Преломление фона, стеклянные блики и упругая анимация")
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassNavigationBar(
    items: List<NavDestination>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val backdrop = rememberLayerBackdrop()
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex) {
        indicator.animateTo(
            selectedIndex.toFloat(),
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f)
        )
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(.94f)
                .height(70.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx())
                        lens(22.dp.toPx(), 28.dp.toPx(), depthEffect = true)
                    },
                    onDrawSurface = { drawRect(Color(0xB8182636)) }
                )
                .border(1.dp, Color.White.copy(alpha = .20f), Capsule())
                .padding(4.dp)
        ) {
            val tabWidth = maxWidth / items.size
            val tabWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { tabWidth.toPx() }

            Box(
                modifier = Modifier
                    .offset(x = tabWidth * indicator.value)
                    .size(width = tabWidth, height = maxHeight)
                    .padding(2.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(2.dp.toPx())
                            lens(12.dp.toPx(), 22.dp.toPx(), chromaticAberration = true)
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = .12f))
                            drawRect(Brush.verticalGradient(listOf(Color.White.copy(.16f), Color.White.copy(.025f))))
                        }
                    )
                    .pointerInput(items.size, tabWidthPx) {
                        var start = indicator.value
                        detectDragGestures(
                            onDragStart = { start = indicator.value },
                            onDragEnd = {
                                val target = indicator.value.roundToInt().coerceIn(items.indices)
                                scope.launch {
                                    indicator.animateTo(target.toFloat(), spring(dampingRatio = .68f, stiffness = 420f))
                                    onSelected(target)
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    indicator.animateTo(selectedIndex.toFloat(), spring())
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val next = (indicator.value + amount.x / tabWidthPx).coerceIn(0f, items.lastIndex.toFloat())
                                scope.launch { indicator.snapTo(next) }
                            }
                        )
                    }
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(Capsule())
                            .clickable { onSelected(index) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (selectedIndex == index) Color.White else Color(0xFFB7C5D6),
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.label,
                            color = if (selectedIndex == index) Color.White else Color(0xFFB7C5D6),
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Glass, androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = .12f), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) { content() }
}

@Composable
private fun FeatureLine(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(vertical = 7.dp)) {
        Text(title, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        Text(subtitle, color = Color(0xFF9EADBF), fontSize = 12.sp)
    }
}
