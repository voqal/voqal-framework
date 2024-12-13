package dev.voqal.core

import com.intellij.diagnostic.ActivityCategory
import com.intellij.openapi.extensions.ExtensionsArea
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.MessageBus
import dev.voqal.services.*

class MockProject : Project {

    var chatToolWindowContentManager = MockChatToolWindowContentManager()
    var directiveService = MockDirectiveService(this)
    var toolService = MockToolService()
    var configService = MockConfigService(this)
    var voiceService = MockVoiceService()
    var contextService = MockContextService()

    override fun <T : Any?> getUserData(key: Key<T>): T? {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
        TODO("Not yet implemented")
    }

    override fun dispose() {
        TODO("Not yet implemented")
    }

    override fun getExtensionArea(): ExtensionsArea {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> getComponent(interfaceClass: Class<T>): T {
        TODO("Not yet implemented")
    }

    override fun hasComponent(interfaceClass: Class<*>): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInjectionForExtensionSupported(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getMessageBus(): MessageBus {
        TODO("Not yet implemented")
    }

    override fun isDisposed(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getDisposed(): Condition<*> {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> getService(serviceClass: Class<T>): T {
        if (serviceClass == VoqalVoiceService::class.java) {
            return voiceService as T
        } else if (serviceClass == VoqalToolService::class.java) {
            return toolService as T
        } else if (serviceClass == VoqalDirectiveService::class.java) {
            return directiveService as T
        } else if (serviceClass == ChatToolWindowContentManager::class.java) {
            return chatToolWindowContentManager as T
        } else if (serviceClass == VoqalConfigService::class.java) {
            return configService as T
        } else if (serviceClass == VoqalContextService::class.java) {
            return contextService as T
        } else {
            throw IllegalArgumentException("Service not found")
        }
    }

    override fun <T : Any?> instantiateClass(aClass: Class<T>, pluginId: PluginId): T {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> instantiateClass(className: String, pluginDescriptor: PluginDescriptor): T & Any {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> instantiateClassWithConstructorInjection(
        aClass: Class<T>,
        key: Any,
        pluginId: PluginId
    ): T {
        TODO("Not yet implemented")
    }

    override fun createError(error: Throwable, pluginId: PluginId): RuntimeException {
        TODO("Not yet implemented")
    }

    override fun createError(message: String, pluginId: PluginId): RuntimeException {
        TODO("Not yet implemented")
    }

    override fun createError(
        message: String,
        error: Throwable?,
        pluginId: PluginId,
        attachments: MutableMap<String, String>?
    ): RuntimeException {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> loadClass(className: String, pluginDescriptor: PluginDescriptor): Class<T> {
        TODO("Not yet implemented")
    }

    override fun getActivityCategory(isExtension: Boolean): ActivityCategory {
        TODO("Not yet implemented")
    }

    override fun getName(): String {
        TODO("Not yet implemented")
    }

    override fun getBaseDir(): VirtualFile {
        TODO("Not yet implemented")
    }

    override fun getBasePath(): String? {
        TODO("Not yet implemented")
    }

    override fun getProjectFile(): VirtualFile? {
        TODO("Not yet implemented")
    }

    override fun getProjectFilePath(): String? {
        TODO("Not yet implemented")
    }

    override fun getWorkspaceFile(): VirtualFile? {
        TODO("Not yet implemented")
    }

    override fun getLocationHash(): String {
        TODO("Not yet implemented")
    }

    override fun save() {
        TODO("Not yet implemented")
    }

    override fun isOpen(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInitialized(): Boolean {
        TODO("Not yet implemented")
    }
}