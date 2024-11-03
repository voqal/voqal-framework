package dev.voqal.assistant.processing

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.vertx.core.json.JsonObject
import java.util.regex.Pattern

object TextUtils {

    fun checkForVuiInteraction(type: String, responseCode: String): Boolean {
        try {
            val json = JsonObject(responseCode)
            if (json.getBoolean(type, false)) {
                return true
            }
        } catch (_: Exception) {
        }
        try {
            val extractedCode = CodeExtractor.extractCodeBlock(responseCode)
            val json = JsonObject(extractedCode)
            if (json.getBoolean(type, false)) {
                return true
            }
        } catch (_: Exception) {
        }

        //try stripping leading and trailing backticks
        if (responseCode.startsWith("`") && responseCode.endsWith("`")) {
            try {
                val trimmed = responseCode.trim('`')
                val json = JsonObject(trimmed)
                if (json.getBoolean(type, false)) {
                    return true
                }
            } catch (_: Exception) {
            }
        }
        return false
    }
}
