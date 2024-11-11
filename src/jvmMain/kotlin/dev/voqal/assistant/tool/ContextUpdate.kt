package dev.voqal.assistant.tool

data class ContextUpdate(
    val context: Map<String, Any>,
    val final: Boolean
)
