package dev.voqal.assistant.context

import com.intellij.openapi.vfs.VirtualFile
import io.vertx.core.json.JsonObject

data class DeveloperContext(
    val transcription: String,
    val viewingFile: VirtualFile? = null,
    val activeBreakpoints: List<Int> = emptyList(),
    val textOnly: Boolean = false,
    val partialTranscription: Boolean = false,
    val chatMessage: Boolean = false
) : VoqalContext {

    fun toJson(): JsonObject {
        return JsonObject().apply {
            if (transcription.isNotEmpty()) {
                put("transcription", transcription + "\n")
            } else {
                put("transcription", transcription)
            }
            viewingFile?.let { put("viewingFile", it.path) }
            put("activeBreakpoints", activeBreakpoints)
            put("textOnly", textOnly)
            put("partialTranscription", partialTranscription)
            put("chatMessage", chatMessage)
        }
    }
}
