/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & gui Contributors.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.service.language;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * LESS language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in LESS highlighting.
 *
 * <p>LESS is a CSS preprocessor that extends CSS with variables, mixins,
 * functions, and other techniques that make CSS more maintainable.</p>
 *
 * @since 2024.1.0
 */
public class LessLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "less";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList("less");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "LESS";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_LESS;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in LESS TokenMaker
        return "org.fife.ui.rsyntaxtextarea.modes.LessTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for LESS
        // RSyntaxTextArea provides sufficient highlighting without ANTLR
        // No-op implementation to avoid exceptions
    }

    @Override
    public int getPriority() {
        return 35;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // LESS uses native RSyntaxTextArea highlighting
    }
}
