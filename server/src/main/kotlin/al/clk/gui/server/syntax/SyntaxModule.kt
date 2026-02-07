package al.clk.gui.server.syntax

import mu.KotlinLogging
import al.clk.gui.server.syntax.languages.*
import org.koin.dsl.module

private val logger = KotlinLogging.logger {}

/**
 * Koin module for syntax highlighting components.
 *
 * Provides:
 * - GenericTokenMaker implementations for each language
 * - TokenMakerRegistry singleton
 * - SyntaxHighlightService for XHTML generation
 */
val syntaxModule = module {

    // Language-specific TokenMakers
    single { JavaTokenMaker() }
    single { KotlinTokenMaker() }
    single { TypeScriptTokenMaker() }
    single { JavaScriptTokenMaker() }

    // TokenMaker Registry with all registered makers
    single {
        TokenMakerRegistry.apply {
            // Register all language-specific makers
            register(get<JavaTokenMaker>())
            register(get<KotlinTokenMaker>())
            register(get<TypeScriptTokenMaker>())
            register(get<JavaScriptTokenMaker>())

            logger.info { "TokenMakerRegistry initialized with ${getAllLanguages().size} languages" }
        }
    }

    // Syntax Highlight Service
    single { SyntaxHighlightService(get()) }
}

/**
 * Service for syntax highlighting source code to XHTML.
 *
 * Uses the TokenMakerRegistry to find appropriate TokenMaker
 * based on file extension and generates highlighted XHTML output.
 */
class SyntaxHighlightService(
    private val registry: TokenMakerRegistry
) {

    /**
     * Highlight source code and return XHTML
     */
    fun highlightToXhtml(
        source: String,
        filename: String,
        includeLineNumbers: Boolean = true
    ): String {
        val extension = filename.substringAfterLast('.', "").lowercase()
        val maker = registry.getByExtension(extension)

        return if (maker != null) {
            try {
                maker.toXhtml(source, includeLineNumbers)
            } catch (e: NotImplementedError) {
                // ANTLR lexer not available, fall back to plain text with wrapper
                wrapPlainText(source, extension, filename, includeLineNumbers)
            } catch (e: Exception) {
                logger.error(e) { "Syntax highlighting failed for $filename" }
                wrapPlainText(source, extension, filename, includeLineNumbers)
            }
        } else {
            wrapPlainText(source, extension, filename, includeLineNumbers)
        }
    }

    /**
     * Tokenize source code and return highlight tokens
     */
    fun tokenize(source: String, filename: String): HighlightResult {
        val extension = filename.substringAfterLast('.', "").lowercase()
        val maker = registry.getByExtension(extension)

        return if (maker != null) {
            try {
                maker.tokenize(source)
            } catch (e: NotImplementedError) {
                HighlightResult(
                    tokens = emptyList(),
                    language = extension,
                    errors = listOf("ANTLR lexer not available for $extension")
                )
            }
        } else {
            HighlightResult(
                tokens = emptyList(),
                language = extension,
                errors = listOf("No TokenMaker found for extension: $extension")
            )
        }
    }

    /**
     * Check if syntax highlighting is available for a file extension
     */
    fun isSupported(extension: String): Boolean {
        return registry.getByExtension(extension) != null
    }

    /**
     * Get all supported file extensions
     */
    fun getSupportedExtensions(): Set<String> = registry.getAllExtensions()

    /**
     * Get all supported language IDs
     */
    fun getSupportedLanguages(): Set<String> = registry.getAllLanguages()

    private fun wrapPlainText(
        source: String,
        extension: String,
        filename: String,
        includeLineNumbers: Boolean
    ): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<!DOCTYPE html>""")
        appendLine("""<html xmlns="http://www.w3.org/1999/xhtml">""")
        appendLine("<head>")
        appendLine("""<meta charset="UTF-8"/>""")
        appendLine("<title>$filename</title>")
        appendLine("<style>")
        appendLine("""
            .source-code { font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 14px; line-height: 1.5; }
            .line-numbers { float: left; text-align: right; padding-right: 1em; border-right: 1px solid #ddd; margin-right: 1em; color: #999; user-select: none; }
            .line-number { display: block; }
        """.trimIndent())
        appendLine("</style>")
        appendLine("</head>")
        appendLine("<body>")
        appendLine("""<pre class="source-code language-$extension">""")

        if (includeLineNumbers) {
            appendLine("""<code class="line-numbers">""")
            val lines = source.lines()
            lines.forEachIndexed { index, _ ->
                appendLine("""<span class="line-number">${index + 1}</span>""")
            }
            appendLine("</code>")
        }

        appendLine("""<code class="source">""")
        append(escapeHtml(source))
        appendLine("</code>")

        appendLine("</pre>")
        appendLine("</body>")
        appendLine("</html>")
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

/**
 * Configuration for a language's syntax highlighting.
 *
 * Used to define language-specific overrides that can be loaded
 * from configuration files or embedded resources.
 */
data class LanguageConfig(
    val languageId: String,
    val displayName: String,
    val fileExtensions: Set<String>,
    val tokenOverrides: Map<String, SyntaxTokenType> = emptyMap(),
    val additionalKeywords: Set<String> = emptySet(),
    val additionalTypes: Set<String> = emptySet(),
    val additionalConstants: Set<String> = emptySet()
)
