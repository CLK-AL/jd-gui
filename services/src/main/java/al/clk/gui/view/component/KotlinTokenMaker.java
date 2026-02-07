/*
 * Copyright (c) 2008-2024 Emmanuel Dupuy & gui Contributors.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.view.component;

import org.fife.ui.rsyntaxtextarea.AbstractJavaTokenMaker;
import org.fife.ui.rsyntaxtextarea.TokenMap;
import org.fife.ui.rsyntaxtextarea.Token;

/**
 * Token maker for Kotlin language.
 * Extends Java token maker and adds Kotlin-specific keywords.
 *
 * Kotlin keywords include:
 * - Hard keywords: as, break, class, continue, do, else, false, for, fun, if, in, interface,
 *   is, null, object, package, return, super, this, throw, true, try, typealias, typeof,
 *   val, var, when, while
 * - Soft keywords: by, catch, constructor, delegate, dynamic, field, file, finally, get,
 *   import, init, param, property, receiver, set, setparam, value, where
 * - Modifier keywords: abstract, actual, annotation, companion, const, crossinline, data,
 *   enum, expect, external, final, infix, inline, inner, internal, lateinit, noinline,
 *   open, operator, out, override, private, protected, public, reified, sealed, suspend,
 *   tailrec, vararg
 *
 * @since 2024.1.0
 */
public class KotlinTokenMaker extends AbstractJavaTokenMaker {

