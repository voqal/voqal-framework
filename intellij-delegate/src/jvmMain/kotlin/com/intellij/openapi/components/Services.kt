package com.intellij.openapi.components

inline fun <reified T> ComponentManager.service(): T {
    return getService(T::class.java)
}
