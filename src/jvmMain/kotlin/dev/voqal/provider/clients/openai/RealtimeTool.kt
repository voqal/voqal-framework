package dev.voqal.provider.clients.openai

import com.intellij.openapi.project.Project
import dev.voqal.services.*
import io.ktor.websocket.*
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.launch

class RealtimeTool(
    private val project: Project,
    private val session: DefaultWebSocketSession,
    convoId: String
) {

    private val log = project.getVoqalLogger(this::class)
    private var executableTool: (() -> Unit)? = null
    private var executionLog = JsonArray()

    init {
        log.info { "Initializing RealtimeTool. Convo id: $convoId" }
    }

    fun executeTool(json: JsonObject) {
        executableTool = {
            doExecution(json)
        }

        executionLog.add(json)
        try {
            executableTool!!.invoke()
        } catch (e: Exception) {
            log.error(e) { "Error executing tool" }
            log.error { "Tool args: $json" }
        }
    }

    private fun doExecution(json: JsonObject) {
        val tool = json.getString("name")
        val args = json.getString("arguments") ?: json.getJsonObject("args").toString()
        log.info("Tool call: $tool - Args: $args")

        val toolService = project.service<VoqalToolService>()
        val voqalTool = toolService.getAvailableTools()[tool]!!
        val callId = json.getString("call_id") ?: json.getString("id")
        toolService.executeTool(args, voqalTool) {
            val chatContentManager = project.service<ChatToolWindowContentManager>()
            chatContentManager.addAssistantToolResponse(voqalTool, callId, args, it)

            if (json.getValue("args") != null) {
                //Gemini Live
                session.send(Frame.Text(JsonObject().put("toolResponse", JsonObject().apply {
                    put("functionResponses", JsonArray().apply {
                        add(JsonObject().apply {
                            put("id", callId)
                            put("name", tool)
                            put("response", JsonObject().apply {
                                put("name", tool)
                                put("content", JsonObject().apply {
                                    put("status", "success")
                                    put("output", it.toString())
                                })
                            })
                        })
                    })
                }).toString()))
            } else {
                //OpenAI Realtime API
                session.send(Frame.Text(JsonObject().apply {
                    put("type", "conversation.item.create")
                    put("item", JsonObject().apply {
                        put("type", "function_call_output")
                        put("call_id", callId)
                        put("output", it.toString())
                    })
                }.toString()))
            }

            if (voqalTool.triggerResponse) {
                log.debug { "Triggering response to results of tool: $tool" }
                session.send(Frame.Text(JsonObject().apply {
                    put("type", "response.create")
                }.toString()))
            }
        }

        if (!voqalTool.manualConfirm) {
            project.scope.launch {
                if (json.getValue("args") == null) {
                    //OpenAI Realtime API
                    session.send(Frame.Text(JsonObject().apply {
                        put("type", "conversation.item.create")
                        put("item", JsonObject().apply {
                            put("type", "function_call_output")
                            put("call_id", callId)
                            put("output", "success")
                        })
                    }.toString()))

                    //todo: dynamic trigger?
                    if (tool == "input_form") {
                        session.send(Frame.Text(JsonObject().apply {
                            put("type", "response.create")
                        }.toString()))
                    }
                }
            }
        }
    }
}
