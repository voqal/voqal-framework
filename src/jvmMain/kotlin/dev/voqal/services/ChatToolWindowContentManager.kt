package dev.voqal.services

import dev.voqal.assistant.tool.VoqalTool

interface ChatToolWindowContentManager {
    fun addUserMessage(transcript: String, speechId: String? = null)
    fun addAssistantMessage(transcript: String, speechId: String? = null)
    fun addAssistantToolResponse(voqalTool: VoqalTool, callId: String, args: String, it: Any?)
}
