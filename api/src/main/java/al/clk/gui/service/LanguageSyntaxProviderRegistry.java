/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & gui Contributors.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.service;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import al.clk.gui.spi.LanguageSyntaxProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for language syntax providers.
 * Provides centralized access to language-specific parsing and highlighting.
 *
 * <p>Providers are loaded from META-INF/services/al.clk.gui.spi.LanguageSyntaxProvider</p>
 *
 * @since 2024.1.0
 */
public class LanguageSyntaxProviderRegistry {

    private static final LanguageSyntaxProviderRegistry INSTANCE = new LanguageSyntaxProviderRegistry();

    private final Map<String, LanguageSyntaxProvider> providersByLanguage = new ConcurrentHashMap<>();
    private final Map<String, LanguageSyntaxProvider> providersByExtension = new ConcurrentHashMap<>();
    private final Map<String, LanguageSyntaxProvider> providersBySyntaxStyle = new ConcurrentHashMap<>();
    private boolean tokenMakersRegistered = false;

    private LanguageSyntaxProviderRegistry() {
        loadProviders();
    }

    /**
     * Returns the singleton registry instance.
     *
     * @return registry instance
     */
    public static LanguageSyntaxProviderRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Loads all registered language syntax providers via ServiceLoader.
     */
    private void loadProviders() {
        ServiceLoader<LanguageSyntaxProvider> loader = ServiceLoader.load(LanguageSyntaxProvider.class);

        for (LanguageSyntaxProvider provider : loader) {
            registerProvider(provider);
        }
    }

    /**
     * Registers a language syntax provider.
     *
     * @param provider the provider to register
     */
    public void registerProvider(LanguageSyntaxProvider provider) {
        String languageId = provider.getLanguageId().toLowerCase();

        // Check if existing provider has lower priority
        LanguageSyntaxProvider existing = providersByLanguage.get(languageId);
        if (existing != null && existing.getPriority() >= provider.getPriority()) {
            return; // Keep existing higher-priority provider
        }

        providersByLanguage.put(languageId, provider);

        // Register by file extensions
        for (String extension : provider.getFileExtensions()) {
            String ext = extension.toLowerCase();
            LanguageSyntaxProvider existingExt = providersByExtension.get(ext);
            if (existingExt == null || existingExt.getPriority() < provider.getPriority()) {
                providersByExtension.put(ext, provider);
            }
        }

        // Register by syntax style
        providersBySyntaxStyle.put(provider.getSyntaxStyle(), provider);
    }

    /**
     * Gets a provider by language identifier.
     *
     * @param languageId the language identifier (e.g., "java", "kotlin")
     * @return the provider, or null if not found
     */
    public LanguageSyntaxProvider getProviderByLanguage(String languageId) {
        return providersByLanguage.get(languageId.toLowerCase());
    }

    /**
     * Gets a provider by file extension.
     *
     * @param extension file extension without leading dot
     * @return the provider, or null if not found
     */
    public LanguageSyntaxProvider getProviderByExtension(String extension) {
        return providersByExtension.get(extension.toLowerCase());
    }

    /**
     * Gets a provider by syntax style.
     *
     * @param syntaxStyle the RSyntaxTextArea syntax style
     * @return the provider, or null if not found
     */
    public LanguageSyntaxProvider getProviderBySyntaxStyle(String syntaxStyle) {
        return providersBySyntaxStyle.get(syntaxStyle);
    }

    /**
     * Returns all registered providers.
     *
     * @return unmodifiable collection of providers
     */
    public Collection<LanguageSyntaxProvider> getAllProviders() {
        return Collections.unmodifiableCollection(providersByLanguage.values());
    }

    /**
     * Returns all supported language identifiers.
     *
     * @return unmodifiable set of language IDs
     */
    public Set<String> getSupportedLanguages() {
        return Collections.unmodifiableSet(providersByLanguage.keySet());
    }

    /**
     * Returns all supported file extensions.
     *
     * @return unmodifiable set of extensions
     */
    public Set<String> getSupportedExtensions() {
        return Collections.unmodifiableSet(providersByExtension.keySet());
    }

    /**
     * Registers all TokenMakers with RSyntaxTextArea.
     * Should be called once during application startup.
     */
    public synchronized void registerTokenMakers() {
        if (tokenMakersRegistered) {
            return;
        }

        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();

        for (LanguageSyntaxProvider provider : providersByLanguage.values()) {
            String syntaxStyle = provider.getSyntaxStyle();
            String tokenMakerClass = provider.getTokenMakerClassName();
            if (syntaxStyle != null && tokenMakerClass != null) {
                atmf.putMapping(syntaxStyle, tokenMakerClass);
            }
        }

        tokenMakersRegistered = true;
    }

    /**
     * Checks if a specific language is supported.
     *
     * @param languageId language identifier
     * @return true if the language is supported
     */
    public boolean isLanguageSupported(String languageId) {
        return providersByLanguage.containsKey(languageId.toLowerCase());
    }

    /**
     * Checks if a specific file extension is supported.
     *
     * @param extension file extension without dot
     * @return true if the extension is supported
     */
    public boolean isExtensionSupported(String extension) {
        return providersByExtension.containsKey(extension.toLowerCase());
    }

    /**
     * Gets the provider for a filename based on its extension.
     *
     * @param filename the filename with extension
     * @return the provider, or null if not found
     */
    public LanguageSyntaxProvider getProviderForFile(String filename) {
        if (filename == null) {
            return null;
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot >= filename.length() - 1) {
            return null;
        }
        String extension = filename.substring(lastDot + 1);
        return getProviderByExtension(extension);
    }
}
