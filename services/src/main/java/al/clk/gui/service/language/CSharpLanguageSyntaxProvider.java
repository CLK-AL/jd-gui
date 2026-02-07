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
 * C# language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in C# highlighting.
 *
 * <p>C# (C-Sharp) is a modern, object-oriented programming language developed
 * by Microsoft. It is widely used for Windows application development, game
 * development with Unity, and enterprise software.</p>
 *
 * @since 2024.1.0
 */
public class CSharpLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "csharp";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList("cs");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "C#";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_CSHARP;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in C# TokenMaker
        return "org.fife.ui.rsyntaxtextarea.modes.CSharpTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for C#
        // RSyntaxTextArea provides sufficient highlighting without ANTLR
        // No-op implementation to avoid exceptions
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // C# uses native RSyntaxTextArea highlighting
    }
}
