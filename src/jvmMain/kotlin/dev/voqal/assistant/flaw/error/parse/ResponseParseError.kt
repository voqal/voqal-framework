package dev.voqal.assistant.flaw.error.parse

import com.aallam.openai.api.chat.ChatCompletion
import dev.voqal.assistant.flaw.error.VoqalError
import io.vertx.core.json.JsonObject

class ResponseParseError(
    completion: ChatCompletion,
    override val message: String
) : VoqalError(completion) {
    override fun toJson(): JsonObject {
        return JsonObject().apply {
            put("message", message)
        }
    }
}
