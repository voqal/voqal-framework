package dev.voqal.services

import com.intellij.openapi.project.Project
import dev.voqal.utils.SharedAudioCapture
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

val scope: CoroutineScope = throw UnsupportedOperationException()
val audioCapture: SharedAudioCapture = throw UnsupportedOperationException()

val Project.scope: CoroutineScope
    get() = throw UnsupportedOperationException()

val Project.audioCapture: SharedAudioCapture
    get() = throw UnsupportedOperationException()

fun Project.getVoqalLogger(kClass: KClass<*>): KLogger {
    return KotlinLogging.logger(kClass.java.name)
}

fun KLogger.warnChat(s: String) {

}

fun KLogger.warnChat(s: String, e: Throwable) {

}

fun KLogger.errorChat(s: String, e: Throwable) {

}
