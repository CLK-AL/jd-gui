/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & gui Contributors.
 * This project is distributed under the GPLv3 license.
 */

package al.clk.gui.service.language;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class RustLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "rust";
    public static final String SYNTAX_STYLE_RUST = "text/rust";
    private static final List<String> FILE_EXTENSIONS = Arrays.asList("rs");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "Rust";
    }

    @Override
    public String getSyntaxStyle() {
        return SYNTAX_STYLE_RUST;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Use C++-style highlighting as fallback (Rust has similar syntax)
        return "org.fife.ui.rsyntaxtextarea.modes.CPlusPlusTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // Rust ANTLR parsing not yet implemented
        // Uses C++-style TokenMaker as fallback for basic highlighting
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
