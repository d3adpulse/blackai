package com.blackai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blackai.app.data.ChatMessage
import com.blackai.app.data.Role
import com.blackai.app.ui.theme.Cyan
import com.blackai.app.ui.theme.CyanDark
import com.blackai.app.ui.theme.ErrorRed
import com.blackai.app.ui.theme.Magenta
import com.blackai.app.ui.theme.SurfaceElevated
import com.blackai.app.ui.theme.TextSecondary

/** Одна "пузырьковая" реплика в чате. */
@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == Role.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Cyan, Magenta))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = if (isUser) "Ты" else "BlackAI",
                style = MaterialTheme.typography.labelSmall,
                color = if (isUser) Cyan else Magenta,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (message.isError) ErrorRed.copy(alpha = 0.18f) else SurfaceElevated,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(
                    text = if (message.text.isEmpty() && message.isStreaming) {
                        "печатает..."
                    } else {
                        message.text
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isError) ErrorRed else MaterialTheme.colorScheme.onSurface,
                    textAlign = if (isUser) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        if (isUser) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(CyanDark, Cyan))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** Заглушка, когда диалог пуст. */
@Composable
fun EmptyChatHint() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Cyan, Magenta))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "B",
                style = MaterialTheme.typography.titleLarge,
                color = Color.Black
            )
        }
        Text(
            text = "BlackAI",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "ИИ-движок работает на твоём ПК.\nНапиши сообщение, чтобы начать.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Индикатор "ИИ печатает" - три пульсирующие точки. */
@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (index == 1) Magenta else Cyan)
            )
        }
    }
}

@Composable
fun SendIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.Send,
        contentDescription = "Отправить"
    )
}