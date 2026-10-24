package ru.diary.training.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import ru.diary.core.ui.DiaryBackdrop
import ru.diary.core.ui.DiaryNavBar
import androidx.compose.ui.graphics.Color
import ru.diary.core.ui.NavItem
import ru.diary.core.update.UpdatePrompt
import ru.diary.training.container

private val TABS = listOf(
    NavItem("Сегодня", "sun"),
    NavItem("План", "calendar"),
    NavItem("Прогресс", "trend"),
    NavItem("Настройки", "sliders"),
)

private const val MAIN = "main"

/**
 * Корень интерфейса. Идущая тренировка вытесняет вкладки, как в веб-версии.
 * Четыре вкладки листаются свайпом влево и вправо и переключаются нижним меню; вложенные экраны
 * (редакторы плана, домашнее занятие) открываются поверх с переходом вбок, и нижнее меню на них скрывается.
 */
@Composable
fun TrainingRoot(vm: TrainingViewModel, integration: LiveIntegration) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(m.text, m.actionLabel, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) m.onAction?.invoke()
        }
    }
    val d = data
    if (!ready || d == null) {
        DiaryBackdrop(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    integration.Overlay()
    // Диалог обновления откладывается, пока идёт тренировка.
    UpdatePrompt(LocalContext.current.container.updater, enabled = active == null)
    val a = active
    if (a != null) {
        DiaryBackdrop {
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { pad ->
                Box(Modifier.padding(pad)) { WorkoutScreen(vm, d, a, today) }
            }
        }
        return
    }

    val nav = rememberNavController()
    val pager = rememberPagerState { TABS.size }
    val scope = rememberCoroutineScope()
    var progressTab by rememberSaveable { mutableStateOf("train") }
    val entry by nav.currentBackStackEntryAsState()
    val onMain = (entry?.destination?.route ?: MAIN) == MAIN

    /** Переход по короткому адресу из карточек («plan/setup», «progress/max»). */
    fun go(target: String) {
        when {
            target.startsWith("progress/") -> { progressTab = target.removePrefix("progress/"); scope.launch { pager.animateScrollToPage(2) } }
            target == "today" -> scope.launch { pager.animateScrollToPage(0) }
            else -> nav.navigate(target) { launchSingleTop = true }
        }
    }

    DiaryBackdrop { Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent,
        bottomBar = {
            AnimatedVisibility(onMain, enter = slideInVertically(tween(260)) { it } + fadeIn(tween(260)), exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(120))) {
                DiaryNavBar(TABS, pager.currentPage, { i -> scope.launch { pager.animateScrollToPage(i) } })
            }
        },
    ) { pad ->
        NavHost(
            nav, startDestination = MAIN, modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 10 } },
            exitTransition = { fadeOut(tween(140)) + slideOutHorizontally(tween(320)) { -it / 14 } },
            popEnterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(320)) { -it / 14 } },
            popExitTransition = { fadeOut(tween(140)) + slideOutHorizontally(tween(320)) { it / 10 } },
        ) {
            composable(MAIN) {
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    when (page) {
                        0 -> TodayScreen(vm, d, today, onOpenTrack = { nav.navigate("today/track/$it") }, onGoto = ::go, extraTiles = { integration.TodayTile() })
                        1 -> PlanOverview(vm, d, today) { nav.navigate(it) }
                        2 -> ProgressScreen(vm, d, today, progressTab, onGoto = ::go, integration = integration)
                        else -> SettingsScreen(vm, d)
                    }
                }
            }
            composable("today/track/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                TrackScreen(vm, d, today, e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onGoto = ::go)
            }
            planGraph(nav, vm, d, today)
        }
    } }
}
