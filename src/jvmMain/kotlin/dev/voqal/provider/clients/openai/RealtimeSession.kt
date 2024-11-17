package dev.voqal.provider.clients.openai

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import dev.voqal.assistant.VoqalDirective
import dev.voqal.assistant.focus.SpokenTranscript
import dev.voqal.config.settings.PromptSettings.FunctionCalling
import dev.voqal.config.settings.VoiceDetectionSettings.VoiceDetectionProvider
import dev.voqal.services.*
import dev.voqal.utils.SharedAudioCapture
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.utils.io.CancellationException
import io.ktor.websocket.*
import io.pebbletemplates.pebble.error.ParserException
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.io.InterruptedIOException
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

class RealtimeSession(
    private val project: Project,
    val wssProviderUrl: String,
    val wssHeaders: Map<String, String> = emptyMap(),
    private val azureHost: Boolean = false
) : Disposable {

    private val log = project.getVoqalLogger(this::class)
    private var capturing = false
    private lateinit var session: DefaultClientWebSocketSession
    private lateinit var readThread: Thread
    private lateinit var writeThread: Thread
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private val jsonEncoder = Json { ignoreUnknownKeys = true }
    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        install(WebSockets)
    }
    private val activeSession = JsonObject()
    private var disposed: Boolean = false
    private val responseQueue = LinkedBlockingQueue<Promise<Any>>()
    private val deltaQueue = LinkedBlockingQueue<Promise<FlowCollector<String>>>()
    private val deltaMap = mutableMapOf<String, Any>()
    private val realtimeAudioMap = mutableMapOf<String, RealtimeAudio>()
    private val realtimeToolMap = mutableMapOf<String, RealtimeTool>()
    private var serverVad = false
    private val ignoreTranscripts = setOf(
        "Eh-hem!",
        "Ahem.",
        "Hmm",
        "Uh-huh.",
        "Mm-hmm",
        "ahem",
        "Mm-hm."
    )
    private var previousConvoId: String? = null
    private val responseIdToConvoId = mutableMapOf<String, String>()
    private val ignoreResponseToConvoIds = mutableSetOf<String>()
    private val ignoreResponseIds = mutableSetOf<String>()

    init {
        val config = project.service<VoqalConfigService>().getConfig()
        if (config.voiceDetectionSettings.provider == VoiceDetectionProvider.NONE) {
            serverVad = true
        }

        restartConnection()
    }

    private fun restartConnection(): Boolean {
        ThreadingAssertions.assertBackgroundThread()
        log.debug("Establishing new Realtime API session")
        if (::readThread.isInitialized) {
            readThread.interrupt()
            readThread.join()
        }
        if (::writeThread.isInitialized) {
            writeThread.interrupt()
            writeThread.join()
        }
        audioQueue.clear()
        responseQueue.clear()

        try {
            session = runBlocking {
                //establish connection, 3 attempts
                var session: DefaultClientWebSocketSession? = null
                for (i in 0..2) {
                    try {
                        withTimeout(10_000) {
                            session = client.webSocketSession(wssProviderUrl) {
                                wssHeaders.forEach { header(it.key, it.value) }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        if (i == 2) {
                            throw e
                        } else {
                            log.warn("Failed to connect to Realtime API. Retrying...")
                        }
                    }
                    if (session != null) break
                }
                session!!
            }
            log.debug("Connected to Realtime API")
            readThread = Thread(readLoop(), "RealtimeSession-Read").apply { start() }
            writeThread = Thread(writeLoop(), "RealtimeSession-Write").apply { start() }

            project.scope.launch {
                while (!disposed) {
                    try {
                        updateSession()
                    } catch (e: ParserException) {
                        log.warn("Failed to update session: ${e.message}")
                    } catch (e: Throwable) {
                        log.warn("Failed to update session", e)
                    }
                    delay(500)
                }
            }
        } catch (e: Exception) {
            val warnMessage = if (e.message != null) {
                "Realtime API connection failed: ${e.message}"
            } else {
                "Failed to connect to Realtime API"
            }
            log.warnChat(warnMessage)
            return false
        }
        return true
    }

    private fun updateSession() {
        val configService = project.service<VoqalConfigService>()
        val toolService = project.service<VoqalToolService>()
        val promptName = configService.getActivePromptName()
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

        val newSession = JsonObject().apply {
            put("modalities", JsonArray().add("text").add("audio"))
            put("instructions", prompt)
            put("input_audio_transcription", JsonObject().apply {
                put("model", "whisper-1")
            })
            put("tools", JsonArray(tools.map {
                it.asTool(nopDirective).function
            }.map {
                JsonObject(jsonEncoder.encodeToJsonElement(it).toString())
                    .put("type", "function")
            }))
            if (!serverVad) {
                if (azureHost) {
                    put("turn_detection", JsonObject().apply {
                        put("type", "none")
                    })
                } else {
                    put("turn_detection", null)
                }
            }
        }
        if (newSession.toString() == activeSession.toString()) {
            return
        } else {
            log.debug { "Updating realtime session prompt" }
        }
        activeSession.mergeIn(newSession)

        val json = JsonObject().apply {
            put("type", "session.update")
            put("session", activeSession)
        }
        runBlocking {
            session.send(Frame.Text(json.toString()))
        }
    }

    private fun readLoop(): Runnable {
        return Runnable {
            try {
                while (!disposed) {
                    val frame = runBlocking { session.incoming.receive() }
                    when (frame) {
                        is Frame.Text -> {
                            val json = JsonObject(frame.readText())
                            if (!json.getString("type").endsWith(".delta")) {
                                if (json.getString("type") == "error") {
                                    log.warn { "Realtime error: $json" }
                                } else {
                                    log.trace { "Realtime event: $json" }
                                }
                            }

                            if (previousConvoId == null && json.getString("type") == "conversation.item.created") {
                                previousConvoId = json.getJsonObject("item").getString("id")!!
                            } else if (json.getString("type") == "response.created") {
                                val responseId = json.getJsonObject("response").getString("id")!!
                                responseIdToConvoId[responseId] = previousConvoId!!

                                if (ignoreResponseToConvoIds.contains(previousConvoId!!)) {
                                    log.info { "Ignoring response $responseId to convo $previousConvoId" }
                                    ignoreResponseIds.add(responseId)
                                    //ignoreResponseToConvoIds.remove(previousConvoId!!)
                                }
                            }

                            if (json.getString("type") == "error") {
                                val errorMessage = json.getJsonObject("error").getString("message")
                                if (errorMessage != "Response parsing interrupted") {
                                    log.warnChat(errorMessage)
                                }
                            } else if (json.getString("type") == "response.function_call_arguments.delta") {
//                                val respId = json.getString("response_id")
//                                val deltaValue = deltaMap.get(respId)
//                                if (deltaValue is FlowCollector<*>) {
//                                    @Suppress("UNCHECKED_CAST") val flowCollector = deltaValue as FlowCollector<String>
//                                    channel.trySend(GlobalScope.launch(start = CoroutineStart.LAZY) {
//                                        flowCollector.emit(json.getString("delta"))
//                                    })
//                                } else if (deltaValue == null) {
//                                    //first delta
//                                    val promise = deltaQueue.take()
//                                    deltaMap[respId] = promise
//                                }
                            } else if (json.getString("type") == "response.function_call_arguments.done") {
                                val responseId = json.getString("response_id")
                                if (ignoreResponseIds.contains(responseId)) {
                                    log.info("Ignoring response $responseId")
                                    continue
                                }
                                val convoId = responseIdToConvoId[responseId]!!
                                val realtimeTool = realtimeToolMap.getOrPut(convoId) {
                                    RealtimeTool(project, session)
                                }
                                realtimeTool.executeTool(json)
                            } else if (json.getString("type") == "response.audio.delta") {
                                val responseId = json.getString("response_id")
                                if (ignoreResponseIds.contains(responseId)) {
                                    log.info("Ignoring response $responseId")
                                    continue
                                }
                                val convoId = responseIdToConvoId[responseId]!!
                                val realtimeAudio = realtimeAudioMap.getOrPut(convoId) {
                                    RealtimeAudio(project, convoId)
                                }
                                realtimeAudio.addAudioData(json)
                            } else if (json.getString("type") == "response.audio.done") {
                                val responseId = json.getString("response_id")
                                if (ignoreResponseIds.contains(responseId)) {
                                    log.info("Ignoring response $responseId")
                                    continue
                                }
                                val convoId = responseIdToConvoId[responseId]!!
                                val realtimeAudio = realtimeAudioMap[convoId]!!
                                realtimeAudio.finishAudio()
                                previousConvoId = null
                            } else if (json.getString("type") == "input_audio_buffer.speech_started") {
                                log.info("Realtime speech started")
                                stopCurrentVoice()
                            } else if (json.getString("type") == "input_audio_buffer.speech_stopped") {
                                log.info("Realtime speech stopped")
                            } else if (json.getString("type") == "conversation.item.input_audio_transcription.completed") {
                                var transcript = json.getString("transcript")
                                if (transcript.endsWith("\n")) {
                                    transcript = transcript.substring(0, transcript.length - 1)
                                }

                                val convoId = json.getString("item_id")
                                if (transcript.isEmpty()) {
                                    log.info("Ignoring empty transcript")
                                    ignoreResponseToConvoIds.add(convoId)
                                    continue
                                } else if (transcript in ignoreTranscripts) {
                                    log.info("Ignoring transcript: $transcript")
                                    ignoreResponseToConvoIds.add(convoId)
                                    continue
                                }

                                log.info("User transcript: $transcript")
                                val chatContentManager = project.service<ChatToolWindowContentManager>()
                                chatContentManager.addUserMessage(transcript)

                                val realtimeAudio = realtimeAudioMap.getOrPut(convoId) {
                                    RealtimeAudio(project, convoId)
                                }
                                realtimeAudio.startAudio()

                                val realtimeTool = realtimeToolMap.getOrPut(convoId) {
                                    RealtimeTool(project, session)
                                }
                                realtimeTool.allowExecution()
                            } else if (json.getString("type") == "response.audio_transcript.done") {
                                val transcript = json.getString("transcript")
                                log.info("Assistant transcript: $transcript")
                                val chatContentManager = project.service<ChatToolWindowContentManager>()
                                chatContentManager.addAssistantMessage(transcript)
                            } else if (json.getString("type") == "response.text.done") {
                                val text = json.getString("text")
                                log.info("Assistant text: $text")

                                responseQueue.take().complete(JsonObject().apply {
                                    put("tool", "answer_question")
                                    put("parameters", JsonObject().apply {
                                        put("answer", text)
                                        //put("audioModality", true)
                                    })
                                }.toString())
                            }
                        }

                        is Frame.Close -> {
                            log.info("Connection closed")
                            break
                        }

                        else -> log.warn("Unexpected frame: $frame")
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                //todo: Deepgram closes socket to indicate end of transcription
                log.debug("Connection closed")
            } catch (_: InterruptedException) {
            } catch (_: InterruptedIOException) {
            } catch (e: Exception) {
                log.error("Error processing audio: ${e.message}", e)
            } finally {
                if (!disposed) {
                    project.scope.launch {
                        restartConnection()
                    }
                }
            }
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

                    if (buffer === SharedAudioCapture.EMPTY_BUFFER) {
                        log.debug("No speech detected, flushing stream")
                        runBlocking {
                            session.send(Frame.Text(JsonObject().apply {
                                put("type", "input_audio_buffer.commit")
                            }.toString()))
                            session.send(Frame.Text(JsonObject().apply {
                                put("type", "response.create")
                            }.toString()))
                        }
                    } else {
                        runBlocking {
                            val json = JsonObject().apply {
                                put("type", "input_audio_buffer.append")
                                put("audio", Base64.getEncoder().encodeToString(buffer))
                            }
                            session.send(Frame.Text(json.toString()))
                        }
                    }
                }
            } catch (_: InterruptedException) {
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                log.error("Error processing audio: ${e.message}", e)
            }
        }
    }

    fun onAudioData(data: ByteArray, detection: SharedAudioCapture.AudioDetection) {
        if (serverVad) {
            audioQueue.put(data)
            return
        }

        if (detection.speechDetected.get()) {
            println("todo")
//            if (playingResponseId != null) {
//                log.warn("Sending response cancel")
//                stopCurrentVoice()
//                runBlocking {
//                    session.send(Frame.Text(JsonObject().apply {
//                        put("type", "response.cancel")
//                    }.toString()))
//                }
//            }

            capturing = true
            detection.framesBeforeVoiceDetected.forEach {
                audioQueue.put(it.data)
            }
            audioQueue.put(data)
        } else if (capturing && !detection.speechDetected.get()) {
            capturing = false
            audioQueue.put(SharedAudioCapture.EMPTY_BUFFER)
        }
    }

    suspend fun chatCompletion(request: ChatCompletionRequest, directive: VoqalDirective?): ChatCompletion {
        val eventId = sendTextMessage(directive!!)

        val promise = Promise.promise<Any>()
        responseQueue.add(promise)

        session.send(Frame.Text(JsonObject().apply {
            put("event_id", "$eventId.response") //todo: doesn't correlate with response
            put("type", "response.create")
        }.toString()))

        //todo: realtime can choose to merge reqs (i.e. hi 3 times quickly = 1 response)
        val responseJson = promise.future().coAwait()
        return ChatCompletion(
            id = "n/a",
            created = System.currentTimeMillis(),
            model = ModelId("n/a"),
            choices = listOf(
                ChatChoice(
                    index = 0,
                    ChatMessage(
                        ChatRole.Assistant,
                        TextContent(
                            content = responseJson as String
                        )
                    )
                )
            )
        )
    }

    suspend fun streamChatCompletion(
        request: ChatCompletionRequest,
        directive: VoqalDirective?
    ): Flow<ChatCompletionChunk> {
        val eventId = sendTextMessage(directive!!)

        val promise = Promise.promise<FlowCollector<String>>()
        deltaQueue.add(promise)

        session.send(Frame.Text(JsonObject().apply {
            put("event_id", "$eventId.response") //todo: doesn't correlate with response
            put("type", "response.create")
        }.toString()))

        TODO()

//        val responseFlow = promise.future().coAwait()
//        return object : Flow<ChatCompletionChunk> {
//            override suspend fun collect(collector: FlowCollector<ChatCompletionChunk>) {
//                responseFlow.collect {
//                    collector.emit(ChatCompletionChunk(it))
//                }
//            }
//        }
    }

    private suspend fun sendTextMessage(directive: VoqalDirective): String {
        val eventId = "voqal." + UUID.randomUUID().toString()
        val json = JsonObject().apply {
            put("event_id", "$eventId.conversation.item")
            put("type", "conversation.item.create")
            put("item", JsonObject().apply {
                put("type", "message")
                put("status", "completed")
                put("role", "user")
                put("content", JsonArray().add(JsonObject().apply {
                    put("type", "input_text")
                    put("text", directive.transcription)
                }))
            })
        }
        session.send(Frame.Text(json.toString()))

        return eventId
    }

    fun sampleRate() = 24000f

    override fun dispose() {
        disposed = true
        if (::session.isInitialized) {
            runBlocking { session.close(CloseReason(CloseReason.Codes.NORMAL, "Disposed")) }
        }
        if (::writeThread.isInitialized) writeThread.interrupt()
    }
}
