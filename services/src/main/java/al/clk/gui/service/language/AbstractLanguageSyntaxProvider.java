/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & gui Contributors.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.service.language;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import al.clk.gui.spi.LanguageSyntaxProvider;

import java.util.Collection;
import java.util.Collections;

/**
 * Abstract base class for language syntax providers.
 * Provides common functionality and sensible defaults for language implementations.
 *
 * <p>To add support for a new language (e.g., Kotlin, TypeScript, JavaScript):</p>
 * <ol>
 *   <li>Add ANTLR grammar files (.g4) to api/src/main/antlr4/[language]/</li>
 *   <li>Create a TokenMaker class extending AbstractTokenMaker or language-specific base</li>
 *   <li>Extend this class and implement required methods</li>
 *   <li>Register in META-INF/services/al.clk.gui.spi.LanguageSyntaxProvider</li>
 * </ol>
 *
 * <h3>Example for Kotlin:</h3>
 * <pre>
 * public class KotlinLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {
 *     {@literal @}Override
 *     public String getLanguageId() { return "kotlin"; }
 *
 *     {@literal @}Override
 *     public String getDisplayName() { return "Kotlin"; }
 *
 *     {@literal @}Override
 *     public String getSyntaxStyle() { return "text/kotlin"; }
 *
 *     {@literal @}Override
 *     public Collection{@literal <}String{@literal >} getFileExtensions() {
 *         return Arrays.asList("kt", "kts");
 *     }
 *
 *     {@literal @}Override
 *     public String getTokenMakerClassName() {
 *         return KotlinTokenMaker.class.getName();
 *     }
 *
 *     {@literal @}Override
 *     public void parse(CharStream input, ParseTreeListener listener) {
 *         KotlinLexer lexer = new KotlinLexer(input);
 *         CommonTokenStream tokens = new CommonTokenStream(lexer);
 *         KotlinParser parser = new KotlinParser(tokens);
 *         ParseTreeWalker.DEFAULT.walk(listener, parser.kotlinFile());
 *     }
 * }
 * </pre>
 *
 * @since 2024.1.0
 */
public abstract class AbstractLanguageSyntaxProvider implements LanguageSyntaxProvider {

    @Override
    public LanguageDeclarationListener createDeclarationListener() {
        // Default implementation returns null (no declaration support)
        // Override in language-specific implementations
        return null;
    }

    @Override
    public LanguageReferenceListener createReferenceListener() {
        // Default implementation returns null (no reference support)
        // Override in language-specific implementations
        return null;
    }

    @Override
    public int getPriority() {
        // Default priority - can be overridden by specific languages
        return 0;
    }

    @Override
    public boolean supportsExtension(String extension) {
        if (extension == null) {
            return false;
        }
        String ext = extension.toLowerCase();
        return getFileExtensions().stream()
                .anyMatch(e -> e.equalsIgnoreCase(ext));
    }

    /**
     * Checks if this provider supports ANTLR-based parsing.
     * Override to return true when parse() is fully implemented.
     *
     * @return true if ANTLR parsing is available
     */
    public boolean supportsAntlrParsing() {
        return false;
    }

    /**
     * Checks if this provider supports declaration/reference analysis.
     * This enables hyperlink navigation in the source view.
     *
     * @return true if declaration analysis is available
     */
    public boolean supportsDeclarationAnalysis() {
        return createDeclarationListener() != null;
    }

    /**
     * Checks if this provider supports reference analysis.
     * This enables "find usages" functionality.
     *
     * @return true if reference analysis is available
     */
    public boolean supportsReferenceAnalysis() {
        return createReferenceListener() != null;
    }
}
