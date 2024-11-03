package dev.voqal.services

import com.intellij.openapi.Disposable
import dev.voqal.config.ConfigurableSettings
import dev.voqal.config.VoqalConfig
import dev.voqal.config.settings.*
import dev.voqal.provider.AiProvider
import dev.voqal.utils.SharedAudioCapture
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.VisibleForTesting

/**
 * Holds the project's current configuration.
 */
interface VoqalConfigService {

    fun getScope(): CoroutineScope

    fun getSharedAudioCapture(): SharedAudioCapture

    fun onConfigChange(
        disposable: Disposable? = null,
        listener: (VoqalConfig) -> Unit
    )

    fun updateConfig(settings: ConfigurableSettings): VoqalConfig

    fun getConfig(): VoqalConfig

    fun getAiProvider(): AiProvider

    fun resetAiProvider()

    fun resetCachedConfig()

    @VisibleForTesting
    fun setCachedConfig(config: VoqalConfig)

    fun getPromptSettings(promptName: String): PromptSettings

    fun getCurrentPromptSettings(): PromptSettings

    fun getCurrentLanguageModelSettings(): LanguageModelSettings

    fun getLanguageModelSettings(promptSettings: PromptSettings): LanguageModelSettings

    fun getPromptTemplate(promptSettings: PromptSettings): String

    fun getPromptTemplate(promptName: String): String

    fun getCurrentPromptMode(): String

    companion object {
        fun toHeaderMap(headerStr: String): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            headerStr.split(",").filter { it.isNotEmpty() }.forEach {
                val (key, value) = it.split(":")
                headers[key] = value
            }
            return headers
        }
    }
}
