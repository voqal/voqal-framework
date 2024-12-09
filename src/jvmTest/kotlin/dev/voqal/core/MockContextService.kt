package dev.voqal.core

import com.intellij.formatting.Block
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import dev.voqal.assistant.VoqalDirective
import dev.voqal.services.VoqalContextService

class MockContextService : VoqalContextService {

    override fun getTokenCount(text: String) = -1

    override fun getSelectedTextEditor(): Editor? {
        TODO("Not yet implemented")
    }

    override fun cropAsNecessary(fullDirective: VoqalDirective): VoqalDirective {
        TODO("Not yet implemented")
    }

    override fun getOpeningBlockAt(
        psiFile: PsiFile,
        editRange: TextRange
    ): Block? {
        TODO("Not yet implemented")
    }
}
