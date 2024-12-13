package dev.voqal.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import dev.voqal.config.ConfigurableSettings
import dev.voqal.config.VoqalConfig
import dev.voqal.config.settings.LanguageModelSettings
import dev.voqal.config.settings.PromptSettings
import dev.voqal.provider.AiProvider
import dev.voqal.services.VoqalConfigService
import dev.voqal.utils.SharedAudioCapture
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import org.mockito.kotlin.mock
import kotlin.reflect.KClass

open class MockConfigService(
    private val project: Project
) : VoqalConfigService {

    private var config = VoqalConfig()

    override fun invokeLater(action: () -> Unit) {
        TODO("Not yet implemented")
    }

    override fun <T> getVoqalService(clazz: Class<T>): T {
        return project.getService(clazz)
    }

    override fun getVoqalLogger(kClass: KClass<*>): KLogger {
        return KotlinLogging.logger(kClass.java.name)
    }

    override fun getScope(): CoroutineScope {
        return GlobalScope
    }

    override fun getSharedAudioCapture(): SharedAudioCapture {
        return mock<SharedAudioCapture> {}
    }

    override fun onConfigChange(disposable: Disposable?, listener: (VoqalConfig) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun updateConfig(settings: ConfigurableSettings): VoqalConfig {
        TODO("Not yet implemented")
    }

    override fun getConfig(): VoqalConfig {
        return config
    }

    override fun getAiProvider(): AiProvider {
        TODO("Not yet implemented")
    }

    override fun resetAiProvider() {
        TODO("Not yet implemented")
    }

    override fun resetCachedConfig() {
        TODO("Not yet implemented")
    }

    override fun setCachedConfig(config: VoqalConfig) {
        this.config = config
    }

    override fun getPromptSettings(promptName: String): PromptSettings {
        TODO("Not yet implemented")
    }

    override fun getCurrentPromptSettings(): PromptSettings {
        TODO("Not yet implemented")
    }

    override fun getCurrentLanguageModelSettings(): LanguageModelSettings {
        TODO("Not yet implemented")
    }

    override fun getLanguageModelSettings(promptSettings: PromptSettings): LanguageModelSettings {
        TODO("Not yet implemented")
    }

    override fun getPromptTemplate(promptSettings: PromptSettings): String {
        if (promptSettings.provider == PromptSettings.PProvider.CUSTOM_TEXT) {
            return promptSettings.promptText
        } else {
            TODO("Not yet implemented")
        }
    }

    override fun getPromptTemplate(promptName: String): String {
        TODO("Not yet implemented")
    }

    override fun getActivePromptName(): String {
        return "voqal"
    }
}