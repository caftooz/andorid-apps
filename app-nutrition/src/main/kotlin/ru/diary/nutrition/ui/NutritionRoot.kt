package ru.diary.nutrition.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.diary.core.ui.DiaryBackdrop
import ru.diary.core.ui.DiaryNavBar
import androidx.compose.ui.graphics.Color
import ru.diary.core.ui.NavItem
import ru.diary.core.update.UpdatePrompt
import ru.diary.nutrition.container

private val TABS = listOf(NavItem("Блюда", "dish"), NavItem("Дневник", "book"), NavItem("Неделя", "chart"), NavItem("Настройки", "sliders"))

/** Корень «Рациона»: четыре вкладки, листаются свайпом и переключаются нижним меню. */
@Composable
fun NutritionRoot(vm: NutritionViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()
    var date by rememberSaveable { mutableStateOf(today) }
    val pager = rememberPagerState { TABS.size }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(m.text, m.actionLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) m.onAction?.invoke()
        }
    }
    UpdatePrompt(LocalContext.current.container.updater)
    val d = data
    DiaryBackdrop { Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent,
        bottomBar = { DiaryNavBar(TABS, pager.currentPage, { i -> scope.launch { pager.animateScrollToPage(i) } }) },
    ) { pad ->
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else HorizontalPager(pager, Modifier.fillMaxSize().padding(pad), beyondViewportPageCount = 1) { page ->
            when (page) {
                1 -> DiaryScreen(vm, d, today, date, { date = it }, link)
                2 -> WeekScreen(d, today, link)
                3 -> SettingsScreen(vm, link)
                else -> DishesScreen(vm, d, today, date) { date = it }
            }
        }
    } }
}
