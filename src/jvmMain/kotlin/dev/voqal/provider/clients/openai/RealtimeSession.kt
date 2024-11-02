package dev.voqal.provider.clients.openai

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import dev.voqal.utils.SharedAudioCapture

class RealtimeSession(
    private val project: Project,
    val wssProviderUrl: String,
    val wssHeaders: Map<String, String> = emptyMap(),
    private val azureHost: Boolean = false
) : Disposable {

    fun onAudioData(data: ByteArray, detection: SharedAudioCapture.AudioDetection) {
    }

    fun sampleRate(): Float {
        throw UnsupportedOperationException()
    }

    override fun dispose() = Unit
}
