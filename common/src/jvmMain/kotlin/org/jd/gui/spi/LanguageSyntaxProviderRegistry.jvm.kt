/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.spi

import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM implementation of LanguageSyntaxProviderRegistry using ServiceLoader.
 */
actual object LanguageSyntaxProviderRegistry {
    private val providersByLanguage = ConcurrentHashMap<String, LanguageSyntaxProvider>()
    private val providersByExtension = ConcurrentHashMap<String, LanguageSyntaxProvider>()
    private val allProviders = mutableListOf<LanguageSyntaxProvider>()

    init {
        // Load providers via ServiceLoader
        loadProvidersFromServiceLoader()
    }

    private fun loadProvidersFromServiceLoader() {
        val loader = ServiceLoader.load(LanguageSyntaxProvider::class.java)
        loader.forEach { provider ->
            registerProviderInternal(provider)
        }
    }

    private fun registerProviderInternal(provider: LanguageSyntaxProvider) {
        allProviders.add(provider)
        providersByLanguage[provider.languageId.lowercase()] = provider
        provider.fileExtensions.forEach { ext ->
            val existing = providersByExtension[ext.lowercase()]
            if (existing == null || provider.priority > existing.priority) {
                providersByExtension[ext.lowercase()] = provider
            }
        }
    }

    actual fun getProviders(): List<LanguageSyntaxProvider> = allProviders.toList()

    actual fun getProviderByLanguage(languageId: String): LanguageSyntaxProvider? {
        return providersByLanguage[languageId.lowercase()]
    }

    actual fun getProviderByExtension(extension: String): LanguageSyntaxProvider? {
        val ext = extension.lowercase().removePrefix(".")
        return providersByExtension[ext]
    }

    actual fun registerProvider(provider: LanguageSyntaxProvider) {
        registerProviderInternal(provider)
    }

    /**
     * Reload all providers (useful for plugin systems).
     */
    fun reload() {
        providersByLanguage.clear()
        providersByExtension.clear()
        allProviders.clear()
        loadProvidersFromServiceLoader()
    }

    /**
     * Get all supported file extensions.
     */
    fun getSupportedExtensions(): Set<String> = providersByExtension.keys.toSet()

    /**
     * Get all supported language IDs.
     */
    fun getSupportedLanguages(): Set<String> = providersByLanguage.keys.toSet()
}
