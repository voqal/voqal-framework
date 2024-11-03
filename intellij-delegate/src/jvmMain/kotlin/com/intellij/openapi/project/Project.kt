package com.intellij.openapi.project

import com.intellij.openapi.components.ComponentManager

interface Project : ComponentManager {
    val isDisposed: Boolean
    val basePath: String
    val name: String
}
