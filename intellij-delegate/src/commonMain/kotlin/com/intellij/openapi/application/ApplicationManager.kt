package com.intellij.openapi.application

object ApplicationManager {

    fun getApplication(): Application {
        return Application()
    }

    class Application {
        val isUnitTestMode = false
    }
}
