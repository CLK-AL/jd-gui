/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & JD-GUI Contributors.
 * This project is distributed under the GPLv3 license.
 */

package org.jd.gui.service.language;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.jd.gui.view.component.ProtobufTokenMaker;

import java.util.Arrays;
import java.util.Collection;

public class ProtobufLanguageSyntaxProvider extends AbstractLanguageSyntaxProvider {
    public static final String LANGUAGE_ID = "protobuf";
    public static final String SYNTAX_STYLE_PROTOBUF = "text/protobuf";

    @Override public String getLanguageId() { return LANGUAGE_ID; }
    @Override public String getDisplayName() { return "Protocol Buffers"; }
    @Override public String getSyntaxStyle() { return SYNTAX_STYLE_PROTOBUF; }
    @Override public Collection<String> getFileExtensions() { return Arrays.asList("proto"); }
    @Override public String getTokenMakerClassName() { return ProtobufTokenMaker.class.getName(); }
    @Override public void parse(CharStream input, ParseTreeListener listener) { throw new UnsupportedOperationException(); }
    @Override public int getPriority() { return 40; }
}
