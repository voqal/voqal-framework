package dev.voqal.provider.clients.openai

import com.intellij.openapi.project.Project
import dev.voqal.services.*
import dev.voqal.utils.SharedAudioCapture
import dev.voqal.utils.SharedAudioCapture.Companion.FORMAT
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat

class RealtimeAudio(private val project: Project, convoId: String) {

    private val log = project.getVoqalLogger(this::class)
    private val audioPlaying = AtomicBoolean(false)
    private val audioPlayed = AtomicBoolean(false)
    private val audioFinished = AtomicBoolean(false)
    private val audioWrote = AtomicBoolean(false)
    private var pis = PipedInputStream()
    private var pos = PipedOutputStream()
    private val channel = Channel<Job>(capacity = Channel.UNLIMITED).apply {
        project.scope.launch { consumeEach { it.join() } }
    }
    private val audioBytesWritten = AtomicLong(0)

    init {
        log.debug { "Initializing RealtimeAudio. Convo id: $convoId" }
        pos.connect(pis)
    }

    fun addAudioData(json: JsonObject) {
        audioWrote.set(true)
        channel.trySend(project.scope.launch(start = CoroutineStart.LAZY) {
            try {
                val data = if (json.containsKey("delta")) {
                    json.getString("delta") //OpenAI Realtime API
                } else if (json.containsKey("inlineData")) {
                    json.getJsonObject("inlineData").getString("data") //Gemini Live
                } else {
                    throw IllegalStateException("Unknown audio data format")
                }
                val audioData = Base64.getDecoder().decode(data)
                audioBytesWritten.addAndGet(audioData.size.toLong())
                pos.write(audioData)
            } catch (e: Throwable) {
                if (audioPlaying.get()) {
                    log.error(e) { "Error writing audio data" }
                } else {
                    log.trace { "Ignoring audio data error on non-playing audio" }
                }
            }
        })

        if (!audioPlaying.get()) {
            log.debug { "Started audio via first delta" }
            startAudio()
        }
    }

    fun finishAudio() {
        if (audioFinished.get()) {
            log.debug { "Audio has already been finished" }
            return
        } else if (!audioWrote.get()) {
            log.debug { "No audio data has been written" }
            return
        }

        channel.trySend(project.scope.launch(start = CoroutineStart.LAZY) {
            try {
                pos.write(SharedAudioCapture.EMPTY_BUFFER)

                val targetFormat = AudioFormat(
                    FORMAT.encoding,
                    24000.0f,
                    FORMAT.sampleSizeInBits,
                    FORMAT.channels,
                    FORMAT.frameSize,
                    24000.0f,
                    FORMAT.isBigEndian
                )
                val duration = audioBytesWritten.get().toDouble() / (targetFormat.frameSize * targetFormat.frameRate)
                project.service<VoqalConfigService>().getAiProvider().asObservabilityProvider()
                    .logStmCost(RealtimeSession.calculateTtsCost(duration))
            } catch (e: Throwable) {
                if (audioPlaying.get()) {
                    log.error(e) { "Error writing audio data" }
                } else {
                    log.trace { "Ignoring audio data error on non-playing audio" }
                }
            }
        })
        audioFinished.set(true)
    }

    private fun startAudio() {
        if (audioPlayed.get()) {
            log.debug { "Audio has already been played" }
            return
        } else if (!audioPlaying.compareAndSet(false, true)) {
            log.debug { "Audio is already playing" }
            return
        }

        project.scope.launch {
            project.service<VoqalVoiceService>().playStreamingWavFile(pis)
            audioPlayed.set(true)
            stopAudio()
        }
    }

    fun stopAudio() {
        if (!audioPlaying.get()) {
            log.trace { "Audio is not playing" }
            return
        }

        log.debug { "Stopping audio" }
        try {
            audioPlaying.set(false)
            pos.close()
            pis.close()
            channel.close()
        } catch (e: Throwable) {
            log.error(e) { "Error stopping audio" }
        }
    }
}
