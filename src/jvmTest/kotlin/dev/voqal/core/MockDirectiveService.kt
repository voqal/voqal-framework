package dev.voqal.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import dev.voqal.assistant.VoqalDirective
import dev.voqal.assistant.VoqalResponse
import dev.voqal.assistant.context.AssistantContext
import dev.voqal.assistant.context.VoqalContext
import dev.voqal.assistant.focus.DirectiveExecution
import dev.voqal.assistant.focus.SpokenTranscript
import dev.voqal.config.settings.PromptSettings
import dev.voqal.config.settings.TextToSpeechSettings
import dev.voqal.services.VoqalDirectiveService
import org.mockito.kotlin.mock

open class MockDirectiveService(private val project: Project) : VoqalDirectiveService {

    override suspend fun handlePartialTranscription(spokenTranscript: SpokenTranscript) {
        TODO("Not yet implemented")
    }

    override suspend fun handleTranscription(
        spokenTranscript: SpokenTranscript,
        textOnly: Boolean,
        chatMessage: Boolean,
        usingAudioModality: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override fun onDirectiveExecution(
        disposable: Disposable?,
        listener: (VoqalDirectiveService, DirectiveExecution) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun isActive(): Boolean {
        TODO("Not yet implemented")
    }

    override fun createDirective(
        transcription: SpokenTranscript,
        textOnly: Boolean,
        usingAudioModality: Boolean,
        chatMessage: Boolean,
        promptName: String
    ): VoqalDirective {
        return VoqalDirective(project, mutableMapOf<String, VoqalContext>().apply {
            put("assistant", AssistantContext(mock {}, emptyList(), mock {}, PromptSettings()))
        })
    }

    override suspend fun executeDirective(directive: VoqalDirective, addMessage: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun handleResponse(
        input: String,
        tts: TextToSpeechSettings?,
        isTextOnly: Boolean,
        response: VoqalResponse?
    ) {
        TODO("Not yet implemented")
    }
}