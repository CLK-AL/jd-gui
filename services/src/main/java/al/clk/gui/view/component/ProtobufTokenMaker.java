/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & gui Contributors.
 * This project is distributed under the GPLv3 license.
 */

package al.clk.gui.view.component;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

import javax.swing.text.Segment;

/**
 * Token maker for Protocol Buffers (.proto) files.
 */
public class ProtobufTokenMaker extends AbstractTokenMaker {

    @Override
    public TokenMap getWordsToHighlight() {
        TokenMap tokenMap = new TokenMap();

        // Keywords
        tokenMap.put("syntax", Token.RESERVED_WORD);
        tokenMap.put("package", Token.RESERVED_WORD);
        tokenMap.put("import", Token.RESERVED_WORD);
        tokenMap.put("option", Token.RESERVED_WORD);
        tokenMap.put("message", Token.RESERVED_WORD);
        tokenMap.put("enum", Token.RESERVED_WORD);
        tokenMap.put("service", Token.RESERVED_WORD);
        tokenMap.put("rpc", Token.RESERVED_WORD);
        tokenMap.put("returns", Token.RESERVED_WORD);
        tokenMap.put("oneof", Token.RESERVED_WORD);
        tokenMap.put("map", Token.RESERVED_WORD);
        tokenMap.put("reserved", Token.RESERVED_WORD);
        tokenMap.put("extensions", Token.RESERVED_WORD);
        tokenMap.put("extend", Token.RESERVED_WORD);
        tokenMap.put("optional", Token.RESERVED_WORD);
        tokenMap.put("required", Token.RESERVED_WORD);
        tokenMap.put("repeated", Token.RESERVED_WORD);
        tokenMap.put("stream", Token.RESERVED_WORD);

        // Built-in types
        tokenMap.put("double", Token.DATA_TYPE);
        tokenMap.put("float", Token.DATA_TYPE);
        tokenMap.put("int32", Token.DATA_TYPE);
        tokenMap.put("int64", Token.DATA_TYPE);
        tokenMap.put("uint32", Token.DATA_TYPE);
        tokenMap.put("uint64", Token.DATA_TYPE);
        tokenMap.put("sint32", Token.DATA_TYPE);
        tokenMap.put("sint64", Token.DATA_TYPE);
        tokenMap.put("fixed32", Token.DATA_TYPE);
        tokenMap.put("fixed64", Token.DATA_TYPE);
        tokenMap.put("sfixed32", Token.DATA_TYPE);
        tokenMap.put("sfixed64", Token.DATA_TYPE);
        tokenMap.put("bool", Token.DATA_TYPE);
        tokenMap.put("string", Token.DATA_TYPE);
        tokenMap.put("bytes", Token.DATA_TYPE);

        // Boolean values
        tokenMap.put("true", Token.LITERAL_BOOLEAN);
        tokenMap.put("false", Token.LITERAL_BOOLEAN);

        return tokenMap;
    }

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        resetTokenList();
        char[] array = text.array;
        int offset = text.offset;
        int count = text.count;
        int end = offset + count;
        int newStartOffset = startOffset - offset;
        int currentTokenStart = offset;
        int currentTokenType = initialTokenType;

        for (int i = offset; i < end; i++) {
            char c = array[i];

            switch (currentTokenType) {
                case Token.NULL:
                    currentTokenStart = i;
                    if (Character.isWhitespace(c)) {
                        currentTokenType = Token.WHITESPACE;
                    } else if (c == '"') {
                        currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
                    } else if (c == '/' && i + 1 < end) {
                        if (array[i + 1] == '/') {
                            currentTokenType = Token.COMMENT_EOL;
                        } else if (array[i + 1] == '*') {
                            currentTokenType = Token.COMMENT_MULTILINE;
                        } else {
                            currentTokenType = Token.OPERATOR;
                        }
                    } else if (Character.isDigit(c)) {
                        currentTokenType = Token.LITERAL_NUMBER_DECIMAL_INT;
                    } else if (Character.isLetter(c) || c == '_') {
                        currentTokenType = Token.IDENTIFIER;
                    } else {
                        currentTokenType = Token.OPERATOR;
                    }
                    break;

                case Token.WHITESPACE:
                    if (!Character.isWhitespace(c)) {
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
                    if (!Character.isDigit(c) && c != '.') {
                        addToken(text, currentTokenStart, i - 1, Token.LITERAL_NUMBER_DECIMAL_INT, newStartOffset + currentTokenStart);
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
                        i++;
                    }
                    break;

                case Token.COMMENT_EOL:
                    break;

                case Token.COMMENT_MULTILINE:
                    if (c == '*' && i + 1 < end && array[i + 1] == '/') {
                        addToken(text, currentTokenStart, i + 1, Token.COMMENT_MULTILINE, newStartOffset + currentTokenStart);
                        currentTokenStart = i + 2;
                        currentTokenType = Token.NULL;
                        i++;
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

        if (currentTokenStart < end) {
            addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart);
        }
        addNullToken();
        return firstToken;
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
}
