package org.jd.gui.server.handlers

import com.vladsch.flexmark.ast.*
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.toc.TocExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataSet
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.docx4j.Docx4J
import org.docx4j.openpackaging.packages.WordprocessingMLPackage
import org.docx4j.openpackaging.packages.SpreadsheetMLPackage
import org.docx4j.openpackaging.packages.PresentationMLPackage
import org.docx4j.TextUtils
import org.jd.gui.server.di.MimeCategory
import java.io.ByteArrayInputStream
import java.io.StringWriter

private val logger = KotlinLogging.logger {}

// ============================================
// Flexmark Markdown Handler
// ============================================

/**
 * Enhanced Markdown handler using Flexmark parser
 * Supports CommonMark, GFM, tables, task lists, TOC
 */
class FlexmarkMarkdownHandler : AbstractDataFormatHandler() {
    override val handlerId = "flexmark-markdown"
    override val displayName = "Markdown (Flexmark)"
    override val supportedExtensions = setOf("md", "markdown", "mdown", "mkd", "mkdn")
    override val mimeTypes = setOf("text/markdown", "text/x-markdown")
    override val priority = 20 // Higher priority than basic handler

    private val parserOptions = MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListExtension.create(),
            TocExtension.create()
        ))
    }

    private val parser: Parser = Parser.builder(parserOptions).build()
    private val renderer: HtmlRenderer = HtmlRenderer.builder(parserOptions).build()

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val text = content.asString()
            val document = parser.parse(text)
            val html = renderer.render(document)

            val structure = extractStructure(document)

            ProcessingResult(
                success = true,
                contentType = "text/html",
                processedContent = html,
                metadata = mapOf(
                    "headings" to structure.headings.size.toString(),
                    "links" to structure.links.size.toString(),
                    "images" to structure.images.size.toString(),
                    "codeBlocks" to structure.codeBlocks.size.toString(),
                    "tables" to structure.tables.toString(),
                    "taskItems" to structure.taskItems.toString()
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse Markdown with Flexmark" }
            ProcessingResult(
                success = false,
                contentType = "text/plain",
                error = e.message
            )
        }
    }

    override suspend fun validate(content: FileContent): Boolean {
        return try {
            parser.parse(content.asString())
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val text = content.asString()
        val document = parser.parse(text)
        val structure = extractStructure(document)

        return mapOf(
            "format" to "markdown",
            "parser" to "flexmark",
            "headingCount" to structure.headings.size.toString(),
            "linkCount" to structure.links.size.toString(),
            "imageCount" to structure.images.size.toString(),
            "codeBlockCount" to structure.codeBlocks.size.toString(),
            "tableCount" to structure.tables.toString(),
            "taskItemCount" to structure.taskItems.toString(),
            "wordCount" to text.split(Regex("\\s+")).size.toString(),
            "lineCount" to text.lines().size.toString()
        )
    }

    /**
     * Extract document structure from parsed AST
     */
    fun extractStructure(document: Node): MarkdownStructure {
        val headings = mutableListOf<HeadingInfo>()
        val links = mutableListOf<LinkInfo>()
        val images = mutableListOf<ImageInfo>()
        val codeBlocks = mutableListOf<CodeBlockInfo>()
        var tables = 0
        var taskItems = 0

        document.children.forEach { node ->
            extractFromNode(node, headings, links, images, codeBlocks, { tables++ }, { taskItems++ })
        }

        return MarkdownStructure(headings, links, images, codeBlocks, tables, taskItems)
    }

    private fun extractFromNode(
        node: Node,
        headings: MutableList<HeadingInfo>,
        links: MutableList<LinkInfo>,
        images: MutableList<ImageInfo>,
        codeBlocks: MutableList<CodeBlockInfo>,
        onTable: () -> Unit,
        onTaskItem: () -> Unit
    ) {
        when (node) {
            is Heading -> {
                headings.add(HeadingInfo(
                    level = node.level,
                    text = node.text.toString(),
                    line = node.startLineNumber
                ))
            }
            is Link -> {
                links.add(LinkInfo(
                    text = node.text.toString(),
                    url = node.url.toString(),
                    title = node.title.toString()
                ))
            }
            is Image -> {
                images.add(ImageInfo(
                    alt = node.text.toString(),
                    url = node.url.toString(),
                    title = node.title.toString()
                ))
            }
            is FencedCodeBlock -> {
                codeBlocks.add(CodeBlockInfo(
                    language = node.info.toString(),
                    content = node.contentChars.toString(),
                    line = node.startLineNumber
                ))
            }
            is IndentedCodeBlock -> {
                codeBlocks.add(CodeBlockInfo(
                    language = "",
                    content = node.contentChars.toString(),
                    line = node.startLineNumber
                ))
            }
        }

        // Check class name for extension types
        val className = node.javaClass.simpleName
        if (className.contains("Table")) onTable()
        if (className.contains("TaskListItem")) onTaskItem()

        // Recurse into children
        node.children.forEach { child ->
            extractFromNode(child, headings, links, images, codeBlocks, onTable, onTaskItem)
        }
    }

    /**
     * Convert Markdown to HTML
     */
    fun toHtml(markdown: String): String {
        val document = parser.parse(markdown)
        return renderer.render(document)
    }

    /**
     * Extract table of contents
     */
    fun extractToc(markdown: String): List<HeadingInfo> {
        val document = parser.parse(markdown)
        val headings = mutableListOf<HeadingInfo>()

        fun extractHeadings(node: Node) {
            if (node is Heading) {
                headings.add(HeadingInfo(
                    level = node.level,
                    text = node.text.toString(),
                    line = node.startLineNumber
                ))
            }
            node.children.forEach { extractHeadings(it) }
        }

        extractHeadings(document)
        return headings
    }
}

