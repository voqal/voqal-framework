package dev.voqal.core

import dev.voqal.assistant.VoqalDirective
import dev.voqal.services.VoqalContextService

class MockContextService : VoqalContextService {

    override fun getTokenCount(text: String) = -1

    override fun cropAsNecessary(fullDirective: VoqalDirective): VoqalDirective {
        TODO("Not yet implemented")
    }
}
