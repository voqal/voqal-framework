package dev.voqal.provider.clients.silero

import com.google.common.io.Resources
import com.intellij.openapi.project.Project
import dev.voqal.provider.VadProvider
import dev.voqal.provider.clients.picovoice.NativesExtractor
import dev.voqal.services.audioCapture
import dev.voqal.services.getVoqalLogger
import dev.voqal.utils.SharedAudioCapture
import java.io.BufferedOutputStream
import java.io.File

//todo: impl config
class SileroVadClient(
    private val project: Project,
    var voiceDetectionThreshold: Double,
    override var voiceSilenceThreshold: Long,
    override var speechSilenceThreshold: Long,
    override var sustainedDurationMillis: Long,
    override var amnestyPeriodMillis: Long = speechSilenceThreshold * 2,
    override var testMode: Boolean = false
) : VadProvider(project) {

    private val SAMPLE_RATE = 16000
    private val START_THRESHOLD = 0.6f
    private val END_THRESHOLD = 0.45f
    private val MIN_SILENCE_DURATION_MS = 600
    private val SPEECH_PAD_MS = 500
    private val log = project.getVoqalLogger(this::class)
    private val vadDetector: SlieroVadDetector

    init {
        val installDir = NativesExtractor.workingDirectory.parentFile
        val sileroVadFile = File(installDir, "silero_vad.onnx")
        if (!sileroVadFile.exists()) {
            log.debug { "Extracting silero_vad.onnx" }
            Resources.getResource(SileroVadClient::class.java, "/silero_vad.onnx").openStream().use { input ->
                BufferedOutputStream(sileroVadFile.outputStream()).use { output ->
                    input.copyTo(output)
                }
            }
            if (!sileroVadFile.exists()) {
                throw IllegalStateException("Failed to extract silero_vad.onnx")
            }
            log.debug { "Extracted silero_vad.onnx to ${sileroVadFile.absolutePath}" }
        }

        vadDetector = SlieroVadDetector(
            sileroVadFile.absolutePath,
            START_THRESHOLD,
            END_THRESHOLD,
            SAMPLE_RATE,
            MIN_SILENCE_DURATION_MS,
            SPEECH_PAD_MS
        )

        project.audioCapture.registerListener(this)
        log.debug { "SileroVadClient initialized" }
    }

    override fun onAudioData(data: ByteArray, detection: SharedAudioCapture.AudioDetection) {
        voiceProbability = vadDetector.speechProbability(data) * 100.00
        if (voiceProbability >= voiceDetectionThreshold) {
            handleVoiceDetected()
        } else {
            handleVoiceNotDetected()
        }
        detection.voiceCaptured.set(isVoiceCaptured)
        detection.voiceDetected.set(isVoiceDetected)
        detection.speechDetected.set(isSpeechDetected)
    }

    override fun dispose() = project.audioCapture.removeListener(this)
}
