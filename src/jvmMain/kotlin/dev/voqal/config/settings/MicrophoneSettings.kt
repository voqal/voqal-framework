package dev.voqal.config.settings

import dev.voqal.config.ConfigurableSettings
import io.vertx.core.json.JsonObject

data class MicrophoneSettings(
    val enabled: Boolean = true,
    val microphoneName: String = "",
    val pauseOnFocusLost: Boolean = true,
    val wakeMode: WakeMode = WakeMode.VOICE_ACTIVITY
) : ConfigurableSettings {

    /**
     * Need to set defaults so config changes don't reset stored config due to parse error.
     */
    constructor(json: JsonObject) : this(
        enabled = json.getBoolean("enabled", true),
        microphoneName = json.getString("microphoneName", ""),
        pauseOnFocusLost = json.getBoolean("pauseOnFocusLost", true),
        wakeMode = WakeMode.lenientValueOf(json.getString("wakeMode", WakeMode.VOICE_ACTIVITY.name))
    )

    override fun toJson(): JsonObject {
        return JsonObject().apply {
            put("enabled", enabled)
            put("microphoneName", microphoneName)
            put("pauseOnFocusLost", pauseOnFocusLost)
            put("wakeMode", wakeMode.name)
        }
    }

    override fun withKeysRemoved(): MicrophoneSettings {
        return copy()
    }

    override fun withPiiRemoved(): MicrophoneSettings {
        return withKeysRemoved().copy(microphoneName = if (microphoneName.isEmpty()) "" else "***")
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
