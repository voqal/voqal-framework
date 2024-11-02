package dev.voqal.config.settings

import dev.voqal.config.ConfigurableSettings
import io.vertx.core.json.JsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

data class BrowserSettings(
    val enabled: Boolean = true,
    val microphoneName: String = "",
    val pauseOnFocusLost: Boolean = true,
    val installDir: String = getDefaultVoqalHome().absolutePath,
    val logFile: String = Paths.get(installDir, "voqal-browser.log").toString(),
    val logLevel: String = "INFO",
    val theme: String = "DARK",
    val defaultUrl: String = "https://github.com/voqal",
    val activePrompt: String = "general"
) : ConfigurableSettings {

    companion object {
        fun getDefaultVoqalHome(): File {
            val userHome = System.getProperty("user.home")
            val persistentDir = Paths.get(userHome, ".voqal-browser")

            if (Files.notExists(persistentDir)) {
                Files.createDirectory(persistentDir)
            }
            return persistentDir.toFile()
        }
    }

    /**
     * Need to set defaults so config changes don't reset stored config due to parse error.
     */
    constructor(json: JsonObject) : this(
        enabled = json.getBoolean("enabled", true),
        microphoneName = json.getString("microphoneName", ""),
        pauseOnFocusLost = json.getBoolean("pauseOnFocusLost", true),
        installDir = json.getString(
            "installDir", getDefaultVoqalHome().absolutePath
        ),
        logFile = json.getString(
            "logFile", Paths.get(getDefaultVoqalHome().absolutePath, "voqal-browser.log").toString()
        ),
        logLevel = json.getString("logLevel", "DEBUG"),
        theme = json.getString("theme", "DARK"),
        defaultUrl = json.getString("defaultUrl", "https://github.com/voqal"),
        activePrompt = json.getString("activePrompt", "general")
    )

    override fun toJson(): JsonObject {
        return JsonObject().apply {
            put("enabled", enabled)
            put("microphoneName", microphoneName)
            put("pauseOnFocusLost", pauseOnFocusLost)
            put("installDir", installDir)
            put("logFile", logFile)
            put("logLevel", logLevel)
            put("theme", theme)
            put("defaultUrl", defaultUrl)
            put("activePrompt", activePrompt)
        }
    }

    override fun withKeysRemoved(): BrowserSettings {
        return copy()
    }

    override fun withPiiRemoved(): BrowserSettings {
        return withKeysRemoved().copy(
            microphoneName = if (microphoneName.isEmpty()) "" else "***",
            installDir = "***",
            logFile = "***"
        )
    }
}
