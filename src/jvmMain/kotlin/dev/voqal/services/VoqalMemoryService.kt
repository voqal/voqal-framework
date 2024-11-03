package dev.voqal.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import dev.voqal.assistant.memory.MemorySlice
import dev.voqal.config.settings.PromptSettings

/**
 * Manages persistent and temporary memory for Voqal.
 */
interface  VoqalMemoryService : Disposable {

    //todo: does nothing for thread memory system
    fun resetMemory()

    fun getCurrentMemory(promptSettings: PromptSettings? = null): MemorySlice

    fun saveEditLabel(memoryId: String, editor: Editor)

    fun putUserData(key: String, data: Any)

    fun getUserData(key: String): Any?

    fun removeUserData(key: String): Any?

    fun putLongTermUserData(key: String, data: Any?)

    fun getLongTermUserData(key: String): Any?

    fun removeLongTermUserData(key: String): Any?

    override fun dispose()
}
