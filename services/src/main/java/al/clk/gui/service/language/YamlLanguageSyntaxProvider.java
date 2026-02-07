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
 * YAML language syntax provider implementation.
 * Uses RSyntaxTextArea's built-in YAML highlighting.
 * Also handles OpenAPI and AsyncAPI specification files.
 *
 * @since 2024.1.0
 */
public class YamlLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "yaml";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList(
            "yaml", "yml",
            // OpenAPI/Swagger specs
            "openapi.yaml", "openapi.yml", "swagger.yaml", "swagger.yml",
            // AsyncAPI specs
            "asyncapi.yaml", "asyncapi.yml",
            // Common config files
            "docker-compose.yml", "docker-compose.yaml",
            ".gitlab-ci.yml", "bitbucket-pipelines.yml"
    );

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "YAML";
    }

    @Override
    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_YAML;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        // Uses RSyntaxTextArea's built-in YAML TokenMaker
        return "org.fife.ui.rsyntaxtextarea.modes.YamlTokenMaker";
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not implemented for YAML
        throw new UnsupportedOperationException("YAML ANTLR parsing not implemented");
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false;
    }
}
