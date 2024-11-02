package dev.voqal.services

import com.intellij.openapi.project.Project
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

val scope: CoroutineScope = throw UnsupportedOperationException()
val Project.scope: CoroutineScope
    get() = throw UnsupportedOperationException()

fun Project.getVoqalLogger(clazz: KClass<*>): KLogger {
    throw UnsupportedOperationException()
}
