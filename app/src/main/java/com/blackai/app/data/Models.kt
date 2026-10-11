package com.blackai.app.data

/** Сообщение в чате. */
data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

enum class Role { USER, ASSISTANT, SYSTEM }

/** Статус соединения с ИИ-движком на ПК. */
sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Checking : ConnectionState
    data class Online(val model: String) : ConnectionState
    data class Offline(val reason: String) : ConnectionState
}