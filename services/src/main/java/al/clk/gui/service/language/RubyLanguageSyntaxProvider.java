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
import java.util.List;

public class RubyLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "ruby";
    private static final List<String> FILE_EXTENSIONS = Arrays.asList("rb", "rbw", "rake", "gemspec");

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "Ruby";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_RUBY;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        return "org.fife.ui.rsyntaxtextarea.modes.RubyTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // RSyntaxTextArea handles Ruby highlighting natively
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
