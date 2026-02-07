/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & gui Contributors.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.service.language;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import al.clk.gui.view.component.KotlinTokenMaker;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Kotlin language syntax provider implementation.
 * Provides RSyntaxTextArea highlighting for Kotlin source files.
 *
 * @since 2024.1.0
 */
public class KotlinLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "kotlin";
    public static final String SYNTAX_STYLE_KOTLIN = "text/kotlin";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList("kt", "kts");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "Kotlin";
    }

    @Override
    public String getSyntaxStyle() {
        return SYNTAX_STYLE_KOTLIN;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        return KotlinTokenMaker.class.getName();
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not yet implemented for Kotlin
        // Grammar available at: https://github.com/antlr/grammars-v4/tree/master/kotlin/kotlin
        throw new UnsupportedOperationException("Kotlin ANTLR parsing not yet implemented");
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // Will be true when Kotlin grammar is added
    }
}
