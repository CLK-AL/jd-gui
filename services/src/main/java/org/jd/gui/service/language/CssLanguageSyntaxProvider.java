/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & JD-GUI Contributors.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.service.language;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * CSS language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in CSS highlighting.
 *
 * @since 2024.1.0
 */
public class CssLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "css";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList("css");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "CSS";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_CSS;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in CSS TokenMaker
        return "org.fife.ui.rsyntaxtextarea.modes.CSSTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for CSS
        // RSyntaxTextArea provides sufficient highlighting without ANTLR
        throw new UnsupportedOperationException("CSS ANTLR parsing not implemented");
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // CSS uses native RSyntaxTextArea highlighting
    }
}
