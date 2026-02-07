/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.spi

/**
 * JavaScript implementation of LanguageSyntaxProviderRegistry.
 * Uses manual registration since ServiceLoader is not available.
 */
actual object LanguageSyntaxProviderRegistry {
    private val providersByLanguage = mutableMapOf<String, LanguageSyntaxProvider>()
    private val providersByExtension = mutableMapOf<String, LanguageSyntaxProvider>()
    private val allProviders = mutableListOf<LanguageSyntaxProvider>()

    init {
        // Register built-in providers
        registerBuiltInProviders()
    }

    private fun registerBuiltInProviders() {
        // Built-in providers will be registered here
        // Additional providers can be registered via registerProvider()
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
     * Get all supported file extensions.
     */
    fun getSupportedExtensions(): Set<String> = providersByExtension.keys.toSet()

    /**
     * Get all supported language IDs.
     */
    fun getSupportedLanguages(): Set<String> = providersByLanguage.keys.toSet()
}
