package com.blackai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackai.app.data.AiSettings
import com.blackai.app.data.ConnectionState
import com.blackai.app.ui.ChatViewModel
import com.blackai.app.ui.theme.BorderSubtle
import com.blackai.app.ui.theme.Cyan
import com.blackai.app.ui.theme.ErrorRed
import com.blackai.app.ui.theme.Magenta
import com.blackai.app.ui.theme.SurfaceElevated
import com.blackai.app.ui.theme.SuccessGreen
import com.blackai.app.ui.theme.TextSecondary
import com.blackai.app.ui.theme.WarningAmber
import com.blackai.app.ui.theme.BackgroundStyle
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    background: BackgroundStyle,
    onBackgroundChange: (BackgroundStyle) -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()

    var portText by remember(settings.port) { mutableStateOf(settings.port.toString()) }
    var hostText by remember(settings.host) { mutableStateOf(settings.host) }
    var modelText by remember(settings.model) { mutableStateOf(settings.model) }
    var promptText by remember(settings.systemPrompt) { mutableStateOf(settings.systemPrompt) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Шапка настроек
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextSecondary)
            }
            Text(
                "Настройки",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- Статус подключения ----
            SettingsCard(title = "Соединение с ПК") {
                val conn = connection
                val (statusColor, statusText) = when (conn) {
                    is ConnectionState.Online -> SuccessGreen to "Подключено: ${conn.model}"
                    is ConnectionState.Checking -> WarningAmber to "Проверка соединения..."
                    is ConnectionState.Offline -> ErrorRed to conn.reason
                    ConnectionState.Idle -> TextSecondary to "Соединение не проверялось"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(statusColor)
                    )
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Button(
                    onClick = { viewModel.checkConnection() },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Проверить подключение", color = Cyan)
                }
            }

            // ---- Параметры модели ----
            SettingsCard(title = "ИИ-движок на ПК") {
                StyledField(
                    value = hostText,
                    onValueChange = { hostText = it },
                    label = "IP-адрес ПК (например 192.168.1.5)"
                )
                StyledField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                    label = "Порт (8000)"
                )
                StyledField(
                    value = modelText,
                    onValueChange = { modelText = it },
                    label = "Имя модели"
                )
            }

            // ---- Параметры генерации ----
            SettingsCard(title = "Генерация") {
                Text(
                    "Температура: ${"%.2f".format(settings.temperature)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Slider(
                    value = settings.temperature,
                    onValueChange = { v ->
                        viewModel.updateSettings { it.copy(temperature = (v * 100).roundToInt() / 100f) }
                    },
                    valueRange = 0f..2f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Cyan,
                        activeTrackColor = Cyan,
                        inactiveTrackColor = SurfaceElevated
                    )
                )

                Text(
                    "Максимум токенов: ${settings.maxTokens}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Slider(
                    value = settings.maxTokens.toFloat(),
                    onValueChange = { v ->
                        viewModel.updateSettings { it.copy(maxTokens = (v / 64).roundToInt() * 64) }
                    },
                    valueRange = 64f..4096f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Magenta,
                        activeTrackColor = Magenta,
                        inactiveTrackColor = SurfaceElevated
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Потоковый вывод",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.streamingEnabled,
                        onCheckedChange = { v ->
                            viewModel.updateSettings { it.copy(streamingEnabled = v) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Cyan,
                            uncheckedTrackColor = SurfaceElevated
                        )
                    )
                }
            }

            // ---- Фон ----
            SettingsCard(title = "Фон приложения") {
                BackgroundStyle.entries.forEach { style ->
                    val isSelected = style == background
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) Cyan.copy(alpha = 0.12f) else Color.Transparent
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) Cyan else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onBackgroundChange(style) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    com.blackai.app.ui.theme.BackgroundBrush(style)
                                )
                                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                        )
                        Text(
                            style.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) Cyan else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            // ---- Системный промпт ----
            SettingsCard(title = "Системный промпт") {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceElevated,
                        unfocusedContainerColor = SurfaceElevated,
                        focusedIndicatorColor = Cyan,
                        unfocusedIndicatorColor = BorderSubtle,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = Cyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Button(
                onClick = {
                    viewModel.updateSettings {
                        it.copy(
                            host = hostText.trim(),
                            port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: 8000,
                            model = modelText.trim().ifEmpty { "blackai-local" },
                            systemPrompt = promptText
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Сохранить и проверить", color = Cyan)
            }

            Text(
                text = "BlackAI 1.0 • ИИ-движок: Python на вашем ПК",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** Карточка-секция в настройках. */
@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceElevated.copy(alpha = 0.55f))
            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Magenta,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        content()
    }
}

/** Текстовое поле в стиле приложения. */
@Composable
private fun StyledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        label = { Text(label, color = TextSecondary) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceElevated.copy(alpha = 0.4f),
            unfocusedContainerColor = SurfaceElevated.copy(alpha = 0.4f),
            focusedIndicatorColor = Cyan,
            unfocusedIndicatorColor = BorderSubtle,
            focusedLabelColor = Cyan,
            unfocusedLabelColor = TextSecondary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = Cyan
        ),
        shape = RoundedCornerShape(12.dp)
    )
}