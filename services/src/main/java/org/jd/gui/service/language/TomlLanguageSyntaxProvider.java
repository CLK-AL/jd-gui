/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & JD-GUI Contributors.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.service.language;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * TOML language syntax provider implementation.
 * Uses RSyntaxTextArea's INI file highlighting as a fallback
 * since TOML is not natively supported by RSyntaxTextArea.
 *
 * <p>TOML (Tom's Obvious Minimal Language) shares similar syntax
 * characteristics with INI files, making INI highlighting a
 * reasonable approximation.</p>
 *
 * <p>Supports .toml file extension.</p>
 *
 * @since 2024.1.0
 */
public class TomlLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "toml";

    private static final List<String> FILE_EXTENSIONS = Collections.singletonList("toml");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "TOML";
    }

    @Override
    public String getSyntaxStyle() {
        // Use INI style as fallback since TOML is not natively supported
        return SyntaxConstants.SYNTAX_STYLE_INI;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in INI TokenMaker as fallback
        return "org.fife.ui.rsyntaxtextarea.modes.IniTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for TOML files
        // Using INI-style highlighting from RSyntaxTextArea
        // No-op implementation to avoid exceptions
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // TOML uses INI-style highlighting from RSyntaxTextArea
    }
}
