package com.blackai.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blackai.app.data.AiClient
import com.blackai.app.data.AiSettings
import com.blackai.app.data.ChatMessage
import com.blackai.app.data.ConnectionState
import com.blackai.app.data.Role
import com.blackai.app.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    private val aiClient = AiClient()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _settings = MutableStateFlow(AiSettings())
    val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var sendJob: Job? = null

    init {
        viewModelScope.launch {
            repository.settings.collect { s ->
                _settings.value = s
            }
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            _connection.value = ConnectionState.Checking
            aiClient.checkConnection(_settings.value)
                .onSuccess { info ->
                    _connection.value = ConnectionState.Online(info.model)
                }
                .onFailure { e ->
                    _connection.value = ConnectionState.Offline(
                        e.message ?: "Не удалось подключиться"
                    )
                }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        val userMessage = ChatMessage(UUID.randomUUID().toString(), Role.USER, trimmed)
        val assistantId = UUID.randomUUID().toString()
        val history = _messages.value + userMessage

        _messages.value = history + ChatMessage(
            id = assistantId,
            role = Role.ASSISTANT,
            text = "",
            isStreaming = true
        )
        _isGenerating.value = true

        sendJob = viewModelScope.launch {
            var fullText = StringBuilder()
            try {
                aiClient.streamChat(_settings.value, history).collect { chunk ->
                    fullText.append(chunk)
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == assistantId) {
                            msg.copy(text = fullText.toString(), isStreaming = true)
                        } else msg
                    }
                }
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == assistantId) {
                        msg.copy(
                            text = fullText.toString().ifEmpty { "(пустой ответ)" },
                            isStreaming = false
                        )
                    } else msg
                }
            } catch (e: Exception) {
                val reason = e.message ?: "Ошибка соединения"
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == assistantId) {
                        msg.copy(
                            text = "⚠ $reason",
                            isStreaming = false,
                            isError = true
                        )
                    } else msg
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun stopGenerating() {
        sendJob?.cancel()
        aiClient.cancel()
        _isGenerating.value = false
        _messages.value = _messages.value.map { it.copy(isStreaming = false) }
    }

    fun clearChat() {
        stopGenerating()
        _messages.value = emptyList()
    }

    fun deleteMessage(id: String) {
        _messages.value = _messages.value.filterNot { it.id == id }
    }

    fun updateSettings(transform: (AiSettings) -> AiSettings) {
        viewModelScope.launch {
            repository.update(transform)
            checkConnection()
        }
    }

    suspend fun currentSettings(): AiSettings = repository.settings.first()
}