package com.intellij.openapi.util.text

import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.fragments.LineFragmentImpl
import com.intellij.openapi.progress.EmptyProgressIndicator

class TwosideTextDiffProvider {
    fun compare(text1: String, text2: String, progress: EmptyProgressIndicator): List<LineFragment>? {
        throw UnsupportedOperationException()
    }
}