package dev.voqal.services

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import dev.voqal.utils.SharedAudioCapture
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

val Project.scope: CoroutineScope
    get() = throw UnsupportedOperationException()

fun Project.getVoqalLogger(kClass: KClass<*>): KLogger {
    throw UnsupportedOperationException()
}

//
//val Project.messageBusConnection: MessageBusConnection
//    get() = service<ProjectScopedService>().messageBusConnection
//val Project.scope: CoroutineScope
//    get() = service<ProjectScopedService>().scope
//val Project.logsTab: VoqalLogsTab
//    get() = service<ProjectScopedService>().voqalLogsTab
val Project.audioCapture: SharedAudioCapture
    get() = throw UnsupportedOperationException()

fun KLogger.warnChat(s: String, e: Throwable? = null) {
    warn(s, e)
    //todo: project.service<VoqalStatusService>().warnChat(input)
}

fun KLogger.errorChat(s: String, e: Throwable? = null) {
    error(s, e)
    //todo: project.service<VoqalStatusService>().errorChat(input)
}

fun Project.invokeLater(action: () -> Unit) {
    throw UnsupportedOperationException()
}

fun PsiElement.isFunction(): Boolean {
    throw UnsupportedOperationException()
}

fun PsiElement.isCodeBlock(): Boolean {
    throw UnsupportedOperationException()
}

fun PsiElement.isField(): Boolean {
    throw UnsupportedOperationException()
}

fun PsiElement.isClass(): Boolean {
    throw UnsupportedOperationException()
}

fun PsiElement.isFile(): Boolean {
    throw UnsupportedOperationException()
}

fun PsiElement.isIdentifier(): Boolean {
    throw UnsupportedOperationException()
}

fun PsiElement.isJvm(): Boolean {
    throw UnsupportedOperationException()
}

fun PsiElement.isPython(): Boolean {
    throw UnsupportedOperationException()
}

fun PsiElement.isGo(): Boolean {
    throw UnsupportedOperationException()
}

fun PsiElement.getCodeBlock(): PsiElement? {
    throw UnsupportedOperationException()
}

fun PsiFile.getFunctions(): List<PsiNamedElement> {
    throw UnsupportedOperationException()
}

fun PsiFile.getFields(): List<PsiNamedElement> {
    throw UnsupportedOperationException()
}

fun PsiFile.getClasses(): List<PsiNamedElement> {
    throw UnsupportedOperationException()
}

val RangeMarker.range: TextRange?
    get() = throw UnsupportedOperationException()
