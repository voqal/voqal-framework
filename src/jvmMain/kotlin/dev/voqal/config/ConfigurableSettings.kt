package dev.voqal.config

import io.vertx.core.json.JsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

interface ConfigurableSettings {
    companion object {
        fun getDefaultVoqalHome(): File {
            val userHome = System.getProperty("user.home")
            val persistentDir = Paths.get(userHome, ".voqal")

            if (Files.notExists(persistentDir)) {
                Files.createDirectory(persistentDir)
            }
            return persistentDir.toFile()
        }
    }

    fun toJson(): JsonObject
    fun withKeysRemoved(): ConfigurableSettings
    fun withPiiRemoved(): ConfigurableSettings
}
