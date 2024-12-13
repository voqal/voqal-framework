package dev.voqal.provider.clients.googleapi

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import dev.voqal.assistant.VoqalDirective
import dev.voqal.assistant.focus.SpokenTranscript
import dev.voqal.config.settings.PromptSettings.FunctionCalling
import dev.voqal.config.settings.VoiceDetectionSettings.VoiceDetectionProvider
import dev.voqal.provider.LlmProvider
import dev.voqal.provider.StmProvider
import dev.voqal.provider.clients.openai.RealtimeAudio
import dev.voqal.provider.clients.openai.RealtimeTool
import dev.voqal.services.*
import dev.voqal.utils.SharedAudioCapture
import dev.voqal.utils.SharedAudioCapture.Companion.FORMAT
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.utils.io.CancellationException
import io.ktor.websocket.*
import io.pebbletemplates.pebble.error.PebbleException
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.selects.SelectClause2
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.asynchttpclient.Dsl.*
import org.asynchttpclient.ws.WebSocket
import org.asynchttpclient.ws.WebSocketListener
import org.asynchttpclient.ws.WebSocketUpgradeHandler
import org.jetbrains.annotations.VisibleForTesting
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.coroutines.CoroutineContext

class GeminiLiveClient(
    override val name: String,
    private val project: Project,
    private val providerKey: String,
    private val modelName: String,
    private val responseModalities: List<String> = listOf("AUDIO")
) : LlmProvider, StmProvider, SharedAudioCapture.AudioDataListener, WebSocketListener {

    private val log = project.getVoqalLogger(this::class)
    private var capturingVoice = false
    private var capturingSpeech = false
    private val wssProviderUrl =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    private lateinit var writeThread: Thread
    private lateinit var updateSessionThread: Thread
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private val activeSession = JsonObject()
    private var disposed: Boolean = false
    private val realtimeAudioMap = mutableMapOf<String, RealtimeAudio>()
    private val realtimeToolMap = mutableMapOf<String, RealtimeTool>()
    private var clientRequestEndTime = -1L
    private var currentInputTokens = 0
    private var setupComplete = false
    private var convoListener: ((String) -> Unit)? = null
    private var convoId = 0
    private var textResponse: StringWriter? = null
    private var socket: WebSocket? = null
    private val asyncClient = asyncHttpClient(config().setWebSocketMaxFrameSize(Integer.MAX_VALUE))
    private var turnComplete = true
    private var previousFullSpeech = LinkedBlockingQueue<ByteArray>()
    private val previousAudio = LinkedBlockingQueue<ByteArray>()
    private val fullSpeech = LinkedBlockingQueue<ByteArray>()

    init {
        //todo: gemini live only supports server vad
        val config = project.service<VoqalConfigService>().getConfig()
        if (config.voiceDetectionSettings.provider == VoiceDetectionProvider.NONE) {
            //todo: best way i've found to use gemini live is to use a new socket for each context change
            log.error { "Gemini Live requires voice detection for best results" }
        }

        restartConnection()
        project.audioCapture.registerListener(this)
    }

    private fun restartConnection(): Boolean {
        ThreadingAssertions.assertBackgroundThread()
        log.debug { "Establishing new Gemini Live session" }
        if (::writeThread.isInitialized) {
            writeThread.interrupt()
            writeThread.join()
        }
        if (::updateSessionThread.isInitialized) {
            updateSessionThread.interrupt()
            updateSessionThread.join()
        }
        audioQueue.clear()
        currentInputTokens = 0
        if (turnComplete) {
            log.debug("Previous turn was complete")
        } else {
            log.debug("Previous turn was not complete")
        }
        setupComplete = false

        try {
            asyncClient.prepareGet("$wssProviderUrl?key=$providerKey")
                .execute(WebSocketUpgradeHandler.Builder().addWebSocketListener(this).build()).get()
            log.trace { "Connected to Gemini Live" }

            writeThread = Thread(writeLoop(), "GeminiLiveClient-Write").apply { start() }
            updateSessionThread = Thread(updateSessionLoop(), "GeminiLiveClient-UpdateSession").apply { start() }
        } catch (e: Exception) {
            val warnMessage = if (e.message != null) {
                "Gemini Live connection failed: ${e.message}"
            } else {
                "Failed to connect to Gemini Live"
            }
            log.warnChat(warnMessage)
            return false
        }
        return true
    }

    private fun updateSession() {
        if (!setupComplete || !turnComplete) return //wait till ready
        val configService = project.service<VoqalConfigService>()
        val promptName = configService.getActivePromptName()
        if (configService.getConfig().promptLibrarySettings.prompts.none { it.promptName == promptName }) {
            log.warn { "Prompt $promptName not found in prompt library" }
            return
        }

        val toolService = project.service<VoqalToolService>()
        var nopDirective = project.service<VoqalDirectiveService>().createDirective(
            transcription = SpokenTranscript("n/a", null),
            promptName = promptName,
            usingAudioModality = true
        )
        nopDirective = nopDirective.copy(
            contextMap = nopDirective.contextMap.toMutableMap().apply {
                put(
                    "assistant", nopDirective.assistant.copy(
                        availableActions = toolService.getAvailableTools().values,
                        promptSettings = nopDirective.assistant.promptSettings?.copy(
                            functionCalling = FunctionCalling.NATIVE
                        )
                    )
                )
            }
        )
        val prompt = nopDirective.toMarkdown()
        val tools = nopDirective.assistant.availableActions
            .filter { it.isVisible(nopDirective) }
            .filter {
                //todo: dynamic
                if (nopDirective.assistant.promptSettings!!.promptName.lowercase() == "edit mode") {
                    it.name in setOf("edit_text", "looks_good", "cancel")
                } else {
                    it.name != "answer_question"
                }
            }
        val toolsJson = JsonArray().apply {
            val toolsArray = JsonArray(
                tools.map { it.asTool(nopDirective) }.map { JsonObject(Json.encodeToString(it)) }
            )
            val functionDeclarations = toolsArray.map {
                val jsonObject = it as JsonObject
                jsonObject.remove("type")
                jsonObject.getJsonObject("function").apply {
                    if (getJsonObject("parameters").isEmpty) {
                        remove("parameters")
                    }
                }
            }
            add(JsonObject().put("function_declarations", JsonArray(functionDeclarations)))
        }

        val newSession = JsonObject().apply {
            put("prompt", prompt)
            put("tools", toolsJson)
        }
        if (newSession.toString() == activeSession.toString()) return

        if (capturingVoice) {
            log.debug { "Skipping session update while capturing voice" }
            return
        }
        log.debug { "Updating realtime session prompt" }
        activeSession.mergeIn(newSession)
        runBlocking {
            socket!!.sendCloseFrame().await()
        }
        project.scope.launch {
            restartConnection()
        }
    }

    private fun setup() {
        val configService = project.service<VoqalConfigService>()
        val promptName = configService.getActivePromptName()
        if (configService.getConfig().promptLibrarySettings.prompts.none { it.promptName == promptName }) {
            log.warn { "Prompt $promptName not found in prompt library" }
            return
        }

        val toolService = project.service<VoqalToolService>()
        var nopDirective = project.service<VoqalDirectiveService>().createDirective(
            transcription = SpokenTranscript("n/a", null),
            promptName = promptName,
            usingAudioModality = true
        )
        nopDirective = nopDirective.copy(
            contextMap = nopDirective.contextMap.toMutableMap().apply {
                put(
                    "assistant", nopDirective.assistant.copy(
                        availableActions = toolService.getAvailableTools().values,
                        promptSettings = nopDirective.assistant.promptSettings?.copy(
                            functionCalling = FunctionCalling.NATIVE
                        )
                    )
                )
            }
        )
        val prompt = nopDirective.toMarkdown()
        val tools = nopDirective.assistant.availableActions
            .filter { it.isVisible(nopDirective) }
            .filter {
                //todo: dynamic
                if (nopDirective.assistant.promptSettings!!.promptName.lowercase() == "edit mode") {
                    it.name in setOf("edit_text", "looks_good", "cancel")
                } else {
                    it.name != "answer_question"
                }
            }
        val toolsJson = JsonArray().apply {
            val toolsArray = JsonArray(
                tools.map { it.asTool(nopDirective) }.map { JsonObject(Json.encodeToString(it)) }
            )
            val functionDeclarations = toolsArray.map {
                val jsonObject = it as JsonObject
                jsonObject.remove("type")
                jsonObject.getJsonObject("function").apply {
                    if (getJsonObject("parameters").isEmpty) {
                        remove("parameters")
                    }
                }
            }
            add(JsonObject().put("function_declarations", JsonArray(functionDeclarations)))
        }
        //requestJson.put("tool_config", JsonObject().put("function_calling_config", JsonObject().put("mode", "ANY")))

        runBlocking {
            val setupConfig = JsonObject().put("setup", JsonObject().apply {
                put("model", "models/$modelName")
                put("generation_config", JsonObject().apply {
                    put("response_modalities", JsonArray(responseModalities))
                    put("speech_config", JsonObject().apply {
                        put("voice_config", JsonObject().apply {
                            put("prebuilt_voice_config", JsonObject().apply {
                                put("voice_name", "Charon")
                            })
                        })
                    })
                })
                val content = JsonObject().apply {
                    put("parts", JsonArray().apply {
                        add(JsonObject().apply {
                            put("text", prompt)
                        })
                    })
                    put("role", "model")
                }
                put("system_instruction", content)

                if (!toolsJson.isEmpty) {
                    put("tools", toolsJson)
                }
            }).toString()
            activeSession.mergeIn(JsonObject().apply {
                put("prompt", prompt)
                put("tools", toolsJson)
            })
            socket!!.sendTextFrame(setupConfig)
        }
    }

    override fun onOpen(websocket: WebSocket) {
        socket = websocket
        setup()
    }

    override fun onClose(websocket: WebSocket, code: Int, reason: String) {
        //if (code == 1000) return //normal close
        log.debug { "Connection closed. Code: $code, Reason: $reason" }
    }

    override fun onError(t: Throwable) {
        log.error(t) { "Encountered error in websocket" }
    }

    override fun onBinaryFrame(buffer: ByteArray, finalFragment: Boolean, rsv: Int) {
        val json = JsonObject(buffer.toString(Charsets.UTF_8))

        if (json.containsKey("serverContent")) {
            if (json.getJsonObject("serverContent").getBoolean("turnComplete") == true) {
                log.debug { json.toString() }
                convoId++

                if (textResponse != null) {
                    val text = textResponse!!.toString()
                    convoListener?.invoke(text)
                    textResponse = null
                }
                turnComplete = true
                return
            } else if (json.getJsonObject("serverContent").getBoolean("interrupted") == true) {
                log.debug { json.toString() }
                stopCurrentVoice() //todo: stop tool calls?
                convoId++
                turnComplete = true
                return
            }
            turnComplete = false

            if (clientRequestEndTime != -1L) {
                project.service<VoqalConfigService>().getAiProvider().asObservabilityProvider()
                    .logStmLatency(System.currentTimeMillis() - clientRequestEndTime)
                clientRequestEndTime = -1L
            }

            val parts = json.getJsonObject("serverContent").getJsonObject("modelTurn")
                .getJsonArray("parts")
            if (parts.size() > 1) {
                log.warn { "Multiple parts not yet supported. Using first only" }
            }

            val part = parts.getJsonObject(0)
            if (part.containsKey("text")) {
                if (textResponse == null) {
                    textResponse = StringWriter()
                }
                textResponse!!.append(part.getString("text"))
            } else {
                val convoId = convoId.toString() //json.getString("item_id")
                val realtimeAudio = realtimeAudioMap.getOrPut(convoId) {
                    RealtimeAudio(project, convoId)
                }
                realtimeAudio.addAudioData(part)
                convoListener?.invoke("audio response")
            }
        } else if (json.containsKey("setupComplete")) {
            val previousAudioData = previousAudio.toList()
            previousAudio.clear()
            if (previousAudioData.isNotEmpty()) {
                log.debug { "Sending previous audio data" }
                previousAudioData.forEach { buffer ->

                    //add wav headers to pcm data
                    val format = AudioFormat(
                        16000f,
                        FORMAT.sampleSizeInBits,
                        FORMAT.channels,
                        FORMAT.encoding == AudioFormat.Encoding.PCM_SIGNED,
                        FORMAT.isBigEndian
                    )
                    val ais = AudioInputStream(ByteArrayInputStream(buffer), format, buffer.size.toLong())
                    val baos = ByteArrayOutputStream()
                    ais.use { AudioSystem.write(ais, AudioFileFormat.Type.WAVE, baos) }
                    val buffer2 = baos.toByteArray()

                    val speechFile =
                        File("C:\\Users\\Brandon\\IdeaProjects\\voqal-dev\\build\\speech\\developer-2c61990f-a7dd-480c-a8b3-47705e0b3e54.wav")
                    val audio1Bytes = speechFile.readBytes()

                    val json = JsonObject().put("client_content", JsonObject().apply {
                        put("turns", JsonArray().apply {
                            add(JsonObject().apply {
                                put("parts", JsonArray().apply {
//                                    add(JsonObject().apply {
//                                        put("inline_data", JsonObject().apply {
//                                            put("mimeType", "audio/wav")
//                                            put("data", Base64.getEncoder().encodeToString(audio1Bytes))
//                                        })
//                                    })
                                    add(JsonObject().apply {
                                        put("text", "What time is it?")
                                    })
                                })
                                put("role", "user")
                            })
                        })
//                        put("turn_complete", true)
                    })
                    socket!!.sendTextFrame(json.toString())
                }
            }
            log.debug { "Gemini Live setup complete" }
            setupComplete = true
//            project.scope.launch {
//                val directiveService = project.service<VoqalDirectiveService>()
//                directiveService.wakeWordDetected()
//            }
        } else if (json.containsKey("toolCall")) {
            turnComplete = false
            if (clientRequestEndTime != -1L) {
                project.service<VoqalConfigService>().getAiProvider().asObservabilityProvider()
                    .logStmLatency(System.currentTimeMillis() - clientRequestEndTime)
                clientRequestEndTime = -1L
            }

            log.debug { json }
            val functionCalls = json.getJsonObject("toolCall").getJsonArray("functionCalls")
            val convoId = convoId.toString()//json.getString("item_id")
            val realtimeTool = realtimeToolMap.getOrPut(convoId) {
                RealtimeTool(project, AsyncSession(), convoId)
            }
            realtimeTool.executeTool(functionCalls.getJsonObject(0)) //todo: multiple tools
            convoListener?.invoke("tool response")
            if (functionCalls.size() > 1) {
                println("here")
            }
        } else {
            log.warn { "Unexpected binary frame: $json" }
        }
    }

    private fun stopCurrentVoice() {
        realtimeAudioMap.values.forEach { it.stopAudio() }
    }

    private fun writeLoop(): Runnable {
        return Runnable {
            try {
                while (!disposed) {
                    val buffer = try {
                        audioQueue.take()
                    } catch (_: InterruptedException) {
                        break
                    }

                    runBlocking {
                        val json = JsonObject().put("realtimeInput", JsonObject().apply {
                            put("mediaChunks", JsonArray().apply {
                                add(JsonObject().apply {
                                    put("mimeType", "audio/pcm;rate=16000")
                                    put("data", Base64.getEncoder().encodeToString(buffer))
                                })
                            })
                        })
                        socket!!.sendTextFrame(json.toString())
                    }
                }
            } catch (_: InterruptedException) {
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                log.error(e) { "Error processing write: ${e.message}" }
            }
        }
    }

    private fun updateSessionLoop(): Runnable {
        return Runnable {
            try {
                while (!disposed) {
                    try {
                        updateSession()
                    } catch (e: PebbleException) {
                        log.warn { "Failed to update session: ${e.message}" }
                    }
                    runBlocking { delay(500) }
                }
            } catch (_: InterruptedException) {
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                log.error(e) { "Error processing session update: ${e.message}" }
            }
        }
    }

    override suspend fun chatCompletion(
        request: ChatCompletionRequest,
        directive: VoqalDirective?
    ): ChatCompletion {
        val promise = Promise.promise<String>()
        convoListener = {
            convoListener = null
            promise.complete(it)
        }
        val json = JsonObject().put("clientContent", JsonObject().apply {
            put("turns", JsonArray().apply {
                add(JsonObject().apply {
                    put("parts", JsonArray().apply {
                        add(JsonObject().apply {
                            put("text", request.messages.last().content)
                        })
                    })
                    put("role", "user")
                })
            })
            put("turn_complete", true)
        })
        clientRequestEndTime = System.currentTimeMillis()
        socket!!.sendTextFrame(json.toString())

        val convoResponse = promise.future().coAwait()
        val toolCall = if (convoResponse in listOf("audio response", "tool response")) {
            ToolCall.Function(
                id = ToolId("ignore"),
                function = FunctionCall(
                    nameOrNull = "ignore",
                    argumentsOrNull = JsonObject().apply {
                        put("transcription", "")
                        put("ignore_reason", convoResponse)
                    }.toString()
                )
            )
        } else {
            ToolCall.Function(
                id = ToolId("answer_question"),
                function = FunctionCall(
                    nameOrNull = "answer_question",
                    argumentsOrNull = JsonObject().apply {
                        put("text", convoResponse.trim())
                    }.toString()
                )
            )
        }
        return ChatCompletion(
            id = "n/a",
            created = System.currentTimeMillis(),
            model = ModelId("n/a"),
            choices = listOf(
                ChatChoice(
                    index = 0,
                    ChatMessage(
                        ChatRole.Assistant,
                        messageContent = null,
                        toolCalls = listOf(toolCall)
                    )
                )
            )
        )
    }

    override fun onAudioData(data: ByteArray, detection: SharedAudioCapture.AudioDetection) {
        if (!setupComplete) return

        if (!capturingVoice && detection.voiceDetected.get()) {
            capturingVoice = true
        } else if (capturingVoice && !detection.voiceDetected.get()) {
            capturingVoice = false
            clientRequestEndTime = System.currentTimeMillis()
        }

        audioQueue.put(data)
    }

    @VisibleForTesting
    fun isSetupComplete(): Boolean {
        return setupComplete
    }

    @VisibleForTesting
    fun setConvoListener(listener: (String) -> Unit) {
        convoListener = listener
    }

    override fun getAvailableModelNames() = listOf("gemini-2.0-flash-exp")
    override fun isLiveDataListener() = true
    override fun isStmProvider() = true

    override fun dispose() {
        disposed = true
//        if (::session.isInitialized) {
//            runBlocking { session.close(CloseReason(CloseReason.Codes.NORMAL, "Disposed")) }
//        }
        if (::writeThread.isInitialized) writeThread.interrupt()
    }

    private inner class AsyncSession : DefaultWebSocketSession {
        override val closeReason: Deferred<CloseReason?>
            get() = throw UnsupportedOperationException()
        override var pingIntervalMillis: Long
            get() = throw UnsupportedOperationException()
            set(value) {
                throw UnsupportedOperationException()
            }
        override var timeoutMillis: Long
            get() = throw UnsupportedOperationException()
            set(value) {
                throw UnsupportedOperationException()
            }

        @InternalAPI
        override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {
            throw UnsupportedOperationException()
        }

        override val extensions: List<WebSocketExtension<*>>
            get() = throw UnsupportedOperationException()
        override val incoming: ReceiveChannel<Frame>
            get() = throw UnsupportedOperationException()
        override var masking: Boolean
            get() = throw UnsupportedOperationException()
            set(value) {
                throw UnsupportedOperationException()
            }
        override var maxFrameSize: Long
            get() = throw UnsupportedOperationException()
            set(value) {
                throw UnsupportedOperationException()
            }
        override val outgoing: SendChannel<Frame>
            get() = object : SendChannel<Frame> {
                @DelicateCoroutinesApi
                override val isClosedForSend: Boolean
                    get() = throw UnsupportedOperationException()
                override val onSend: SelectClause2<Frame, SendChannel<Frame>>
                    get() = throw UnsupportedOperationException()

                override fun close(cause: Throwable?): Boolean {
                    throw UnsupportedOperationException()
                }

                override fun invokeOnClose(handler: (Throwable?) -> Unit) {
                    throw UnsupportedOperationException()
                }

                override suspend fun send(element: Frame) {
                    socket!!.sendBinaryFrame(element.data)
                }

                override fun trySend(element: Frame): ChannelResult<Unit> {
                    throw UnsupportedOperationException()
                }
            }

        override suspend fun flush() {
            throw UnsupportedOperationException()
        }

        @Deprecated("Use cancel() instead.", replaceWith = ReplaceWith("cancel()", "kotlinx.coroutines.cancel"))
        override fun terminate() {
            throw UnsupportedOperationException()
        }

        override val coroutineContext: CoroutineContext
            get() = throw UnsupportedOperationException()
    }
}
