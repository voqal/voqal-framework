package dev.voqal.status

/**
 * Represents the current status of Voqal.
 */
enum class VoqalStatus {
    ERROR,
    DISABLED,
    IDLE,
    EDITING,
    SEARCHING;

    val presentableText: String
        get() {
            return when (this) {
                DISABLED -> "Click to view controls"
                else -> "Status: " + name.lowercase().replaceFirstChar(Char::titlecase)
            }
        }
}
