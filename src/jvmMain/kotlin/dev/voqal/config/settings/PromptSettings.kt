package dev.voqal.config.settings

import dev.voqal.config.ConfigurableSettings
import dev.voqal.config.settings.BrowserSettings.Companion.getDefaultVoqalHome
import io.vertx.core.json.JsonObject
import java.nio.file.Paths

data class PromptSettings(
    val provider: PProvider = PProvider.VOQAL,
    val promptName: String = "",
    val promptFile: String = "",
    val promptText: String = "",
    val promptUrl: String = "",
    val languageModel: String = "",
    val decomposeDirectives: Boolean = false,
    val vectorStoreId: String = "",
    val assistantId: String = "",
    val assistantThreadId: String = "",
    val streamCompletions: Boolean = false,
    val functionCalling: FunctionCalling = FunctionCalling.NATIVE,
    val toolsDir: String = Paths.get(getDefaultVoqalHome().absolutePath, "tools").toString()
) : ConfigurableSettings {

    /**
     * Need to set defaults so config changes don't reset stored config due to parse error.
     */
    constructor(json: JsonObject) : this(
        provider = PProvider.lenientValueOf(json.getString("provider") ?: PProvider.VOQAL.name),
        promptName = json.getString("promptName", ""),
        promptFile = json.getString("promptFile", ""),
        promptText = json.getString("promptText", ""),
        promptUrl = json.getString("promptUrl", ""),
        languageModel = json.getString("languageModel", json.getString("modelName", "")),
        decomposeDirectives = json.getBoolean("decomposeDirectives", false),
        vectorStoreId = json.getString("vectorStoreId", ""),
        assistantId = json.getString("assistantId", ""),
        assistantThreadId = json.getString("assistantThreadId", ""),
        streamCompletions = json.getBoolean("streamCompletions", false),
        functionCalling = FunctionCalling.lenientValueOf(
            json.getString("functionCalling", FunctionCalling.NATIVE.name)
        ),
        toolsDir = json.getString(
            "toolsDir", Paths.get(getDefaultVoqalHome().absolutePath, "tools").toString()
        )
    )

    override fun toJson(): JsonObject {
        return JsonObject().apply {
            put("provider", provider.name)
            put("promptName", promptName)
            put("promptFile", promptFile)
            put("promptText", promptText)
            put("promptUrl", promptUrl)
            put("languageModel", languageModel)
            put("decomposeDirectives", decomposeDirectives)
            put("vectorStoreId", vectorStoreId)
            put("assistantId", assistantId)
            put("assistantThreadId", assistantThreadId)
            put("streamCompletions", streamCompletions)
            put("functionCalling", functionCalling.name)
            put("toolsDir", toolsDir)
        }
    }

    override fun withKeysRemoved(): PromptSettings {
        return copy()
    }

    override fun withPiiRemoved(): PromptSettings {
        return withKeysRemoved().copy(
            promptFile = if (promptFile.isEmpty()) "" else "***",
            promptText = if (promptText.isEmpty()) "" else "***",
            promptUrl = if (promptUrl.isEmpty()) "" else "***",
            languageModel = if (languageModel.isEmpty()) "" else "***",
            toolsDir = if (toolsDir.isEmpty()) "" else "***",
        )
    }

    enum class PProvider(val displayName: String) {
        VOQAL("Voqal"),
        CUSTOM_TEXT("Custom Text"),
        CUSTOM_FILE("Custom File"),
        CUSTOM_URL("Custom URL");

        companion object {
            @JvmStatic
            fun lenientValueOf(str: String): PProvider {
                if (str.equals("DEFAULT", true)) return VOQAL
                return PProvider.valueOf(str.uppercase().replace(" ", "_"))
            }
        }
    }

    enum class FunctionCalling {
        NATIVE,
        MARKDOWN;

        val displayName = name.replace("_", " ").lowercase().capitalize()

        companion object {
            @JvmStatic
            fun lenientValueOf(str: String): FunctionCalling {
                return FunctionCalling.valueOf(str.replace(" ", "_").uppercase())
            }
        }
    }
}
