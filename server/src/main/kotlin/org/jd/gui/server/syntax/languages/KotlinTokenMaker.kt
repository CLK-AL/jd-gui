package org.jd.gui.server.syntax.languages

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.Vocabulary
import org.jd.gui.server.syntax.GenericTokenMaker
import org.jd.gui.server.syntax.SyntaxTokenType

/**
 * TokenMaker for Kotlin source files.
 *
 * Extends the generic TokenMaker with Kotlin-specific rules:
 * - Hard keywords (fun, val, var, object, etc.)
 * - Soft keywords (by, get, set, etc.)
 * - Modifier keywords (suspend, inline, crossinline, etc.)
 * - Kotlin standard library types
 *
 * File extensions: .kt, .kts
 */
class KotlinTokenMaker : GenericTokenMaker(
    languageId = "kotlin",
    displayName = "Kotlin",
    fileExtensions = setOf("kt", "kts")
) {

    override val tokenOverrides = mapOf(
        // Kotlin-specific tokens
        "QUOTE_OPEN" to SyntaxTokenType.LITERAL_STRING,
        "QUOTE_CLOSE" to SyntaxTokenType.LITERAL_STRING,
        "TRIPLE_QUOTE_OPEN" to SyntaxTokenType.LITERAL_STRING,
        "TRIPLE_QUOTE_CLOSE" to SyntaxTokenType.LITERAL_STRING,
        "LineStrRef" to SyntaxTokenType.LITERAL_STRING,
        "MultiLineStrRef" to SyntaxTokenType.LITERAL_STRING,
        "AT_NO_WS" to SyntaxTokenType.ANNOTATION,
        "AT_PRE_WS" to SyntaxTokenType.ANNOTATION,
        "AT_POST_WS" to SyntaxTokenType.ANNOTATION
    )

    override val additionalKeywords = setOf(
        // Hard keywords
        "as", "as?", "break", "class", "continue", "do", "else", "false", "for",
        "fun", "if", "in", "!in", "interface", "is", "!is", "null", "object",
        "package", "return", "super", "this", "throw", "true", "try", "typealias",
        "typeof", "val", "var", "when", "while",

        // Soft keywords
        "by", "catch", "constructor", "delegate", "dynamic", "field", "file",
        "finally", "get", "import", "init", "param", "property", "receiver",
        "set", "setparam", "value", "where",

        // Modifier keywords
        "abstract", "actual", "annotation", "companion", "const", "crossinline",
        "data", "enum", "expect", "external", "final", "infix", "inline", "inner",
        "internal", "lateinit", "noinline", "open", "operator", "out", "override",
        "private", "protected", "public", "reified", "sealed", "suspend", "tailrec",
        "vararg",

        // Kotlin 1.5+
        "value",

        // Kotlin 1.9+
        "context"
    )

    override val additionalTypes = setOf(
        // Basic types
        "Any", "Unit", "Nothing", "Boolean", "Byte", "Char", "Short", "Int",
        "Long", "Float", "Double", "String", "Number",

        // Unsigned types
        "UByte", "UShort", "UInt", "ULong",

        // Collections
        "Array", "List", "Set", "Map", "Collection", "Iterable", "Iterator",
        "MutableList", "MutableSet", "MutableMap", "MutableCollection",
        "MutableIterable", "MutableIterator",
        "ArrayList", "HashMap", "HashSet", "LinkedHashMap", "LinkedHashSet",

        // Sequences and ranges
        "Sequence", "IntRange", "LongRange", "CharRange", "IntProgression",

        // Functional types
        "Function", "KFunction", "KProperty", "KClass",

        // Coroutines
        "Deferred", "Job", "Flow", "StateFlow", "SharedFlow",
        "CoroutineScope", "CoroutineContext", "CoroutineDispatcher",

        // Result types
        "Result", "Pair", "Triple",

        // Common
        "Throwable", "Exception", "Error", "Lazy", "Comparable",
        "Regex", "MatchResult"
    )

    override val additionalConstants = setOf(
        "it", "this", "super"  // Implicit receivers
    )

    override fun classifyToken(token: Token, tokenName: String, text: String): SyntaxTokenType? {
        // Elvis operator
        if (text == "?:") {
            return SyntaxTokenType.OPERATOR
        }

        // Safe call operator
        if (text == "?.") {
            return SyntaxTokenType.OPERATOR
        }

        // Not-null assertion
        if (text == "!!") {
            return SyntaxTokenType.OPERATOR
        }

        // Lambda arrow
        if (text == "->") {
            return SyntaxTokenType.OPERATOR
        }

        // Range operators
        if (text == ".." || text == "..<" || text == "until" || text == "downTo" || text == "step") {
            return SyntaxTokenType.OPERATOR
        }

        // Implicit receiver 'it'
        if (tokenName == "IDENTIFIER" && text == "it") {
            return SyntaxTokenType.IDENTIFIER_PARAMETER
        }

        return null
    }

    // TODO: Generate Kotlin lexer from ANTLR grammar:
    // 1. Download: https://github.com/antlr/grammars-v4/tree/master/kotlin/kotlin
    // 2. Run: antlr4 -Dlanguage=Java KotlinLexer.g4
    // 3. Place generated files in services/src/main/gen/
    // 4. Update this class to use KotlinLexer

    override fun createLexer(input: CharStream): Lexer? {
        // Kotlin ANTLR lexer not yet generated
        // Return null to fall back to keyword-based highlighting
        return null
    }

    override fun getVocabulary(): Vocabulary? {
        // Return null until Kotlin lexer is generated
        return null
    }

    override fun supportsAntlrLexer(): Boolean = false
}
