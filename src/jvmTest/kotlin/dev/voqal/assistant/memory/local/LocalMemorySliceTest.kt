package dev.voqal.assistant.memory.local

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ToolCall
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import dev.voqal.assistant.VoqalDirective
import dev.voqal.assistant.focus.SpokenTranscript
import dev.voqal.config.settings.LanguageModelSettings
import dev.voqal.config.settings.PromptSettings
import dev.voqal.core.MockConfigService
import dev.voqal.core.MockProject
import dev.voqal.provider.AiProvider
import dev.voqal.provider.LlmProvider
import dev.voqal.provider.clients.AiProvidersClient
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

class LocalMemorySliceTest {

    @Test
    fun singleToolCallTest(): Unit = runBlocking {
        val project = MockProject()
        val memory = LocalMemorySlice(project)

        val mockApplication = mock<Application> {
            on { isUnitTestMode } doReturn true
        }
        ApplicationManager.setApplication(mockApplication, project)
        project.configService = object : MockConfigService(project) {
            override fun getAiProvider(): AiProvider {
                val client = AiProvidersClient(project)

                val streamFile =
                    File("C:\\Users\\Brandon\\IdeaProjects\\voqal-framework\\src\\jvmTest\\resources\\streaming\\gpt-4o_single_tool_call.jsonl")
                client.addLlmProvider(object : LlmProvider {
                    override val name: String = "none"

                    override suspend fun streamChatCompletion(
                        request: ChatCompletionRequest,
                        directive: VoqalDirective?
                    ): Flow<ChatCompletionChunk> {
                        return streamFile.inputStream().bufferedReader().lineSequence().map {
                            Json.decodeFromString(ChatCompletionChunk.serializer(), it)
                        }.asFlow()
                    }

                    override suspend fun chatCompletion(
                        request: ChatCompletionRequest,
                        directive: VoqalDirective?
                    ): ChatCompletion {
                        TODO("Not yet implemented")
                    }

                    override fun getAvailableModelNames(): List<String> {
                        TODO("Not yet implemented")
                    }

                    override fun isStreamable() = true
                    override fun dispose() = Unit
                })
                return client
            }
        }

        var directive = project.directiveService.createDirective(SpokenTranscript("test", null))
        directive = directive.copy(
            contextMap = directive.contextMap.toMutableMap().apply {
                put(
                    "assistant", directive.assistant.copy(
                        languageModelSettings = LanguageModelSettings(),
                        promptSettings = PromptSettings(
                            streamCompletions = true,
                            functionCalling = PromptSettings.FunctionCalling.NATIVE
                        )
                    )
                )
            }
        )
        val response = memory.addMessage(directive)
        val tools = response.toolCalls
        assertEquals(1, tools.size)
        val toolCall = tools.first() as ToolCall.Function
        assertEquals("open_url", toolCall.function.name)
        val args = JsonObject(toolCall.function.arguments)
        assertEquals("https://www.google.com", args.getString("url"))
    }
}
