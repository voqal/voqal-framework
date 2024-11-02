package dev.voqal.services

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

val scope: CoroutineScope = throw UnsupportedOperationException()
val Project.scope: CoroutineScope
    get() = throw UnsupportedOperationException()

fun getVoqalLogger(clazz: KClass<*>): org.slf4j.Logger {
    throw UnsupportedOperationException()
}

fun Project.getVoqalLogger(clazz: KClass<*>): org.slf4j.Logger {
    throw UnsupportedOperationException()
}
