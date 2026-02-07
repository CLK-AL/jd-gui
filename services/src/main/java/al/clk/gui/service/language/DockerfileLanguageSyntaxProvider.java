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
 * Dockerfile language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in Dockerfile highlighting if available,
 * falls back to Unix Shell highlighting otherwise.
 *
 * <p>Supports Dockerfile and dockerfile file names.</p>
 *
 * @since 2024.1.0
 */
public class DockerfileLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "dockerfile";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList(
            "Dockerfile", "dockerfile"
    );

    // Check if SYNTAX_STYLE_DOCKERFILE is available (RSyntaxTextArea 3.1.0+)
    private static final String DOCKERFILE_SYNTAX_STYLE = getSyntaxStyleSafely();

    private static String getSyntaxStyleSafely() {
        try {
            // Try to use SYNTAX_STYLE_DOCKERFILE if available
            return SyntaxConstants.SYNTAX_STYLE_DOCKERFILE;
        } catch (NoSuchFieldError e) {
            // Fall back to Unix Shell for older RSyntaxTextArea versions
            return SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
        }
    }

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "Dockerfile";
    }

    @Override
    public String getSyntaxStyle() {
        return DOCKERFILE_SYNTAX_STYLE;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Try Dockerfile TokenMaker first, fall back to Unix Shell if not available
        if (DOCKERFILE_SYNTAX_STYLE.equals(SyntaxConstants.SYNTAX_STYLE_DOCKERFILE)) {
            return "org.fife.ui.rsyntaxtextarea.modes.DockerTokenMaker";
        }
        return "org.fife.ui.rsyntaxtextarea.modes.UnixShellTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for Dockerfiles
        // RSyntaxTextArea provides sufficient highlighting without ANTLR
        // No-op implementation to avoid exceptions
    }

    @Override
    public int getPriority() {
        return 35;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // Dockerfile uses native RSyntaxTextArea highlighting
    }
}
