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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackai.app.data.ChatMessage
import com.blackai.app.data.ConnectionState
import com.blackai.app.ui.ChatViewModel
import com.blackai.app.ui.theme.BorderSubtle
import com.blackai.app.ui.theme.Cyan
import com.blackai.app.ui.theme.ErrorRed
import com.blackai.app.ui.theme.Magenta
import com.blackai.app.ui.theme.SuccessGreen
import com.blackai.app.ui.theme.SurfaceElevated
import com.blackai.app.ui.theme.TextSecondary
import com.blackai.app.ui.theme.WarningAmber

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Шапка
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Cyan, Magenta))),
                contentAlignment = Alignment.Center
            ) {
                Text("B", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)) {
                Text(
                    "BlackAI",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                ConnectionBadge(connection)
            }

            IconButton(onClick = viewModel::clearChat) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Очистить чат",
                    tint = TextSecondary
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Настройки",
                    tint = TextSecondary
                )
            }
        }

        // Список сообщений
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item { EmptyChatHint() }
            } else {
                items(messages, key = { it.id }) { message: ChatMessage ->
                    MessageBubble(message)
                }
            }
        }

        // Поле ввода
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp)),
                placeholder = { Text("Сообщение...", color = TextSecondary) },
                maxLines = 5,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceElevated,
                    unfocusedContainerColor = SurfaceElevated,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = Cyan
                )
            )

            Box(
                modifier = Modifier.padding(start = 8.dp).size(50.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(Brush.linearGradient(listOf(ErrorRed, WarningAmber)))
                            .clickable { viewModel.stopGenerating() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Stop, "Стоп", tint = Color.White)
                    }
                } else {
                    val sendEnabled = inputText.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .then(
                                if (sendEnabled) {
                                    Modifier.background(Brush.linearGradient(listOf(Cyan, Magenta)))
                                } else {
                                    Modifier.background(SurfaceElevated)
                                }
                            )
                            .border(
                                width = if (sendEnabled) 0.dp else 1.dp,
                                color = if (sendEnabled) Color.Transparent else BorderSubtle,
                                shape = RoundedCornerShape(23.dp)
                            )
                            .clickable(enabled = sendEnabled) {
                                viewModel.send(inputText)
                                inputText = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить",
                            tint = if (sendEnabled) Color.Black else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/** Индикатор статуса соединения с ПК. */
@Composable
private fun ConnectionBadge(state: ConnectionState) {
    val (color, label) = when (state) {
        is ConnectionState.Online -> SuccessGreen to "на связи: ${state.model}"
        is ConnectionState.Checking -> WarningAmber to "проверка..."
        is ConnectionState.Offline -> ErrorRed to "нет связи с ПК"
        ConnectionState.Idle -> TextSecondary to "не проверялось"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}