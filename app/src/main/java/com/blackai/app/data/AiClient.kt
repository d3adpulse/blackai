package com.blackai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Клиент к ИИ-движку BlackAI, запущенному на ПК.
 * Протокол: обычный HTTP + OpenAI-совместимый формат сообщений.
 * Поддерживает потоковую выдачу (streaming) через chunked-ответ.
 */
class AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private var activeCall: Call? = null

    /** Проверка доступности движка на ПК. */
    suspend fun checkConnection(settings: AiSettings): Result<HealthInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${settings.baseUrl}/health")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    HealthInfo(
                        status = json.optString("status", "ok"),
                        model = json.optString("model", "unknown"),
                        engine = json.optString("engine", "unknown"),
                        device = json.optString("device", "")
                    )
                }
            }
        }

    /**
     * Отправляет сообщение и возвращает поток кусочков ответа.
     * Если streaming выключен - придёт один чанк с полным текстом.
     */
    fun streamChat(
        settings: AiSettings,
        history: List<ChatMessage>
    ): Flow<String> = flow {
        val payload = buildRequestPayload(settings, history)

        val request = Request.Builder()
            .url("${settings.baseUrl}/chat")
            .post(payload.toRequestBody(jsonMedia))
            .header("Accept", if (settings.streamingEnabled) "text/event-stream" else "application/json")
            .build()

        val call = client.newCall(request)
        activeCall = call

        val response = suspendCoroutine { cont ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }
            })
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw IOException("Сервер вернул HTTP ${resp.code}: ${err.take(300)}")
            }

            val body = resp.body ?: throw IOException("Пустой ответ сервера")
            val source = body.source()
            val contentType = resp.header("Content-Type").orEmpty()

            // Потоковый режим: text/event-stream, строки вида "data: {...}"
            if (settings.streamingEnabled && contentType.contains("event-stream")) {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data:")) {
                        val payloadChunk = line.removePrefix("data:").trim()
                        if (payloadChunk.isEmpty() || payloadChunk == "[DONE]") continue
                        extractDelta(payloadChunk)?.takeIf { it.isNotEmpty() }?.let { emit(it) }
                    }
                }
            } else {
                // Обычный JSON-ответ целиком
                val full = source.readUtf8()
                extractContent(full)?.let { emit(it) }
            }
        }
        activeCall = null
    }.flowOn(Dispatchers.IO)

    /** Прерывает текущий запрос. */
    fun cancel() {
        activeCall?.cancel()
        activeCall = null
    }

    private fun buildRequestPayload(settings: AiSettings, history: List<ChatMessage>): String {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", settings.systemPrompt)
        })
        history.forEach { msg ->
            messages.put(JSONObject().apply {
                put("role", if (msg.role == Role.USER) "user" else "assistant")
                put("content", msg.text)
            })
        }
        return JSONObject().apply {
            put("model", settings.model)
            put("messages", messages)
            put("temperature", settings.temperature.toDouble())
            put("max_tokens", settings.maxTokens)
            put("stream", settings.streamingEnabled)
        }.toString()
    }

    private fun extractDelta(chunk: String): String? = runCatching {
        val json = JSONObject(chunk)
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            choices.optJSONObject(0)?.optJSONObject("delta")?.optString("content")
        } else {
            json.optString("content").takeIf { it.isNotEmpty() }
        }
    }.getOrNull()

    private fun extractContent(body: String): String? = runCatching {
        val json = JSONObject(body)
        val choices = json.optJSONArray("choices")
        when {
            choices != null && choices.length() > 0 ->
                choices.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            else -> json.optString("content").takeIf { it.isNotEmpty() }
        }
    }.getOrNull()

    data class HealthInfo(val status: String, val model: String, val engine: String, val device: String)
}