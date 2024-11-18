package dev.voqal.config.settings

import dev.voqal.config.ConfigurableSettings
import io.vertx.core.json.JsonObject

data class SocialConnectionSettings(
    val provider: SCProvider = SCProvider.GMAIL,
    val refreshToken: String = "",
    val scope: String = ""
) : ConfigurableSettings {

    /**
     * Need to set defaults so config changes don't reset stored config due to parse error.
     */
    constructor(json: JsonObject) : this(
        provider = SCProvider.lenientValueOf(json.getString("provider") ?: SCProvider.GMAIL.name),
        refreshToken = json.getString("refreshToken") ?: "",
        scope = json.getString("scope") ?: ""
    )

    override fun toJson(): JsonObject {
        return JsonObject().apply {
            put("provider", provider.name)
            put("refreshToken", refreshToken)
            put("scope", scope)
        }
    }

    override fun withKeysRemoved(): SocialConnectionSettings {
        return copy(
            refreshToken = if (refreshToken.isNotEmpty()) "***" else ""
        )
    }

    override fun withPiiRemoved(): SocialConnectionSettings {
        return withKeysRemoved().copy()
    }

    enum class SCProvider(val displayName: String) {
        GMAIL("Gmail");

        companion object {
            @JvmStatic
            fun lenientValueOf(str: String): SCProvider {
                return SCProvider.valueOf(str.uppercase().replace(" ", "_"))
            }
        }
    }
}
