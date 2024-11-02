package dev.voqal.services

import com.aallam.openai.api.chat.ToolCall
import dev.voqal.assistant.VoqalResponse
import dev.voqal.assistant.focus.SpokenTranscript
import io.vertx.core.json.JsonObject
import org.yaml.snakeyaml.Yaml
import java.io.StringReader
import java.util.regex.Matcher

interface  VoqalToolService {

    companion object {
        fun fixIllegalDollarEscape(jsonString: String): String {
            val regex = """\\\$""".toRegex()
            return regex.replace(jsonString, Matcher.quoteReplacement("\$"))
        }

        fun parseYaml(yamlString: String): Map<String, Any> {
            val yaml = Yaml()
            val reader = StringReader(yamlString)
            return yaml.load(reader) as Map<String, Any>
        }
    }

//    fun getAvailableTools() = availableToolsMap
//
//    suspend fun intentCheck(spokenTranscript: SpokenTranscript): DetectedIntent?
//
//    suspend fun blindExecute(
//        tool: VoqalTool,
//        args: JsonObject = JsonObject(),
//        chatMessage: Boolean = false,
//        memoryId: String? = null
//    )

    suspend fun handleFunctionCall(toolCall: ToolCall.Function, response: VoqalResponse)
}
