/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & JD-GUI Contributors.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

import javax.swing.text.Segment;

/**
 * Token maker for GraphQL schema and query files.
 *
 * GraphQL keywords include:
 * - Type system: type, interface, union, enum, input, scalar, directive
 * - Schema: schema, query, mutation, subscription
 * - Modifiers: extend, implements, on
 * - Values: true, false, null
 * - Built-in directives: @deprecated, @skip, @include, @specifiedBy
 *
 * @since 2024.1.0
 */
public class GraphQLTokenMaker extends AbstractTokenMaker {

    private int currentTokenStart;
    private int currentTokenType;

    @Override
    public TokenMap getWordsToHighlight() {
        TokenMap tokenMap = new TokenMap();

        // Type definition keywords
        tokenMap.put("type", Token.RESERVED_WORD);
        tokenMap.put("interface", Token.RESERVED_WORD);
        tokenMap.put("union", Token.RESERVED_WORD);
        tokenMap.put("enum", Token.RESERVED_WORD);
        tokenMap.put("input", Token.RESERVED_WORD);
        tokenMap.put("scalar", Token.RESERVED_WORD);
        tokenMap.put("directive", Token.RESERVED_WORD);

        // Schema keywords
        tokenMap.put("schema", Token.RESERVED_WORD);
        tokenMap.put("query", Token.RESERVED_WORD);
        tokenMap.put("mutation", Token.RESERVED_WORD);
        tokenMap.put("subscription", Token.RESERVED_WORD);

        // Modifiers
        tokenMap.put("extend", Token.RESERVED_WORD);
        tokenMap.put("implements", Token.RESERVED_WORD);
        tokenMap.put("on", Token.RESERVED_WORD);
        tokenMap.put("fragment", Token.RESERVED_WORD);
        tokenMap.put("repeatable", Token.RESERVED_WORD);

        // Built-in values
        tokenMap.put("true", Token.LITERAL_BOOLEAN);
        tokenMap.put("false", Token.LITERAL_BOOLEAN);
        tokenMap.put("null", Token.RESERVED_WORD);

        // Built-in scalars
        tokenMap.put("Int", Token.DATA_TYPE);
        tokenMap.put("Float", Token.DATA_TYPE);
        tokenMap.put("String", Token.DATA_TYPE);
        tokenMap.put("Boolean", Token.DATA_TYPE);
        tokenMap.put("ID", Token.DATA_TYPE);

        return tokenMap;
    }

    @Override
    public void addToken(Segment segment, int start, int end, int tokenType, int startOffset) {
        if (tokenType == Token.IDENTIFIER) {
            int value = wordsToHighlight.get(segment, start, end);
            if (value != -1) {
                tokenType = value;
            }
        }
        super.addToken(segment, start, end, tokenType, startOffset);
    }

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        resetTokenList();

        char[] array = text.array;
        int offset = text.offset;
        int count = text.count;
        int end = offset + count;

        int newStartOffset = startOffset - offset;
        currentTokenStart = offset;
        currentTokenType = initialTokenType;

        for (int i = offset; i < end; i++) {
            char c = array[i];

            switch (currentTokenType) {
                case Token.NULL:
                    currentTokenStart = i;
                    switch (c) {
                        case ' ':
                        case '\t':
                            currentTokenType = Token.WHITESPACE;
                            break;
                        case '"':
                            if (i + 2 < end && array[i + 1] == '"' && array[i + 2] == '"') {
                                currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
                                i += 2; // Skip to end of opening triple quote
                            } else {
                                currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
                            }
                            break;
                        case '#':
                            currentTokenType = Token.COMMENT_EOL;
                            break;
                        case '@':
                            currentTokenType = Token.ANNOTATION;
                            break;
                        case '$':
                            currentTokenType = Token.VARIABLE;
                            break;
                        default:
                            if (Character.isDigit(c) || (c == '-' && i + 1 < end && Character.isDigit(array[i + 1]))) {
                                currentTokenType = Token.LITERAL_NUMBER_DECIMAL_INT;
                            } else if (Character.isLetter(c) || c == '_') {
                                currentTokenType = Token.IDENTIFIER;
                            } else {
                                currentTokenType = Token.OPERATOR;
                            }
                    }
                    break;

                case Token.WHITESPACE:
                    if (c != ' ' && c != '\t') {
                        addToken(text, currentTokenStart, i - 1, Token.WHITESPACE, newStartOffset + currentTokenStart);
                        currentTokenStart = i;
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case Token.IDENTIFIER:
                    if (!Character.isLetterOrDigit(c) && c != '_') {
                        addToken(text, currentTokenStart, i - 1, Token.IDENTIFIER, newStartOffset + currentTokenStart);
                        currentTokenStart = i;
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case Token.LITERAL_NUMBER_DECIMAL_INT:
                    if (!Character.isDigit(c) && c != '.' && c != 'e' && c != 'E' && c != '-' && c != '+') {
                        addToken(text, currentTokenStart, i - 1, Token.LITERAL_NUMBER_FLOAT, newStartOffset + currentTokenStart);
                        currentTokenStart = i;
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case Token.LITERAL_STRING_DOUBLE_QUOTE:
                    if (c == '"') {
                        addToken(text, currentTokenStart, i, Token.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset + currentTokenStart);
                        currentTokenStart = i + 1;
                        currentTokenType = Token.NULL;
                    } else if (c == '\\' && i + 1 < end) {
                        i++; // Skip escaped character
                    }
                    break;

                case Token.COMMENT_EOL:
                    // Comment goes to end of line
                    break;

                case Token.ANNOTATION:
                    if (!Character.isLetterOrDigit(c) && c != '_') {
                        addToken(text, currentTokenStart, i - 1, Token.ANNOTATION, newStartOffset + currentTokenStart);
                        currentTokenStart = i;
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case Token.VARIABLE:
                    if (!Character.isLetterOrDigit(c) && c != '_') {
                        addToken(text, currentTokenStart, i - 1, Token.VARIABLE, newStartOffset + currentTokenStart);
                        currentTokenStart = i;
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case Token.OPERATOR:
                    addToken(text, currentTokenStart, i - 1, Token.OPERATOR, newStartOffset + currentTokenStart);
                    currentTokenStart = i;
                    currentTokenType = Token.NULL;
                    i--;
                    break;
            }
        }

        // Add final token
        if (currentTokenStart < end) {
            if (currentTokenType == Token.LITERAL_STRING_DOUBLE_QUOTE) {
                addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart);
            } else if (currentTokenType == Token.NULL) {
                addNullToken();
            } else {
                addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart);
            }
        }

        addNullToken();
        return firstToken;
    }
}
