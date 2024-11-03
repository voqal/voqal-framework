package dev.voqal.services

import com.intellij.openapi.Disposable
import dev.voqal.assistant.VoqalResponse
import dev.voqal.status.VoqalStatus

/**
 * Keeps track of Voqal's current [VoqalStatus] and notifies listeners when it changes.
 */
interface VoqalStatusService {

    fun getCurrentStatus(): Pair<VoqalStatus, String?>

    fun getStatus(): VoqalStatus

    fun update(status: VoqalStatus, message: String? = null)

    fun onStatusChange(
        disposable: Disposable? = null,
        listener: (VoqalStatus, String?) -> Unit
    )

    fun updateText(input: String, response: VoqalResponse? = null)

    fun warnChat(input: String)
}
