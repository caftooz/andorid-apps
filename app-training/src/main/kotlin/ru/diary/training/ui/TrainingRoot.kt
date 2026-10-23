package ru.diary.training.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.diary.core.ui.DiaryIcons
import ru.diary.core.update.UpdatePrompt
import ru.diary.training.container

private data class Tab(val route: String, val label: String, val icon: String)

private val TABS = listOf(
    Tab("today", "Сегодня", "sun"),
    Tab("plan", "План", "calendar"),
    Tab("progress/train", "Прогресс", "trend"),
    Tab("settings", "Настройки", "gear"),
)

/** Корень интерфейса: идущая тренировка вытесняет вкладки, как в веб-версии. */
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
            val r = snackbar.showSnackbar(m.text, m.actionLabel, duration = androidx.compose.material3.SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) m.onAction?.invoke()
        }
    }
    val d = data
    if (!ready || d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    integration.Overlay()
    // Диалог обновления откладывается, пока идёт тренировка.
    UpdatePrompt(androidx.compose.ui.platform.LocalContext.current.container.updater, enabled = active == null)
    val a = active
    if (a != null) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
            Box(Modifier.padding(pad)) { WorkoutScreen(vm, d, a, today) }
        }
        return
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "today"
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                TABS.forEach { t ->
                    val selected = route.substringBefore('/').substringBefore('?') == t.route.substringBefore('/')
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTab(nav, t.route) },
                        icon = { Icon(DiaryIcons.get(t.icon), contentDescription = null) },
                        label = { Text(t.label) },
                        modifier = Modifier.padding(0.dp),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(
            nav, startDestination = "today", modifier = Modifier.padding(pad),
            // Между вкладками: «сквозное затухание» с лёгким масштабом. Вглубь экрана: сдвиг вбок.
            enterTransition = {
                if (isTabSwitch()) fadeIn(tween(260, delayMillis = 70)) + scaleIn(tween(260, delayMillis = 70), initialScale = 0.94f)
                else fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 10 }
            },
            exitTransition = {
                if (isTabSwitch()) fadeOut(tween(90)) else fadeOut(tween(140)) + slideOutHorizontally(tween(320)) { -it / 14 }
            },
            popEnterTransition = {
                if (isTabSwitch()) fadeIn(tween(260, delayMillis = 70)) + scaleIn(tween(260, delayMillis = 70), initialScale = 0.94f)
                else fadeIn(tween(260)) + slideInHorizontally(tween(320)) { -it / 14 }
            },
            popExitTransition = {
                if (isTabSwitch()) fadeOut(tween(90)) else fadeOut(tween(140)) + slideOutHorizontally(tween(320)) { it / 10 }
            },
        ) {
            composable("today") {
                TodayScreen(vm, d, today, onOpenTrack = { nav.navigate("today/track/$it") }, onGoto = { goto(nav, it) }, extraTiles = { integration.TodayTile() })
            }
            composable("today/track/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                TrackScreen(vm, d, today, e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onGoto = { goto(nav, it) })
            }
            planGraph(nav, vm, d, today)
            composable("progress/{tab}", listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "train" })) { e ->
                ProgressScreen(vm, d, today, e.arguments?.getString("tab") ?: "train", onGoto = { goto(nav, it) }, integration = integration)
            }
            composable("settings") { SettingsScreen(vm, d) }
        }
    }
}

/** Переход между корнями вкладок, а не вглубь одной вкладки. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    fun root(e: NavBackStackEntry) = e.destination.route?.substringBefore('/')?.substringBefore('?')
    return root(initialState) != root(targetState)
}

private fun navigateTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Переход по короткому адресу из карточек («plan/setup», «progress/max»). */
fun goto(nav: NavHostController, target: String) {
    if (target.startsWith("progress/") || target == "today") navigateTab(nav, target) else {
        navigateTab(nav, "plan")
        nav.navigate(target) { launchSingleTop = true }
    }
}
