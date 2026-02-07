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
 * Markdown language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in Markdown highlighting.
 *
 * <p>Markdown is a lightweight markup language for creating formatted text
 * using a plain-text editor. It is commonly used for documentation, README
 * files, and content management systems.</p>
 *
 * @since 2024.1.0
 */
public class MarkdownLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "markdown";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList("md", "markdown");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "Markdown";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in Markdown TokenMaker
        return "org.fife.ui.rsyntaxtextarea.modes.MarkdownTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for Markdown
        // RSyntaxTextArea provides sufficient highlighting without ANTLR
        // No-op implementation to avoid exceptions
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // Markdown uses native RSyntaxTextArea highlighting
    }
}
