package al.clk.gui.server.handlers

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.toc.TocExtension
import com.vladsch.flexmark.util.data.MutableDataSet
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.ToXMLContentHandler
import org.docx4j.Docx4J
import org.docx4j.openpackaging.packages.WordprocessingMLPackage
import org.docx4j.openpackaging.packages.SpreadsheetMLPackage
import org.docx4j.openpackaging.packages.PresentationMLPackage
import al.clk.gui.server.di.MimeCategory
import org.xml.sax.ContentHandler
import java.io.ByteArrayInputStream
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.sax.SAXTransformerFactory
import javax.xml.transform.sax.TransformerHandler
import javax.xml.transform.stream.StreamResult

private val logger = KotlinLogging.logger {}

/**
 * XHTML Converter Service - Common Ground for All File Converters
 *
 * Uses Apache Tika as the base backend handler with XHTML as the
 * universal output format that works with both Kotlin and JavaScript.
 *
 * Architecture:
 * - Tika serves as the fallback/universal parser
 * - Specialized parsers (Flexmark, PDFBox, docx4j) provide enhanced output
 * - All output converges to XHTML for consistent rendering
 */
class XhtmlConverterService {
    private val tika = Tika()
    private val tikaParser = AutoDetectParser()

    // Flexmark for enhanced Markdown
    private val flexmarkOptions = MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListExtension.create(),
            TocExtension.create()
        ))
    }
    private val markdownParser: Parser = Parser.builder(flexmarkOptions).build()
    private val htmlRenderer: HtmlRenderer = HtmlRenderer.builder(flexmarkOptions).build()

    /**
     * Convert any file to XHTML using the best available parser
     */
    fun convertToXhtml(content: ByteArray, filename: String): XhtmlResult {
        val mimeType = tika.detect(content, filename)
        val extension = filename.substringAfterLast('.', "").lowercase()

        logger.debug { "Converting $filename (detected: $mimeType) to XHTML" }

        return when {
            // Markdown - use Flexmark for enhanced GFM support
            mimeType == "text/markdown" || extension in setOf("md", "markdown", "mdown") -> {
                convertMarkdownToXhtml(content, filename)
            }

            // PDF - use PDFBox for text extraction
            mimeType == "application/pdf" || extension == "pdf" -> {
                convertPdfToXhtml(content, filename)
            }

            // Word documents - use docx4j for structure preservation
            mimeType.contains("wordprocessingml") || extension == "docx" -> {
                convertDocxToXhtml(content, filename)
            }

            // Excel spreadsheets
            mimeType.contains("spreadsheetml") || extension == "xlsx" -> {
                convertXlsxToXhtml(content, filename)
            }

            // PowerPoint presentations
            mimeType.contains("presentationml") || extension == "pptx" -> {
                convertPptxToXhtml(content, filename)
            }

            // Source code - syntax highlight wrapper
            isSourceCode(mimeType, extension) -> {
                convertSourceCodeToXhtml(content, filename, extension)
            }

            // Images - embed as data URI
            mimeType.startsWith("image/") -> {
                convertImageToXhtml(content, filename, mimeType)
            }

            // Fallback - use Tika's XHTML output
            else -> {
                convertWithTika(content, filename, mimeType)
            }
        }
    }

    /**
     * Convert Markdown to XHTML using Flexmark
     */
    private fun convertMarkdownToXhtml(content: ByteArray, filename: String): XhtmlResult {
        return try {
            val text = String(content, Charsets.UTF_8)
            val document = markdownParser.parse(text)
            val htmlBody = htmlRenderer.render(document)

            val xhtml = wrapInXhtml(
                title = filename,
                body = htmlBody,
                cssClass = "markdown-content"
            )

            XhtmlResult(
                success = true,
                xhtml = xhtml,
                mimeType = "text/markdown",
                parser = "flexmark",
                metadata = mapOf(
                    "format" to "markdown",
                    "parser" to "flexmark",
                    "wordCount" to text.split(Regex("\\s+")).size.toString()
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Flexmark parsing failed for $filename" }
            convertWithTika(content, filename, "text/markdown")
        }
    }

    /**
     * Convert PDF to XHTML using PDFBox
     */
    private fun convertPdfToXhtml(content: ByteArray, filename: String): XhtmlResult {
        return try {
            Loader.loadPDF(content).use { doc ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(doc)
                val info = doc.documentInformation

                // Convert text to XHTML paragraphs
                val paragraphs = text.split("\n\n")
                    .filter { it.isNotBlank() }
                    .joinToString("\n") { "<p>${escapeHtml(it.trim())}</p>" }

                val xhtml = wrapInXhtml(
                    title = info.title ?: filename,
                    body = """
                        <div class="pdf-content">
                            <div class="pdf-metadata">
                                ${info.author?.let { "<span class='author'>Author: $it</span>" } ?: ""}
                                <span class="pages">Pages: ${doc.numberOfPages}</span>
                            </div>
                            <div class="pdf-text">$paragraphs</div>
                        </div>
                    """.trimIndent(),
                    cssClass = "pdf-document"
                )

                XhtmlResult(
                    success = true,
                    xhtml = xhtml,
                    mimeType = "application/pdf",
                    parser = "pdfbox",
                    metadata = mapOf(
                        "pageCount" to doc.numberOfPages.toString(),
                        "title" to (info.title ?: ""),
                        "author" to (info.author ?: ""),
                        "parser" to "pdfbox"
                    )
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "PDFBox parsing failed for $filename" }
            convertWithTika(content, filename, "application/pdf")
        }
    }

    /**
     * Convert DOCX to XHTML using docx4j
     */
    private fun convertDocxToXhtml(content: ByteArray, filename: String): XhtmlResult {
        return try {
            ByteArrayInputStream(content).use { stream ->
                val pkg = WordprocessingMLPackage.load(stream)
                val writer = StringWriter()

                // Use docx4j HTML export
                Docx4J.toHTML(pkg, null, null, writer)
                val htmlContent = writer.toString()

                // Extract body content from full HTML
                val bodyContent = extractBodyContent(htmlContent)

                val props = pkg.docPropsCorePart?.jaxbElement
                val xhtml = wrapInXhtml(
                    title = props?.title?.value ?: filename,
                    body = bodyContent,
                    cssClass = "docx-content"
                )

                XhtmlResult(
                    success = true,
                    xhtml = xhtml,
                    mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    parser = "docx4j",
                    metadata = mapOf(
                        "title" to (props?.title?.value ?: ""),
                        "creator" to (props?.creator?.value ?: ""),
                        "parser" to "docx4j"
                    )
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "docx4j parsing failed for $filename" }
            convertWithTika(content, filename, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        }
    }

    /**
     * Convert XLSX to XHTML
     */
    private fun convertXlsxToXhtml(content: ByteArray, filename: String): XhtmlResult {
        return try {
            ByteArrayInputStream(content).use { stream ->
                val pkg = SpreadsheetMLPackage.load(stream)
                val sheets = pkg.workbookPart?.contents?.sheets?.sheet ?: emptyList()

                val tablesHtml = StringBuilder()
                sheets.forEachIndexed { index, sheet ->
                    tablesHtml.append("""
                        <div class="sheet" data-sheet-index="$index">
                            <h3 class="sheet-name">${escapeHtml(sheet.name)}</h3>
                            <table class="spreadsheet">
                                <tbody>
                                    <tr><td>Sheet content would be extracted here</td></tr>
                                </tbody>
                            </table>
                        </div>
                    """.trimIndent())
                }

                val xhtml = wrapInXhtml(
                    title = filename,
                    body = """<div class="xlsx-content">$tablesHtml</div>""",
                    cssClass = "spreadsheet-document"
                )

                XhtmlResult(
                    success = true,
                    xhtml = xhtml,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    parser = "docx4j",
                    metadata = mapOf(
                        "sheetCount" to sheets.size.toString(),
                        "parser" to "docx4j"
                    )
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "docx4j XLSX parsing failed for $filename" }
            convertWithTika(content, filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        }
    }

    /**
     * Convert PPTX to XHTML
     */
    private fun convertPptxToXhtml(content: ByteArray, filename: String): XhtmlResult {
        return try {
            ByteArrayInputStream(content).use { stream ->
                val pkg = PresentationMLPackage.load(stream)
                val slides = pkg.presentationPart?.contents?.sldIdLst?.sldId ?: emptyList()

                val slidesHtml = StringBuilder()
                slides.forEachIndexed { index, _ ->
                    slidesHtml.append("""
                        <div class="slide" data-slide-index="${index + 1}">
                            <h3 class="slide-number">Slide ${index + 1}</h3>
                            <div class="slide-content">
                                <!-- Slide content would be extracted here -->
                            </div>
                        </div>
                    """.trimIndent())
                }

                val xhtml = wrapInXhtml(
                    title = filename,
                    body = """<div class="pptx-content">$slidesHtml</div>""",
                    cssClass = "presentation-document"
                )

                XhtmlResult(
                    success = true,
                    xhtml = xhtml,
                    mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    parser = "docx4j",
                    metadata = mapOf(
                        "slideCount" to slides.size.toString(),
                        "parser" to "docx4j"
                    )
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "docx4j PPTX parsing failed for $filename" }
            convertWithTika(content, filename, "application/vnd.openxmlformats-officedocument.presentationml.presentation")
        }
    }

    /**
     * Convert source code to XHTML with syntax highlighting markers
     */
    private fun convertSourceCodeToXhtml(content: ByteArray, filename: String, extension: String): XhtmlResult {
        val text = String(content, Charsets.UTF_8)
        val languageClass = getLanguageClass(extension)

        val codeHtml = """
            <pre><code class="language-$languageClass" data-filename="$filename">${escapeHtml(text)}</code></pre>
        """.trimIndent()

        val xhtml = wrapInXhtml(
            title = filename,
            body = """<div class="source-code">$codeHtml</div>""",
            cssClass = "source-document"
        )

        return XhtmlResult(
            success = true,
            xhtml = xhtml,
            mimeType = "text/plain",
            parser = "source-code",
            metadata = mapOf(
                "language" to languageClass,
                "lineCount" to text.lines().size.toString(),
                "parser" to "source-code"
            )
        )
    }

    /**
     * Convert image to XHTML with embedded data URI
     */
    private fun convertImageToXhtml(content: ByteArray, filename: String, mimeType: String): XhtmlResult {
        val base64 = java.util.Base64.getEncoder().encodeToString(content)
        val dataUri = "data:$mimeType;base64,$base64"

        val imageHtml = """
            <figure class="image-container">
                <img src="$dataUri" alt="${escapeHtml(filename)}" />
                <figcaption>${escapeHtml(filename)}</figcaption>
            </figure>
        """.trimIndent()

        val xhtml = wrapInXhtml(
            title = filename,
            body = imageHtml,
            cssClass = "image-document"
        )

        return XhtmlResult(
            success = true,
            xhtml = xhtml,
            mimeType = mimeType,
            parser = "image-embed",
            metadata = mapOf(
                "size" to content.size.toString(),
                "parser" to "image-embed"
            )
        )
    }

    /**
     * Universal fallback - use Apache Tika XHTML output
     */
    private fun convertWithTika(content: ByteArray, filename: String, detectedMimeType: String): XhtmlResult {
        return try {
            val metadata = Metadata()
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename)

            val handler = ToXMLContentHandler()

            ByteArrayInputStream(content).use { stream ->
                tikaParser.parse(stream, handler, metadata)
            }

            val tikaXhtml = handler.toString()

            // Extract body from Tika's XHTML output
            val bodyContent = extractBodyContent(tikaXhtml)

            val xhtml = wrapInXhtml(
                title = metadata.get(TikaCoreProperties.TITLE) ?: filename,
                body = bodyContent,
                cssClass = "tika-content"
            )

            val metadataMap = mutableMapOf<String, String>()
            metadataMap["parser"] = "tika"
            metadataMap["detectedMimeType"] = detectedMimeType
            metadata.names().forEach { name ->
                metadata.get(name)?.let { metadataMap[name] = it }
            }

            XhtmlResult(
                success = true,
                xhtml = xhtml,
                mimeType = detectedMimeType,
                parser = "tika",
                metadata = metadataMap
            )
        } catch (e: Exception) {
            logger.error(e) { "Tika parsing failed for $filename" }
            XhtmlResult(
                success = false,
                xhtml = wrapInXhtml(
                    title = filename,
                    body = "<p class='error'>Failed to parse file: ${escapeHtml(e.message ?: "Unknown error")}</p>",
                    cssClass = "error-document"
                ),
                mimeType = detectedMimeType,
                parser = "error",
                error = e.message
            )
        }
    }

    /**
     * Detect MIME type using Tika
     */
    fun detectMimeType(content: ByteArray, filename: String? = null): String {
        return if (filename != null) {
            tika.detect(content, filename)
        } else {
            tika.detect(content)
        }
    }

    /**
     * Wrap content in valid XHTML document
     */
    private fun wrapInXhtml(title: String, body: String, cssClass: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
            <head>
                <meta http-equiv="Content-Type" content="application/xhtml+xml; charset=UTF-8" />
                <title>${escapeHtml(title)}</title>
                <meta name="generator" content="gui XhtmlConverterService" />
            </head>
            <body class="$cssClass">
                $body
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Extract body content from HTML/XHTML
     */
    private fun extractBodyContent(html: String): String {
        val bodyStart = html.indexOf("<body")
        val bodyEnd = html.lastIndexOf("</body>")

        return if (bodyStart >= 0 && bodyEnd > bodyStart) {
            val contentStart = html.indexOf(">", bodyStart) + 1
            html.substring(contentStart, bodyEnd)
        } else {
            html
        }
    }

    /**
     * Escape HTML special characters
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    /**
     * Check if MIME type represents source code
     */
    private fun isSourceCode(mimeType: String, extension: String): Boolean {
        val sourceExtensions = setOf(
            "java", "kt", "kts", "ts", "tsx", "js", "jsx", "py", "go", "rs",
            "cpp", "c", "h", "cs", "swift", "rb", "php", "scala", "groovy",
            "dart", "ex", "exs", "hs", "m", "mm", "sol", "graphql", "gql",
            "proto", "tf", "cbl", "cob", "f", "f90", "ada", "zig", "v", "vhd",
            "clj", "erl", "pl", "pm", "lua", "sh", "bash", "sql"
        )
        return extension.lowercase() in sourceExtensions ||
               mimeType.startsWith("text/x-") ||
               mimeType.contains("source")
    }

    /**
     * Get language class for syntax highlighting
     */
    private fun getLanguageClass(extension: String): String {
        return when (extension.lowercase()) {
            "kt", "kts" -> "kotlin"
            "ts", "tsx" -> "typescript"
            "js", "jsx" -> "javascript"
            "py" -> "python"
            "rb" -> "ruby"
            "cpp", "c", "h" -> "cpp"
            "cs" -> "csharp"
            "ex", "exs" -> "elixir"
            "hs" -> "haskell"
            "m", "mm" -> "objectivec"
            "cbl", "cob" -> "cobol"
            "f", "f90", "f95" -> "fortran"
            "vhd", "vhdl" -> "vhdl"
            "clj", "cljs" -> "clojure"
            "erl", "hrl" -> "erlang"
            "graphql", "gql" -> "graphql"
            else -> extension.lowercase()
        }
    }
}

/**
 * Result of XHTML conversion
 */
@Serializable
data class XhtmlResult(
    val success: Boolean,
    val xhtml: String,
    val mimeType: String,
    val parser: String,
    val metadata: Map<String, String> = emptyMap(),
    val error: String? = null
)

/**
 * XHTML Handler - Universal file handler using XhtmlConverterService
 */
class XhtmlHandler(
    private val converterService: XhtmlConverterService
) : FileExtensionHandler {
    override val handlerId = "xhtml-universal"
    override val displayName = "Universal XHTML Converter"
    override val supportedExtensions = setOf("*") // Handles any extension
    override val mimeTypes = setOf("application/xhtml+xml", "text/html")
    override val category = MimeCategory.DOCUMENT
    override val priority = 5 // Medium priority - specialized handlers take precedence

    override suspend fun process(content: FileContent): ProcessingResult {
        val result = converterService.convertToXhtml(content.content, content.name)

        return ProcessingResult(
            success = result.success,
            contentType = "application/xhtml+xml",
            processedContent = result.xhtml,
            metadata = result.metadata,
            error = result.error
        )
    }

    override suspend fun validate(content: FileContent): Boolean {
        return try {
            converterService.detectMimeType(content.content, content.name)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val result = converterService.convertToXhtml(content.content, content.name)
        return result.metadata
    }
}
