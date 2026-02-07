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
 * SCSS/Sass language syntax provider implementation.
 * Uses RSyntaxTextArea's CSS highlighting as a fallback (similar syntax).
 *
 * <p>SCSS (Sassy CSS) is a CSS preprocessor that provides variables, nesting,
 * mixins, inheritance, and other features that make CSS more powerful.</p>
 *
 * <p>Supports both .scss (SCSS syntax) and .sass (indented syntax) files.</p>
 *
 * @since 2024.1.0
 */
public class ScssLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "scss";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList("scss", "sass");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "SCSS/Sass";
    }

    @Override
    public String getSyntaxStyle() {
        // Use CSS style as fallback - SCSS syntax is similar to CSS
        return SyntaxConstants.SYNTAX_STYLE_CSS;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's CSS TokenMaker as fallback
        return "org.fife.ui.rsyntaxtextarea.modes.CSSTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for SCSS
        // RSyntaxTextArea CSS highlighting provides reasonable support
        // No-op implementation to avoid exceptions
    }

    @Override
    public int getPriority() {
        return 35;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // SCSS uses native RSyntaxTextArea CSS highlighting
    }
}
