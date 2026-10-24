package ru.diary.nutrition

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.theme.Palette
import ru.diary.nutrition.ui.NutritionRoot
import ru.diary.nutrition.ui.NutritionViewModel

class MainActivity : ComponentActivity() {
    private val vm: NutritionViewModel by viewModels {
        NutritionViewModel.Factory(container.repository, container.settings, container.client, container.lookup)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Раз в 12 часов ищем новую версию на GitHub и скачиваем её; установку подтверждает пользователь.
        lifecycleScope.launch { container.updater.autoRun() }
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            DiaryTheme(settings.theme, Palette.Nutrition, settings.dynamicColor) { NutritionRoot(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshToday()
        vm.refreshLink()
    }
}
