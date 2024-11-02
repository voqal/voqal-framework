package dev.voqal.utils

import com.intellij.openapi.project.Project
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Allows multiple listeners to receive shared microphone audio data.
 */
class SharedAudioCapture(private val project: Project) {

    companion object {
        const val BUFFER_SIZE = 1532 //24khz -> 16khz = 512 samples
        const val SAMPLE_RATE = 24000
        val FORMAT = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val EMPTY_BUFFER = ByteArray(BUFFER_SIZE)

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
    }

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
