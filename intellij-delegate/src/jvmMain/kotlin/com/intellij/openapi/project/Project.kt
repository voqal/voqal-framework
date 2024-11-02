package com.intellij.openapi.project

import com.intellij.openapi.components.ComponentManager
import io.github.oshai.kotlinlogging.KLogger
import kotlin.reflect.KClass

interface Project : ComponentManager {

    val isDisposed: Boolean
    val basePath: String
    val name: String

    fun getVoqalLogger(kClass: KClass<*>): KLogger {
        throw UnsupportedOperationException()
    }
}
