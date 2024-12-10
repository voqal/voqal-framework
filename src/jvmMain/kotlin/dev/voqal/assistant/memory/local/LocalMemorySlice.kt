package dev.voqal.assistant.memory.local

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.exception.OpenAIAPIException
import com.aallam.openai.api.model.ModelId
import com.funnysaltyfish.partialjsonparser.PartialJsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import dev.voqal.assistant.VoqalDirective
import dev.voqal.assistant.VoqalResponse
import dev.voqal.assistant.memory.MemorySlice
import dev.voqal.assistant.processing.ResponseParser
import dev.voqal.assistant.tool.ContextUpdate
import dev.voqal.config.settings.PromptSettings
import dev.voqal.config.settings.PromptSettings.FunctionCalling
import dev.voqal.provider.LlmProvider
import dev.voqal.services.*
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import java.util.*

/**
 * Holds chat messages with the LLM in local memory.
 */
class LocalMemorySlice(
    private val project: Project
) : MemorySlice {

    companion object {
        //todo: better
        val partialContextListeners = mutableMapOf<String, suspend (ContextUpdate) -> Unit>()
    }

    override val id = UUID.randomUUID().toString()

    @VisibleForTesting
    val messageList = mutableListOf<ChatMessage>()

    private val log = project.getVoqalLogger(this::class)

    override suspend fun addMessage(
        directive: VoqalDirective,
        addMessage: Boolean
    ): VoqalResponse {
        val promptSettings = directive.assistant.promptSettings
            ?: throw IllegalStateException("Prompt settings not found")
        val configService = project.service<VoqalConfigService>()
        val config = configService.getConfig()

        var lmSettings = directive.getLanguageModelSettings()
        if (promptSettings.languageModel.isNotBlank()) {
            val settings = config.languageModelsSettings.models.firstOrNull {
                it.name == promptSettings.languageModel
            }
            if (settings != null) {
                lmSettings = settings
            }
        }

        val systemPrompt = directive.toMarkdown()
        if (log.isTraceEnabled) log.trace("${promptSettings.promptName}: $systemPrompt")

        if (promptSettings.promptName == "Edit Mode" || promptSettings.editMode) {
            project.service<VoqalStatusService>().updateText("Querying AI provider: ${lmSettings.name}")
        }
        var includeToolsInMarkdown = promptSettings.functionCalling == FunctionCalling.MARKDOWN
        if (promptSettings.promptName == "Edit Mode" || promptSettings.editMode) {
            includeToolsInMarkdown = true //todo: edit mode doesn't support function calls
        }

        val aiProvider = configService.getAiProvider()
        val llmProvider = aiProvider.asLlmProvider(lmSettings.name)
        val streamingEnabled = promptSettings.streamCompletions && llmProvider.isStreamable()

        val request = if (messageList.isEmpty()) {
            if (addMessage) {
                messageList.add(ChatMessage(ChatRole.System, systemPrompt))
                if (promptSettings.separateInitialUserMessage) {
                    if (directive.transcription.isNotEmpty()) {
                        messageList.add(ChatMessage(ChatRole.User, directive.transcription))
                    } else {
                        log.debug { "No user transcription added" }
                    }
                }
            } else {
                log.debug("No message added")
            }
            if (includeToolsInMarkdown) {
                ChatCompletionRequest(
                    model = ModelId(lmSettings.modelName),
                    messages = getMessages(),
                    responseFormat = ChatResponseFormat.Text,
                    seed = lmSettings.seed,
                    temperature = lmSettings.temperature,
                    streamOptions = if (streamingEnabled) {
                        StreamOptions(includeUsage = true)
                    } else null
                )
            } else {
                val requestTools = directive.assistant.availableActions
                    .filter { it.isVisible(directive) }
                    .map {
                        if (directive.assistant.directiveMode && !it.supportsDirectiveMode()) {
                            it.asTool(directive).asDirectiveTool()
                        } else {
                            it.asTool(directive)
                        }
                    }.takeIf { it.isNotEmpty() }
                ChatCompletionRequest(
                    model = ModelId(lmSettings.modelName),
                    messages = getMessages(),
                    tools = requestTools,
                    toolChoice = if (promptSettings.toolChoice == PromptSettings.ToolChoice.REQUIRED) {
                        ToolChoice.Mode("required")
                    } else null,
                    //responseFormat = ChatResponseFormat.JsonObject, //todo: config JsonFormat, non-markdown tools
                    seed = lmSettings.seed,
                    temperature = lmSettings.temperature,
                    streamOptions = if (streamingEnabled) {
                        StreamOptions(includeUsage = true)
                    } else null
                )
            }
        } else {
            if (addMessage) {
                if (directive.transcription.isNotEmpty()) {
                    messageList.add(ChatMessage(ChatRole.User, directive.transcription))
                } else {
                    log.debug("No user transcription added")
                }
            } else {
                log.debug("No message added")
            }
            if (includeToolsInMarkdown) {
                ChatCompletionRequest(
                    model = ModelId(lmSettings.modelName),
                    messages = getMessages(),
                    responseFormat = ChatResponseFormat.Text,
                    seed = lmSettings.seed,
                    temperature = lmSettings.temperature,
                    streamOptions = if (streamingEnabled) {
                        StreamOptions(includeUsage = true)
                    } else null
                )
            } else {
                val requestTools = directive.assistant.availableActions
                    .filter { it.isVisible(directive) }
                    .map {
                        it.asTool(directive)
                    }.takeIf { it.isNotEmpty() }
                ChatCompletionRequest(
                    model = ModelId(lmSettings.modelName),
                    messages = getMessages(),
                    tools = requestTools,
                    toolChoice = if (promptSettings.toolChoice == PromptSettings.ToolChoice.REQUIRED) {
                        ToolChoice.Mode("required")
                    } else null,
                    //responseFormat = ChatResponseFormat.JsonObject, //todo: config JsonFormat, non-markdown tools
                    seed = lmSettings.seed,
                    temperature = lmSettings.temperature,
                    streamOptions = if (streamingEnabled) {
                        StreamOptions(includeUsage = true)
                    } else null
                )
            }
        }
        if (log.isTraceEnabled) log.trace("Chat completion request: ${request.messages.last()}")

        val requestTime = System.currentTimeMillis()
        var completion: ChatCompletion? = null
        try {
            completion = if (streamingEnabled) {
                collectAndCombineChunks(llmProvider, request, directive, promptSettings)
            } else {
                llmProvider.chatCompletion(request, directive)
            }
            val responseTime = System.currentTimeMillis()
            aiProvider.asObservabilityProvider().logLlmLatency(responseTime - requestTime)

            try {
                //todo: could just use JsonObject()
                if (completion.choices.first().message.toolCalls != null) {
                    val toolCall = completion.choices.first().message.toolCalls!!.first() as ToolCall.Function
                    val result = PartialJsonParser.parse(toolCall.function.arguments) as Map<String, Any>
                    val listener = partialContextListeners[toolCall.function.name]
                    if (listener != null) {
                        log.debug { "Sending final context update to listener" }
                        listener.invoke(ContextUpdate(result, true))
                    }
                }
            } catch (e: Throwable) {//todo: ```json {} ```
                log.warn("Failed to parse tool call arguments: ${e.message}", e)
            }

            //todo: check other choices
            val messageContent = completion.choices.firstOrNull()?.message?.messageContent
            val textContent = if (messageContent is TextContent) {
                messageContent.content
            } else {
                messageContent.toString()
            }
            messageList.add(completion.choices.first().message)
            val toolCalled = completion.choices.first().message.toolCalls?.firstOrNull() as ToolCall.Function?
            if (toolCalled != null) {
                val voqalTool = project.service<VoqalToolService>().getAvailableTools()[toolCalled.function.name]
                if (voqalTool?.manualConfirm != true) {
                    log.debug { "Auto-confirming tool call: ${toolCalled.function.name}" }
                    messageList.add(
                        ChatMessage(
                            role = ChatRole.Tool,
                            messageContent = TextContent(content = "success"),
                            toolCallId = toolCalled.id
                        )
                    )
                }
            }

            val response = when {
                promptSettings.promptName == "Edit Mode" || promptSettings.editMode -> {
                    ResponseParser.parseEditMode(completion, directive)
                }

                else -> ResponseParser.parse(completion, directive)
            }
            if (aiProvider.isObservabilityProvider()) {
                log.debug("Logging successful observability data")
                val op = aiProvider.asObservabilityProvider()
                if (ApplicationManager.getApplication().isUnitTestMode) {
                    op.log(request, response, requestTime, responseTime)
                } else {
                    op.asyncLog(project, request, response, requestTime, responseTime)
                }
            }

            //todo: TPS should be a part of observability
            val tokenCount = project.service<VoqalContextService>().getTokenCount(textContent)
            val elapsedTimeMs = responseTime - requestTime
            val elapsedTimeSeconds = elapsedTimeMs / 1000.0
            val tps = if (elapsedTimeSeconds > 0) tokenCount / elapsedTimeSeconds else 0.0
            log.debug("Response TPS: $tps")

            //todo: pricing should be moved to provider code
            val currency = response.getSpentCurrency()
            if (currency != -1.0) {
                aiProvider.asObservabilityProvider().logLlmCost(currency)
            } else {
                log.debug { "Unable to determine cost of model: ${lmSettings.name}" }
            }

            return response
        } catch (e: Throwable) {
            val responseTime = System.currentTimeMillis()
            if (aiProvider.isObservabilityProvider()) {
                log.debug("Logging failure observability data")
                val response = VoqalResponse(directive, emptyList(), completion, e)
                val op = aiProvider.asObservabilityProvider()
                val statusCode = (e as? OpenAIAPIException)?.statusCode ?: 500
                if (ApplicationManager.getApplication().isUnitTestMode) {
                    op.log(request, response, requestTime, responseTime, statusCode)
                } else {
                    op.asyncLog(project, request, response, requestTime, responseTime, statusCode)
                }
            }
            throw e
        }
    }

    private suspend fun collectAndCombineChunks(
        llmProvider: LlmProvider,
        request: ChatCompletionRequest,
        directive: VoqalDirective,
        promptSettings: PromptSettings
    ): ChatCompletion {
        val chunks = mutableListOf<ChatCompletionChunk>()
        var deltaRole: Role? = null

        val fullTexts = mutableMapOf<Int, StringBuilder>()
        var deltaToolCalls = mutableListOf<ToolCallChunk>()
        var partialContextData = mutableMapOf<Int, Map<String, Any>>()

        val chunkProcessingChannel = Channel<ChatCompletionChunk>(capacity = Channel.UNLIMITED)
        val processingJob = CoroutineScope(Dispatchers.Default).launch {
            for (chunk in chunkProcessingChannel) {
                deltaToolCalls = deltaToolCalls.map { toolCall ->
                    val args = fullTexts[toolCall.index]?.toString() ?: return@map toolCall
                    toolCall.copy(
                        function = toolCall.function!!.copy(
                            argumentsOrNull = args
                        )
                    )
                }.toMutableList()

                try {
                    val updatedChunk = chunk.copy(
                        choices = chunk.choices.map { choice ->
                            if (deltaToolCalls.isNotEmpty()) {
                                choice.copy(
                                    delta = choice.delta!!.copy(
                                        role = deltaRole,
                                        toolCalls = deltaToolCalls
                                    )
                                )
                            } else {
                                choice.copy(
                                    delta = choice.delta!!.copy(
                                        role = deltaRole,
                                        content = fullTexts[-1]?.toString()
                                    )
                                )
                            }
                        }
                    )
                    try {
                        updatedChunk.choices.first().delta?.toolCalls?.forEach { toolCall ->
                            val args = toolCall.function?.argumentsOrNull ?: return@forEach
                            val result = PartialJsonParser.parse(args) as Map<String, Any>?
                            if (partialContextData[toolCall.index] != result && result != null) {
                                partialContextData[toolCall.index] = result
                                if (result.isNotEmpty()) {
                                    val listener = toolCall.function?.name?.let { partialContextListeners[it] }
                                    if (listener != null) {
                                        log.trace { "Sending partial context update to listener for tool call index ${toolCall.index}" }
                                        listener.invoke(ContextUpdate(result, false))
                                    }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        log.warn("Failed to parse tool call arguments: ${e.message}")
                        continue
                    }
                } catch (_: Throwable) {
                    continue
                }
            }
        }

        llmProvider.streamChatCompletion(request, directive).collect { chunk ->
            chunks.add(chunk)
            if (deltaRole == null) {
                deltaRole = chunk.choices.firstOrNull()?.delta?.role
            }

            val toolCalls = chunk.choices.firstOrNull()?.delta?.toolCalls
            if (toolCalls != null) {
                toolCalls.forEach { toolCall ->
                    val index = toolCall.index
                    if (!fullTexts.containsKey(index)) {
                        fullTexts[index] = StringBuilder()
                        deltaToolCalls.add(toolCall.copy())
                    }
                    fullTexts[index]?.append(toolCall.function?.arguments ?: "")
                }
            } else {
                val content = chunk.choices.firstOrNull()?.delta?.content ?: ""
                if (fullTexts.containsKey(-1)) {
                    fullTexts[-1]?.append(content)
                } else {
                    fullTexts[-1] = StringBuilder().append(content)
                }
                //todo: \n is a condition EditText needs, not general purpose
                if (!content.contains("\n")) {
                    return@collect //wait till new line to progress streaming edit
                }
            }

            chunkProcessingChannel.send(chunk)
        }

        chunkProcessingChannel.close()
        processingJob.join()

        return toChatCompletion(
            deltaRole!!,
            deltaToolCalls,
            chunks,
            fullTexts[-1]?.toString() ?: "",
            promptSettings.editMode
        )
    }

    private fun getMessages(): List<ChatMessage> {
        return messageList.toList() //use copy
    }

    private fun toChatCompletion(
        role: Role,
        toolCalls: List<ToolCallChunk>,
        chunks: List<ChatCompletionChunk>,
        fullText: String,
        editMode: Boolean
    ): ChatCompletion {
        var messageContent: Content? = null
        var toolCalls = toolCalls
        val chunk = chunks.last()
        if (editMode) {
            messageContent = TextContent(content = fullText)
        } else if (toolCalls.isEmpty()) {
            val toolCallChunk = ResponseParser.parseToolCallChunk(fullText)
            if (toolCallChunk == null) {
                //default to answer_question tool
                toolCalls = listOf(
                    ToolCallChunk(
                        index = 0,
                        type = "function",
                        id = ToolId("answer_question"),
                        function = FunctionCall(
                            nameOrNull = "answer_question",
                            argumentsOrNull = JsonObject().apply {
                                put("text", fullText)
                            }.toString()
                        )
                    )
                )
            } else {
                toolCalls = listOf(toolCallChunk)
            }
        }
        return ChatCompletion(
            id = chunk.id,
            created = chunk.created.toLong(),
            model = chunk.model,
            choices = listOf(
                ChatChoice(
                    index = 0,
                    message = ChatMessage(
                        role = role,
                        messageContent = messageContent,
                        toolCalls = if (toolCalls.isNotEmpty()) {
                            toolCalls.map {
                                ToolCall.Function(
                                    id = it.id!!,
                                    function = it.function!!
                                )
                            }
                        } else null
                    )
                )
            ),
            usage = chunk.usage,
            systemFingerprint = chunk.systemFingerprint
        )
    }
}

fun Tool.asDirectiveTool(): Tool {
    return copy(
        function = function.copy(
            parameters = Parameters.fromJsonString(
                JsonObject().apply {
                    put("type", "object")
                    put("properties", JsonObject().apply {
                        put("directive", JsonObject().apply {
                            put("type", "string")
                            put("description", "The directive to pass to the tool")
                        })
                    })
                }.toString()
            )
        )
    )
}
