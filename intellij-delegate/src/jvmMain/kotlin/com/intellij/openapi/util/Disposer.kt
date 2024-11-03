package com.intellij.openapi.util

import com.intellij.openapi.Disposable

object Disposer {
    fun newDisposable() = object : Disposable {
        override fun dispose() = Unit
    }

    fun register(parent: Disposable, child: Disposable) {
    }

    fun register(parent: Disposable, exec: () -> Unit) {
    }

    fun dispose(disposable: Disposable) {
    }
}
