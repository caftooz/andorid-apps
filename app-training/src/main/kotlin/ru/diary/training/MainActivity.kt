package ru.diary.training

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.theme.Palette
import ru.diary.training.ui.NoIntegration
import ru.diary.training.rest.AndroidRestController
import ru.diary.training.ui.TrainingRoot
import ru.diary.training.ui.TrainingViewModel

class MainActivity : ComponentActivity() {
    private val vm: TrainingViewModel by viewModels {
        TrainingViewModel.Factory(container.repository, container.settings, ru.diary.training.rest.AndroidRestController(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            DiaryTheme(settings.theme, Palette.Training, settings.dynamicColor) {
                TrainingRoot(vm, NoIntegration)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshToday()
    }
}
