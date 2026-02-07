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
 * LaTeX language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in LaTeX highlighting.
 *
 * <p>LaTeX is a document preparation system and markup language for high-quality
 * typesetting. It is widely used for scientific and mathematical documents,
 * as well as academic papers and technical reports.</p>
 *
 * @since 2024.1.0
 */
public class LatexLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "latex";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList("tex", "latex", "sty");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "LaTeX";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_LATEX;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in LaTeX TokenMaker
        return "org.fife.ui.rsyntaxtextarea.modes.LatexTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for LaTeX
        // RSyntaxTextArea provides sufficient highlighting without ANTLR
        // No-op implementation to avoid exceptions
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // LaTeX uses native RSyntaxTextArea highlighting
    }
}
