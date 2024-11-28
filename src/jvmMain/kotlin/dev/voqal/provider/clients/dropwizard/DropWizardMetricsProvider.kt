package dev.voqal.provider.clients.dropwizard

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Snapshot
import dev.voqal.assistant.VoqalResponse
import dev.voqal.provider.ObservabilityProvider
import java.util.concurrent.ConcurrentLinkedDeque

class DropWizardMetricsProvider : ObservabilityProvider {

    private val metrics = MetricRegistry()
    private val sttLatencyLog = ConcurrentLinkedDeque<Long>()
    private val ttsLatencyLog = ConcurrentLinkedDeque<Long>()
    private val llmLatencyLog = ConcurrentLinkedDeque<Long>()
    private var sttCost: Double = 0.0
    private var ttsCost: Double = 0.0
    private var llmCost: Double = 0.0
    private var stmCost: Double = 0.0

    private fun updateLog(log: ConcurrentLinkedDeque<Long>, value: Long) {
        if (log.size >= 100) {
            log.pollFirst()
        }
        log.addLast(value)
    }

    override fun logSttLatency(durationMs: Long) {
        metrics.timer("stt-latency").update(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        updateLog(sttLatencyLog, durationMs)
    }

    fun getSttLatency(): Snapshot {
        return metrics.timer("stt-latency").snapshot
    }

    override fun logSttCost(cost: Double) {
        sttCost += cost
    }

    fun getSttCost(): Double {
        return sttCost
    }

    fun getSttLatencyLog(): List<Long> {
        return sttLatencyLog.toList()
    }

    override fun logTtsLatency(durationMs: Long) {
        metrics.timer("tts-latency").update(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        updateLog(ttsLatencyLog, durationMs)
    }

    fun getTtsLatency(): Snapshot {
        return metrics.timer("tts-latency").snapshot
    }

    override fun logTtsCost(cost: Double) {
        ttsCost += cost
    }

    fun getTtsCost(): Double {
        return ttsCost
    }

    fun getTtsLatencyLog(): List<Long> {
        return ttsLatencyLog.toList()
    }

    override fun logLlmLatency(durationMs: Long) {
        metrics.timer("llm-latency").update(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        updateLog(llmLatencyLog, durationMs)
    }

    fun getLlmLatency(): Snapshot {
        return metrics.timer("llm-latency").snapshot
    }

    override fun logLlmCost(cost: Double) {
        llmCost += cost
    }

    fun getLlmCost(): Double {
        return llmCost
    }

    fun getLlmLatencyLog(): List<Long> {
        return llmLatencyLog.toList()
    }

    override fun logStmLatency(durationMs: Long) {
        metrics.timer("stm-latency").update(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    override fun logStmCost(cost: Double) {
        stmCost += cost
    }

    fun getStmLatency(): Snapshot {
        return metrics.timer("stm-latency").snapshot
    }

    fun getStmCost(): Double {
        return stmCost
    }

    override suspend fun log(
        request: ChatCompletionRequest,
        response: VoqalResponse,
        requestTime: Long,
        responseTime: Long,
        statusCode: Int,
        cacheId: String?
    ) = Unit

    override fun dispose() = Unit
}
