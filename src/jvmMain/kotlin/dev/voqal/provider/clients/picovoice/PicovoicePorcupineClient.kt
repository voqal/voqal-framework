package dev.voqal.provider.clients.picovoice

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import dev.voqal.config.VoqalConfig
import dev.voqal.config.settings.WakeSettings
import dev.voqal.provider.WakeProvider
import dev.voqal.provider.clients.picovoice.NativesExtractor.workingDirectory
import dev.voqal.provider.clients.picovoice.natives.PicovoiceNative
import dev.voqal.provider.clients.picovoice.natives.PorcupineNative
import dev.voqal.services.VoqalConfigService
import dev.voqal.services.audioCapture
import dev.voqal.services.getVoqalLogger
import dev.voqal.utils.SharedAudioCapture
import dev.voqal.utils.SharedAudioCapture.Companion.convertBytesToShorts
import org.apache.commons.lang3.SystemUtils
import java.io.File

class PicovoicePorcupineClient(
    private val project: Project,
    picovoiceKey: String,
    sensitivity: Float
) : WakeProvider, SharedAudioCapture.AudioDataListener {

    private val log = project.getVoqalLogger(this::class)
    private lateinit var native: PorcupineNative
    private lateinit var porcupine: Pointer

    init {
        NativesExtractor.extractNatives(project)

        val configService = project.service<VoqalConfigService>()
        val config = configService.getConfig()
        var loadedWakeWord = config.wakeSettings.wakeWord
        loadWakeWordDetector(config, picovoiceKey, sensitivity)
        configService.onConfigChange(this) {
            if (loadedWakeWord != it.wakeSettings.wakeWord) {
                loadedWakeWord = it.wakeSettings.wakeWord
                loadWakeWordDetector(it, picovoiceKey, sensitivity)
                log.info { "Wake word changed to ${it.wakeSettings.wakeWord}" }
            }
        }

        project.audioCapture.registerListener(this)
    }

    private fun loadWakeWordDetector(config: VoqalConfig, picovoiceKey: String, sensitivity: Float) {
        val wakeWord = config.wakeSettings.wakeWord
        val keywordFile = if (wakeWord == WakeSettings.WakeWord.CustomFile.name) {
            File(config.wakeSettings.customWakeWordFile)
        } else if (wakeWord == WakeSettings.WakeWord.Voqal.name) {
            if (SystemUtils.IS_OS_WINDOWS) {
                File(workingDirectory, "vocal_en_windows_v3_0_0.ppn")
            } else if (SystemUtils.IS_OS_LINUX) {
                File(workingDirectory, "vocal_en_linux_v3_0_0.ppn")
            } else {
                File(workingDirectory, "vocal_en_mac_v3_0_0.ppn")
            }
        } else {
            if (SystemUtils.IS_OS_WINDOWS) {
                File(
                    workingDirectory,
                    "pvporcupine\\resources\\keyword_files\\windows\\${wakeWord.lowercase()}_windows.ppn"
                )
            } else if (SystemUtils.IS_OS_LINUX) {
                File(
                    workingDirectory,
                    "pvporcupine/resources/keyword_files/linux/${wakeWord.lowercase()}_linux.ppn"
                )
            } else {
                File(
                    workingDirectory,
                    "pvporcupine/resources/keyword_files/mac/${wakeWord.lowercase()}_mac.ppn"
                )
            }
        }
        val pvporcupineLibraryPath = if (SystemUtils.IS_OS_WINDOWS) {
            File(
                workingDirectory,
                "pvporcupine/lib/windows/amd64/libpv_porcupine.dll".replace("/", "\\")
            )
        } else if (SystemUtils.IS_OS_LINUX) {
            File(workingDirectory, "pvporcupine/lib/linux/x86_64/libpv_porcupine.so")
        } else {
            val arch = NativesExtractor.getMacArchitecture()
            File(workingDirectory, "pvporcupine/lib/mac/$arch/libpv_porcupine.dylib")
        }
        val porcupineModelPath = File(
            workingDirectory,
            "pvporcupine/lib/common/porcupine_params.pv".replace("/", File.separator)
        )

        if (!this::native.isInitialized) {
            native = PorcupineNative.getINSTANCE(pvporcupineLibraryPath)
            log.debug { "Porcupine version: " + native.pv_porcupine_version() }
        }

        val porcupineRef = PointerByReference()
        val status = native.pv_porcupine_init(
            picovoiceKey,
            porcupineModelPath.absolutePath,
            1,
            arrayOf(keywordFile.absolutePath),
            floatArrayOf(sensitivity),
            porcupineRef
        )
        PicovoiceNative.throwIfError(log, native, status)

        if (this::porcupine.isInitialized) {
            native.pv_porcupine_delete(porcupine)
        }
        porcupine = porcupineRef.value
    }

    override fun onAudioData(data: ByteArray, detection: SharedAudioCapture.AudioDetection) {
        val pcm = convertBytesToShorts(data)

        val keywordIndexRef = IntByReference()
        PicovoiceNative.throwIfError(
            log, native, native.pv_porcupine_process(porcupine, pcm, keywordIndexRef)
        )

        detection.wakeWordDetected.set(keywordIndexRef.value >= 0)
    }

    override fun dispose() {
        project.audioCapture.removeListener(this)
        native.pv_porcupine_delete(porcupine)
    }

    override fun isLiveDataListener() = true
}
