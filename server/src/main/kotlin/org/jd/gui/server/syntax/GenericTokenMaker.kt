package org.jd.gui.server.syntax

import mu.KotlinLogging
import org.antlr.v4.runtime.*

private val logger = KotlinLogging.logger {}

/**
 * Generic ANTLR-based TokenMaker that can be extended per-language.
 *
 * Base implementation provides:
 * - Automatic ANTLR token name → SyntaxTokenType mapping
 * - Default keyword/literal/comment detection from naming conventions
 *
 * Language-specific extensions can:
 * - Override token type mappings via [tokenOverrides]
 * - Add custom keywords via [additionalKeywords]
 * - Add custom type names via [additionalTypes]
 * - Define custom token classification logic via [classifyToken]
 */
abstract class GenericTokenMaker(
    val languageId: String,
    val displayName: String,
    val fileExtensions: Set<String>
) {

    /**
     * Override specific ANTLR token types with custom SyntaxTokenType
     * Key: ANTLR token name (e.g., "ABSTRACT", "STRING_LITERAL")
     * Value: Desired SyntaxTokenType
     */
    open val tokenOverrides: Map<String, SyntaxTokenType> = emptyMap()

    /**
     * Additional keywords beyond ANTLR token names
     * These identifiers will be highlighted as keywords
     */
    open val additionalKeywords: Set<String> = emptySet()

    /**
     * Additional type names (built-in types, common types)
     * These identifiers will be highlighted as types
     */
    open val additionalTypes: Set<String> = emptySet()

    /**
     * Additional constants/literals
     */
    open val additionalConstants: Set<String> = emptySet()

    /**
     * Create the ANTLR lexer for this language.
     * Returns null if the ANTLR lexer is not yet generated.
     */
    abstract fun createLexer(input: CharStream): Lexer?

    /**
     * Get vocabulary (token names) from the lexer.
     * Returns null if the ANTLR lexer is not yet generated.
     */
    abstract fun getVocabulary(): Vocabulary?

    /**
     * Returns true if this language has a working ANTLR lexer implementation.
     * When false, tokenization falls back to keyword-based highlighting.
     */
    open fun supportsAntlrLexer(): Boolean = true

    /**
     * Optional: Custom token classification logic
     * Return null to use default classification
     */
    open fun classifyToken(token: Token, tokenName: String, text: String): SyntaxTokenType? = null

    /**
     * Tokenize source code and return highlight tokens
     */
    fun tokenize(source: String): HighlightResult {
        val tokens = mutableListOf<HighlightToken>()
        val errors = mutableListOf<String>()

        try {
            val input = CharStreams.fromString(source)
            val lexer = createLexer(input)
            val vocabulary = getVocabulary()

            // If ANTLR lexer not available, fall back to keyword-based highlighting
            if (lexer == null || vocabulary == null) {
                return tokenizeWithKeywords(source)
            }

            // Collect lexer errors
            lexer.removeErrorListeners()
            lexer.addErrorListener(object : BaseErrorListener() {
                override fun syntaxError(
                    recognizer: Recognizer<*, *>?,
                    offendingSymbol: Any?,
                    line: Int,
                    charPositionInLine: Int,
                    msg: String?,
                    e: RecognitionException?
                ) {
                    errors.add("Line $line:$charPositionInLine - $msg")
                }
            })

            // Process all tokens
            var token = lexer.nextToken()
            while (token.type != Token.EOF) {
                val tokenName = vocabulary.getSymbolicName(token.type) ?: "UNKNOWN_${token.type}"
                val text = token.text

                val syntaxType = classifyTokenInternal(token, tokenName, text)

                tokens.add(HighlightToken(
                    startOffset = token.startIndex,
                    endOffset = token.stopIndex + 1,
                    text = text,
                    tokenType = syntaxType,
                    antlrTokenType = token.type,
                    antlrTokenName = tokenName
                ))

                token = lexer.nextToken()
            }

        } catch (e: Exception) {
            logger.error(e) { "Tokenization failed for $languageId" }
            errors.add("Tokenization error: ${e.message}")
        }

        return HighlightResult(
            tokens = tokens,
            language = languageId,
            errors = errors
        )
    }

    /**
     * Fallback tokenization using keyword-based highlighting.
     * Used when ANTLR lexer is not available.
     */
    private fun tokenizeWithKeywords(source: String): HighlightResult {
        val tokens = mutableListOf<HighlightToken>()
        val wordPattern = Regex("""[a-zA-Z_][a-zA-Z0-9_]*|"[^"]*"|'[^']*'|//.*|/\*[\s\S]*?\*/|[0-9]+\.?[0-9]*|[^\s]""")

        var offset = 0
        for (line in source.lines()) {
            wordPattern.findAll(line).forEach { match ->
                val text = match.value
                val startOffset = offset + match.range.first
                val endOffset = offset + match.range.last + 1

                val tokenType = when {
                    text in additionalKeywords -> SyntaxTokenType.KEYWORD
                    text in additionalTypes -> SyntaxTokenType.TYPE_BUILTIN
                    text in additionalConstants -> SyntaxTokenType.IDENTIFIER_CONSTANT
                    text.startsWith("//") -> SyntaxTokenType.COMMENT_LINE
                    text.startsWith("/*") -> SyntaxTokenType.COMMENT_BLOCK
                    text.startsWith("\"") || text.startsWith("'") -> SyntaxTokenType.LITERAL_STRING
                    text.first().isDigit() -> SyntaxTokenType.LITERAL_NUMBER
                    text.first().isUpperCase() && text.all { it.isLetterOrDigit() || it == '_' } ->
                        SyntaxTokenType.TYPE_USER
                    text.first().isLetter() || text.first() == '_' -> SyntaxTokenType.IDENTIFIER
                    else -> SyntaxTokenType.OPERATOR
                }

                tokens.add(HighlightToken(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    text = text,
                    tokenType = tokenType,
                    antlrTokenType = -1,
                    antlrTokenName = "FALLBACK"
                ))
            }
            offset += line.length + 1 // +1 for newline
        }

        return HighlightResult(
            tokens = tokens,
            language = languageId,
            errors = listOf("Note: Using fallback keyword-based highlighting (ANTLR lexer not available)")
        )
    }

    /**
     * Internal token classification with override chain
     */
    private fun classifyTokenInternal(token: Token, tokenName: String, text: String): SyntaxTokenType {
        // 1. Check custom classification first
        classifyToken(token, tokenName, text)?.let { return it }

        // 2. Check explicit overrides
        tokenOverrides[tokenName]?.let { return it }

        // 3. Check if identifier matches additional keywords/types
        if (tokenName == "IDENTIFIER" || tokenName == "ID" || tokenName == "NAME") {
            when {
                text in additionalKeywords -> return SyntaxTokenType.KEYWORD
                text in additionalTypes -> return SyntaxTokenType.TYPE_BUILTIN
                text in additionalConstants -> return SyntaxTokenType.IDENTIFIER_CONSTANT
                text.first().isUpperCase() && text.all { it.isLetterOrDigit() || it == '_' } ->
                    return SyntaxTokenType.TYPE_USER  // PascalCase = likely a type
            }
        }

        // 4. Use default ANTLR name-based classification
        return SyntaxTokenType.fromAntlrTokenName(tokenName)
    }

    /**
     * Generate XHTML with syntax highlighting
     */
    fun toXhtml(source: String, includeLineNumbers: Boolean = true): String {
        val result = tokenize(source)
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<!DOCTYPE html>""")
            appendLine("""<html xmlns="http://www.w3.org/1999/xhtml">""")
            appendLine("<head>")
            appendLine("""<meta charset="UTF-8"/>""")
            appendLine("<title>$displayName Source</title>")
            appendLine("<style>")
            appendLine(defaultCssStyles())
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("""<pre class="source-code language-$languageId">""")

            if (includeLineNumbers) {
                appendLine("""<code class="line-numbers">""")
                val lines = source.lines()
                lines.forEachIndexed { index, _ ->
                    appendLine("""<span class="line-number">${index + 1}</span>""")
                }
                appendLine("</code>")
            }

            appendLine("""<code class="source">""")
            var lastEnd = 0
            for (token in result.tokens) {
                // Add any text between tokens (shouldn't happen but safety)
                if (token.startOffset > lastEnd) {
                    append(escapeHtml(source.substring(lastEnd, token.startOffset)))
                }
                append("""<span class="${token.tokenType.cssClass}">""")
                append(escapeHtml(token.text))
                append("</span>")
                lastEnd = token.endOffset
            }
            // Add remaining text
            if (lastEnd < source.length) {
                append(escapeHtml(source.substring(lastEnd)))
            }
            appendLine("</code>")

            appendLine("</pre>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun defaultCssStyles(): String = """
        .source-code { font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 14px; line-height: 1.5; }
        .line-numbers { float: left; text-align: right; padding-right: 1em; border-right: 1px solid #ddd; margin-right: 1em; color: #999; user-select: none; }
        .line-number { display: block; }

        /* Token styles - Dark theme */
        .keyword { color: #cc7832; font-weight: bold; }
        .keyword-declaration { color: #cc7832; font-weight: bold; }
        .keyword-control { color: #cc7832; }
        .keyword-modifier { color: #cc7832; }
        .keyword-type { color: #cc7832; }

        .type { color: #a9b7c6; }
        .type-builtin { color: #6897bb; }
        .type-user { color: #a9b7c6; }

        .string { color: #6a8759; }
        .string-escape { color: #cc7832; }
        .char { color: #6a8759; }
        .number { color: #6897bb; }
        .number-float { color: #6897bb; }
        .number-hex { color: #6897bb; }
        .boolean { color: #cc7832; }
        .null { color: #cc7832; }

        .comment-line { color: #808080; font-style: italic; }
        .comment-block { color: #808080; font-style: italic; }
        .comment-doc { color: #629755; font-style: italic; }

        .annotation { color: #bbb529; }
        .decorator { color: #bbb529; }

        .identifier { color: #a9b7c6; }
        .function { color: #ffc66d; }
        .variable { color: #a9b7c6; }
        .constant { color: #9876aa; font-style: italic; }
        .parameter { color: #a9b7c6; }
        .field { color: #9876aa; }
        .package { color: #a9b7c6; }

        .operator { color: #a9b7c6; }
        .delimiter { color: #a9b7c6; }
        .bracket { color: #a9b7c6; }
        .brace { color: #a9b7c6; }
        .paren { color: #a9b7c6; }

        .error { color: #ff6b68; text-decoration: wavy underline red; }
        .unknown { color: #a9b7c6; }
    """.trimIndent()
}

/**
 * Registry of language-specific TokenMakers
 */
object TokenMakerRegistry {
    private val makers = mutableMapOf<String, GenericTokenMaker>()
    private val makersByExtension = mutableMapOf<String, GenericTokenMaker>()

    fun register(maker: GenericTokenMaker) {
        makers[maker.languageId] = maker
        maker.fileExtensions.forEach { ext ->
            makersByExtension[ext.lowercase()] = maker
        }
        logger.info { "Registered TokenMaker: ${maker.languageId} for extensions: ${maker.fileExtensions}" }
    }

    fun getByLanguage(languageId: String): GenericTokenMaker? = makers[languageId]

    fun getByExtension(extension: String): GenericTokenMaker? = makersByExtension[extension.lowercase()]

    fun getAllLanguages(): Set<String> = makers.keys

    fun getAllExtensions(): Set<String> = makersByExtension.keys
}
