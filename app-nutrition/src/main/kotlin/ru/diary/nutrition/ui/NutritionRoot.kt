package ru.diary.nutrition.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.diary.core.ui.DiaryIcons
import ru.diary.core.update.UpdatePrompt
import ru.diary.nutrition.container

private val TABS = listOf("dishes" to ("Блюда" to "dish"), "diary" to ("Дневник" to "book"), "week" to ("Неделя" to "chart"), "data" to ("Настройки" to "gear"))

/** Корень «Рациона»: четыре вкладки, как в веб-версии. */
@Composable
fun NutritionRoot(vm: NutritionViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("dishes") }
    var date by rememberSaveable { mutableStateOf(today) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(m.text, m.actionLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) m.onAction?.invoke()
        }
    }
    UpdatePrompt(androidx.compose.ui.platform.LocalContext.current.container.updater)
    val d = data
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                TABS.forEach { (id, v) ->
                    NavigationBarItem(selected = tab == id, onClick = { tab = id }, icon = { Icon(DiaryIcons.get(v.second), null) }, label = { Text(v.first) })
                }
            }
        },
    ) { pad ->
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else Crossfade(tab, Modifier.padding(pad), animationSpec = tween(220), label = "tab") { t ->
            when (t) {
                "diary" -> DiaryScreen(vm, d, today, date, { date = it }, link)
                "week" -> WeekScreen(d, today, link)
                "data" -> SettingsScreen(vm, link)
                else -> DishesScreen(vm, d, today, date) { date = it }
            }
        }
    }
}
