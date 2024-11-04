package dev.voqal.assistant.context

import dev.voqal.assistant.VoqalDirective
import io.vertx.core.json.JsonObject

/**
 * Holds data used to populate prompt sent to LLM.
 */
interface VoqalContext {
    fun toJson(directive: VoqalDirective): JsonObject
}
