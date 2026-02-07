/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & JD-GUI Contributors.
 * This project is distributed under the GPLv3 license.
 */

package org.jd.gui.service.language;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class LuaLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "lua";
    private static final List<String> FILE_EXTENSIONS = Arrays.asList("lua");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "Lua";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_LUA;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        return "org.fife.ui.rsyntaxtextarea.modes.LuaTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // RSyntaxTextArea handles Lua highlighting natively
        // ANTLR parsing not required for basic syntax highlighting
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
