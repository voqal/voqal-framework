package dev.voqal.config

import dev.voqal.config.settings.*
import io.vertx.core.json.JsonObject

open class VoqalConfig(
    val microphoneSettings: MicrophoneSettings = MicrophoneSettings(),
    val voiceDetectionSettings: VoiceDetectionSettings = VoiceDetectionSettings(),
    val speechToTextSettings: SpeechToTextSettings = SpeechToTextSettings(),
    val languageModelsSettings: LanguageModelsSettings = LanguageModelsSettings(),
    val textToSpeechSettings: TextToSpeechSettings = TextToSpeechSettings(),
    val promptLibrarySettings: PromptLibrarySettings = PromptLibrarySettings()
) : ConfigurableSettings {

    /**
     * Need to set defaults so config changes don't reset stored config due to parse error.
     */
    constructor(json: JsonObject) : this(
        microphoneSettings = json.getJsonObject("microphoneSettings")?.let {
            MicrophoneSettings(it)
        } ?: MicrophoneSettings(),
        voiceDetectionSettings = json.getJsonObject("voiceDetectionSettings")?.let {
            VoiceDetectionSettings(it)
        } ?: VoiceDetectionSettings(),
        speechToTextSettings = json.getJsonObject("speechToTextSettings")?.let {
            SpeechToTextSettings(it)
        } ?: SpeechToTextSettings(),
        languageModelsSettings = json.getJsonObject("languageModelsSettings")?.let {
            LanguageModelsSettings(it)
        } ?: LanguageModelsSettings(),
        textToSpeechSettings = json.getJsonObject("textToSpeechSettings")?.let {
            TextToSpeechSettings(it)
        } ?: TextToSpeechSettings(),
        promptLibrarySettings = json.getJsonObject("promptLibrarySettings")?.let {
            PromptLibrarySettings(it)
        } ?: PromptLibrarySettings()
    )

    override fun toJson(): JsonObject {
        return JsonObject().apply {
            put("microphoneSettings", microphoneSettings.toJson())
            put("voiceDetectionSettings", voiceDetectionSettings.toJson())
            put("speechToTextSettings", speechToTextSettings.toJson())
            put("languageModelsSettings", languageModelsSettings.toJson())
            put("textToSpeechSettings", textToSpeechSettings.toJson())
            put("promptLibrarySettings", promptLibrarySettings.toJson())
        }
    }

    override fun withKeysRemoved(): VoqalConfig {
        return VoqalConfig(
            microphoneSettings = microphoneSettings.withKeysRemoved(),
            voiceDetectionSettings = voiceDetectionSettings.withKeysRemoved(),
            speechToTextSettings = speechToTextSettings.withKeysRemoved(),
            languageModelsSettings = languageModelsSettings.withKeysRemoved(),
            textToSpeechSettings = textToSpeechSettings.withKeysRemoved(),
            promptLibrarySettings = promptLibrarySettings.withKeysRemoved()
        )
    }

    override fun withPiiRemoved(): ConfigurableSettings {
        return VoqalConfig(
            microphoneSettings = microphoneSettings.withPiiRemoved(),
            voiceDetectionSettings = voiceDetectionSettings.withPiiRemoved(),
            speechToTextSettings = speechToTextSettings.withPiiRemoved(),
            languageModelsSettings = languageModelsSettings.withPiiRemoved(),
            textToSpeechSettings = textToSpeechSettings.withPiiRemoved(),
            promptLibrarySettings = promptLibrarySettings.withPiiRemoved()
        )
    }

    open fun copy(
        microphoneSettings: MicrophoneSettings = this.microphoneSettings,
        voiceDetectionSettings: VoiceDetectionSettings = this.voiceDetectionSettings,
        speechToTextSettings: SpeechToTextSettings = this.speechToTextSettings,
        languageModelsSettings: LanguageModelsSettings = this.languageModelsSettings,
        textToSpeechSettings: TextToSpeechSettings = this.textToSpeechSettings,
        promptLibrarySettings: PromptLibrarySettings = this.promptLibrarySettings
    ): VoqalConfig {
        return VoqalConfig(
            microphoneSettings = microphoneSettings,
            voiceDetectionSettings = voiceDetectionSettings,
            speechToTextSettings = speechToTextSettings,
            languageModelsSettings = languageModelsSettings,
            textToSpeechSettings = textToSpeechSettings,
            promptLibrarySettings = promptLibrarySettings
        )
    }
}
