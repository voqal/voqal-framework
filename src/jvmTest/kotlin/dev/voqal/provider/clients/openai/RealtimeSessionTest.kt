package dev.voqal.provider.clients.openai

import com.aallam.openai.api.chat.FunctionTool
import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.chat.ToolType
import dev.voqal.VoqalTest
import dev.voqal.assistant.VoqalDirective
import dev.voqal.assistant.tool.VoqalTool
import dev.voqal.core.MockChatToolWindowContentManager
import dev.voqal.core.MockProject
import dev.voqal.core.MockToolService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.joor.Reflect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class RealtimeSessionTest : VoqalTest {

    @Test
    fun selectUnreadEmails(): Unit = runBlocking {
        var socketSession: DefaultWebSocketSession? = null
        val server = embeddedServer(Netty, port = 8080) {
            install(WebSockets)

            routing {
                webSocket("/test") {
                    socketSession = this

                    for (frame in incoming) {
                        frame as? Frame.Text ?: continue
                        val msg = JsonObject(frame.readText())
                        if (msg.getString("type") == "session.update") {
                            continue
                        }
                    }
                }
            }
        }
        server.start(false)

        val testContext = VertxTestContext()
        val project = MockProject().apply {
            chatToolWindowContentManager = object : MockChatToolWindowContentManager() {
                override fun addUserMessage(transcript: String, speechId: String?) {
                    testContext.verify {
                        assertEquals("Select the unread emails", transcript)
                    }
                }

                override fun addAssistantMessage(transcript: String, speechId: String?) {
                    testContext.verify {
                        assertEquals("The unread emails have been selected.", transcript)
                    }
                    testContext.completeNow()
                }
            }

            toolService = object : MockToolService() {
                override fun getAvailableTools(): Map<String, VoqalTool> {
                    return mapOf("select_emails" to object : VoqalTool() {
                        override val name = "select_emails"

                        override suspend fun actionPerformed(args: JsonObject, directive: VoqalDirective) {
                            TODO("Not yet implemented")
                        }

                        override fun asTool(directive: VoqalDirective): Tool {
                            return Tool(type = ToolType.Function, function = FunctionTool(name = "select_emails"))
                        }
                    })
                }

                override fun executeTool(args: String?, voqalTool: VoqalTool, onFinish: suspend (Any?) -> Unit) {
                    testContext.verify {
                        val inputs = JsonObject(args).getJsonArray("inputs")
                        assertEquals(4, inputs.size())
                        assertEquals(":1r", inputs.getJsonObject(0).getString("email_id"))
                        assertEquals(":25", inputs.getJsonObject(1).getString("email_id"))
                        assertEquals(":2j", inputs.getJsonObject(2).getString("email_id"))
                        assertEquals(":2x", inputs.getJsonObject(3).getString("email_id"))
                    }
                    runBlocking { onFinish.invoke(null) }
                }
            }
        }

        val session = RealtimeSession(project, "ws://localhost:8080/test")
        GlobalScope.launch {
            delay(1000)
            File("src/jvmTest/resources/realtime/select-unread-emails.jsonl").forEachLine {
                runBlocking {
                    socketSession!!.send(it)
                }
            }
        }
        errorOnTimeout(testContext)

        val realtimeAudioMap = Reflect.on(session).get<Map<String, RealtimeAudio>>("realtimeAudioMap")
        assertEquals(1, realtimeAudioMap.size)
        val realtimeAudio = realtimeAudioMap["item_AU0Bm4zcud6sRKZ5hhajp"]
        assertNotNull(realtimeAudio)
        assertEquals(false, Reflect.on(realtimeAudio).get<AtomicBoolean>("audioPlaying").get())
        assertEquals(true, Reflect.on(realtimeAudio).get<AtomicBoolean>("audioPlayed").get())
        assertEquals(true, Reflect.on(realtimeAudio).get<AtomicBoolean>("audioFinished").get())
        assertEquals(true, Reflect.on(realtimeAudio).get<AtomicBoolean>("audioWrote").get())
        assertEquals(true, Reflect.on(realtimeAudio).get<AtomicBoolean>("audioReady").get())

        val realtimeToolMap = Reflect.on(session).get<Map<String, RealtimeTool>>("realtimeToolMap")
        assertEquals(1, realtimeToolMap.size)
        val realtimeTool = realtimeToolMap["item_AU0Bm4zcud6sRKZ5hhajp"]
        assertNotNull(realtimeTool)
        assertEquals(true, Reflect.on(realtimeTool).get<AtomicBoolean>("executeAllowed").get())
        assertEquals(true, Reflect.on(realtimeTool).get<AtomicBoolean>("toolExecuted").get())
        assertEquals("select_emails", JsonObject(Reflect.on(realtimeTool).get<String>("toolArgs")).getString("name"))

        val responseIdToConvoId = Reflect.on(session).get<Map<String, String>>("responseIdToConvoId")
        assertEquals(2, responseIdToConvoId.size)
        assertEquals("item_AU0Bm4zcud6sRKZ5hhajp", responseIdToConvoId["resp_AU0Bpc05T4L2oW0dxHepe"])
        assertEquals("item_AU0Bm4zcud6sRKZ5hhajp", responseIdToConvoId["resp_AU0BqyVw3pEv3mvN6DlMo"])

        session.dispose()
        socketSession!!.close()
        server.stop()
    }
}

