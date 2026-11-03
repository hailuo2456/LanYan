package com.lanyan.chat.data.remote

import android.util.Base64
import com.lanyan.chat.data.model.ApiSettings
import com.lanyan.chat.data.model.PromptMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

interface ModelGateway {
    suspend fun streamChat(
        settings: ApiSettings,
        messages: List<PromptMessage>,
        onDelta: (String) -> Unit,
    ): String

    suspend fun complete(settings: ApiSettings, messages: List<PromptMessage>, maxTokens: Int = 600): String
}

class OpenAiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : ModelGateway {

    override suspend fun streamChat(
        settings: ApiSettings,
        messages: List<PromptMessage>,
        onDelta: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val requestBody = ChatRequest(
            model = settings.model,
            messages = messages.toRequestMessages(),
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            stream = true,
        )
        val call = client.newCall(newRequest(settings, requestBody))
        coroutineContext.job.invokeOnCompletion { call.cancel() }
        call.execute().use { response ->
            if (!response.isSuccessful) throw apiError(response.code, response.body?.string())
            val body = response.body ?: throw IOException("模型服务返回了空响应")
            if (body.contentType()?.subtype?.contains("json", ignoreCase = true) == true) {
                val raw = body.string()
                val parsed = runCatching { json.decodeFromString<CompletionResponse>(raw) }.getOrElse {
                    throw IOException("无法解析模型响应：${it.message ?: "格式错误"}")
                }
                parsed.error?.let { throw IOException(it.message) }
                val text = parsed.choices.firstOrNull()?.message?.text().orEmpty()
                if (text.isNotEmpty()) onDelta(text)
                return@withContext text
            }
            val result = StringBuilder()
            body.source().use { source ->
                while (true) {
                    coroutineContext.ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    if (payload.isBlank()) continue
                    val chunk = runCatching { json.decodeFromString<StreamResponse>(payload) }.getOrElse {
                        throw IOException("无法解析流式响应：${it.message ?: "格式错误"}")
                    }
                    chunk.error?.let { throw IOException(it.message) }
                    val delta = chunk.choices.firstOrNull()?.delta?.content.orEmpty()
                    if (delta.isNotEmpty()) {
                        result.append(delta)
                        onDelta(delta)
                    }
                }
            }
            result.toString()
        }
    }

    override suspend fun complete(
        settings: ApiSettings,
        messages: List<PromptMessage>,
        maxTokens: Int,
    ): String = withContext(Dispatchers.IO) {
        val requestBody = ChatRequest(
            model = settings.model,
            messages = messages.toRequestMessages(),
            temperature = 0.2,
            maxTokens = maxTokens,
            stream = false,
        )
        val call = client.newCall(newRequest(settings, requestBody))
        coroutineContext.job.invokeOnCompletion { call.cancel() }
        call.execute().use { response ->
            if (!response.isSuccessful) throw apiError(response.code, response.body?.string())
            val raw = response.body?.string() ?: throw IOException("模型服务返回了空响应")
            val parsed = runCatching { json.decodeFromString<CompletionResponse>(raw) }.getOrElse {
                throw IOException("无法解析模型响应：${it.message ?: "格式错误"}")
            }
            parsed.error?.let { throw IOException(it.message) }
            parsed.choices.firstOrNull()?.message?.text()?.trim().orEmpty().ifBlank {
                throw IOException("模型没有返回文本内容")
            }
        }
    }

    private fun newRequest(settings: ApiSettings, body: ChatRequest): Request {
        require(settings.model.isNotBlank()) { "请先填写模型名称" }
        val builder = Request.Builder()
            .url(ApiEndpoint.chatCompletions(settings.baseUrl))
            .header("Accept", "text/event-stream, application/json")
            .header("Content-Type", "application/json")
            .post(json.encodeToString(ChatRequest.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
        if (settings.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${settings.apiKey.trim()}")
        }
        return builder.build()
    }

    private fun apiError(code: Int, rawBody: String?): IOException {
        val message = rawBody?.let { raw ->
            runCatching { json.decodeFromString<ApiErrorResponse>(raw).error?.message }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: rawBody?.take(300)?.takeIf { it.isNotBlank() } ?: "请检查 API 地址、密钥和模型名称"
        return IOException("请求失败（HTTP $code）：$message")
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean,
)

@Serializable
private data class ChatMessageDto(val role: String, val content: JsonElement) {
    fun text(): String = (content as? JsonPrimitive)?.contentOrNull.orEmpty()
}

/** 把带图片的消息转换成 OpenAI 兼容的多模态 content 数组。 */
private fun List<PromptMessage>.toRequestMessages(): List<ChatMessageDto> = map { message ->
    val imageUrls = message.images.mapNotNull { encodeImage(it) }
    if (imageUrls.isEmpty()) {
        ChatMessageDto(message.role.wireValue, JsonPrimitive(message.content))
    } else {
        ChatMessageDto(
            role = message.role.wireValue,
            content = buildJsonArray {
                addJsonObject {
                    put("type", "text")
                    put("text", message.content.ifBlank { "请描述这些图片。" })
                }
                imageUrls.forEach { dataUrl ->
                    addJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") { put("url", dataUrl) }
                    }
                }
            },
        )
    }
}

private fun encodeImage(path: String): String? {
    val file = File(path)
    if (!file.isFile) return null
    val mime = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }
    return runCatching {
        "data:$mime;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }.getOrNull()
}

@Serializable
private data class StreamResponse(
    val choices: List<StreamChoice> = emptyList(),
    val error: ApiError? = null,
)

@Serializable
private data class StreamChoice(val delta: Delta = Delta())

@Serializable
private data class Delta(val content: String? = null)

@Serializable
private data class CompletionResponse(
    val choices: List<CompletionChoice> = emptyList(),
    val error: ApiError? = null,
)

@Serializable
private data class CompletionChoice(val message: ChatMessageDto)

@Serializable
private data class ApiErrorResponse(val error: ApiError? = null)

@Serializable
private data class ApiError(val message: String)
