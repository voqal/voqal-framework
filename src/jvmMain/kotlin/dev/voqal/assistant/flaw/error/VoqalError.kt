package dev.voqal.assistant.flaw.error

import com.aallam.openai.api.chat.ChatCompletion
import dev.voqal.assistant.flaw.VoqalFlaw
import io.vertx.core.json.JsonObject

abstract class VoqalError(
    val completion: ChatCompletion
) : Exception(), VoqalFlaw {
    abstract fun toJson(): JsonObject
}
