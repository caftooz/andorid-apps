package ru.diary.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.diary.core.ui.DiarySheet
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.QuietButton
import ru.diary.training.domain.Guides

/** Ссылка «Как делать ›» под шагом или упражнением, если для него есть инструкция. */
@Composable
fun GuideLink(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Как делать ›") {
    QuietButton(label, onClick, modifier)
}

/** Всплывающая панель с подробной инструкцией. */
@Composable
fun GuideSheet(title: String, text: String, icon: String = "star", onDismiss: () -> Unit) {
    DiarySheet(onDismiss, title = title, icon = icon) { close ->
        Guides.parse(text).forEach { b ->
            when (b) {
                is Guides.Block.Heading -> Text(
                    b.text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                )
                is Guides.Block.Para -> Text(b.text, style = MaterialTheme.typography.bodyMedium)
                is Guides.Block.Item -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(b.mark, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(min = 18.dp))
                    Text(b.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
        PrimaryButton("Понятно", close, Modifier.padding(top = 4.dp))
    }
}
