package dev.voqal.services

import dev.voqal.assistant.tool.VoqalTool

interface ChatToolWindowContentManager {
    fun addUserMessage(transcript: String, speechId: String? = null)
    fun addAssistantMessage(transcript: String, speechId: String? = null)
    fun addAssistantToolResponse(voqalTool: VoqalTool, callId: String, args: String, it: Any?)
    fun getConversation(): List<ChatMessage>

    data class ChatMessage(
        val text: String,
        val isUser: Boolean
    )
}
