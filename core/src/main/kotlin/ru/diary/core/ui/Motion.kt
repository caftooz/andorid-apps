package ru.diary.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * «Пружинящее» нажатие: элемент слегка уменьшается под пальцем и возвращается с лёгким отскоком.
 * Источник взаимодействия тот же, что у кнопки или карточки, поэтому ripple и масштаб работают вместе.
 */
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.96f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) pressedScale else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

/**
 * Плавное появление блока при показе экрана: прозрачность и сдвиг снизу с задержкой по номеру,
 * поэтому карточки выстраиваются каскадом, а не выскакивают разом.
 */
@Composable
fun Appear(index: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(minOf(index, 8) * 45L); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(380, easing = FastOutSlowInEasing), label = "appear")
    Box(modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 28.dp.toPx() }) { content() }
}

