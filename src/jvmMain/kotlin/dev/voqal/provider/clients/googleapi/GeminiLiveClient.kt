package dev.voqal.provider.clients.googleapi

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import dev.voqal.assistant.VoqalDirective
import dev.voqal.assistant.focus.SpokenTranscript
import dev.voqal.config.settings.PromptSettings.FunctionCalling
import dev.voqal.provider.LlmProvider
import dev.voqal.provider.StmProvider
import dev.voqal.provider.clients.openai.RealtimeAudio
import dev.voqal.provider.clients.openai.RealtimeTool
import dev.voqal.services.*
import dev.voqal.utils.SharedAudioCapture
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.utils.io.CancellationException
import io.ktor.websocket.*
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InterruptedIOException
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

class GeminiLiveClient(
    override val name: String,
    private val project: Project,
    private val providerKey: String,
    private val modelName: String
) : LlmProvider, StmProvider, SharedAudioCapture.AudioDataListener {

    private val log = project.getVoqalLogger(this::class)
    private var capturing = false
    private val wssProviderUrl =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    private val wssHeaders: Map<String, String> = emptyMap()
    private lateinit var session: DefaultClientWebSocketSession
    private lateinit var readThread: Thread
    private lateinit var writeThread: Thread
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private val client = HttpClient {
        install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        install(WebSockets)
    }
    private var disposed: Boolean = false
    private val realtimeAudioMap = mutableMapOf<String, RealtimeAudio>()
    private val realtimeToolMap = mutableMapOf<String, RealtimeTool>()
    private var serverVad = false
    private var speechStartTime = -1L
    private var speechEndTime = -1L
    private var currentInputTokens = 0
    private var setupComplete = false
    private var convoListener: ((String) -> Unit)? = null

    init {
        //todo: gemini live only supports server vad
        serverVad = true

        restartConnection()
        project.audioCapture.registerListener(this)
    }

    private fun restartConnection(): Boolean {
        ThreadingAssertions.assertBackgroundThread()
        log.debug { "Establishing new Gemini Live session" }
        if (::readThread.isInitialized) {
            readThread.interrupt()
            readThread.join()
        }
        if (::writeThread.isInitialized) {
            writeThread.interrupt()
            writeThread.join()
        }
        audioQueue.clear()
//        responseQueue.clear()
        currentInputTokens = 0
        setupComplete = false

        try {
            session = runBlocking {
                //establish connection, 3 attempts
                var session: DefaultClientWebSocketSession? = null
                for (i in 0..2) {
                    try {
                        withTimeout(10_000) {
                            session = client.webSocketSession("$wssProviderUrl?key=$providerKey") {
                                wssHeaders.forEach { header(it.key, it.value) }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        if (i == 2) {
                            throw e
                        } else {
                            log.warn { "Failed to connect to Gemini Live. Retrying..." }
                        }
                    }
                    if (session != null) break
                }
                session!!
            }
            log.debug { "Connected to Gemini Live" }
            readThread = Thread(readLoop(), "GeminiLiveClient-Read").apply { start() }
            writeThread = Thread(writeLoop(), "GeminiLiveClient-Write").apply { start() }

            setup()
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
//        requestJson.put("tool_config", JsonObject().put("function_calling_config", JsonObject().put("mode", "ANY")))

        runBlocking {
            val data = JsonObject().put("setup", JsonObject().apply {
                put("model", "models/$modelName")
                put("generation_config", JsonObject().apply {
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
                    //put("role", "model")
                }
                put("system_instruction", content)
                put("tools", toolsJson)
            }).toString()
            session.send(Frame.Text(data))
        }
    }

    private var convoId = 0
    private fun readLoop(): Runnable {
        return Runnable {
            try {
                while (!disposed) {
                    val frame = runBlocking { session.incoming.receive() }
                    when (frame) {
                        is Frame.Binary -> {
                            val buffer = frame.readBytes()
                            if (buffer.isNotEmpty()) {
                                val json = JsonObject(buffer.toString(Charsets.UTF_8))

                                if (json.containsKey("serverContent")) {
                                    if (json.getJsonObject("serverContent").getBoolean("turnComplete") == true) {
                                        log.debug { json.toString() }
                                        convoId++
                                        continue
                                    } else if (json.getJsonObject("serverContent").getBoolean("interrupted") == true) {
                                        log.debug { json.toString() }
                                        stopCurrentVoice() //todo: stop tool calls?
                                        convoId++
                                        continue
                                    }

                                    if (speechEndTime != -1L) {
                                        project.service<VoqalConfigService>().getAiProvider().asObservabilityProvider()
                                            .logStmLatency(System.currentTimeMillis() - speechEndTime)
                                        speechEndTime = -1L
                                    }

                                    val convoId = convoId.toString() //json.getString("item_id")
                                    val realtimeAudio = realtimeAudioMap.getOrPut(convoId) {
                                        RealtimeAudio(project, convoId)
                                    }
                                    realtimeAudio.addAudioData(json)
                                    convoListener?.invoke("audio response")
                                } else if (json.containsKey("setupComplete")) {
                                    log.debug { "Setup complete" }
                                    setupComplete = true
                                } else if (json.containsKey("toolCall")) {
                                    if (speechEndTime != -1L) {
                                        project.service<VoqalConfigService>().getAiProvider().asObservabilityProvider()
                                            .logStmLatency(System.currentTimeMillis() - speechEndTime)
                                        speechEndTime = -1L
                                    }

                                    log.debug { json }
                                    val functionCalls = json.getJsonObject("toolCall").getJsonArray("functionCalls")
                                    val convoId = convoId.toString()//json.getString("item_id")
                                    val realtimeTool = realtimeToolMap.getOrPut(convoId) {
                                        RealtimeTool(project, session, convoId)
                                    }
                                    realtimeTool.executeTool(functionCalls.getJsonObject(0)) //todo: multiple tools
                                    convoListener?.invoke("tool response")
                                } else {
                                    log.warn { "Unexpected binary frame: $json" }
                                }
                            } else {
                                log.warn { "Empty binary frame" }
                            }
                        }

                        is Frame.Close -> {
                            log.info { "Connection closed" }
                            break
                        }

                        else -> log.warn { "Unexpected frame: $frame" }
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
                runBlocking {
                    val closeReason = session.closeReason.await()?.message ?: "Unknown"
                    log.debug { "Connection closed. Reason: $closeReason" }

                    if (closeReason.contains("Quota exceeded") == true) {
                        log.debug { "Quota exceeded. Restarting connection in 5 seconds..." }
                        delay(5000)
                    }
                }
            } catch (_: InterruptedException) {
            } catch (_: InterruptedIOException) {
            } catch (e: Exception) {
                log.error(e) { "Error processing audio: ${e.message}" }
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
//                        log.debug { "No speech detected, flushing stream" }
//                        runBlocking {
//                            session.send(Frame.Text(JsonObject().apply {
//                                put("mediaChunks", JsonArray().apply {
//                                    add(JsonObject().apply {
//                                        put("mimeType", "audio/pcm;rate=16000")
//                                        put("data", "")
//                                    })
//                                })
//                            }.toString()))
//                        }
                    } else {
                        runBlocking {
                            val json = JsonObject().put("realtimeInput", JsonObject().apply {
                                put("mediaChunks", JsonArray().apply {
                                    add(JsonObject().apply {
                                        put("mimeType", "audio/pcm;rate=16000")
                                        put("data", Base64.getEncoder().encodeToString(buffer))
                                    })
                                })
                            })
                            session.send(Frame.Text(json.toString()))
                        }
                    }
                }
            } catch (_: InterruptedException) {
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                log.error(e) { "Error processing audio: ${e.message}" }
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
                    //put("role", "model")
                })
            })
            put("turn_complete", true)
        })
        session.send(Frame.Text(json.toString()))

        val responseType = promise.future().coAwait()
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
                            content = JsonObject().put("ignore", JsonObject().apply {
                                put("transcription", "")
                                put("ignore_reason", responseType)
                            }).toString()
                        )
                    )
                )
            )
        )
    }

    override fun onAudioData(data: ByteArray, detection: SharedAudioCapture.AudioDetection) {
        if (!setupComplete) return
        if (serverVad) {
            audioQueue.put(data)
//            return
        }

        if (detection.speechDetected.get()) {
            speechStartTime = System.currentTimeMillis()
//            stopCurrentVoice()
//            //todo: response.cancel

            capturing = true
//            detection.framesBeforeVoiceDetected.forEach {
//                audioQueue.put(it.data)
//            }
//            audioQueue.put(data)
        } else if (capturing && !detection.speechDetected.get()) {
            speechEndTime = System.currentTimeMillis()
//            capturing = false
//            audioQueue.put(SharedAudioCapture.EMPTY_BUFFER)

//            project.service<VoqalConfigService>().getAiProvider().asObservabilityProvider()
//                .logStmCost(calculateSttCost((speechEndTime - speechStartTime) / 1000.0))
        }
    }

    override fun getAvailableModelNames() = listOf("gemini-2.0-flash-exp")
    override fun isLiveDataListener() = true
    override fun isStmProvider() = true

    override fun dispose() {
        disposed = true
        if (::session.isInitialized) {
            runBlocking { session.close(CloseReason(CloseReason.Codes.NORMAL, "Disposed")) }
        }
        if (::writeThread.isInitialized) writeThread.interrupt()
    }
}
