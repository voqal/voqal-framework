package dev.voqal.provider

/**
 * Provider that offers wake word detection.
 */
interface WakeProvider : AiProvider {
    override fun isWakeProvider() = true
}
