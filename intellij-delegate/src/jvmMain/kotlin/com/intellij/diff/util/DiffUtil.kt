package com.intellij.diff.util

import com.intellij.openapi.util.text.TwosideTextDiffProvider

class DiffUtil {
    companion object {
        @JvmStatic
        fun createTextDiffProvider(
            project: Any,
            request: Any,
            settings: Any,
            onSettingsChange: () -> Unit,
            disposable: Any
        ): TwosideTextDiffProvider {
            throw UnsupportedOperationException()
        }
    }
}