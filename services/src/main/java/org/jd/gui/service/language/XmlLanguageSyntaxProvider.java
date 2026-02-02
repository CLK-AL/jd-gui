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
 * XML language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in XML highlighting.
 *
 * @since 2024.1.0
 */
public class XmlLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "xml";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList(
            "xml", "xsd", "xsl", "xslt", "dtd", "svg", "pom", "fxml",
            "xaml", "xhtml", "wsdl", "rss", "atom", "config", "csproj",
            "vbproj", "props", "targets", "nuspec", "resx"
    );

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "XML";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_XML;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in XML TokenMaker
        return "org.fife.ui.rsyntaxtextarea.modes.XMLTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for XML
        // RSyntaxTextArea provides sufficient highlighting without ANTLR
        throw new UnsupportedOperationException("XML ANTLR parsing not implemented");
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // XML uses native RSyntaxTextArea highlighting
    }
}
