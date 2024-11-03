package com.intellij.formatting

import com.intellij.openapi.util.TextRange

interface Block {
    val textRange: TextRange
}