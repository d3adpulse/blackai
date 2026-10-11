package com.blackai.app

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackai.app.data.SettingsRepository
import com.blackai.app.ui.ChatViewModel
import com.blackai.app.ui.screens.ChatScreen
import com.blackai.app.ui.screens.SettingsScreen
import com.blackai.app.ui.theme.AppBackground
import com.blackai.app.ui.theme.BackgroundStyle
import com.blackai.app.ui.theme.BlackAiTheme

/** Одноэкземплярный репозиторий настроек на всё приложение. */
class BlackAiApp : Application() {
    val repository: SettingsRepository by lazy { SettingsRepository(dataStore) }
}

private val Context.dataStore by androidx.datastore.preferences.preferencesDataStore(
    name = "blackai_settings"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repository = (application as BlackAiApp).repository

        setContent {
            BlackAiTheme {
                BlackAiRoot(repository)
            }
        }
    }
}

@Composable
private fun BlackAiRoot(repository: SettingsRepository) {
    val viewModel: ChatViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(repository) as T
        }
    )

    var showSettings by remember { mutableStateOf(false) }
    var backgroundStyle by remember { mutableStateOf(BackgroundStyle.PURE_BLACK) }

    // При запуске сразу проверяем, жив ли ИИ-движок на ПК
    LaunchedEffect(Unit) {
        viewModel.checkConnection()
    }

    AppBackground(style = backgroundStyle) {
        AnimatedContent(
            targetState = showSettings,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
                } else {
                    fadeIn() togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "screen_switch"
        ) { isSettings ->
            if (isSettings) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { showSettings = false },
                    background = backgroundStyle,
                    onBackgroundChange = { backgroundStyle = it }
                )
            } else {
                ChatScreen(viewModel = viewModel, onOpenSettings = { showSettings = true })
            }
        }
    }
}