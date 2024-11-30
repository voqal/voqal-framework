package dev.voqal.assistant.template.extension

import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Function
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate

class WithLineNumbersExtension : AbstractExtension() {

    override fun getFunctions() = mapOf(
        "withLineNumbers" to AddContextFunction()
    )

    class AddContextFunction : Function {
        override fun getArgumentNames(): List<String> {
            return listOf("text")
        }

        override fun execute(
            args: Map<String, Any?>,
            self: PebbleTemplate,
            context: EvaluationContext,
            lineNumber: Int
        ): Any? {
            val text = args["text"] as? String ?: return ""
            return text.lines()
                .mapIndexed { index, line -> "${index + 1}|$line" }
                .joinToString("\n")
        }
    }
}
