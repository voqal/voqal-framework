package dev.voqal.services

import com.intellij.openapi.components.ComponentManager

fun ComponentManager.getVoqalService(clazz: Class<*>): Any {
    throw UnsupportedOperationException()
}

inline fun <reified T> ComponentManager.service(): T {
    return getVoqalService(T::class.java) as T
}