@Serializable
data class MarkdownStructure(
    val headings: List<HeadingInfo>,
    val links: List<LinkInfo>,
    val images: List<ImageInfo>,
    val codeBlocks: List<CodeBlockInfo>,
    val tables: Int,
    val taskItems: Int
)

@Serializable
data class HeadingInfo(val level: Int, val text: String, val line: Int)

@Serializable
data class LinkInfo(val text: String, val url: String, val title: String)

@Serializable
data class ImageInfo(val alt: String, val url: String, val title: String)

@Serializable
data class CodeBlockInfo(val language: String, val content: String, val line: Int)

// ============================================
// PDFBox PDF Handler
// ============================================

/**
 * PDF handler using Apache PDFBox
 * Extracts text, metadata, and document structure
 */
class PdfBoxHandler : FileExtensionHandler {
    override val handlerId = "pdfbox-pdf"
    override val displayName = "PDF (PDFBox)"
    override val supportedExtensions = setOf("pdf")
    override val mimeTypes = setOf("application/pdf")
    override val category = MimeCategory.DOCUMENT
    override val priority = 20

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val pdfContent = extractContent(content.content)

            ProcessingResult(
                success = true,
                contentType = "text/plain",
                processedContent = pdfContent.text,
                metadata = pdfContent.metadata
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to process PDF with PDFBox" }
            ProcessingResult(
                success = false,
                contentType = "application/pdf",
                error = e.message
            )
        }
    }

    override suspend fun validate(content: FileContent): Boolean {
        return try {
            Loader.loadPDF(content.content).use { doc ->
                doc.numberOfPages > 0
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        return try {
            Loader.loadPDF(content.content).use { doc ->
                val info = doc.documentInformation
                buildMap {
                    put("pageCount", doc.numberOfPages.toString())
                    put("pdfVersion", doc.version.toString())
                    info.title?.let { put("title", it) }
                    info.author?.let { put("author", it) }
                    info.subject?.let { put("subject", it) }
                    info.keywords?.let { put("keywords", it) }
                    info.creator?.let { put("creator", it) }
                    info.producer?.let { put("producer", it) }
                    info.creationDate?.let { put("creationDate", it.time.toString()) }
                    info.modificationDate?.let { put("modificationDate", it.time.toString()) }
                    put("encrypted", doc.isEncrypted.toString())
                }
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Failed to extract metadata"))
        }
    }

    /**
     * Extract text and metadata from PDF
     */
    fun extractContent(bytes: ByteArray): PdfContent {
        return Loader.loadPDF(bytes).use { doc ->
            val stripper = PDFTextStripper()
            val text = stripper.getText(doc)

            val info = doc.documentInformation
            val metadata = buildMap {
                put("pageCount", doc.numberOfPages.toString())
                put("pdfVersion", doc.version.toString())
                info.title?.let { put("title", it) }
                info.author?.let { put("author", it) }
                info.subject?.let { put("subject", it) }
                info.keywords?.let { put("keywords", it) }
            }

            PdfContent(text, metadata, doc.numberOfPages)
        }
    }

    /**
     * Extract text from specific page range
     */
    fun extractPages(bytes: ByteArray, startPage: Int, endPage: Int): String {
        return Loader.loadPDF(bytes).use { doc ->
            val stripper = PDFTextStripper().apply {
                this.startPage = startPage
                this.endPage = minOf(endPage, doc.numberOfPages)
            }
            stripper.getText(doc)
        }
    }

    /**
     * Get page count
     */
    fun getPageCount(bytes: ByteArray): Int {
        return Loader.loadPDF(bytes).use { it.numberOfPages }
    }
}

data class PdfContent(
    val text: String,
    val metadata: Map<String, String>,
    val pageCount: Int
)

// ============================================
// docx4j Office Document Handlers
// ============================================

/**
 * DOCX handler using docx4j
 * Extracts text, styles, and document structure
 */
class DocxHandler : FileExtensionHandler {
    override val handlerId = "docx4j-docx"
    override val displayName = "Word Document (docx4j)"
    override val supportedExtensions = setOf("docx")
    override val mimeTypes = setOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )
    override val category = MimeCategory.DOCUMENT
    override val priority = 20

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val docContent = extractContent(content.content)

            ProcessingResult(
                success = true,
                contentType = "text/plain",
                processedContent = docContent.text,
                metadata = docContent.metadata
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to process DOCX with docx4j" }
            ProcessingResult(
                success = false,
                contentType = mimeTypes.first(),
                error = e.message
            )
        }
    }

    override suspend fun validate(content: FileContent): Boolean {
        return try {
            ByteArrayInputStream(content.content).use { stream ->
                WordprocessingMLPackage.load(stream)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        return try {
            ByteArrayInputStream(content.content).use { stream ->
                val pkg = WordprocessingMLPackage.load(stream)
                val props = pkg.docPropsCorePart?.jaxbElement

                buildMap {
                    props?.title?.value?.let { put("title", it) }
                    props?.creator?.value?.let { put("creator", it) }
                    props?.description?.value?.let { put("description", it) }
                    props?.subject?.value?.let { put("subject", it) }
                    props?.keywords?.let { put("keywords", it) }
                    props?.lastModifiedBy?.value?.let { put("lastModifiedBy", it) }
                    props?.created?.value?.let { put("created", it.toString()) }
                    props?.modified?.value?.let { put("modified", it.toString()) }
                    put("format", "docx")
                    put("parser", "docx4j")
                }
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Failed to extract metadata"))
        }
    }

    /**
     * Extract text content from DOCX
     */
    fun extractContent(bytes: ByteArray): DocxContent {
        return ByteArrayInputStream(bytes).use { stream ->
            val pkg = WordprocessingMLPackage.load(stream)
            val writer = StringWriter()
            TextUtils.extractText(pkg.mainDocumentPart, writer)

            val props = pkg.docPropsCorePart?.jaxbElement
            val metadata = buildMap {
                props?.title?.value?.let { put("title", it) }
                props?.creator?.value?.let { put("creator", it) }
                put("format", "docx")
            }

            DocxContent(writer.toString(), metadata)
        }
    }

    /**
     * Extract document as HTML
     */
    fun toHtml(bytes: ByteArray): String {
        return ByteArrayInputStream(bytes).use { stream ->
            val pkg = WordprocessingMLPackage.load(stream)
            val writer = StringWriter()
            Docx4J.toHTML(pkg, null, null, writer)
            writer.toString()
        }
    }
}

data class DocxContent(
    val text: String,
    val metadata: Map<String, String>
)

/**
 * XLSX handler using docx4j
 */
class XlsxHandler : FileExtensionHandler {
    override val handlerId = "docx4j-xlsx"
    override val displayName = "Excel Spreadsheet (docx4j)"
    override val supportedExtensions = setOf("xlsx")
    override val mimeTypes = setOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    override val category = MimeCategory.DOCUMENT
    override val priority = 20

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val xlsxContent = extractContent(content.content)

            ProcessingResult(
                success = true,
                contentType = "text/plain",
                processedContent = xlsxContent.text,
                metadata = xlsxContent.metadata
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to process XLSX with docx4j" }
            ProcessingResult(
                success = false,
                contentType = mimeTypes.first(),
                error = e.message
            )
        }
    }

    override suspend fun validate(content: FileContent): Boolean {
        return try {
            ByteArrayInputStream(content.content).use { stream ->
                SpreadsheetMLPackage.load(stream)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        return try {
            ByteArrayInputStream(content.content).use { stream ->
                val pkg = SpreadsheetMLPackage.load(stream)
                val props = pkg.docPropsCorePart?.jaxbElement

                buildMap {
                    props?.title?.value?.let { put("title", it) }
                    props?.creator?.value?.let { put("creator", it) }
                    put("format", "xlsx")
                    put("parser", "docx4j")
                    put("sheetCount", pkg.workbookPart?.contents?.sheets?.sheet?.size?.toString() ?: "0")
                }
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Failed to extract metadata"))
        }
    }

    /**
     * Extract content from XLSX
     */
    fun extractContent(bytes: ByteArray): XlsxContent {
        return ByteArrayInputStream(bytes).use { stream ->
            val pkg = SpreadsheetMLPackage.load(stream)
            val sheets = pkg.workbookPart?.contents?.sheets?.sheet ?: emptyList()

            val text = StringBuilder()
            sheets.forEachIndexed { index, sheet ->
                text.appendLine("=== Sheet ${index + 1}: ${sheet.name} ===")
                // Basic text extraction - full implementation would iterate cells
            }

            val metadata = mapOf(
                "sheetCount" to sheets.size.toString(),
                "format" to "xlsx"
            )

            XlsxContent(text.toString(), metadata, sheets.size)
        }
    }
}

data class XlsxContent(
    val text: String,
    val metadata: Map<String, String>,
    val sheetCount: Int
)

/**
 * PPTX handler using docx4j
 */
class PptxHandler : FileExtensionHandler {
    override val handlerId = "docx4j-pptx"
    override val displayName = "PowerPoint Presentation (docx4j)"
    override val supportedExtensions = setOf("pptx")
    override val mimeTypes = setOf(
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    )
    override val category = MimeCategory.DOCUMENT
    override val priority = 20

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val pptxContent = extractContent(content.content)

            ProcessingResult(
                success = true,
                contentType = "text/plain",
                processedContent = pptxContent.text,
                metadata = pptxContent.metadata
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to process PPTX with docx4j" }
            ProcessingResult(
                success = false,
                contentType = mimeTypes.first(),
                error = e.message
            )
        }
    }

    override suspend fun validate(content: FileContent): Boolean {
        return try {
            ByteArrayInputStream(content.content).use { stream ->
                PresentationMLPackage.load(stream)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        return try {
            ByteArrayInputStream(content.content).use { stream ->
                val pkg = PresentationMLPackage.load(stream)
                val props = pkg.docPropsCorePart?.jaxbElement

                buildMap {
                    props?.title?.value?.let { put("title", it) }
                    props?.creator?.value?.let { put("creator", it) }
                    put("format", "pptx")
                    put("parser", "docx4j")
                    put("slideCount", pkg.presentationPart?.contents?.sldIdLst?.sldId?.size?.toString() ?: "0")
                }
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Failed to extract metadata"))
        }
    }

    /**
     * Extract content from PPTX
     */
    fun extractContent(bytes: ByteArray): PptxContent {
        return ByteArrayInputStream(bytes).use { stream ->
            val pkg = PresentationMLPackage.load(stream)
            val slides = pkg.presentationPart?.contents?.sldIdLst?.sldId ?: emptyList()

            val text = StringBuilder()
            slides.forEachIndexed { index, _ ->
                text.appendLine("=== Slide ${index + 1} ===")
                // Basic extraction - full implementation would extract text from shapes
            }

            val metadata = mapOf(
                "slideCount" to slides.size.toString(),
                "format" to "pptx"
            )

            PptxContent(text.toString(), metadata, slides.size)
        }
    }
}

data class PptxContent(
    val text: String,
    val metadata: Map<String, String>,
    val slideCount: Int
)

// ============================================
// Apache Tika Content Detection Handler
// ============================================

/**
 * Universal content detection handler using Apache Tika
 * Detects MIME type and extracts content from any file type
 */
class TikaHandler : FileExtensionHandler {
    override val handlerId = "tika-universal"
    override val displayName = "Universal (Apache Tika)"
    override val supportedExtensions = setOf("*") // Handles any extension
    override val mimeTypes = setOf("application/octet-stream")
    override val category = MimeCategory.BINARY
    override val priority = 1 // Low priority - fallback handler

    private val tika = Tika()

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val detected = detectAndExtract(content.content, content.name)

            ProcessingResult(
                success = true,
                contentType = detected.mimeType,
                processedContent = detected.text,
                metadata = detected.metadata
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to process with Tika" }
            ProcessingResult(
                success = false,
                contentType = "application/octet-stream",
                error = e.message
            )
        }
    }

    override suspend fun validate(content: FileContent): Boolean {
        return try {
            tika.detect(content.content)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        return try {
            val metadata = Metadata()
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, content.name)

            ByteArrayInputStream(content.content).use { stream ->
                tika.parse(stream, metadata)
            }

            buildMap {
                put("detectedMimeType", tika.detect(content.content, content.name))
                metadata.names().forEach { name ->
                    metadata.get(name)?.let { put(name, it) }
                }
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Failed to extract metadata"))
        }
    }

    /**
     * Detect MIME type
     */
    fun detectMimeType(bytes: ByteArray, filename: String? = null): String {
        return if (filename != null) {
            tika.detect(bytes, filename)
        } else {
            tika.detect(bytes)
        }
    }

    /**
     * Detect MIME type from filename only
     */
    fun detectFromFilename(filename: String): String {
        return tika.detect(filename)
    }

    /**
     * Extract content with full metadata
     */
    fun detectAndExtract(bytes: ByteArray, filename: String? = null): TikaContent {
        val metadata = Metadata()
        filename?.let { metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, it) }

        val text = ByteArrayInputStream(bytes).use { stream ->
            tika.parseToString(stream, metadata)
        }

        val detectedType = if (filename != null) {
            tika.detect(bytes, filename)
        } else {
            tika.detect(bytes)
        }

        val metadataMap = buildMap {
            put("detectedMimeType", detectedType)
            metadata.names().forEach { name ->
                metadata.get(name)?.let { put(name, it) }
            }
        }

        return TikaContent(text, detectedType, metadataMap)
    }

    /**
     * Check if file is a specific type
     */
    fun isType(bytes: ByteArray, expectedMimeType: String): Boolean {
        return tika.detect(bytes).startsWith(expectedMimeType)
    }
}

data class TikaContent(
    val text: String,
    val mimeType: String,
    val metadata: Map<String, String>
)

// ============================================
// Font Handler using FontBox
// ============================================

/**
 * Font file handler using Apache FontBox
 */
class FontHandler : FileExtensionHandler {
    override val handlerId = "fontbox-font"
    override val displayName = "Font (FontBox)"
    override val supportedExtensions = setOf("ttf", "otf", "woff", "woff2", "eot")
    override val mimeTypes = setOf(
        "font/ttf",
        "font/otf",
        "font/woff",
        "font/woff2",
        "application/vnd.ms-fontobject"
    )
    override val category = MimeCategory.BINARY
    override val priority = 15

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val fontInfo = extractFontInfo(content.content, content.extension)

            ProcessingResult(
                success = true,
                contentType = mimeTypes.first(),
                metadata = fontInfo
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to process font with FontBox" }
            ProcessingResult(
                success = false,
                contentType = "application/octet-stream",
                error = e.message
            )
        }
    }

    override suspend fun validate(content: FileContent): Boolean {
        // Basic validation - check magic bytes
        return when (content.extension.lowercase()) {
            "ttf", "otf" -> content.content.size >= 4 &&
                (content.content[0].toInt() == 0x00 || content.content[0].toInt() == 0x4F)
            "woff" -> content.content.size >= 4 &&
                String(content.content.take(4).toByteArray()) == "wOFF"
            "woff2" -> content.content.size >= 4 &&
                String(content.content.take(4).toByteArray()) == "wOF2"
            else -> true
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        return extractFontInfo(content.content, content.extension)
    }

    private fun extractFontInfo(bytes: ByteArray, extension: String): Map<String, String> {
        return buildMap {
            put("format", extension.uppercase())
            put("fileSize", bytes.size.toString())

            // Check font type from magic bytes
            when {
                bytes.size >= 4 && String(bytes.take(4).toByteArray()) == "wOFF" -> {
                    put("type", "WOFF")
                }
                bytes.size >= 4 && String(bytes.take(4).toByteArray()) == "wOF2" -> {
                    put("type", "WOFF2")
                }
                bytes.size >= 4 && bytes[0].toInt() == 0x00 && bytes[1].toInt() == 0x01 -> {
                    put("type", "TrueType")
                }
                bytes.size >= 4 && bytes[0].toInt() == 0x4F && bytes[1].toInt() == 0x54 -> {
                    put("type", "OpenType")
                }
            }
        }
    }
}
