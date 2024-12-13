package dev.voqal.assistant.template

import dev.voqal.assistant.template.extension.*
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.extension.Extension
import io.pebbletemplates.pebble.lexer.Syntax
import io.pebbletemplates.pebble.loader.StringLoader
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.io.File

class VoqalTemplateEngine {

    companion object {
        var libraryDir = File(File(System.getProperty("java.io.tmpdir")), "voqal-library")
        val customExtensions: MutableList<Extension> = mutableListOf()

        private val ENGINE by lazy {
            PebbleEngine.Builder()
                .syntax(
                    Syntax.Builder()
                        .setEnableNewLineTrimming(false)
                        .build()
                )
                .autoEscaping(false)
                .loader(StringLoader())
                .extension(WithLineNumbersExtension())
                .extension(AddUserContextExtension())
                .extension(GetUserContextExtension())
                .extension(SlurpUrlExtension())
                .extension(LibraryIncludeExtension())
                .apply {
                    customExtensions.forEach { extension(it) }
                }
                .build()
        }

        fun getTemplate(templateName: String): PebbleTemplate {
            return ENGINE.getTemplate(templateName)
        }
    }
}
