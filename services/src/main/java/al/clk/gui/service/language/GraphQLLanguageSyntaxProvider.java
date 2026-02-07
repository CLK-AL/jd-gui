/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & gui Contributors.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.service.language;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import al.clk.gui.view.component.GraphQLTokenMaker;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * GraphQL language syntax provider implementation.
 * Provides custom TokenMaker for GraphQL schema and query files.
 *
 * @since 2024.1.0
 */
public class GraphQLLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {

    public static final String LANGUAGE_ID = "graphql";
    public static final String SYNTAX_STYLE_GRAPHQL = "text/graphql";

    private static final List<String> FILE_EXTENSIONS = Arrays.asList(
            "graphql", "gql", "graphqls"
    );

    @Override
    public String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "GraphQL";
    }

    @Override
    public String getSyntaxStyle() {
        return SYNTAX_STYLE_GRAPHQL;
    }

    @Override
    public Collection<String> getFileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public String getTokenMakerClassName() {
        return GraphQLTokenMaker.class.getName();
    }

    @Override
    public void parse(CharStream input, ParseTreeListener listener) {
        // ANTLR parsing not yet implemented for GraphQL
        // Grammar available at: https://github.com/antlr/grammars-v4/tree/master/graphql
        throw new UnsupportedOperationException("GraphQL ANTLR parsing not yet implemented");
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public boolean supportsAntlrParsing() {
        return false; // Will be true when GraphQL grammar is added
    }
}
