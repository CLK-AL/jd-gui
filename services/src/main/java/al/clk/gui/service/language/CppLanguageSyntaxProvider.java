/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & gui Contributors.
 * This project is distributed under the GPLv3 license.
 */

package al.clk.gui.service.language;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import java.util.Arrays;
import java.util.Collection;

public class CppLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {
    public static final String LANGUAGE_ID = "cpp";

    @Override public String getLanguageId() { return LANGUAGE_ID; }
    @Override public String getDisplayName() { return "C++"; }
    @Override public String getSyntaxStyle() { return SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS; }
    @Override public Collection<String> getFileExtensions() { return Arrays.asList("cpp", "cxx", "cc", "hpp", "hxx", "hh"); }
    @Override public String getTokenMakerClassName() { return "org.fife.ui.rsyntaxtextarea.modes.CPlusPlusTokenMaker"; }
    @Override public void parse(CharStream input, ParseTreeListener listener) { /* Uses RSyntaxTextArea's built-in C++ support */ }
    @Override public int getPriority() { return 50; }
}