    /**
     * Returns the words to highlight for Kotlin.
     *
     * @return A TokenMap containing all Kotlin keywords.
     */
    @Override
    public TokenMap getWordsToHighlight() {
        TokenMap tokenMap = new TokenMap();

        // Hard keywords
        tokenMap.put("as", Token.RESERVED_WORD);
        tokenMap.put("as?", Token.RESERVED_WORD);
        tokenMap.put("break", Token.RESERVED_WORD);
        tokenMap.put("class", Token.RESERVED_WORD);
        tokenMap.put("continue", Token.RESERVED_WORD);
        tokenMap.put("do", Token.RESERVED_WORD);
        tokenMap.put("else", Token.RESERVED_WORD);
        tokenMap.put("false", Token.LITERAL_BOOLEAN);
        tokenMap.put("for", Token.RESERVED_WORD);
        tokenMap.put("fun", Token.RESERVED_WORD);
        tokenMap.put("if", Token.RESERVED_WORD);
        tokenMap.put("in", Token.RESERVED_WORD);
        tokenMap.put("!in", Token.RESERVED_WORD);
        tokenMap.put("interface", Token.RESERVED_WORD);
        tokenMap.put("is", Token.RESERVED_WORD);
        tokenMap.put("!is", Token.RESERVED_WORD);
        tokenMap.put("null", Token.RESERVED_WORD);
        tokenMap.put("object", Token.RESERVED_WORD);
        tokenMap.put("package", Token.RESERVED_WORD);
        tokenMap.put("return", Token.RESERVED_WORD);
        tokenMap.put("super", Token.RESERVED_WORD);
        tokenMap.put("this", Token.RESERVED_WORD);
        tokenMap.put("throw", Token.RESERVED_WORD);
        tokenMap.put("true", Token.LITERAL_BOOLEAN);
        tokenMap.put("try", Token.RESERVED_WORD);
        tokenMap.put("typealias", Token.RESERVED_WORD);
        tokenMap.put("typeof", Token.RESERVED_WORD);
        tokenMap.put("val", Token.RESERVED_WORD);
        tokenMap.put("var", Token.RESERVED_WORD);
        tokenMap.put("when", Token.RESERVED_WORD);
        tokenMap.put("while", Token.RESERVED_WORD);

        // Soft keywords
        tokenMap.put("by", Token.RESERVED_WORD);
        tokenMap.put("catch", Token.RESERVED_WORD);
        tokenMap.put("constructor", Token.RESERVED_WORD);
        tokenMap.put("delegate", Token.RESERVED_WORD);
        tokenMap.put("dynamic", Token.RESERVED_WORD);
        tokenMap.put("field", Token.RESERVED_WORD);
        tokenMap.put("file", Token.RESERVED_WORD);
        tokenMap.put("finally", Token.RESERVED_WORD);
        tokenMap.put("get", Token.RESERVED_WORD);
        tokenMap.put("import", Token.RESERVED_WORD);
        tokenMap.put("init", Token.RESERVED_WORD);
        tokenMap.put("param", Token.RESERVED_WORD);
        tokenMap.put("property", Token.RESERVED_WORD);
        tokenMap.put("receiver", Token.RESERVED_WORD);
        tokenMap.put("set", Token.RESERVED_WORD);
        tokenMap.put("setparam", Token.RESERVED_WORD);
        tokenMap.put("value", Token.RESERVED_WORD);
        tokenMap.put("where", Token.RESERVED_WORD);

        // Modifier keywords
        tokenMap.put("abstract", Token.RESERVED_WORD);
        tokenMap.put("actual", Token.RESERVED_WORD);
        tokenMap.put("annotation", Token.RESERVED_WORD);
        tokenMap.put("companion", Token.RESERVED_WORD);
        tokenMap.put("const", Token.RESERVED_WORD);
        tokenMap.put("crossinline", Token.RESERVED_WORD);
        tokenMap.put("data", Token.RESERVED_WORD);
        tokenMap.put("enum", Token.RESERVED_WORD);
        tokenMap.put("expect", Token.RESERVED_WORD);
        tokenMap.put("external", Token.RESERVED_WORD);
        tokenMap.put("final", Token.RESERVED_WORD);
        tokenMap.put("infix", Token.RESERVED_WORD);
        tokenMap.put("inline", Token.RESERVED_WORD);
        tokenMap.put("inner", Token.RESERVED_WORD);
        tokenMap.put("internal", Token.RESERVED_WORD);
        tokenMap.put("lateinit", Token.RESERVED_WORD);
        tokenMap.put("noinline", Token.RESERVED_WORD);
        tokenMap.put("open", Token.RESERVED_WORD);
        tokenMap.put("operator", Token.RESERVED_WORD);
        tokenMap.put("out", Token.RESERVED_WORD);
        tokenMap.put("override", Token.RESERVED_WORD);
        tokenMap.put("private", Token.RESERVED_WORD);
        tokenMap.put("protected", Token.RESERVED_WORD);
        tokenMap.put("public", Token.RESERVED_WORD);
        tokenMap.put("reified", Token.RESERVED_WORD);
        tokenMap.put("sealed", Token.RESERVED_WORD);
        tokenMap.put("suspend", Token.RESERVED_WORD);
        tokenMap.put("tailrec", Token.RESERVED_WORD);
        tokenMap.put("vararg", Token.RESERVED_WORD);

        // Special identifiers
        tokenMap.put("it", Token.RESERVED_WORD_2); // Implicit lambda parameter

        // Built-in types
        tokenMap.put("Any", Token.DATA_TYPE);
        tokenMap.put("Boolean", Token.DATA_TYPE);
        tokenMap.put("Byte", Token.DATA_TYPE);
        tokenMap.put("Char", Token.DATA_TYPE);
        tokenMap.put("Double", Token.DATA_TYPE);
        tokenMap.put("Float", Token.DATA_TYPE);
        tokenMap.put("Int", Token.DATA_TYPE);
        tokenMap.put("Long", Token.DATA_TYPE);
        tokenMap.put("Nothing", Token.DATA_TYPE);
        tokenMap.put("Short", Token.DATA_TYPE);
        tokenMap.put("String", Token.DATA_TYPE);
        tokenMap.put("Unit", Token.DATA_TYPE);

        // Common collection types
        tokenMap.put("Array", Token.DATA_TYPE);
        tokenMap.put("List", Token.DATA_TYPE);
        tokenMap.put("Map", Token.DATA_TYPE);
        tokenMap.put("Set", Token.DATA_TYPE);
        tokenMap.put("MutableList", Token.DATA_TYPE);
        tokenMap.put("MutableMap", Token.DATA_TYPE);
        tokenMap.put("MutableSet", Token.DATA_TYPE);

        return tokenMap;
    }
}
