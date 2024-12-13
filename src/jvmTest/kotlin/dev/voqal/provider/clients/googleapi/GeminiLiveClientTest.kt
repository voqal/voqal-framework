package dev.voqal.provider.clients.googleapi

import dev.voqal.assistant.VoqalDirective
import dev.voqal.assistant.context.AssistantContext
import dev.voqal.assistant.context.VoqalContext
import dev.voqal.assistant.focus.SpokenTranscript
import dev.voqal.config.VoqalConfig
import dev.voqal.config.settings.PromptLibrarySettings
import dev.voqal.config.settings.PromptSettings
import dev.voqal.core.MockDirectiveService
import dev.voqal.core.MockProject
import dev.voqal.utils.SharedAudioCapture
import io.vertx.core.Promise
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.io.ByteArrayOutputStream
import java.io.File
import javax.sound.sampled.AudioSystem

class GeminiLiveClientTest {

    @Test
    fun sessionUpdate(): Unit = runBlocking {
        val project = MockProject()
        var promptText = "Current time: 10:10 AM"
        var promptSettings = PromptSettings(
            promptName = "voqal",
            provider = PromptSettings.PProvider.CUSTOM_TEXT,
            promptText = promptText
        )
        project.configService.setCachedConfig(
            VoqalConfig(promptLibrarySettings = PromptLibrarySettings(prompts = listOf(promptSettings)))
        )
        project.directiveService = object : MockDirectiveService(project) {
            override fun createDirective(
                transcription: SpokenTranscript,
                textOnly: Boolean,
                usingAudioModality: Boolean,
                chatMessage: Boolean,
                promptName: String
            ): VoqalDirective {
                return VoqalDirective(project, mutableMapOf<String, VoqalContext>().apply {
                    put("assistant", AssistantContext(mock {}, emptyList(), mock {}, promptSettings))
                })
            }
        }

        val client = GeminiLiveClient(
            "test",
            project,
            System.getenv("GOOGLE_API_KEY"),
            "gemini-2.0-flash-exp",
            responseModalities = listOf("TEXT")
        )
        val timeout = 5_000L
        var startTime = System.currentTimeMillis()
        while (!client.isSetupComplete()) {
            if (System.currentTimeMillis() - startTime > timeout) {
                throw Exception("Gemini client setup failed (timed out)")
            }
            Thread.sleep(100)
        }

        val audioFile = File("src/jvmTest/resources/audio/what-time-is-it.wav")
        val audioPcm = extractPcmData(audioFile)

        val firstResponse = Promise.promise<String>()
        client.setConvoListener { string ->
            firstResponse.complete(string)
        }

        val voiceDetected = SharedAudioCapture.AudioDetection()
        client.onAudioData(audioPcm, voiceDetected)

        val responseText = firstResponse.future().coAwait()
        assertTrue(responseText.contains("10:10 AM"))

        //force session update
        promptText = "Current time: 10:11 AM"
        promptSettings = PromptSettings(
            promptName = "voqal",
            provider = PromptSettings.PProvider.CUSTOM_TEXT,
            promptText = promptText
        )
        project.configService.setCachedConfig(
            VoqalConfig(promptLibrarySettings = PromptLibrarySettings(prompts = listOf(promptSettings)))
        )
        Thread.sleep(2000)

        startTime = System.currentTimeMillis()
        while (!client.isSetupComplete()) {
            if (System.currentTimeMillis() - startTime > timeout) {
                throw Exception("Gemini client setup failed (timed out)")
            }
            Thread.sleep(100)
        }

        val secondResponse = Promise.promise<String>()
        client.setConvoListener { string ->
            secondResponse.complete(string)
        }

        client.onAudioData(audioPcm, voiceDetected)

        val responseText2 = secondResponse.future().coAwait()
        assertTrue(responseText2.contains("10:11 AM"))
    }

    fun extractPcmData(wavFile: File): ByteArray {
        AudioSystem.getAudioInputStream(wavFile).use { audioInputStream ->
            val buffer = ByteArray(4096)
            val output = ByteArrayOutputStream()

            var bytesRead: Int
            while (audioInputStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            return output.toByteArray()
        }
    }
}