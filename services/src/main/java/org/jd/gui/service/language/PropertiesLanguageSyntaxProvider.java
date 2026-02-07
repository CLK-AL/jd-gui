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
 * Properties file language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in Properties file highlighting.
 *
 * <p>Supports .properties, .props, and .conf file extensions.</p>
 *
 * @since 2024.1.0
 */
public class PropertiesLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "properties";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList(
            "properties", "props", "conf"
    );

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "Properties";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in Properties file TokenMaker
        return "org.fife.ui.rsyntaxtextarea.modes.PropertiesFileTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for Properties files
        // RSyntaxTextArea provides sufficient highlighting without ANTLR
        // No-op implementation to avoid exceptions
    }

    @Override
    public int getPriority() {
        return 25;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // Properties uses native RSyntaxTextArea highlighting
    }
}
