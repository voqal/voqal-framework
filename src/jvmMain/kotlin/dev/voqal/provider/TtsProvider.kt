package dev.voqal.provider

import com.aallam.openai.api.audio.SpeechRequest
import com.aallam.openai.api.audio.Voice
import com.aallam.openai.api.model.ModelId
import io.ktor.utils.io.*
import java.util.*

/**
 * Provider that offers text-to-speech.
 */
interface TtsProvider : AiProvider {

    override fun isTtsProvider() = true
    fun isWavOutput() = false
    fun isRawOutput() = false
    fun isTtsStreamable() = false

    suspend fun speech(request: SpeechRequest): RawAudio
    suspend fun speechStream(request: SpeechStreamRequest): RawAudio =
        throw NotImplementedError("speechStream(request: SpeechStreamRequest) is not implemented")

    data class RawAudio(
        val audio: ByteReadChannel,
        val sampleRate: Float,
        val bitsPerSample: Int,
        val channels: Int
    )

    data class SpeechStreamRequest(
        val model: ModelId,
        val queue: Queue<String>,
        val voice: Voice? = null,
        var isFinished: Boolean = false
    )
}
