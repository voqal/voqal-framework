package dev.voqal.config.settings

import dev.voqal.config.ConfigurableSettings
import io.vertx.core.json.JsonObject

data class WakeSettings(
    val provider: WProvider = WProvider.NONE,
    val providerKey: String = "",
    val wakeWord: String = "Voqal",
    val customWakeWordFile: String = "",
    val wakeMode: WakeMode = WakeMode.VOICE_ACTIVITY
) : ConfigurableSettings {

    /**
     * Need to set defaults so config changes don't reset stored config due to parse error.
     */
    constructor(json: JsonObject) : this(
        provider = WProvider.lenientValueOf(json.getString("provider") ?: WProvider.NONE.name),
        providerKey = json.getString("providerKey", ""),
        wakeWord = json.getString("wakeWord", WakeWord.Voqal.name),
        customWakeWordFile = json.getString("customWakeWordFile", ""),
        wakeMode = WakeMode.lenientValueOf(json.getString("wakeMode", WakeMode.VOICE_ACTIVITY.name))
    )

    override fun toJson(): JsonObject {
        return JsonObject().apply {
            put("provider", provider.name)
            put("providerKey", providerKey)
            put("wakeWord", wakeWord)
            put("customWakeWordFile", customWakeWordFile)
            put("wakeMode", wakeMode.name)
        }
    }

    override fun withKeysRemoved(): WakeSettings {
        return copy(providerKey = if (providerKey == "") "" else "***")
    }

    override fun withPiiRemoved(): WakeSettings {
        return withKeysRemoved().copy(customWakeWordFile = if (customWakeWordFile.isEmpty()) "" else "***")
    }

    enum class WProvider(val displayName: String) {
        NONE("None"),
        PICOVOICE("Picovoice");

        fun isKeyRequired(): Boolean {
            return this in setOf(PICOVOICE)
        }

        fun isCustomWakeFileAllowed(): Boolean {
            return this in setOf(PICOVOICE)
        }

        companion object {
            @JvmStatic
            fun lenientValueOf(str: String): WProvider {
                return WProvider.valueOf(str)
            }
        }
    }

    enum class WakeWord {
        Americano,
        Blueberry,
        Bumblebee,
        Computer,
        Grapefruit,
        Grasshopper,
        Jarvis,
        Picovoice,
        Porcupine,
        Terminator,
        Voqal,
        CustomFile;

        companion object {
            @JvmStatic
            fun valueOfOrNull(str: String): WakeWord? {
                return try {
                    WakeWord.valueOf(str)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }
    }

    enum class WakeMode(val displayName: String) {
        VOICE_ACTIVITY("Voice Activity"),
        WAKE_WORD("Wake Word");

        companion object {
            @JvmStatic
            fun lenientValueOf(str: String): WakeMode {
                return WakeMode.valueOf(str.uppercase().replace(" ", "_"))
            }
        }
    }
}
