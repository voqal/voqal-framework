package com.intellij.openapi.application

import com.intellij.openapi.util.Condition

interface Application {
    val isUnitTestMode: Boolean

    fun invokeLater(var1: Runnable)
    fun invokeLater(var1: Runnable, var2: Condition<*>)
}