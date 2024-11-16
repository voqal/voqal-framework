package dev.voqal.core

import com.aallam.openai.api.chat.ToolCall
import com.intellij.openapi.actionSystem.AnAction
import dev.voqal.assistant.VoqalResponse
import dev.voqal.assistant.focus.DetectedIntent
import dev.voqal.assistant.focus.SpokenTranscript
import dev.voqal.assistant.tool.VoqalTool
import dev.voqal.services.VoqalToolService
import io.vertx.core.json.JsonObject

open class MockToolService : VoqalToolService {

    override fun getAvailableTools(): Map<String, VoqalTool> {
        return emptyMap()
    }

    override suspend fun intentCheck(spokenTranscript: SpokenTranscript): DetectedIntent? {
        TODO("Not yet implemented")
    }

    override suspend fun handleFunctionCall(
        toolCall: ToolCall.Function,
        response: VoqalResponse
    ): Any? {
        TODO("Not yet implemented")
    }

    override fun executeTool(args: String?, voqalTool: VoqalTool, onFinish: suspend (Any?) -> Unit) {
        TODO("Not yet implemented")
    }

    override suspend fun blindExecute(
        tool: VoqalTool,
        args: JsonObject,
        chatMessage: Boolean,
        memoryId: String?
    ) {
        TODO("Not yet implemented")
    }

    override fun executeAnAction(args: Map<String, Any>, action: AnAction) {
        TODO("Not yet implemented")
    }
}