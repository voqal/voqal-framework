package dev.voqal.utils

import com.aallam.openai.api.exception.OpenAIException
import com.intellij.openapi.project.Project
import dev.voqal.assistant.focus.SpokenTranscript
import dev.voqal.config.settings.VoiceDetectionSettings.VoiceDetectionProvider
import dev.voqal.config.settings.WakeSettings.WProvider
import dev.voqal.config.settings.WakeSettings.WakeMode
import dev.voqal.provider.AiProvider
import dev.voqal.provider.VadProvider
import dev.voqal.provider.WakeProvider
import dev.voqal.services.*
import dev.voqal.status.VoqalStatus
import dev.voqal.utils.SharedAudioCapture.AudioDetection.Companion.PRE_SPEECH_BUFFER_SIZE
import dev.voqal.utils.SharedAudioSystem.SharedAudioLine
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.*

/**
 * Allows multiple listeners to receive shared microphone audio data.
 */
class SharedAudioCapture(private val project: Project) {

    companion object {
        const val BUFFER_SIZE = 1532 //24khz -> 16khz = 512 samples
        const val SAMPLE_RATE = 24000
        val FORMAT = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val EMPTY_BUFFER = ByteArray(BUFFER_SIZE) { -1 }

        @JvmStatic
        fun convertBytesToShorts(audioBytes: ByteArray): ShortArray {
            val audioData = ShortArray(audioBytes.size / 2)
            for (i in audioData.indices) {
                audioData[i] = ((audioBytes[2 * i + 1].toInt() shl 8) or (audioBytes[2 * i].toInt() and 0xFF)).toShort()
            }
            return audioData
        }

        fun to16khz(byteArray: ByteArray): ByteArray {
            val sourceFrameLength = byteArray.size / FORMAT.frameSize
            val sourceStream = AudioInputStream(ByteArrayInputStream(byteArray), FORMAT, sourceFrameLength.toLong())

            val targetFormat = AudioFormat(
                16000f,
                FORMAT.sampleSizeInBits,
                FORMAT.channels,
                FORMAT.encoding == AudioFormat.Encoding.PCM_SIGNED,
                FORMAT.isBigEndian
            )
            val targetStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream)
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (targetStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            sourceStream.close()
            targetStream.close()

            return outputStream.toByteArray()
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

    private val log = project.getVoqalLogger(this::class)
    private val listeners: MutableList<AudioDataListener> = CopyOnWriteArrayList()
    private var paused = false
    private var active = false
    private var thread: Thread? = null
    private var microphoneName: String = ""
    private var line: SharedAudioLine? = null
    private val modeProviders = mutableMapOf<String, AiProvider>()

    data class AudioDetection(
        val wakeWordDetected: AtomicBoolean = AtomicBoolean(false),
        val voiceCaptured: AtomicBoolean = AtomicBoolean(false),
        val voiceDetected: AtomicBoolean = AtomicBoolean(false),
        val speechDetected: AtomicBoolean = AtomicBoolean(false),
        val framesBeforeVoiceDetected: CircularListFIFO<Frame> = CircularListFIFO(PRE_SPEECH_BUFFER_SIZE)
    ) {
        companion object {
            const val PRE_SPEECH_BUFFER_SIZE = 25
        }
    }

    fun interface AudioDataListener {
        fun onAudioData(data: ByteArray, detection: AudioDetection)
        fun isTestListener(): Boolean = false
        fun isLiveDataListener(): Boolean = false
        fun sampleRate(): Float = 16000f
    }

    init {
        log.debug { "Using audio format: $FORMAT" }
        val configService = project.service<VoqalConfigService>()
        val config = configService.getConfig()
        if (config.microphoneSettings.microphoneName == "") {
            val availableMicrophones = getAvailableMicrophones()
            if (availableMicrophones.isNotEmpty()) {
                configService.updateConfig(
                    config.microphoneSettings.copy(
                        microphoneName = availableMicrophones.first().name
                    )
                )
            }
        } else {
            microphoneName = config.microphoneSettings.microphoneName
        }
        if (config.microphoneSettings.enabled) {
            startCapture()
        }

        var enabled = config.microphoneSettings.enabled
        val statusService = project.service<VoqalStatusService>()
        statusService.onStatusChange { it, _ ->
            if (!enabled && it == VoqalStatus.IDLE) {
                enabled = true
                restart()
            } else if (it == VoqalStatus.DISABLED) {
                enabled = false
                cancel()
            }
        }

        var vadProvider: VoiceDetectionProvider? = config.voiceDetectionSettings.provider
        configService.onConfigChange {
            val newVadProvider = it.voiceDetectionSettings.provider
            if (vadProvider == VoiceDetectionProvider.NONE && newVadProvider != VoiceDetectionProvider.NONE) {
                log.info { "Voice detection enabled" }
                vadProvider = newVadProvider
                restart()
            } else if (vadProvider != VoiceDetectionProvider.NONE && newVadProvider == VoiceDetectionProvider.NONE) {
                log.info { "Voice detection disabled" }
                vadProvider = newVadProvider
                restart()
            } else if (it.microphoneSettings.enabled && !active) {
                log.info { "Microphone settings changed. Restarting audio capture" }
                restart()
            } else if (!it.microphoneSettings.enabled && active) {
                log.info { "Microphone settings changed. Stopping audio capture" }
                cancel()
            }

            project.scope.launch {
                val aiProvider = configService.getAiProvider()
                configService.getConfig().promptLibrarySettings.prompts.forEach {
                    val provider = aiProvider.findProvider(it.languageModel)
                    if (provider == null) {
                        log.warn { "Unable to find provider: " + it.languageModel }
                    } else {
                        modeProviders[it.promptName] = provider
                    }
                }
            }
        }
    }

    fun setMicrophone(project: Project, microphoneName: String) {
        if (microphoneName == this.microphoneName) {
            return
        }
        this.microphoneName = microphoneName

        project.scope.launch {
            val configService = project.service<VoqalConfigService>()
            val config = configService.getConfig()
            configService.updateConfig(
                config.microphoneSettings.copy(
                    microphoneName = microphoneName
                )
            )

            restart()
        }
    }

    fun startCapture() {
        if (System.getProperty("VQL_TEST_MODE") == "true") return
        log.debug { "Starting shared audio capture" }
        active = true
        thread = Thread { captureAudio() }.apply {
            name = "Voqal Shared Audio Capture"
            isDaemon = true
            start()
        }
    }

    fun registerListener(listener: AudioDataListener) {
        //ensure no dupe listeners
        val listenersOfSameType = listeners.filter { it::class == listener::class }
        if (listener.isTestListener() && listenersOfSameType.any { it.isTestListener() }) {
            log.warn { "Test listener already registered" }
            return
        } else if (!listener.isTestListener() && listenersOfSameType.any { !it.isTestListener() }) {
            log.warn { "Listener already registered" }
            return
        }

        listeners.add(listener)
        log.debug { "Added audio data listeners. Active listeners: ${listeners.size}" }
    }

    fun removeListener(listener: AudioDataListener) {
        listeners.remove(listener)
        log.debug { "Removed audio data listener. Active listeners: ${listeners.size}" }
    }

    private fun captureAudio() {
        try {
            val configService = project.service<VoqalConfigService>()
            val availableMicrophones = getAvailableMicrophones()
            if (availableMicrophones.isEmpty()) {
                log.warn { "No microphone available" }
                return
            }
            log.debug { "Available microphones: $availableMicrophones" }
            microphoneName = configService.getConfig().microphoneSettings.microphoneName
            if (microphoneName.isEmpty()) {
                log.info { "Using default microphone" }
            } else {
                log.info { "Using microphone: $microphoneName" }
            }

            val mixerInfo = availableMicrophones.firstOrNull { it.name == microphoneName }
                ?: availableMicrophones.firstOrNull()
            val line = if (mixerInfo != null) {
                SharedAudioSystem.getTargetDataLine(project, FORMAT, mixerInfo)
            } else {
                SharedAudioSystem.getTargetDataLine(project, FORMAT)
            }
            this.line = line

            var index = 0L
            val audioQueue = LinkedBlockingQueue<Frame>()
            val buffer = ByteArray(BUFFER_SIZE)
            val captureJob = CoroutineScope(Dispatchers.IO).launch {
                line.use {
                    while (active) {
                        val bytesRead = line.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            audioQueue.put(Frame(index++, buffer.copyOf(bytesRead)))
                        }
                    }
                }
            }

            var wakeWordDetected = false
            var voiceCaptured = false
            var speechDetected = false
            var readyForMicrophoneAudio = false
            val capturedVoice = LinkedList<Frame>()
            val audioDetection = AudioDetection()
            val aiProvider = configService.getAiProvider()
            configService.getConfig().promptLibrarySettings.prompts.forEach {
                val provider = aiProvider.findProvider(it.languageModel)
                if (provider == null) {
                    log.warn { "Unable to find provider: " + it.languageModel }
                } else {
                    modeProviders[it.promptName] = provider
                }
            }

            val processJob = CoroutineScope(Dispatchers.Default).launch {
                while (active) {
                    val audioData = audioQueue.take()
                    val liveDataListeners = listeners.filter { it.isLiveDataListener() }
                    val modeProvider = modeProviders[configService.getActivePromptName()]
                    val testMode = listeners.any { it.isTestListener() }
                    if (!testMode && paused) continue

                    for (listener in liveDataListeners) {
                        val updateListener = (testMode && listener.isTestListener()) ||
                                (!testMode && !listener.isTestListener())
                        if (updateListener) {
                            if (!testMode && !listener.isTestListener()) {
                                if ((listener !is VadProvider && listener !is WakeProvider) && listener !== modeProvider) {
                                    continue //ignore audio, mode provider is handling
                                }
                            }

                            if (listener.sampleRate() == 24000f) {
                                listener.onAudioData(audioData.data, audioDetection)
                            } else if (listener.sampleRate() == 16000f) {
                                listener.onAudioData(to16khz(audioData.data), audioDetection)
                            } else {
                                throw UnsupportedOperationException("Sample rate must be 16khz or 24khz")
                            }
                        }
                    }

                    val config = configService.getConfig()
                    if (!voiceCaptured && audioDetection.voiceCaptured.get()) {
                        log.debug { "Voice captured. Frame: ${audioData.index}" }
                        voiceCaptured = true
                    } else if (!wakeWordDetected && !speechDetected && voiceCaptured &&
                        !audioDetection.voiceCaptured.get() && !audioDetection.voiceDetected.get()
                    ) {
                        log.debug { "Insufficient voice captured. Frame: ${audioData.index}" }
                        voiceCaptured = false
                    }
                    if (audioDetection.wakeWordDetected.get()) {
                        log.info { "Wake word detected. Frame: ${audioData.index}" }
                        wakeWordDetected = true
                        audioDetection.wakeWordDetected.set(false)

                        val wakeSettings = config.wakeSettings
                        if (wakeSettings.provider != WProvider.NONE && wakeSettings.wakeMode == WakeMode.WAKE_WORD) {
                            speechDetected = false
                            audioDetection.framesBeforeVoiceDetected.clear()
                            capturedVoice.clear()
                            //todo: active speech should keep line open, no speech should trigger close
                        }

                        project.scope.launch {
                            val directiveService = project.service<VoqalDirectiveService>()
                            directiveService.wakeWordDetected()
                        }
                    } else if (audioDetection.speechDetected.get()) {
                        if (audioDetection.framesBeforeVoiceDetected.isNotEmpty()) {
                            log.info { "Talking started. Frame: ${audioData.index}" }
                            speechDetected = true
                            audioDetection.framesBeforeVoiceDetected.forEach {
                                capturedVoice.add(it)
                            }
                            audioDetection.framesBeforeVoiceDetected.clear()
                        }
                        capturedVoice.add(audioData)
                    } else if (speechDetected && !audioDetection.speechDetected.get()) {
                        log.info { "Talking stopped. Frame: ${audioData.index}" }
                        capturedVoice.add(audioData)

                        //ensure captured voice has no skipped frames
                        val firstFrameIndex = capturedVoice.first().index
                        for (i in 0 until capturedVoice.size) {
                            if (capturedVoice[i].index != firstFrameIndex + i) {
                                log.warn { "Skipped frame detected: ${capturedVoice[i].index}" }
                            }
                        }
                        log.debug { "Captured voice frames: $firstFrameIndex - ${capturedVoice.last().index}" }

                        val mergedAudioArray = ByteArray(capturedVoice.sumOf { it.data.size })
                        var offset = 0
                        for (audio in capturedVoice) {
                            System.arraycopy(audio.data, 0, mergedAudioArray, offset, audio.data.size)
                            offset += audio.data.size
                        }
                        val mergedAudio = mergedAudioArray.copyOf(mergedAudioArray.size)
                        capturedVoice.clear()
                        speechDetected = false
                        voiceCaptured = false

                        if (testMode) {
                            wakeWordDetected = false
                            continue //skip process test audio to transcript (currently no test mode STT)
                        } else if (!wakeWordDetected) {
                            val wakeSettings = config.wakeSettings
                            if (wakeSettings.provider != WProvider.NONE && wakeSettings.wakeMode == WakeMode.WAKE_WORD) {
                                log.debug { "No wake word detected" }
                                continue
                            }
                        }
                        wakeWordDetected = false

                        val audioLengthMs = (mergedAudio.size.toDouble() / FORMAT.frameSize) * 1000.0 / SAMPLE_RATE
                        log.debug { "Speech audio length: ${audioLengthMs}ms" }

                        val speechDir = File(config.speechToTextSettings.speechDir)
                        speechDir.mkdirs()
                        val speechId = aiProvider.asVadProvider().speechId
                        val speechFile = File(speechDir, "developer-$speechId.wav")
                        convertToWavFormat(mergedAudio, speechFile)

                        val directiveService = project.service<VoqalDirectiveService>()
                        if (modeProvider?.isStmProvider() == true) {
                            if ((modeProvider as? AudioDataListener)?.isLiveDataListener() == true) {
                                log.debug { "Mode provider already sent data" }
                                continue //already sent data
                            }

                            val audioInputStream = AudioSystem.getAudioInputStream(speechFile)
                            val audioLengthSeconds = (audioInputStream.frameLength / audioInputStream.format.frameRate)
                                .toDouble()
                            val audioLengthFormat = if (audioLengthSeconds > 0) {
                                String.format("%.2f", audioLengthSeconds) + "s"
                            } else {
                                "invalid duration"
                            }
                            log.info { "Audio input: $audioLengthFormat" }
//                            project.service<ChatToolWindowContentManager>()
//                                .addUserMessage("Audio input: $audioLengthFormat", speechId)

                            directiveService.handleTranscription(
                                SpokenTranscript("", speechId),
                                usingAudioModality = true
                            )
                        } else if (aiProvider.isSttProvider()) {
                            val sttProvider = aiProvider.asSttProvider()
                            if ((sttProvider as? AudioDataListener)?.isLiveDataListener() == true) {
                                log.debug { "STT provider already sent data" }
                                continue //already sent data
                            }

                            val sttModelName = config.speechToTextSettings.modelName
                            try {
                                val sttStartTime = System.currentTimeMillis()
                                val transcript = sttProvider.transcribe(speechFile, sttModelName)
                                aiProvider.asObservabilityProvider()
                                    .logSttLatency(System.currentTimeMillis() - sttStartTime)
                                val spokenTranscript = SpokenTranscript(transcript, speechId, isFinal = true)
                                log.info { "Transcript: ${spokenTranscript.transcript}" }
                                directiveService.handleTranscription(spokenTranscript)
                            } catch (e: OpenAIException) {
                                val errorMessage = e.message ?: "An unknown error occurred"
                                log.warnChat(errorMessage)
                            } catch (e: Exception) {
                                val errorMessage = e.message ?: "An unknown error occurred"
                                log.errorChat(errorMessage, e)
                            }
                        } else {
                            log.warnChat("No speech-to-text provider available")
                        }
                    } else {
                        audioDetection.framesBeforeVoiceDetected.add(audioData)

                        if (!readyForMicrophoneAudio && audioDetection.framesBeforeVoiceDetected.size == PRE_SPEECH_BUFFER_SIZE) {
                            readyForMicrophoneAudio = true
                            log.info { "Ready for microphone audio" }
                        }
                    }
                }
            }

            runBlocking {
                captureJob.join()
                processJob.cancelAndJoin()
            }
            log.info { "Stopping shared audio capture" }
        } catch (e: InterruptedException) {
            log.warn { "Audio capture interrupted: ${e.message}" }
        } catch (e: LineUnavailableException) {
            log.warn { "Line unavailable: ${e.message}" }
        } catch (e: Exception) {
            if (e.message?.startsWith("Line unsupported") == true) {
                log.warn { e.message }
            } else {
                log.error(e) { "Error while capturing audio: ${e.message}" }
            }
        }
    }

    fun restart() {
        log.debug { "Restarting audio capture" }
        cancel()

        if (project.service<VoqalConfigService>().getConfig().microphoneSettings.enabled) {
            startCapture()
        }
    }

    private fun getAvailableMicrophones(): List<Mixer.Info> {
        val microphones = mutableListOf<Mixer.Info>()
        val mixerInfoArray = AudioSystem.getMixerInfo()
        for (mixerInfo in mixerInfoArray) {
            val mixer = AudioSystem.getMixer(mixerInfo)
            val lineInfoArray = mixer.targetLineInfo
            for (lineInfo in lineInfoArray) {
                if (lineInfo is DataLine.Info && lineInfo.isFormatSupported(FORMAT)) {
                    try {
                        val line = mixer.getLine(lineInfo) as? TargetDataLine
                        line?.let { microphones.add(mixerInfo) }
                    } catch (e: LineUnavailableException) {
                        // This line is unavailable - skip it
                    }
                }
            }
        }
        return microphones
    }

    fun getAvailableMicrophoneNames(): List<String> {
        return getAvailableMicrophones().map { it.name }
    }

    fun pause() {
        if (paused) return
        log.trace { "Pausing audio capture" }
        this.paused = true
    }

    fun resume() {
        if (!paused) return
        log.trace { "Resuming audio capture" }
        this.paused = false
    }

    fun cancel() {
        log.debug { "Cancelling audio capture" }
        this.active = false
        line?.close()
        line = null
        thread?.interrupt()
        thread?.join()
        thread = null
        log.debug { "Audio capture cancelled" }
    }

    fun isPaused(): Boolean {
        return paused
    }

    private fun convertToWavFormat(data: ByteArray, outputFile: File) {
        ByteArrayInputStream(data).use { bis ->
            AudioInputStream(
                bis,
                FORMAT,
                (data.size / FORMAT.frameSize).toLong()
            ).use { ais ->
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile)
            }
        }
    }

    class CircularListFIFO<E>(private val maxCapacity: Int) : ArrayList<E>() {
        override fun add(element: E): Boolean {
            if (size == maxCapacity) {
                removeAt(0)
            }
            return super.add(element)
        }
    }

    data class Frame(
        val index: Long,
        val data: ByteArray
    )
}
