package dev.voqal.provider.clients.cerebras

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.exception.*
import com.intellij.openapi.project.Project
import dev.voqal.assistant.VoqalDirective
import dev.voqal.provider.LlmProvider
import dev.voqal.services.getVoqalLogger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class CerebrasClient(
    override val name: String,
    project: Project,
    private val providerKey: String
) : LlmProvider {

    companion object {
        const val DEFAULT_MODEL = "llama3.1-70b"

        @JvmStatic
        val MODELS = listOf(
            "llama3.1-70b",
            "llama3.1-8b"
        )

        @JvmStatic
        fun getTokenLimit(modelName: String): Int {
            return when {
                modelName.startsWith("llama3.1") -> 8192
                else -> -1
            }
        }
    }

    private val log = project.getVoqalLogger(this::class)
    private val jsonDecoder = Json { ignoreUnknownKeys = true }
    private val client = HttpClient {
        install(ContentNegotiation) { json(jsonDecoder) }
        install(HttpTimeout) { requestTimeoutMillis = 30_000 }
    }
    private val providerUrl = "https://api.cerebras.ai/v1/chat/completions"

    override suspend fun chatCompletion(request: ChatCompletionRequest, directive: VoqalDirective?): ChatCompletion {
        val requestJson = toRequestJson(request)

        val response = try {
            client.post(providerUrl) {
                header("Content-Type", "application/json")
                header("Accept", "application/json")
                header("Authorization", "Bearer $providerKey")
                setBody(requestJson.encode())
            }
        } catch (e: HttpRequestTimeoutException) {
            throw OpenAITimeoutException(e)
        }
        val roundTripTime = response.responseTime.timestamp - response.requestTime.timestamp
        log.debug("Cerebras response status: ${response.status} in $roundTripTime ms")

        throwIfError(response)
        val completion = response.body<ChatCompletion>()
        log.debug("Completion: $completion")
        return completion
    }

    override suspend fun streamChatCompletion(
        request: ChatCompletionRequest,
        directive: VoqalDirective?
    ): Flow<ChatCompletionChunk> = flow {
        val requestJson = toRequestJson(request).put("stream", true)

        try {
            client.preparePost(providerUrl) {
                header("Content-Type", "application/json")
                header("Accept", "application/json")
                header("Authorization", "Bearer $providerKey")
                setBody(requestJson.encode())
            }.execute { response ->
                throwIfError(response)

                var hasError = false
                val channel: ByteReadChannel = response.body()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line()?.takeUnless { it.isEmpty() } ?: continue

                    if (line == "event: error") {
                        hasError = true
                        continue
                    } else if (hasError) {
                        val errorJson = JsonObject(line.substringAfter("data: "))
                        log.warn("Received error while streaming completions: $errorJson")

                        val statusCode = errorJson.getJsonObject("error").getInteger("status_code")
                        throw UnknownAPIException(statusCode, jsonDecoder.decodeFromString(errorJson.toString()))
                    }

                    val chunkJson = line.substringAfter("data: ")
                    if (chunkJson != "[DONE]") {
                        emit(jsonDecoder.decodeFromString(chunkJson))
                    }
                }
            }
        } catch (e: HttpRequestTimeoutException) {
            throw OpenAITimeoutException(e)
        }
    }

    private fun toRequestJson(request: ChatCompletionRequest): JsonObject {
        val requestJson = JsonObject()
            .put("model", request.model.id)
            .put("messages", JsonArray(request.messages.map { it.toJson() }))
        return requestJson
    }

    private suspend fun throwIfError(response: HttpResponse) {
        if (response.status.isSuccess()) return

        val response = response.call.save().response
        val responseBody = response.bodyAsText()
        if (response.status.value == 401) {
            throw AuthenticationException(
                response.status.value,
                OpenAIError(
                    OpenAIErrorDetails(
                        code = null,
                        message = "Unauthorized access to Cerebras. Please check your API key and try again.",
                        param = null,
                        type = null
                    )
                ),
                ClientRequestException(response, responseBody)
            )
        }

        val error = jsonDecoder.decodeFromString<OpenAIError>(responseBody)
        throw InvalidRequestException(
            response.status.value,
            OpenAIError(OpenAIErrorDetails(message = error.detail?.message)),
            ClientRequestException(response, responseBody)
        )
    }

    override fun isStreamable() = true
    override fun getAvailableModelNames() = MODELS
    override fun dispose() = client.close()
    private fun ChatMessage.toJson() = JsonObject().put("role", role.role.lowercase()).put("content", content)
}
