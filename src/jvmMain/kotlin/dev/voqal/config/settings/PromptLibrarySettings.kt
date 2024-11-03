package dev.voqal.config.settings

import dev.voqal.config.ConfigurableSettings
import dev.voqal.config.settings.PromptSettings.FunctionCalling
import io.vertx.core.json.JsonObject

data class PromptLibrarySettings(
    val prompts: List<PromptSettings> = emptyList()
) : ConfigurableSettings {

    /**
     * Need to set defaults so config changes don't reset stored config due to parse error.
     */
    constructor(json: JsonObject) : this(
        prompts = json.getJsonArray("prompts")?.map { PromptSettings(it as JsonObject) } ?: emptyList()
    )

    override fun toJson(): JsonObject {
        return JsonObject().apply {
            put("prompts", prompts.map { it.toJson() })
        }
    }

    override fun withKeysRemoved(): PromptLibrarySettings {
        return copy(
            prompts = prompts.map { it.withKeysRemoved() }
        )
    }

    override fun withPiiRemoved(): PromptLibrarySettings {
        return withKeysRemoved().copy(
            prompts = prompts.map { it.withPiiRemoved() }
        )
    }
}
