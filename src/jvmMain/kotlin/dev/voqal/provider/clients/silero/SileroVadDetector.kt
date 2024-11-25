package dev.voqal.provider.clients.silero

import ai.onnxruntime.OrtException
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max

//Source: https://github.com/snakers4/silero-vad
class SileroVadDetector(
    modelPath: String,
    startThreshold: Float,
    endThreshold: Float,
    samplingRate: Int,
    minSilenceDurationMs: Int,
    speechPadMs: Int
) {
    // OnnxModel model used for speech processing
    private val model: SileroVadOnnxModel

    // Threshold for speech start
    private val startThreshold: Float

    // Threshold for speech end
    private val endThreshold: Float

    // Sampling rate
    private val samplingRate: Int

    // Minimum number of silence samples to determine the end threshold of speech
    private val minSilenceSamples: Float

    // Additional number of samples for speech start or end to calculate speech start or end time
    private val speechPadSamples: Float

    // Whether in the triggered state (i.e. whether speech is being detected)
    private var triggered = false

    // Temporarily stored number of speech end samples
    private var tempEnd = 0

    // Number of samples currently being processed
    private var currentSample = 0


    init {
        // Check if the sampling rate is 8000 or 16000, if not, throw an exception
        require(!(samplingRate != 8000 && samplingRate != 16000)) { "does not support sampling rates other than [8000, 16000]" }

        // Initialize the parameters
        this.model = SileroVadOnnxModel(modelPath)
        this.startThreshold = startThreshold
        this.endThreshold = endThreshold
        this.samplingRate = samplingRate
        this.minSilenceSamples = samplingRate * minSilenceDurationMs / 1000f
        this.speechPadSamples = samplingRate * speechPadMs / 1000f
        // Reset the state
        reset()
    }

    // Method to reset the state, including the model state, trigger state, temporary end time, and current sample count
    fun reset() {
        model.resetStates()
        triggered = false
        tempEnd = 0
        currentSample = 0
    }

    fun speechProbability(data: ByteArray): Float {
        val audioData = FloatArray(data.size / 2)
        for (i in audioData.indices) {
            audioData[i] = ((data[i * 2].toInt() and 0xff) or (data[i * 2 + 1].toInt() shl 8)) / 32767.0f
        }

        // Get the length of the audio array as the window size
        val windowSizeSamples = audioData.size
        // Update the current sample count
        currentSample += windowSizeSamples

        // Call the model to get the prediction probability of speech
        var speechProb = 0f
        try {
            speechProb = model.call(arrayOf<FloatArray>(audioData), samplingRate)!![0]
        } catch (e: OrtException) {
            throw RuntimeException(e)
        }
        return speechProb
    }

    //todo: probably some good logic in hear voqal vad should support
    // apply method for processing the audio array, returning possible speech start or end times
    fun apply(data: ByteArray, returnSeconds: Boolean): MutableMap<String?, Double?> {
        // Convert the byte array to a float array

        val audioData = FloatArray(data.size / 2)
        for (i in audioData.indices) {
            audioData[i] = ((data[i * 2].toInt() and 0xff) or (data[i * 2 + 1].toInt() shl 8)) / 32767.0f
        }

        // Get the length of the audio array as the window size
        val windowSizeSamples = audioData.size
        // Update the current sample count
        currentSample += windowSizeSamples

        // Call the model to get the prediction probability of speech
        var speechProb = 0f
        try {
            speechProb = model.call(arrayOf<FloatArray>(audioData), samplingRate)!![0]
        } catch (e: OrtException) {
            throw RuntimeException(e)
        }

        // If the speech probability is greater than the threshold and the temporary end time is not 0, reset the temporary end time
        // This indicates that the speech duration has exceeded expectations and needs to recalculate the end time
        if (speechProb >= startThreshold && tempEnd != 0) {
            tempEnd = 0
        }

        // If the speech probability is greater than the threshold and not in the triggered state, set to triggered state and calculate the speech start time
        if (speechProb >= startThreshold && !triggered) {
            triggered = true
            var speechStart = (currentSample - speechPadSamples).toInt()
            speechStart = max(speechStart.toDouble(), 0.0).toInt()
            val result: MutableMap<String?, Double?> = HashMap<String?, Double?>()
            // Decide whether to return the result in seconds or sample count based on the returnSeconds parameter
            if (returnSeconds) {
                val speechStartSeconds = speechStart / samplingRate.toDouble()
                val roundedSpeechStart =
                    BigDecimal.valueOf(speechStartSeconds).setScale(1, RoundingMode.HALF_UP).toDouble()
                result.put("start", roundedSpeechStart)
            } else {
                result.put("start", speechStart.toDouble())
            }

            return result
        }

        // If the speech probability is less than a certain threshold and in the triggered state, calculate the speech end time
        if (speechProb < endThreshold && triggered) {
            // Initialize or update the temporary end time
            if (tempEnd == 0) {
                tempEnd = currentSample
            }
            // If the number of silence samples between the current sample and the temporary end time is less than the minimum silence samples, return null
            // This indicates that it is not yet possible to determine whether the speech has ended
            if (currentSample - tempEnd < minSilenceSamples) {
                return mutableMapOf<String?, Double?>()
            } else {
                // Calculate the speech end time, reset the trigger state and temporary end time
                val speechEnd = (tempEnd + speechPadSamples).toInt()
                tempEnd = 0
                triggered = false
                val result: MutableMap<String?, Double?> = HashMap<String?, Double?>()

                if (returnSeconds) {
                    val speechEndSeconds = speechEnd / samplingRate.toDouble()
                    val roundedSpeechEnd =
                        BigDecimal.valueOf(speechEndSeconds).setScale(1, RoundingMode.HALF_UP).toDouble()
                    result.put("end", roundedSpeechEnd)
                } else {
                    result.put("end", speechEnd.toDouble())
                }
                return result
            }
        }

        // If the above conditions are not met, return null by default
        return mutableMapOf<String?, Double?>()
    }

    @Throws(OrtException::class)
    fun close() {
        reset()
        model.close()
    }
}
