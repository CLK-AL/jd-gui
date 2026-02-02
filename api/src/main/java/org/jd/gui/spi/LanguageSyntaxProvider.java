/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & JD-GUI Contributors.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.spi;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;

import java.util.Collection;

/**
 * Service provider interface for language-specific syntax support.
 * Implementations provide ANTLR parsing and RSyntaxTextArea highlighting
 * for different programming languages (Java, Kotlin, TypeScript, JavaScript, etc.).
 *
 * <p>Register implementations in META-INF/services/org.jd.gui.spi.LanguageSyntaxProvider</p>
 *
 * @since 2024.1.0
 */
public interface LanguageSyntaxProvider {

    /**
     * Returns the unique identifier for this language.
     * Examples: "java", "kotlin", "typescript", "javascript"
     *
     * @return language identifier string (lowercase)
     */
    String getLanguageId();

    /**
     * Returns human-readable display name for the language.
     * Examples: "Java", "Kotlin", "TypeScript", "JavaScript"
     *
     * @return display name for UI
     */
    String getDisplayName();

    /**
     * Returns the syntax style identifier for RSyntaxTextArea.
     * This is used with RSyntaxTextArea.setSyntaxEditingStyle().
     *
     * @return syntax style string (e.g., "text/java", "text/kotlin")
     */
    String getSyntaxStyle();

    /**
     * Returns file extensions supported by this language provider.
     * Extensions should not include the leading dot.
     *
     * @return collection of file extensions (e.g., ["java"], ["kt", "kts"])
     */
    Collection<String> getFileExtensions();

    /**
     * Returns the fully qualified class name of the TokenMaker for RSyntaxTextArea.
     * The TokenMaker provides syntax highlighting tokens.
     *
     * @return TokenMaker class name
     */
    String getTokenMakerClassName();

    /**
     * Parses the given input and calls methods on the provided listener.
     * This is the main entry point for ANTLR-based parsing.
     *
     * @param input the character stream to parse
     * @param listener the parse tree listener to receive parse events
     */
    void parse(CharStream input, ParseTreeListener listener);

    /**
     * Creates a new declaration listener for collecting type/method/field declarations.
     * The listener is language-specific but follows common patterns.
     *
     * @return new declaration listener instance, or null if not supported
     */
    LanguageDeclarationListener createDeclarationListener();

    /**
     * Creates a new reference listener for collecting type/method references.
     * The reference listener typically requires initialization from a declaration listener.
     *
     * @return new reference listener instance, or null if not supported
     */
    LanguageReferenceListener createReferenceListener();

    /**
     * Returns priority for this provider when multiple providers match.
     * Higher values have higher priority.
     *
     * @return priority value (default is 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Checks if this provider supports the given file extension.
     *
     * @param extension file extension without leading dot
     * @return true if this provider handles the extension
     */
    default boolean supportsExtension(String extension) {
        return getFileExtensions().stream()
                .anyMatch(ext -> ext.equalsIgnoreCase(extension));
    }

    /**
     * Listener interface for collecting declarations (types, methods, fields).
     */
    interface LanguageDeclarationListener extends ParseTreeListener {
        /**
         * Returns collected declaration data after parsing.
         *
         * @return collection of declarations
         */
        Collection<DeclarationData> getDeclarations();

        /**
         * Returns string declarations for hyperlink display.
         *
         * @return collection of string declarations
         */
        Collection<StringData> getStrings();
    }

    /**
     * Listener interface for collecting references to types/methods.
     */
    interface LanguageReferenceListener extends ParseTreeListener {
        /**
         * Initializes the reference listener with declaration data.
         *
         * @param declarationListener the declaration listener with parsed declarations
         */
        void init(LanguageDeclarationListener declarationListener);

        /**
         * Returns collected reference data after parsing.
         *
         * @return collection of references
         */
        Collection<ReferenceData> getReferences();
    }

    /**
     * Data class for declaration information (types, methods, fields).
     */
    class DeclarationData {
        public final int startPosition;
        public final int endPosition;
        public final String typeName;
        public final String name;
        public final String descriptor;

        public DeclarationData(int startPosition, int endPosition, String typeName, String name, String descriptor) {
            this.startPosition = startPosition;
            this.endPosition = endPosition;
            this.typeName = typeName;
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    /**
     * Data class for reference information (type/method references).
     */
    class ReferenceData {
        public final int startPosition;
        public final int endPosition;
        public final String typeName;
        public final String name;
        public final String descriptor;
        public final String owner;

        public ReferenceData(int startPosition, int endPosition, String typeName, String name, String descriptor, String owner) {
            this.startPosition = startPosition;
            this.endPosition = endPosition;
            this.typeName = typeName;
            this.name = name;
            this.descriptor = descriptor;
            this.owner = owner;
        }
    }

    /**
     * Data class for string literals in the source code.
     */
    class StringData {
        public final int startPosition;
        public final int endPosition;
        public final String text;
        public final String owner;

        public StringData(int startPosition, int endPosition, String text, String owner) {
            this.startPosition = startPosition;
            this.endPosition = endPosition;
            this.text = text;
            this.owner = owner;
        }
    }
}
