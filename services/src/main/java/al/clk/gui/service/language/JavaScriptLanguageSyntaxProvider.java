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
 * JavaScript language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in JavaScript highlighting.
 *
 * @since 2024.1.0
 */
public class JavaScriptLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "javascript";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList("js", "jsx", "mjs", "cjs");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "JavaScript";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in JavaScript TokenMaker
        return "org.fife.ui.rsyntaxtextarea.modes.JavaScriptTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not yet implemented for JavaScript
        // Grammar available at: https://github.com/AntlrMegaGrammar/JavaScript
        throw new UnsupportedOperationException("JavaScript ANTLR parsing not yet implemented");
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false;
    }
}
