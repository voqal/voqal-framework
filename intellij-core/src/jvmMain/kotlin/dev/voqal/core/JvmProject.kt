package dev.voqal.core

interface JvmProject : Project {
    fun <T> getService(clazz: Class<T>): T
}