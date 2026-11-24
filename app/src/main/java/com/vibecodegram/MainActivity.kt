package com.vibecodegram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val Night = Color(0xFF080B12)
private val Glass = Color(0xB8202938)
private val Accent = Color(0xFF75D7FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VibeCodeGramApp() }
    }
}

@Composable
private fun VibeCodeGramApp() {
    var selected by remember { mutableIntStateOf(0) }
    val items = listOf(
        "Лента" to Icons.Default.Home,
        "Чаты" to Icons.Default.ChatBubble,
        "Видео" to Icons.Default.PlayCircle,
        "Обзор" to Icons.Default.Explore,
        "Профиль" to Icons.Default.Person
    )
    MaterialTheme {
        Scaffold(
            containerColor = Night,
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Night)
                        .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .clip(RoundedCornerShape(30.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xE82D4055),
                                        Color(0xE8172638),
                                        Color(0xE8293548)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(Color.White.copy(.34f), Accent.copy(.22f), Color.White.copy(.08f))
                                ),
                                shape = RoundedCornerShape(30.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEachIndexed { index, item ->
                            LiquidGlassNavButton(
                                label = item.first,
                                selected = selected == index,
                                onClick = { selected = index },
                                onSwipe = { direction ->
                                    selected = (index + direction).coerceIn(0, items.lastIndex)
                                },
                                icon = {
                                    Icon(
                                        item.second,
                                        contentDescription = item.first,
                                        tint = if (selected == index) Color.White else Color(0xFFB7C5D6),
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF142338), Night, Color(0xFF10131D))
                        )
                    )
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("VIBECODE", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
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
                    listOf("Твоя лента", "Сообщения", "Видео", "Интересное", "Профиль")[selected],
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                GlassPanel {
                    Text("Добро пожаловать в vibecodeGram", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
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
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            "ПЕРВЫЙ ЭТАП · ОСНОВА ПРИЛОЖЕНИЯ",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                GlassPanel {
                    Text("Что будет внутри", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.height(10.dp))
                    FeatureLine("Мессенджер", "Диалоги, группы, каналы и звонки")
                    FeatureLine("Социальная лента", "Публикации, истории, реакции и комментарии")
                    FeatureLine("Настройка", "Темы, жесты, интерфейс и расширения")
                    FeatureLine("Liquid Glass", "Полупрозрачные панели и глубина слоёв")
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassNavButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onSwipe: (Int) -> Unit,
    icon: @Composable () -> Unit
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    val animatedDragX by animateFloatAsState(
        targetValue = dragX,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 420f),
        label = "liquidDrag"
    )
    val stretch = (abs(animatedDragX) / 150f).coerceIn(0f, 0.13f)

    Column(
        modifier = Modifier
            .width(54.dp)
            .graphicsLayer {
                translationX = animatedDragX * 0.22f
                scaleX = 1f + stretch
                scaleY = 1f - stretch * 0.35f
            }
            .pointerInput(label) {
                detectDragGestures(
                    onDragEnd = {
                        val distance = dragX
                        dragX = 0f
                        if (abs(distance) > 34f) onSwipe(if (distance < 0f) 1 else -1)
                    },
                    onDragCancel = { dragX = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX = (dragX + amount.x).coerceIn(-76f, 76f)
                    }
                )
            }
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) Brush.verticalGradient(
                    listOf(Color(0xAA8BDFFF), Color(0x553B91C4), Color(0x33263E56))
                ) else Brush.verticalGradient(
                    listOf(Color(0x1FFFFFFF), Color(0x08000000))
                )
            )
            .border(
                1.dp,
                if (selected) Color.White.copy(.34f) else Color.White.copy(.09f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .background(
                    if (selected) Color.White.copy(.13f) else Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (selected) Color.White else Color(0xFFB7C5D6),
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun GlassPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Glass, RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) { content() }
}

@Composable
private fun FeatureLine(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(vertical = 7.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Medium)
        Text(subtitle, color = Color(0xFF9EADBF), fontSize = 12.sp)
    }
}
