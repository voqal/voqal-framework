package com.intellij.openapi.project

class Project {

    val isDisposed: Boolean = false

    inline fun <reified T> service(): T {
        throw IllegalArgumentException("Unknown service type: ${T::class}")
    }
}
