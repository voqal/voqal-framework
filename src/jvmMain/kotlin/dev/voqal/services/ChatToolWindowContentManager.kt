package voqal.services

interface ChatToolWindowContentManager {
    fun addUserMessage(transcript: String, speechId: String? = null)
}
