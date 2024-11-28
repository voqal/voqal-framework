package dev.voqal.provider.clients

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import dev.voqal.assistant.VoqalResponse
import dev.voqal.provider.*
import dev.voqal.provider.clients.dropwizard.DropWizardMetricsProvider
import dev.voqal.services.VoqalConfigService
import dev.voqal.services.service

class AiProvidersClient(private val project: Project) : AiProvider {

    private val wakeProviders = mutableListOf<WakeProvider>()
    private val vadProviders = mutableListOf<VadProvider>()
    private val llmProviders = mutableListOf<LlmProvider>()
    private val sttProviders = mutableListOf<SttProvider>()
    private val ttsProviders = mutableListOf<TtsProvider>()
    private val assistantProviders = mutableListOf<AssistantProvider>()
    private val observabilityProviders = mutableListOf<ObservabilityProvider>()
    private val stmProviders = mutableListOf<StmProvider>()

    init {
        addObservabilityProvider(DropWizardMetricsProvider())
    }

    fun addWakeProvider(provider: WakeProvider) {
        wakeProviders.add(provider)
        Disposer.register(this, provider)
    }

    fun addVadProvider(provider: VadProvider) {
        vadProviders.add(provider)
        Disposer.register(this, provider)
    }

    fun addLlmProvider(provider: LlmProvider) {
        llmProviders.add(provider)
        Disposer.register(this, provider)
    }

    fun addSttProvider(provider: SttProvider) {
        sttProviders.add(provider)
        Disposer.register(this, provider)
    }

    fun addTtsProvider(provider: TtsProvider) {
        ttsProviders.add(provider)
        Disposer.register(this, provider)
    }

    fun addAssistantProvider(provider: AssistantProvider) {
        assistantProviders.add(provider)
        Disposer.register(this, provider)
    }

    fun addObservabilityProvider(provider: ObservabilityProvider) {
        observabilityProviders.add(provider)
        Disposer.register(this, provider)
    }

    fun addStmProvider(provider: StmProvider) {
        stmProviders.add(provider)
        Disposer.register(this, provider)
    }

    override fun isWakeProvider(): Boolean {
        return wakeProviders.any { it.isWakeProvider() }
    }

    override fun asWakeProvider(): WakeProvider {
        return wakeProviders.first { it.isWakeProvider() }.asWakeProvider()
    }

    override fun isVadProvider(): Boolean {
        return vadProviders.any { it.isVadProvider() }
    }

    override fun asVadProvider(): VadProvider {
        return vadProviders.first { it.isVadProvider() }.asVadProvider()
    }

    override fun isLlmProvider(): Boolean {
        return llmProviders.any { it.isLlmProvider() }
    }

    override fun asLlmProvider(): LlmProvider {
        return llmProviders.first { it.isLlmProvider() }.asLlmProvider()
    }

    override fun asLlmProvider(name: String): LlmProvider {
        if (name == "None") return llmProviders.first()
        return llmProviders.first { it.name == name }
    }

    override fun isSttProvider(): Boolean {
        return sttProviders.any { it.isSttProvider() }
    }

    override fun asSttProvider(): SttProvider {
        return sttProviders.first { it.isSttProvider() }.asSttProvider()
    }

    override fun isTtsProvider(): Boolean {
        return ttsProviders.any { it.isTtsProvider() }
    }

    override fun asTtsProvider(): TtsProvider {
        return ttsProviders.first { it.isTtsProvider() }.asTtsProvider()
    }

    override fun isAssistantProvider(): Boolean {
        return assistantProviders.any { it.isAssistantProvider() }
    }

    override fun asAssistantProvider(): AssistantProvider {
        return assistantProviders.first { it.isAssistantProvider() }.asAssistantProvider()
    }

    override fun isObservabilityProvider(): Boolean {
        return observabilityProviders.any { it.isObservabilityProvider() }
    }

    override fun asObservabilityProvider(): ObservabilityProvider {
        return object : ObservabilityProvider {
            override suspend fun log(
                request: ChatCompletionRequest,
                response: VoqalResponse,
                requestTime: Long,
                responseTime: Long,
                statusCode: Int,
                cacheId: String?
            ) {
                observabilityProviders.forEach {
                    it.log(request, response, requestTime, responseTime, statusCode, cacheId)
                }
            }

            override fun logSttLatency(durationMs: Long) {
                observabilityProviders.forEach {
                    it.logSttLatency(durationMs)
                }
            }

            override fun logSttCost(cost: Double) {
                observabilityProviders.forEach {
                    it.logSttCost(cost)
                }
            }

            override fun logTtsLatency(durationMs: Long) {
                observabilityProviders.forEach {
                    it.logTtsLatency(durationMs)
                }
            }

            override fun logTtsCost(cost: Double) {
                observabilityProviders.forEach {
                    it.logTtsCost(cost)
                }
            }

            override fun logLlmLatency(durationMs: Long) {
                observabilityProviders.forEach {
                    it.logLlmLatency(durationMs)
                }
            }

            override fun logLlmCost(cost: Double) {
                observabilityProviders.forEach {
                    it.logLlmCost(cost)
                }
            }

            override fun dispose() {
                observabilityProviders.forEach {
                    it.dispose()
                }
            }
        }
    }

    override fun isStmProvider(): Boolean {
        val promptSettings = project.service<VoqalConfigService>().getCurrentPromptSettings()
        return stmProviders
            .filter { it.name == promptSettings.languageModel || promptSettings.languageModel == "" }
            .any { it.isStmProvider() }
    }

    override fun asStmProvider(): StmProvider {
        val promptSettings = project.service<VoqalConfigService>().getCurrentPromptSettings()
        return stmProviders
            .filter { it.name == promptSettings.languageModel || promptSettings.languageModel == "" }
            .first { it.isStmProvider() }.asStmProvider()
    }

    override fun findProvider(name: String): AiProvider? {
        return llmProviders.find { it.name == name }
            ?: stmProviders.find { it.name == name }
    }

    fun hasNecessaryProviders(): Boolean {
        return llmProviders.isNotEmpty() ||
                sttProviders.isNotEmpty() ||
                ttsProviders.isNotEmpty() ||
                assistantProviders.isNotEmpty() ||
                observabilityProviders.isNotEmpty() ||
                stmProviders.isNotEmpty()
    }

    fun getObservabilityProviders(): List<ObservabilityProvider> {
        return observabilityProviders
    }

    override fun dispose() = Unit
}
