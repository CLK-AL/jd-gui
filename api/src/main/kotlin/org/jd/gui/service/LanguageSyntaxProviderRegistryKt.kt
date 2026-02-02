/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & JD-GUI Contributors.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.service

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory
import org.jd.gui.spi.LanguageSyntaxProvider
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Kotlin registry for language syntax providers.
 * Provides centralized access to language-specific parsing and highlighting.
 *
 * Providers are loaded from META-INF/services/org.jd.gui.spi.LanguageSyntaxProvider
 *
 * @since 2026.2.2
 */
object LanguageSyntaxProviderRegistryKt {

    private val providersByLanguage = ConcurrentHashMap<String, LanguageSyntaxProvider>()
    private val providersByExtension = ConcurrentHashMap<String, LanguageSyntaxProvider>()
    private val providersBySyntaxStyle = ConcurrentHashMap<String, LanguageSyntaxProvider>()

    @Volatile
    private var tokenMakersRegistered = false

    init {
        loadProviders()
    }

    /**
     * Loads all registered language syntax providers via ServiceLoader.
     */
    private fun loadProviders() {
        ServiceLoader.load(LanguageSyntaxProvider::class.java).forEach { provider ->
            registerProvider(provider)
        }
    }

    /**
     * Registers a language syntax provider.
     */
    fun registerProvider(provider: LanguageSyntaxProvider) {
        val languageId = provider.languageId.lowercase()

        // Check if existing provider has lower priority
        val existing = providersByLanguage[languageId]
        if (existing != null && existing.priority >= provider.priority) {
            return // Keep existing higher-priority provider
        }

        providersByLanguage[languageId] = provider

        // Register by file extensions
        provider.fileExtensions.forEach { extension ->
            val ext = extension.lowercase()
            val existingExt = providersByExtension[ext]
            if (existingExt == null || existingExt.priority < provider.priority) {
                providersByExtension[ext] = provider
            }
        }

        // Register by syntax style
        providersBySyntaxStyle[provider.syntaxStyle] = provider
    }

    /**
     * Gets a provider by language identifier.
     */
    fun getProviderByLanguage(languageId: String): LanguageSyntaxProvider? =
        providersByLanguage[languageId.lowercase()]

    /**
     * Gets a provider by file extension.
     */
    fun getProviderByExtension(extension: String): LanguageSyntaxProvider? =
        providersByExtension[extension.lowercase()]

    /**
     * Gets a provider by syntax style.
     */
    fun getProviderBySyntaxStyle(syntaxStyle: String): LanguageSyntaxProvider? =
        providersBySyntaxStyle[syntaxStyle]

    /**
     * Returns all registered providers.
     */
    val allProviders: Collection<LanguageSyntaxProvider>
        get() = providersByLanguage.values.toList()

    /**
     * Returns all supported language identifiers.
     */
    val supportedLanguages: Set<String>
        get() = providersByLanguage.keys.toSet()

    /**
     * Returns all supported file extensions.
     */
    val supportedExtensions: Set<String>
        get() = providersByExtension.keys.toSet()

    /**
     * Registers all TokenMakers with RSyntaxTextArea.
     * Should be called once during application startup.
     */
    @Synchronized
    fun registerTokenMakers() {
        if (tokenMakersRegistered) return

        val atmf = TokenMakerFactory.getDefaultInstance() as AbstractTokenMakerFactory

        providersByLanguage.values.forEach { provider ->
            val syntaxStyle = provider.syntaxStyle
            val tokenMakerClass = provider.tokenMakerClassName
            if (syntaxStyle != null && tokenMakerClass != null) {
                atmf.putMapping(syntaxStyle, tokenMakerClass)
            }
        }

        tokenMakersRegistered = true
    }

    /**
     * Checks if a specific language is supported.
     */
    fun isLanguageSupported(languageId: String): Boolean =
        providersByLanguage.containsKey(languageId.lowercase())

    /**
     * Checks if a specific file extension is supported.
     */
    fun isExtensionSupported(extension: String): Boolean =
        providersByExtension.containsKey(extension.lowercase())

    /**
     * Gets the provider for a filename based on its extension.
     */
    fun getProviderForFile(filename: String?): LanguageSyntaxProvider? {
        if (filename == null) return null
        val lastDot = filename.lastIndexOf('.')
        if (lastDot < 0 || lastDot >= filename.length - 1) return null
        val extension = filename.substring(lastDot + 1)
        return getProviderByExtension(extension)
    }
}

// Extension functions for easier Kotlin usage
fun LanguageSyntaxProvider.isSupported(): Boolean =
    LanguageSyntaxProviderRegistryKt.isLanguageSupported(this.languageId)

fun String.toLanguageProvider(): LanguageSyntaxProvider? =
    LanguageSyntaxProviderRegistryKt.getProviderByExtension(this)
