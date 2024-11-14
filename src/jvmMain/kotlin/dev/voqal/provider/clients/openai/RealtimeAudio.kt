package dev.voqal.provider.clients.openai

import com.intellij.openapi.project.Project
import dev.voqal.services.VoqalVoiceService
import dev.voqal.services.getVoqalLogger
import dev.voqal.services.scope
import dev.voqal.services.service
import dev.voqal.utils.SharedAudioCapture
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

class RealtimeAudio(private val project: Project, val convoId: String) {

    private val log = project.getVoqalLogger(this::class)
    private val audioPlaying = AtomicBoolean(false)
    private val audioPlayed = AtomicBoolean(false)
    private val audioFinished = AtomicBoolean(false)
    private val audioWrote = AtomicBoolean(false)
    private val audioReady = AtomicBoolean(false)
    private var pis = PipedInputStream()
    private var pos = PipedOutputStream()
    private val channel = Channel<Job>(capacity = Channel.UNLIMITED).apply {
        project.scope.launch { consumeEach { it.join() } }
    }

    init {
        log.info("Initializing RealtimeAudio. Convo id: $convoId")
        pos.connect(pis)
    }

    fun addAudioData(json: JsonObject) {
        audioWrote.set(true)
        channel.trySend(project.scope.launch(start = CoroutineStart.LAZY) {
            try {
                pos.write(Base64.getDecoder().decode(json.getString("delta")))
            } catch (e: Throwable) {
                if (audioPlaying.get()) {
                    log.error("Error writing audio data", e)
                } else {
                    log.trace { "Ignoring audio data error on non-playing audio" }
                }
            }
        })

        if (audioReady.get() && !audioPlaying.get()) {
            log.debug { "Started audio via first delta" }
            startAudio(true)
        }
    }

    fun finishAudio() {
        channel.trySend(project.scope.launch(start = CoroutineStart.LAZY) {
            try {
                pos.write(SharedAudioCapture.EMPTY_BUFFER)
            } catch (e: Throwable) {
                if (audioPlaying.get()) {
                    log.error("Error writing audio data", e)
                } else {
                    log.trace { "Ignoring audio data error on non-playing audio" }
                }
            }
        })
        audioFinished.set(true)
    }

    fun startAudio(firstDelta: Boolean = false) {
        if (audioPlayed.get()) {
            log.debug("Audio has already been played")
            return
        } else if (!audioWrote.get()) {
            log.debug("No audio data to play")
            audioReady.set(true)
            return
        } else if (!audioPlaying.compareAndSet(false, true)) {
            log.debug("Audio is already playing")
            return
        }

        if (!firstDelta) {
            log.debug { "Started audio via startAudio" }
        }

        project.scope.launch {
            project.service<VoqalVoiceService>().playStreamingWavFile(pis)
            stopAudio()
        }
    }

    fun stopAudio() {
        if (!audioPlayed.compareAndSet(false, true)) {
            log.debug("Audio is not playing")
            return
        }

        log.debug("Stopping audio")
        try {
            audioPlaying.set(false)
            pos.close()
            pis.close()
            channel.close()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}