package al.clk.gui.server.syntax.languages

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.Vocabulary
import al.clk.gui.server.syntax.GenericTokenMaker
import al.clk.gui.server.syntax.SyntaxTokenType

/**
 * TokenMaker for TypeScript source files.
 *
 * Extends the generic TokenMaker with TypeScript-specific rules:
 * - TypeScript keywords (interface, type, enum, etc.)
 * - Type annotations and generics
 * - Decorators (@Component, etc.)
 * - Template literals
 *
 * File extensions: .ts, .tsx
 */
class TypeScriptTokenMaker : GenericTokenMaker(
    languageId = "typescript",
    displayName = "TypeScript",
    fileExtensions = setOf("ts", "tsx")
) {

    override val tokenOverrides = mapOf(
        "TemplateHead" to SyntaxTokenType.LITERAL_STRING,
        "TemplateMiddle" to SyntaxTokenType.LITERAL_STRING,
        "TemplateTail" to SyntaxTokenType.LITERAL_STRING,
        "BackTick" to SyntaxTokenType.LITERAL_STRING,
        "At" to SyntaxTokenType.DECORATOR
    )

    override val additionalKeywords = setOf(
        // TypeScript-specific
        "type", "interface", "enum", "namespace", "module", "declare",
        "readonly", "abstract", "override", "private", "protected", "public",
        "static", "implements", "extends", "as", "is", "keyof", "typeof",
        "infer", "asserts", "satisfies",

        // JavaScript keywords
        "let", "const", "var", "function", "class", "if", "else", "for",
        "while", "do", "switch", "case", "default", "break", "continue",
        "return", "throw", "try", "catch", "finally", "new", "delete",
        "typeof", "instanceof", "in", "of", "void", "this", "super",
        "import", "export", "from", "async", "await", "yield", "get", "set",

        // Nullish
        "null", "undefined"
    )

    override val additionalTypes = setOf(
        // Primitive types
        "string", "number", "boolean", "symbol", "bigint", "object",
        "any", "unknown", "never", "void", "null", "undefined",

        // Utility types
        "Partial", "Required", "Readonly", "Record", "Pick", "Omit",
        "Exclude", "Extract", "NonNullable", "Parameters", "ReturnType",
        "InstanceType", "ThisType", "Awaited", "ConstructorParameters",

        // Built-in objects
        "Array", "Object", "Function", "String", "Number", "Boolean",
        "Symbol", "BigInt", "Date", "RegExp", "Error", "Map", "Set",
        "WeakMap", "WeakSet", "Promise", "Proxy", "Reflect",

        // TypedArrays
        "Int8Array", "Uint8Array", "Int16Array", "Uint16Array",
        "Int32Array", "Uint32Array", "Float32Array", "Float64Array",

        // DOM types
        "HTMLElement", "Element", "Document", "Window", "Event",

        // React types (common in .tsx)
        "React", "ReactNode", "ReactElement", "FC", "Component",
        "JSX", "Props", "State"
    )

    override val additionalConstants = setOf(
        "true", "false", "NaN", "Infinity", "globalThis", "console", "JSON", "Math"
    )

    override fun classifyToken(token: Token, tokenName: String, text: String): SyntaxTokenType? {
        // Decorators
        if (text.startsWith("@")) {
            return SyntaxTokenType.DECORATOR
        }

        // Optional chaining
        if (text == "?.") {
            return SyntaxTokenType.OPERATOR
        }

        // Nullish coalescing
        if (text == "??") {
            return SyntaxTokenType.OPERATOR
        }

        // Spread operator
        if (text == "...") {
            return SyntaxTokenType.OPERATOR
        }

        // Arrow function
        if (text == "=>") {
            return SyntaxTokenType.OPERATOR
        }

        // Type assertion
        if (text == "as" || text == "is") {
            return SyntaxTokenType.KEYWORD
        }

        return null
    }

    // TODO: Generate TypeScript lexer from ANTLR grammar:
    // 1. Download: https://github.com/nicktejada/antlr4-typescript-parser
    // 2. Or use: https://github.com/nicktejada/antlr4-javascript-parser for JS
    // 3. Run: antlr4 -Dlanguage=Java TypeScriptLexer.g4
    // 4. Place generated files in services/src/main/gen/
    // 5. Update this class to use TypeScriptLexer

    override fun createLexer(input: CharStream): Lexer? {
        // TypeScript ANTLR lexer not yet generated
        // Return null to fall back to keyword-based highlighting
        return null
    }

    override fun getVocabulary(): Vocabulary? {
        // Return null until TypeScript lexer is generated
        return null
    }

    override fun supportsAntlrLexer(): Boolean = false
}

/**
 * TokenMaker for JavaScript source files.
 * Extends TypeScript maker but without type annotations.
 */
class JavaScriptTokenMaker : GenericTokenMaker(
    languageId = "javascript",
    displayName = "JavaScript",
    fileExtensions = setOf("js", "jsx", "mjs", "cjs")
) {

    override val additionalKeywords = setOf(
        "let", "const", "var", "function", "class", "if", "else", "for",
        "while", "do", "switch", "case", "default", "break", "continue",
        "return", "throw", "try", "catch", "finally", "new", "delete",
        "typeof", "instanceof", "in", "of", "void", "this", "super",
        "import", "export", "from", "async", "await", "yield", "get", "set",
        "null", "undefined", "true", "false", "with", "debugger"
    )

    override val additionalTypes = setOf(
        "Array", "Object", "Function", "String", "Number", "Boolean",
        "Symbol", "BigInt", "Date", "RegExp", "Error", "Map", "Set",
        "WeakMap", "WeakSet", "Promise", "Proxy", "Reflect", "JSON", "Math"
    )

    // TODO: Generate JavaScript lexer from ANTLR grammar:
    // 1. Download: https://github.com/nicktejada/antlr4-javascript-parser
    // 2. Run: antlr4 -Dlanguage=Java JavaScriptLexer.g4
    // 3. Place generated files in services/src/main/gen/
    // 4. Update this class to use JavaScriptLexer

    override fun createLexer(input: CharStream): Lexer? {
        // JavaScript ANTLR lexer not yet generated
        // Return null to fall back to keyword-based highlighting
        return null
    }

    override fun getVocabulary(): Vocabulary? {
        // Return null until JavaScript lexer is generated
        return null
    }

    override fun supportsAntlrLexer(): Boolean = false
}
