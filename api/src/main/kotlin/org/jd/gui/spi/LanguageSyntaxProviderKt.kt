/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & JD-GUI Contributors.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.spi

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.tree.ParseTreeListener

/**
 * Kotlin interface for language-specific syntax support.
 * This is the Kotlin-idiomatic version of LanguageSyntaxProvider.
 *
 * Implementations provide ANTLR parsing and RSyntaxTextArea highlighting
 * for different programming languages (Java, Kotlin, TypeScript, JavaScript, etc.).
 *
 * Register implementations in META-INF/services/org.jd.gui.spi.LanguageSyntaxProvider
 *
 * @since 2026.2.2
 */
interface LanguageSyntaxProviderKt {

    /**
     * Unique identifier for this language (lowercase).
     * Examples: "java", "kotlin", "typescript", "javascript"
     */
    val languageId: String

    /**
     * Human-readable display name for the language.
     * Examples: "Java", "Kotlin", "TypeScript", "JavaScript"
     */
    val displayName: String

    /**
     * Syntax style identifier for RSyntaxTextArea.
     * Used with RSyntaxTextArea.setSyntaxEditingStyle().
     */
    val syntaxStyle: String

    /**
     * File extensions supported by this language provider.
     * Extensions should not include the leading dot.
     */
    val fileExtensions: Collection<String>

    /**
     * Fully qualified class name of the TokenMaker for RSyntaxTextArea.
     */
    val tokenMakerClassName: String

    /**
     * Priority for this provider when multiple providers match.
     * Higher values have higher priority.
     */
    val priority: Int
        get() = 0

    /**
     * Parses the given input and calls methods on the provided listener.
     *
     * @param input the character stream to parse
     * @param listener the parse tree listener to receive parse events
     */
    fun parse(input: CharStream, listener: ParseTreeListener)

    /**
     * Creates a new declaration listener for collecting type/method/field declarations.
     *
     * @return new declaration listener instance, or null if not supported
     */
    fun createDeclarationListener(): LanguageDeclarationListenerKt?

    /**
     * Creates a new reference listener for collecting type/method references.
     *
     * @return new reference listener instance, or null if not supported
     */
    fun createReferenceListener(): LanguageReferenceListenerKt?

    /**
     * Checks if this provider supports the given file extension.
     */
    fun supportsExtension(extension: String): Boolean =
        fileExtensions.any { it.equals(extension, ignoreCase = true) }
}

/**
 * Listener interface for collecting declarations (types, methods, fields).
 */
interface LanguageDeclarationListenerKt : ParseTreeListener {
    /**
     * Returns collected declaration data after parsing.
     */
    val declarations: Collection<DeclarationDataKt>

    /**
     * Returns string declarations for hyperlink display.
     */
    val strings: Collection<StringDataKt>
}

/**
 * Listener interface for collecting references to types/methods.
 */
interface LanguageReferenceListenerKt : ParseTreeListener {
    /**
     * Initializes the reference listener with declaration data.
     */
    fun init(declarationListener: LanguageDeclarationListenerKt)

    /**
     * Returns collected reference data after parsing.
     */
    val references: Collection<ReferenceDataKt>
}

/**
 * Data class for declaration information (types, methods, fields).
 */
data class DeclarationDataKt(
    val startPosition: Int,
    val endPosition: Int,
    val typeName: String,
    val name: String,
    val descriptor: String
)

/**
 * Data class for reference information (type/method references).
 */
data class ReferenceDataKt(
    val startPosition: Int,
    val endPosition: Int,
    val typeName: String,
    val name: String,
    val descriptor: String,
    val owner: String
)

/**
 * Data class for string literals in the source code.
 */
data class StringDataKt(
    val startPosition: Int,
    val endPosition: Int,
    val text: String,
    val owner: String
)
