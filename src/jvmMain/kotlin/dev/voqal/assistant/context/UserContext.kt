package dev.voqal.assistant.context

import io.vertx.core.json.JsonObject
import java.time.Instant

data class UserContext(
    val memories: Map<String, List<Memory>> = emptyMap(),
    val formattedChat: String = ""
) : VoqalContext {

    fun toJson(): JsonObject {
        return JsonObject().apply {
            put("memories", memories)
            put("formattedChat", formattedChat)
        }
    }

    data class Memory(
        val time: Instant,
        val prompt: String
    )
}
