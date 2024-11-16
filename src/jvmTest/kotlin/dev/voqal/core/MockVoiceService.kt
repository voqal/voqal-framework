package dev.voqal.core

import dev.voqal.config.settings.TextToSpeechSettings
import dev.voqal.services.VoqalVoiceService
import java.io.InputStream

class MockVoiceService : VoqalVoiceService {
    override fun playSound(input: String, tts: TextToSpeechSettings?) {
    }

    override suspend fun playVoiceAndWait(input: String, tts: TextToSpeechSettings?) {
    }

    override fun playStreamingWavFile(inputStream: InputStream) {
    }
}