package com.intellij.openapi.components

import com.intellij.openapi.util.Condition

interface ComponentManager {
    fun <T> getService(clazz: Class<T>): T
    val disposed: Condition<*>
}
