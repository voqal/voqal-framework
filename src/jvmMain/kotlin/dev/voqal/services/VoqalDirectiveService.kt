package dev.voqal.services

import com.intellij.openapi.Disposable
import dev.voqal.assistant.VoqalDirective
import dev.voqal.assistant.VoqalResponse
import dev.voqal.assistant.focus.SpokenTranscript
import dev.voqal.config.settings.TextToSpeechSettings
import dev.voqal.status.VoqalStatus.*
import kotlinx.serialization.json.*

/**
 * Handles developer transcriptions, transforming them into Voqal directives, executing them, and handling the
 * resulting responses.
 */
interface  VoqalDirectiveService {

    companion object {
        fun convertJsonElementToMap(jsonElement: JsonElement): Any? = when (jsonElement) {
            is JsonObject -> jsonElement.mapValues { convertJsonElementToMap(it.value) }
            is JsonArray -> jsonElement.map { convertJsonElementToMap(it) }
            is JsonPrimitive -> when {
                jsonElement.isString -> jsonElement.content
                jsonElement.booleanOrNull != null -> jsonElement.boolean
                jsonElement.intOrNull != null -> jsonElement.int
                jsonElement.longOrNull != null -> jsonElement.long
                jsonElement.doubleOrNull != null -> jsonElement.double
                jsonElement.floatOrNull != null -> jsonElement.float
                else -> jsonElement.contentOrNull
            }

            else -> jsonElement
        }
    }

    suspend fun handlePartialTranscription(spokenTranscript: SpokenTranscript)

    /**
     * Send the finalized developer transcription to the appropriate Voqal mode for processing.
     */
    suspend fun handleTranscription(
        spokenTranscript: SpokenTranscript,
        textOnly: Boolean = false,
        chatMessage: Boolean = false,
        usingAudioModality: Boolean = false
    )

    fun reset()

//    fun onDirectiveExecution(
//        disposable: Disposable = project.service<ProjectScopedService>(),
//        listener: (VoqalDirectiveService, DirectiveExecution) -> Unit
//    )

    fun isActive(): Boolean

    fun createDirective(
        transcription: SpokenTranscript,
        textOnly: Boolean = false,
        usingAudioModality: Boolean = false,
        chatMessage: Boolean = false,
        promptName: String = "Idle Mode"
    ): VoqalDirective

    suspend fun executeDirective(directive: VoqalDirective)

    suspend fun handleResponse(
        input: String,
        tts: TextToSpeechSettings? = null,
        isTextOnly: Boolean = false,
        response: VoqalResponse? = null
    )
}
