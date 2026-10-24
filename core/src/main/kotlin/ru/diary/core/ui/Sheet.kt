package ru.diary.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.diary.core.theme.DiaryTheme

/**
 * Всплывающая панель снизу: формы и подробности открываются поверх экрана, а не раскрываются в нём.
 * Закрывается свайпом вниз, касанием затемнения, кнопкой «Назад» или из содержимого через `close()` —
 * всегда с анимацией. Содержимое прокручивается и поднимается над клавиатурой.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiarySheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    icon: String? = null,
    iconColor: Color? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { if (!state.isVisible) onDismiss() } }
    val x = DiaryTheme.extras
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = cs.surfaceContainerLow, contentColor = cs.onSurface, tonalElevation = 0.dp,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 6.dp).size(width = 44.dp, height = 5.dp).background(x.buttonBrush, CircleShape))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (icon != null) IconBadge(icon, iconColor ?: cs.primary)
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        if (subtitle != null) Muted(subtitle)
                    }
                }
            }
            content(close)
        }
    }
}

/**
 * Компактная строка списка со стрелкой: краткое описание, а подробности открываются во всплывающей панели.
 * Слева — значок или номер, справа — необязательное значение.
 */
@Composable
fun SheetRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: String? = null,
) {
    val src = remember { MutableInteractionSource() }
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, interactionSource = src, shape = RoundedCornerShape(20.dp), color = DiaryTheme.extras.glass,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp).pressScale(src, 0.97f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) Muted(subtitle)
            }
            if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = cs.primary)
            Text("›", style = MaterialTheme.typography.titleLarge, color = cs.primary)
        }
    }
}

/** Номер в кружке для [SheetRow]. */
@Composable
fun NumberBadge(n: Int) {
    val x = DiaryTheme.extras
    Box(Modifier.size(34.dp).background(x.buttonBrush, CircleShape), contentAlignment = Alignment.Center) {
        Text("$n", style = MaterialTheme.typography.labelLarge, color = x.onButton)
    }
}
