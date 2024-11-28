package dev.voqal.provider

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.intellij.openapi.project.Project
import dev.voqal.assistant.VoqalResponse
import dev.voqal.services.scope
import kotlinx.coroutines.launch

/**
 * Provider that offers LLM observability.
 */
interface ObservabilityProvider : AiProvider {
    override fun isObservabilityProvider() = true

    suspend fun log(
        request: ChatCompletionRequest,
        response: VoqalResponse,
        requestTime: Long,
        responseTime: Long,
        statusCode: Int = 200,
        cacheId: String? = null
    )

    fun asyncLog(
        project: Project,
        request: ChatCompletionRequest,
        response: VoqalResponse,
        requestTime: Long,
        responseTime: Long,
        statusCode: Int = 200,
        cacheId: String? = null
    ) {
        project.scope.launch {
            log(request, response, requestTime, responseTime, statusCode, cacheId)
        }
    }

    //todo: MetricsProvider?
    fun logSttLatency(durationMs: Long) = Unit
    fun logSttCost(cost: Double) = Unit
    fun logTtsLatency(durationMs: Long) = Unit
    fun logTtsCost(cost: Double) = Unit
    fun logLlmLatency(durationMs: Long) = Unit
    fun logLlmCost(cost: Double) = Unit
}
