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
 * TypeScript language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in TypeScript highlighting.
 *
 * @since 2024.1.0
 */
public class TypeScriptLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "typescript";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList("ts", "tsx", "mts", "cts");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "TypeScript";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in TypeScript TokenMaker
        return "org.fife.ui.rsyntaxtextarea.modes.TypeScriptTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not yet implemented for TypeScript
        // Grammar available at: https://github.com/AntlrMegaGrammar/TypeScript
        throw new UnsupportedOperationException("TypeScript ANTLR parsing not yet implemented");
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
