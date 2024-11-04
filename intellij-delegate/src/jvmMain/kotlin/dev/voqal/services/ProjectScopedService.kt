package dev.voqal.services

import com.intellij.openapi.project.Project
import dev.voqal.utils.SharedAudioCapture
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

val Project.scope: CoroutineScope
    get() = throw UnsupportedOperationException()

fun Project.getVoqalLogger(kClass: KClass<*>): KLogger {
    throw UnsupportedOperationException()
}

//
//val Project.messageBusConnection: MessageBusConnection
//    get() = service<ProjectScopedService>().messageBusConnection
//val Project.scope: CoroutineScope
//    get() = service<ProjectScopedService>().scope
//val Project.logsTab: VoqalLogsTab
//    get() = service<ProjectScopedService>().voqalLogsTab
val Project.audioCapture: SharedAudioCapture
    get() = throw UnsupportedOperationException()

fun KLogger.warnChat(s: String, e: Throwable? = null) {
    warn(s, e)
    //todo: project.service<VoqalStatusService>().warnChat(input)
}

fun KLogger.errorChat(s: String, e: Throwable? = null) {
    error(s, e)
    //todo: project.service<VoqalStatusService>().errorChat(input)
}

fun Project.invokeLater(action: () -> Unit) {
    throw UnsupportedOperationException()
}
