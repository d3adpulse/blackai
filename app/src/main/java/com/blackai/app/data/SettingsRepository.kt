package com.blackai.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Настройки подключения к ИИ-движку на ПК. */
data class AiSettings(
    val host: String = "192.168.1.7",
    val port: Int = 8000,
    val model: String = "blackai-local",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val systemPrompt: String = "Ты — BlackAI, ИИ-ассистент на телефоне пользователя. " +
        "Ты не ChatGPT, не Anthropic и не другие ассистенты. " +
        "Отвечай на русском языке, кратко и по делу.",
    val streamingEnabled: Boolean = true
) {
    val baseUrl: String get() = "http://$host:$port"
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "blackai_settings")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = stringPreferencesKey("port")
        val MODEL = stringPreferencesKey("model")
        val TEMPERATURE = stringPreferencesKey("temperature")
        val MAX_TOKENS = stringPreferencesKey("max_tokens")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val STREAMING = stringPreferencesKey("streaming")
    }

    val settings: Flow<AiSettings> = dataStore.data.map { prefs ->
        val defaults = AiSettings()
        AiSettings(
            host = prefs[Keys.HOST] ?: defaults.host,
            port = prefs[Keys.PORT]?.toIntOrNull() ?: defaults.port,
            model = prefs[Keys.MODEL] ?: defaults.model,
            temperature = prefs[Keys.TEMPERATURE]?.toFloatOrNull() ?: defaults.temperature,
            maxTokens = prefs[Keys.MAX_TOKENS]?.toIntOrNull() ?: defaults.maxTokens,
            systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: defaults.systemPrompt,
            streamingEnabled = prefs[Keys.STREAMING]?.toBooleanStrictOrNull() ?: defaults.streamingEnabled
        )
    }

    suspend fun update(transform: (AiSettings) -> AiSettings) {
        dataStore.edit { prefs ->
            val current = AiSettings(
                host = prefs[Keys.HOST] ?: AiSettings().host,
                port = prefs[Keys.PORT]?.toIntOrNull() ?: AiSettings().port,
                model = prefs[Keys.MODEL] ?: AiSettings().model,
                temperature = prefs[Keys.TEMPERATURE]?.toFloatOrNull() ?: AiSettings().temperature,
                maxTokens = prefs[Keys.MAX_TOKENS]?.toIntOrNull() ?: AiSettings().maxTokens,
                systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: AiSettings().systemPrompt,
                streamingEnabled = prefs[Keys.STREAMING]?.toBooleanStrictOrNull() ?: AiSettings().streamingEnabled
            )
            val updated = transform(current)
            prefs[Keys.HOST] = updated.host
            prefs[Keys.PORT] = updated.port.toString()
            prefs[Keys.MODEL] = updated.model
            prefs[Keys.TEMPERATURE] = updated.temperature.toString()
            prefs[Keys.MAX_TOKENS] = updated.maxTokens.toString()
            prefs[Keys.SYSTEM_PROMPT] = updated.systemPrompt
            prefs[Keys.STREAMING] = updated.streamingEnabled.toString()
        }
    }
}