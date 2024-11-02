package com.intellij.openapi.project

class Project {
    inline fun <reified T> service(): T {
        throw IllegalArgumentException("Unknown service type: ${T::class}")
    }
}
