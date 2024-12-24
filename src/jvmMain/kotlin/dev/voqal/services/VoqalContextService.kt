package dev.voqal.services

import dev.voqal.assistant.VoqalDirective

/**
 * Used to ensure LLM prompts do not contain ignored files or exceed token limits.
 */
interface VoqalContextService {

    fun getTokenCount(text: String): Int
    fun cropAsNecessary(fullDirective: VoqalDirective): VoqalDirective
}
