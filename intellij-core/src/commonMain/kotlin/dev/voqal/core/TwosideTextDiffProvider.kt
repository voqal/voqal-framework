package dev.voqal.core

interface TwosideTextDiffProvider {
    fun compare(oldText: String, newText: String, emptyProgressIndicator: Any): List<LineFragment>?
}