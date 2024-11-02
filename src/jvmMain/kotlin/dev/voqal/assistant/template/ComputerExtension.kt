package dev.voqal.assistant.template

import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Filter
import io.pebbletemplates.pebble.extension.core.DateFilter
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.util.*
import java.util.function.Supplier

class ComputerExtension : AbstractExtension() {
    override fun getGlobalVariables(): Map<String, Any> {
        return mapOf(
            "computer" to mapOf(
                "currentTime" to LambdaWrapper { Date() },
                "osName" to System.getProperty("os.name"),
                "osVersion" to System.getProperty("os.version"),
                "osArch" to System.getProperty("os.arch")
            )
        )
    }

    override fun getFilters(): Map<String, Filter> {
        return mapOf(
            "date" to CustomDateFilter()
        )
    }

    class LambdaWrapper<T>(private val supplier: () -> T) {
        fun get(): T {
            return supplier()
        }

        override fun toString(): String {
            return supplier().toString()
        }
    }

    class CustomDateFilter : DateFilter() {
        override fun apply(
            input: Any?,
            args: MutableMap<String, Any>?,
            self: PebbleTemplate?,
            context: EvaluationContext?,
            lineNumber: Int
        ): Any {
            return super.apply(unwrapLambda(input), args, self, context, lineNumber)
        }

        private fun unwrapLambda(input: Any?): Any? {
            return when (input) {
                is LambdaWrapper<*> -> input.get()
                is Supplier<*> -> input.get()
                else -> input
            }
        }
    }
}
