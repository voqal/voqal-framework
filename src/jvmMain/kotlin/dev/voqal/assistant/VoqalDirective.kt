package dev.voqal.assistant

import com.intellij.openapi.project.Project
import dev.voqal.assistant.context.AssistantContext
import dev.voqal.assistant.context.VoqalContext
import dev.voqal.assistant.template.VoqalTemplateEngine
import dev.voqal.config.settings.LanguageModelSettings
import dev.voqal.services.VoqalConfigService
import dev.voqal.services.VoqalDirectiveService
import dev.voqal.services.service
import io.vertx.core.json.JsonObject
import kotlinx.serialization.json.Json
import java.io.StringWriter

/**
 * Represents a processable command for Voqal.
 */
data class VoqalDirective(
    val project: Project,
    val contextMap: Map<String, VoqalContext>
) {

    val assistant: AssistantContext by lazy { contextMap["assistant"] as AssistantContext }
    var transcription: String = "" //todo: better

    val requestId by lazy { assistant.memorySlice.id }
    val directiveId by lazy { assistant.parentDirective?.assistant?.memorySlice?.id ?: assistant.memorySlice.id }
    private var promptCache: String? = null

    fun <T : VoqalContext> getContext(key: String): T {
        return contextMap[key] as T
    }

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
        compiledTemplate.evaluate(
            writer, mutableMapOf<String, Any?>(
                "directive" to this
            ).apply {
                contextMap.forEach { (key, value) ->
                    val serializedContext = VoqalDirectiveService.convertJsonElementToMap(
                        Json.parseToJsonElement(value.toJson(this@VoqalDirective).toString())
                    )
                    put(key, serializedContext)
                }
            }
        )
        val fullPrompt = writer.toString().replace("\r\n", "\n")

        //remove front matter
        var cleanPrompt = fullPrompt
        if (fullPrompt.startsWith("---")) {
            val endOfFrontMatter = fullPrompt.indexOf("\n---\n")
            if (endOfFrontMatter != -1) {
                cleanPrompt = fullPrompt.substring(endOfFrontMatter + 5)
            }
        }

        //merge empty new lines into single new line (ignoring code blocks)
        val finalPrompt = StringWriter()
        val fullPromptLines = cleanPrompt.lines()
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
            contextMap.forEach { (key, value) ->
                put(key, value.toJson(this@VoqalDirective))
            }
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
