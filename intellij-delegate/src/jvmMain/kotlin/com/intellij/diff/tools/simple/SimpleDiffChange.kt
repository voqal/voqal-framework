package com.intellij.diff.tools.simple

import com.intellij.diff.fragments.LineFragment

class SimpleDiffChange {
    constructor(index: Int, fragment: Any) {
    }

    val fragment: LineFragment
        get() = throw UnsupportedOperationException()
}