package ru.diary.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Пункт нижнего меню: подпись и имя иконки из [DiaryIcons]. */
data class NavItem(val label: String, val icon: String)

/**
 * Нижнее меню: плавающая «таблетка». Выбранный пункт раскрывается в капсулу с подписью, остальные остаются иконками;
 * ширина и цвет меняются плавно. Размер касания каждого пункта не меньше 48dp.
 */
@Composable
fun DiaryNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
        shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp, shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, item ->
                val isSel = i == selected
                val weight by animateFloatAsState(if (isSel) 2.1f else 1f, spring(dampingRatio = 0.8f, stiffness = 380f), label = "navWeight")
                val bg by animateColorAsState(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest, tween(260), label = "navBg")
                val fg by animateColorAsState(if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, tween(260), label = "navFg")
                val src = remember { MutableInteractionSource() }
                Surface(
                    onClick = { onSelect(i) }, interactionSource = src, shape = CircleShape, color = bg,
                    modifier = Modifier.weight(weight).heightIn(min = 52.dp).pressScale(src, 0.92f).semantics {
                        role = Role.Tab; this.selected = isSel; contentDescription = item.label
                    },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(DiaryIcons.get(item.icon), null, tint = fg, modifier = Modifier.size(24.dp))
                        AnimatedVisibility(
                            isSel, enter = fadeIn(tween(220)) + expandHorizontally(tween(260)), exit = fadeOut(tween(100)) + shrinkHorizontally(tween(200)),
                        ) {
                            Text(item.label, Modifier.padding(start = 8.dp), color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
