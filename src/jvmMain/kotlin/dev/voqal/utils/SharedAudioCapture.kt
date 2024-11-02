package dev.voqal.utils

import com.intellij.openapi.project.Project
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean

class SharedAudioCapture(private val project: Project) {
    data class AudioDetection(
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
