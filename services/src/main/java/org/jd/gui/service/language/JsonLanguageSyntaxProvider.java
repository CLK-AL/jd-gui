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
 * JSON language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in JSON highlighting.
 *
 * @since 2024.1.0
 */
public class JsonLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "json";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList(
            "json", "jsonc", "json5", "geojson", "topojson",
            "har", "webmanifest", "jsonl", "ndjson"
    );

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "JSON";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_JSON;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in JSON TokenMaker
        return "org.fife.ui.rsyntaxtextarea.modes.JsonTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for JSON
        // RSyntaxTextArea provides sufficient highlighting without ANTLR
        throw new UnsupportedOperationException("JSON ANTLR parsing not implemented");
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // JSON uses native RSyntaxTextArea highlighting
    }
}
