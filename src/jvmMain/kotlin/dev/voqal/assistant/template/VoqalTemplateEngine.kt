package dev.voqal.assistant.template

import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.lexer.Syntax
import io.pebbletemplates.pebble.loader.StringLoader
import io.pebbletemplates.pebble.template.PebbleTemplate

class VoqalTemplateEngine {
    companion object {
        private val ENGINE = PebbleEngine.Builder()
            .syntax(
                Syntax.Builder()
                    .setEnableNewLineTrimming(false)
                    .build()
            )
            .autoEscaping(false)
            .loader(StringLoader())
            .extension(ComputerExtension())
            .extension(AddUserContextExtension())
            .extension(GetUserContextExtension())
            .extension(SlurpUrlExtension())
            .build()

        fun getTemplate(templateName: String): PebbleTemplate {
            return ENGINE.getTemplate(templateName)
        }
    }
}
