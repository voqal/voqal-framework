package dev.voqal.assistant.context

import dev.voqal.assistant.VoqalDirective
import dev.voqal.assistant.flaw.error.VoqalError
import io.vertx.core.json.JsonObject

data class FlawContext(
    val previousErrors: MutableList<VoqalError> = mutableListOf()
) : VoqalContext {
    override fun toJson(directive: VoqalDirective): JsonObject {
        return JsonObject().apply {
            put("previousErrors", previousErrors.map { it.toJson() })
        }
    }
}
