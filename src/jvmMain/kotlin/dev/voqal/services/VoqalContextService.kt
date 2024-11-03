package dev.voqal.services

import com.intellij.formatting.Block
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import dev.voqal.assistant.VoqalDirective

/**
 * Used to ensure LLM prompts do not contain ignored files or exceed token limits.
 */
interface VoqalContextService {

    fun getTokenCount(text: String): Int

//    fun getOpenFiles(selectedTextEditor: Editor?): MutableList<ViewingCode>

    fun getSelectedTextEditor(): Editor?

    fun cropAsNecessary(fullDirective: VoqalDirective): VoqalDirective

//    /**
//     * Returns markdown representation of the tree.
//     * ```
//     * ├── src
//     * │   ├── main
//     * │   │   ├── kotlin
//     * ```
//     */
//    fun getProjectStructureAsMarkdownTree(encoding: Encoding? = null, tokenLimit: Int = -1): String
//
//    fun getProjectCodeStructure(encoding: Encoding? = null, tokenLimit: Int = -1): String
//
    fun getOpeningBlockAt(psiFile: PsiFile, editRange: TextRange): Block?
}
