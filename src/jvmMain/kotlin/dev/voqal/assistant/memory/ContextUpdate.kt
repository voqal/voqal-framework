package dev.voqal.assistant.memory

data class ContextUpdate(
    val context: Map<String, Any>,
    val final: Boolean
)
