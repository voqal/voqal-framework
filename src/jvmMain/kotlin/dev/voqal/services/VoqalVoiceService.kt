package dev.voqal.services

import dev.voqal.config.settings.TextToSpeechSettings
import java.io.InputStream

/**
 * Used for TTS.
 */
interface VoqalVoiceService {
    fun playSound(input: String, tts: TextToSpeechSettings? = null)
    suspend fun playVoiceAndWait(input: String, tts: TextToSpeechSettings? = null)
    fun playStreamingWavFile(inputStream: InputStream)
}
