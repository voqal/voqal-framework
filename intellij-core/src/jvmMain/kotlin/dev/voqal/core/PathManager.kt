package dev.voqal.core

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

object PathManager {

    private val log = KotlinLogging.logger {}

    @JvmStatic
    fun getJarPathForClass(aClass: Class<*>): String? {
        val resourceRoot = getJarForClass(aClass)
        return if (resourceRoot == null) null else resourceRoot.toString()
    }

    private fun getJarForClass(aClass: Class<*>): Path? {
        val resourceRoot = getResourceRoot(aClass, '/'.toString() + aClass.name.replace('.', '/') + ".class")
        return if (resourceRoot == null) null else Paths.get(resourceRoot).toAbsolutePath()
    }

    private fun getResourceRoot(context: Class<*>, path: String): String? {
        var url = context.getResource(path)
        if (url == null) {
            url = ClassLoader.getSystemResource(path.substring(1))
        }

        return if (url != null) extractRoot(url, path) else null
    }

    private fun extractRoot(resourceURL: URL, resourcePath: String): String? {
        if (resourcePath.isEmpty() || resourcePath[0] != '/' && resourcePath[0] != '\\') {
            log.warn("precondition failed: $resourcePath")
            return null
        } else {
            var resultPath: String? = null
            val protocol = resourceURL.protocol
            if ("file" == protocol) {
                val result: File
                try {
                    result = File(resourceURL.toURI().schemeSpecificPart)
                } catch (var8: URISyntaxException) {
                    val e = var8
                    throw IllegalArgumentException("URL='$resourceURL'", e)
                }

                val path = result.path
                val testPath = path.replace('\\', '/')
                val testResourcePath = resourcePath.replace('\\', '/')
                if (endsWithIgnoreCase(testPath, testResourcePath)) {
                    resultPath = path.substring(0, path.length - resourcePath.length)
                }
            } else if ("jar" == protocol) {
                val jarPath = splitJarUrl(resourceURL.file)
                if (jarPath != null) {
                    resultPath = jarPath
                }
            } else if ("jrt" == protocol) {
                return null
            }

            if (resultPath == null) {
                log.warn("cannot extract '$resourcePath' from '$resourceURL'")
                return null
            } else {
                return Paths.get(resultPath).normalize().toString()
            }
        }
    }

    private fun splitJarUrl(url: String): String? {
        val pivot = url.indexOf("!/")
        if (pivot < 0) {
            return null
        } else {
            var jarPath = url.substring(0, pivot)
            var startsWithConcatenation = true
            var offset = 0
            val var5 = arrayOf("jar", ":")
            val var6 = var5.size

            for (var7 in 0 until var6) {
                val prefix = var5[var7]
                val prefixLen = prefix.length
                if (!jarPath.regionMatches(offset, prefix, 0, prefixLen)) {
                    startsWithConcatenation = false
                    break
                }

                offset += prefixLen
            }

            if (startsWithConcatenation) {
                jarPath = jarPath.substring("jar".length + 1)
            }

            if (!jarPath.startsWith("file")) {
                return jarPath
            } else {
                try {
                    val parsedUrl = URL(jarPath)

                    val result: File
                    try {
                        result = File(parsedUrl.toURI().schemeSpecificPart)
                    } catch (var10: URISyntaxException) {
                        throw java.lang.IllegalArgumentException("URL='$parsedUrl'", var10)
                    }

                    return result.path.replace('\\', '/')
                } catch (var11: Exception) {
                    jarPath = jarPath.substring("file".length)
                    return if (jarPath.startsWith("://")) {
                        jarPath.substring("://".length)
                    } else {
                        if (!jarPath.isEmpty() && jarPath[0] == ':') jarPath.substring(1) else jarPath
                    }
                }
            }
        }
    }

    private fun endsWithIgnoreCase(text: CharSequence, suffix: CharSequence): Boolean {
        val l1 = text.length
        val l2 = suffix.length
        if (l1 < l2) {
            return false
        } else {
            for (i in l1 - 1 downTo l1 - l2) {
                if (!charsEqualIgnoreCase(text[i], suffix[i + l2 - l1])) {
                    return false
                }
            }
            return true
        }
    }

    private fun charsEqualIgnoreCase(a: Char, b: Char): Boolean {
        return a == b || toUpperCase(a) == toUpperCase(b) || toLowerCase(a) == toLowerCase(b)
    }

    private fun toUpperCase(a: Char): Char {
        return if (a < 'a') {
            a
        } else {
            if (a <= 'z') (a.code + -32).toChar() else a.uppercaseChar()
        }
    }

    private fun toLowerCase(a: Char): Char {
        return if (a > 'z') {
            a.lowercaseChar()
        } else {
            if (a >= 'A' && a <= 'Z') (a.code + 32).toChar() else a
        }
    }
}
