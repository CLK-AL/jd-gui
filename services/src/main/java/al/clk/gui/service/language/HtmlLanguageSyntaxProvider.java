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
 * HTML language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in HTML highlighting.
 *
 * @since 2024.1.0
 */
public class HtmlLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "html";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList(
            "html", "htm", "xhtml"
    );

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "HTML";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_HTML;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in HTML TokenMaker
        return "org.fife.ui.rsyntaxtextarea.modes.HTMLTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for HTML
        // RSyntaxTextArea provides sufficient highlighting without ANTLR
        throw new UnsupportedOperationException("HTML ANTLR parsing not implemented");
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // HTML uses native RSyntaxTextArea highlighting
    }
}
