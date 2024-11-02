package com.intellij.openapi.components

interface ComponentManager {
    fun <T> getService(clazz: Class<T>): T
}
