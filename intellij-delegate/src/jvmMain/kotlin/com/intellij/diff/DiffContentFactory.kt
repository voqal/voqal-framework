package com.intellij.diff

abstract class DiffContentFactory {
    companion object {
        @JvmStatic
        fun getInstance(): DiffContentFactory {
            throw UnsupportedOperationException()
        }
    }

    abstract fun create(text: String): Any
}