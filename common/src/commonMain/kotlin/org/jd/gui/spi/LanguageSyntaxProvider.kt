/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.spi

import org.jd.gui.model.Declaration
import org.jd.gui.model.Reference
import org.jd.gui.model.SyntaxToken

/**
 * Service Provider Interface for language syntax highlighting and parsing.
 * This is the common interface used across all platforms (JVM, JS, WASM).
 *
 * ## Platform-Specific Implementations
 *
 * - **JVM**: Uses ANTLR 4.13.1 grammars and RSyntaxTextArea TokenMakers
 * - **JS/WASM**: Uses lightweight regex-based tokenizers or WebAssembly parsers
 *
 * ## Implementing a New Language
 *
 * ```kotlin
 * class KotlinLanguageSyntaxProvider : LanguageSyntaxProvider {
 *     override val languageId = "kotlin"
 *     override val displayName = "Kotlin"
 *     override val syntaxStyle = "text/kotlin"
 *     override val fileExtensions = listOf("kt", "kts")
 *
 *     override fun tokenize(source: String): List<SyntaxToken> {
 *         // Tokenization logic
 *     }
 * }
 * ```
 *
 * @since 2026.2.2
 */
interface LanguageSyntaxProvider {
    /**
     * Unique identifier for this language (e.g., "java", "kotlin", "python").
     */
    val languageId: String

    /**
     * Human-readable display name (e.g., "Java", "Kotlin", "Python 3").
     */
    val displayName: String

    /**
     * MIME-type style syntax identifier (e.g., "text/java", "text/kotlin").
     */
    val syntaxStyle: String

    /**
     * File extensions supported by this provider (without dots).
     * Example: ["java"], ["kt", "kts"], ["py", "pyw"]
     */
    val fileExtensions: Collection<String>

    /**
     * Priority for provider selection (higher = preferred).
     * Used when multiple providers support the same extension.
     */
    val priority: Int get() = 0

    /**
     * Tokenizes source code into syntax tokens for highlighting.
     *
     * @param source The source code to tokenize
     * @return List of syntax tokens
     */
    fun tokenize(source: String): List<SyntaxToken>

    /**
     * Extracts declarations from source code for indexing.
     *
     * @param source The source code to analyze
     * @return List of declarations found
     */
    fun extractDeclarations(source: String): List<Declaration> = emptyList()

    /**
     * Extracts references from source code for navigation.
     *
     * @param source The source code to analyze
     * @return List of references found
     */
    fun extractReferences(source: String): List<Reference> = emptyList()

    /**
     * Checks if this provider supports the given file extension.
     *
     * @param extension File extension (without dot)
     * @return true if supported
     */
    fun supportsExtension(extension: String): Boolean {
        return fileExtensions.any { it.equals(extension, ignoreCase = true) }
    }

    /**
     * Checks if this provider supports declaration analysis.
     */
    val supportsDeclarationAnalysis: Boolean get() = false

    /**
     * Checks if this provider supports reference analysis.
     */
    val supportsReferenceAnalysis: Boolean get() = false
}

/**
 * Registry for language syntax providers.
 * Uses expect/actual pattern for platform-specific service loading.
 */
expect object LanguageSyntaxProviderRegistry {
    /**
     * Get all registered providers.
     */
    fun getProviders(): List<LanguageSyntaxProvider>

    /**
     * Get provider by language ID.
     */
    fun getProviderByLanguage(languageId: String): LanguageSyntaxProvider?

    /**
     * Get provider by file extension.
     */
    fun getProviderByExtension(extension: String): LanguageSyntaxProvider?

    /**
     * Register a provider manually.
     */
    fun registerProvider(provider: LanguageSyntaxProvider)
}
