package dev.voqal.assistant

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.voqal.assistant.context.AssistantContext
import dev.voqal.assistant.context.DeveloperContext
import dev.voqal.assistant.context.UserContext
import dev.voqal.assistant.template.VoqalTemplateEngine
import dev.voqal.config.settings.LanguageModelSettings
import dev.voqal.services.VoqalConfigService
import dev.voqal.services.VoqalDirectiveService
import io.vertx.core.json.JsonObject
import kotlinx.serialization.json.Json
import java.io.StringWriter

/**
 * Represents a processable command for Voqal.
 *
 * @property assistant Holds the current configuration of the assistant.
 * @property developer Holds information provided by developer.
 */
data class VoqalDirective(
    val project: Project,
    val assistant: AssistantContext,
    val developer: DeveloperContext,
    val user: UserContext? = null
) {

    val requestId by lazy { assistant.memorySlice.id }
    val directiveId by lazy { assistant.parentDirective?.assistant?.memorySlice?.id ?: assistant.memorySlice.id }
    private var promptCache: String? = null

    /**
     * Creates the final prompt which will be sent to LLM.
     */
    fun toMarkdown(useCache: Boolean = false): String {
        if (useCache) return promptCache!!
        val promptSettings = assistant.promptSettings ?: throw IllegalStateException("Prompt settings not found")
        val promptTemplate = project.service<VoqalConfigService>().getPromptTemplate(promptSettings)
        val promptMarkdown = toMarkdown(promptTemplate)
        promptCache = promptMarkdown
        return promptMarkdown
    }

    fun toMarkdown(promptTemplate: String): String {
        val compiledTemplate = VoqalTemplateEngine.getTemplate(promptTemplate)
        val writer = StringWriter()

        val assistantMap = VoqalDirectiveService.convertJsonElementToMap(
            Json.parseToJsonElement(assistant.toJson(this).toString())
        )
        val developerMap = VoqalDirectiveService.convertJsonElementToMap(
            Json.parseToJsonElement(developer.toJson().toString())
        )
        val userMap = user?.let {
            VoqalDirectiveService.convertJsonElementToMap(
                Json.parseToJsonElement(it.toJson().toString())
            )
        }
        val contextMap = mutableMapOf(
            "assistant" to assistantMap,
            "developer" to developerMap,
            "user" to userMap,
            "directive" to this
        )
        compiledTemplate.evaluate(writer, contextMap)
        val fullPrompt = writer.toString()

        //merge empty new lines into single new line (ignoring code blocks)
        val finalPrompt = StringWriter()
        val fullPromptLines = fullPrompt.lines()
        var inCodeBlock = false
        var previousLineBlank = false
        fullPromptLines.forEachIndexed { index, line ->
            if (line.trim().startsWith("```")) {
                inCodeBlock = !inCodeBlock
                finalPrompt.appendLine(line)
                previousLineBlank = false
                return@forEachIndexed
            }

            if (index == 0 && line.isBlank()) {
                previousLineBlank = true
            } else if (inCodeBlock) {
                finalPrompt.appendLine(line)
                previousLineBlank = false
            } else {
                if (line.isBlank()) {
                    if (!previousLineBlank) {
                        finalPrompt.appendLine(line)
                        previousLineBlank = true
                    }
                } else {
                    finalPrompt.appendLine(line)
                    previousLineBlank = false
                }
            }
        }
        val promptMarkdown = finalPrompt.toString()
        return promptMarkdown
    }

    fun toJson(): JsonObject {
        return JsonObject().apply {
            put("assistant", assistant.toJson(this@VoqalDirective))
            user?.let { put("user", it.toJson()) }
            put("developer", developer.toJson())
        }
    }

    fun getLanguageModelSettings(): LanguageModelSettings {
        return assistant.languageModelSettings
    }

    override fun hashCode(): Int {
        return requestId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false
        return requestId == (other as VoqalDirective).requestId
    }

    override fun toString(): String {
        return "VoqalDirective(requestId=$requestId, directiveId=$directiveId)"
    }
}
