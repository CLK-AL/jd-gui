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
 * Groovy language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in Groovy highlighting.
 * Also handles Gradle build files.
 *
 * @since 2024.1.0
 */
public class GroovyLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "groovy";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList(
            "groovy", "gradle", "gvy", "gy", "gsh"
    );

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "Groovy";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_GROOVY;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        return "org.fife.ui.rsyntaxtextarea.modes.GroovyTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for Groovy
        // RSyntaxTextArea provides sufficient highlighting without ANTLR
        // Callers should check supportsAntlrParsing() before calling this method
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // Groovy uses native RSyntaxTextArea highlighting
    }
}
