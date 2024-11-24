package dev.voqal.core

import dev.voqal.assistant.tool.VoqalTool
import dev.voqal.services.ChatToolWindowContentManager

open class MockChatToolWindowContentManager : ChatToolWindowContentManager {

    override fun addUserMessage(transcript: String, speechId: String?) {
    }

    override fun addAssistantMessage(transcript: String, speechId: String?) {
    }

    override fun addAssistantToolResponse(
        voqalTool: VoqalTool,
        callId: String,
        args: String,
        it: Any?
    ) {
    }

    override fun getConversation(): List<ChatToolWindowContentManager.ChatMessage> {
        TODO("Not yet implemented")
    }
}