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
            if (streamingEnabled) {
                val chunks = mutableListOf<ChatCompletionChunk>()
                var deltaRole: Role? = null
                val fullText = StringBuilder()
                var deltaToolCall: ToolCallChunk? = null //todo: support streaming multiple tools

                var partialContextData = mapOf<String, Any>()
                val chunkProcessingChannel = Channel<ChatCompletionChunk>(capacity = Channel.UNLIMITED)
                val processingJob = CoroutineScope(Dispatchers.Default).launch {
                    for (chunk in chunkProcessingChannel) {
                        try {
                            val updatedChunk = chunk.copy(
                                choices = chunk.choices.map {
                                    if (deltaToolCall != null) {
                                        it.copy(
                                            delta = it.delta!!.copy(
                                                role = deltaRole,
                                                toolCalls = listOf(
                                                    deltaToolCall!!.copy(
                                                        function = deltaToolCall!!.function!!.copy(
                                                            argumentsOrNull = fullText.toString()
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    } else {
                                        it.copy(
                                            delta = it.delta!!.copy(
                                                role = deltaRole,
                                                content = fullText.toString()
                                            )
                                        )
                                    }
                                }
                            )
                            try {
                                val result = PartialJsonParser.parse(
                                    updatedChunk.choices.first().delta!!.toolCalls!!.first().function!!.arguments
                                ) as Map<String, Any>?
                                if (partialContextData != result && result != null) {
                                    partialContextData = result
                                    if (result.isNotEmpty()) {
                                        val listener = deltaToolCall?.function?.name?.let {
                                            partialContextListeners[it]
                                        }
                                        if (listener != null) {
                                            log.trace { "Sending partial context update to listener" }
                                            listener.invoke(ContextUpdate(result, false))
                                        }
                                    }
                                }
                            } catch (e: Throwable) {
                                log.warn("Failed to parse tool call arguments: ${e.message}")
                                continue
                            }
                        } catch (e: Throwable) {
                            continue
                        }
                    }
                }

                llmProvider.streamChatCompletion(request, directive).collect {
                    chunks.add(it)
                    if (deltaRole == null) {
                        deltaRole = it.choices.firstOrNull()?.delta?.role
                    }
                    if (deltaToolCall == null) {
                        deltaToolCall = it.choices.firstOrNull()?.delta?.toolCalls?.firstOrNull()
                    }

                    if (it.choices.firstOrNull()?.delta?.toolCalls != null) {
                        fullText.append(
                            it.choices.firstOrNull()?.delta?.toolCalls?.firstOrNull()?.function?.arguments ?: ""
                        )
                        if (fullText.isEmpty()) {
                            return@collect
                        }
                    } else {
                        fullText.append(it.choices.firstOrNull()?.delta?.content ?: "")
                        //todo: \n is a condition EditText needs, not general purpose
                        if (it.choices.firstOrNull()?.delta?.content?.contains("\n") in setOf(null, false)) {
                            return@collect //wait till new line to progress streaming edit
                        }
                    }

                    chunkProcessingChannel.send(it)
                }

                chunkProcessingChannel.close()
                processingJob.join()

                completion = toChatCompletion(deltaRole!!, deltaToolCall, chunks, fullText.toString(), promptSettings.editMode)
            } else {
                completion = llmProvider.chatCompletion(request, directive)
            }
            val responseTime = System.currentTimeMillis()

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
                val voqalTool = directive.service<VoqalToolService>().getAvailableTools()[toolCalled.function.name]
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

    private fun getMessages(): List<ChatMessage> {
        return messageList.toList() //use copy
    }

    private fun toChatCompletion(
        role: Role,
        toolCall: ToolCallChunk?,
        chunks: List<ChatCompletionChunk>,
        fullText: String,
        editMode: Boolean
    ): ChatCompletion {
        var messageContent: Content? = null
        var toolCall = toolCall
        val chunk = chunks.last()
        if (editMode) {
            messageContent = TextContent(content = fullText)
        } else if (toolCall == null) {
            //default to answer_question tool
            toolCall = ToolCallChunk(
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
        } else {
            toolCall = toolCall.copy(
                function = toolCall.function!!.copy(
                    argumentsOrNull = fullText
                )
            )
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
                        toolCalls = if (toolCall != null) {
                            listOf(toolCall.let {
                                ToolCall.Function(
                                    id = it.id!!,
                                    function = it.function!!
                                )
                            })
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
