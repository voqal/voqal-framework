package dev.voqal.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import dev.voqal.config.ConfigurableSettings
import dev.voqal.config.VoqalConfig
import dev.voqal.config.settings.LanguageModelSettings
import dev.voqal.config.settings.PromptSettings
import dev.voqal.provider.AiProvider
import dev.voqal.utils.SharedAudioCapture
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.VisibleForTesting
import kotlin.reflect.KClass

/**
 * Holds the project's current configuration.
 */
interface VoqalConfigService {

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

    fun invokeLater(action: () -> Unit)

    fun <T> getVoqalService(clazz: Class<T>): T

    fun getVoqalLogger(kClass: KClass<*>): KLogger

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
}

val Project.scope: CoroutineScope
    get() = getService(VoqalConfigService::class.java).getScope()

fun Project.getVoqalLogger(kClass: KClass<*>): KLogger {
    return getService(VoqalConfigService::class.java).getVoqalLogger(kClass)
}

val Project.audioCapture: SharedAudioCapture
    get() = getService(VoqalConfigService::class.java).getSharedAudioCapture()

fun KLogger.warnChat(s: String, e: Throwable? = null) {
    if (e == null) warn(s) else warn(s, e)
}

fun KLogger.errorChat(s: String, e: Throwable? = null) {
    if (e == null) error(s) else error(s, e)
}

inline fun <reified T> ComponentManager.service(): T {
    return getService(VoqalConfigService::class.java).getVoqalService(T::class.java)
}

fun Project.invokeLater(action: () -> Unit) {
    getService(VoqalConfigService::class.java).invokeLater(action)
}
