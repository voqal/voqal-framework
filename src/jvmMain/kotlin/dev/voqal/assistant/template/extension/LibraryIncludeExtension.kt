package dev.voqal.assistant.template.extension

import dev.voqal.assistant.template.VoqalTemplateEngine.Companion.libraryDir
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.error.PebbleException
import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.NodeVisitor
import io.pebbletemplates.pebble.lexer.Token
import io.pebbletemplates.pebble.node.AbstractRenderableNode
import io.pebbletemplates.pebble.node.RenderableNode
import io.pebbletemplates.pebble.node.expression.Expression
import io.pebbletemplates.pebble.parser.Parser
import io.pebbletemplates.pebble.template.EvaluationContextImpl
import io.pebbletemplates.pebble.template.PebbleTemplateImpl
import io.pebbletemplates.pebble.tokenParser.TokenParser
import java.io.File
import java.io.Writer
import java.nio.file.Files

class LibraryIncludeExtension : AbstractExtension() {

    override fun getTokenParsers(): List<TokenParser> {
        val parsers = mutableListOf<TokenParser>()
        parsers.add(MyTokenParser())
        return parsers
    }

    class MyTokenParser : TokenParser {

        override fun getTag() = "include"

        override fun parse(token: Token?, parser: Parser?): RenderableNode {
            val stream = parser?.stream
            val lineNumber = token?.lineNumber
            stream?.next()
            val includeExpression = parser?.expressionParser?.parseExpression()
            stream?.expect(Token.Type.EXECUTE_END)
            return MyIncludeNode(lineNumber!!, includeExpression!!)
        }
    }

    class MyIncludeNode(
        lineNumber: Int,
        private val includeExpression: Expression<*>
    ) : AbstractRenderableNode(lineNumber) {

        override fun render(self: PebbleTemplateImpl, writer: Writer, context: EvaluationContextImpl) {
            val templateName = this.includeExpression.evaluate(self, context) as? String
                ?: throw PebbleException(
                    null,
                    "The template name in an include tag evaluated to NULL. If the template name is static, make sure to wrap it in quotes.",
                    this.lineNumber, self.name
                )

            //todo: no way this is the right way to do this
            val templateContent = loadAndProcessTemplate(templateName)
            val engine = self.let {
                val engineField = PebbleTemplateImpl::class.java.getDeclaredField("engine")
                engineField.isAccessible = true
                engineField.get(it)
            } as PebbleEngine
            val templateEngine = engine.getTemplate(templateContent)
            val evaluateMethod = PebbleTemplateImpl::class.java
                .getDeclaredMethod("evaluate", Writer::class.java, EvaluationContextImpl::class.java)
            evaluateMethod.isAccessible = true
            evaluateMethod.invoke(templateEngine, writer, context)
        }

        override fun accept(visitor: NodeVisitor) {
            visitor.visit(this)
        }

        private fun loadAndProcessTemplate(templateName: String): String {
            val path = File(libraryDir, templateName).toPath()
            if (!path.normalize().startsWith(libraryDir.toPath())) {
                throw PebbleException(
                    null,
                    "Illegal template path: $templateName",
                    lineNumber, null
                )
            }

            if (!Files.exists(path)) {
                throw PebbleException(
                    null,
                    "Template file not found: $templateName",
                    lineNumber, null
                )
            }

            //remove front matter
            val fullPrompt = String(Files.readAllBytes(path)).replace("\r\n", "\n")
            var cleanPrompt = fullPrompt
            if (fullPrompt.startsWith("---")) {
                val endOfFrontMatter = fullPrompt.indexOf("\n---\n")
                if (endOfFrontMatter != -1) {
                    cleanPrompt = fullPrompt.substring(endOfFrontMatter + 5)
                }
            }

            return cleanPrompt
        }
    }
}
