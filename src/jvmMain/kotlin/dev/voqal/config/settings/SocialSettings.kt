package dev.voqal.config.settings

import dev.voqal.config.ConfigurableSettings
import io.vertx.core.json.JsonObject

data class SocialSettings(
    val connections: List<SocialConnectionSettings> = emptyList()
) : ConfigurableSettings {

    /**
     * Need to set defaults so config changes don't reset stored config due to parse error.
     */
    constructor(json: JsonObject) : this(
        connections = json.getJsonArray("connections")?.map {
            SocialConnectionSettings(it as JsonObject)
        } ?: emptyList()
    )

    override fun toJson(): JsonObject {
        return JsonObject().apply {
            put("connections", connections.map { it.toJson() })
        }
    }

    override fun withKeysRemoved(): SocialSettings {
        return copy(
            connections = connections.map { it.withKeysRemoved() }
        )
    }

    override fun withPiiRemoved(): SocialSettings {
        return withKeysRemoved().copy(
            connections = connections.map { it.withPiiRemoved() }
        )
    }
}
