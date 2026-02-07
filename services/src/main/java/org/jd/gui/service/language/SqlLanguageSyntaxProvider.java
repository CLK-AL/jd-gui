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

public class SqlLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {
    public static final String LANGUAGE_ID = "sql";

    @Override public String getLanguageId() { return LANGUAGE_ID; }
    @Override public String getDisplayName() { return "SQL"; }
    @Override public String getSyntaxStyle() { return SyntaxConstants.SYNTAX_STYLE_SQL; }
    @Override public Collection<String> getFileExtensions() { return Arrays.asList("sql", "ddl", "dml"); }
    @Override public String getTokenMakerClassName() { return "org.fife.ui.rsyntaxtextarea.modes.SQLTokenMaker"; }
    @Override public void parse(CharStream input, ParseTreeListener listener) { /* No ANTLR parsing for SQL - uses RSyntaxTextArea tokenizer */ }
    @Override public int getPriority() { return 40; }
}
