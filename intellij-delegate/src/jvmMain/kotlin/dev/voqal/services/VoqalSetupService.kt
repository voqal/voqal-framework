package dev.voqal.services

import dev.voqal.core.Project

fun Project.getVoqalService(clazz: Class<*>): Any {
    throw UnsupportedOperationException()
}

inline fun <reified T> Project.service(): T {
    return getVoqalService(T::class.java) as T
}
