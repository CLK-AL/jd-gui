package org.jd.gui.server.handlers

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jd.gui.server.di.MimeCategory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class SpecializedParserHandlersTest {

    @Nested
    inner class FlexmarkMarkdownHandlerTest {
        private lateinit var handler: FlexmarkMarkdownHandler

        @BeforeEach
        fun setup() {
            handler = FlexmarkMarkdownHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("flexmark-markdown", handler.handlerId)
            assertEquals("Markdown (Flexmark)", handler.displayName)
            assertTrue(handler.supportedExtensions.contains("md"))
            assertTrue(handler.supportedExtensions.contains("markdown"))
            assertEquals(20, handler.priority)
        }

        @Test
        fun `should convert markdown to HTML`() {
            val markdown = "# Hello World\n\nThis is a **test**."
            val html = handler.toHtml(markdown)

            assertTrue(html.contains("<h1>"))
            assertTrue(html.contains("Hello World"))
            assertTrue(html.contains("<strong>"))
            assertTrue(html.contains("test"))
        }

        @Test
        fun `should extract table of contents`() {
            val markdown = """
                # Chapter 1
                Some content
                ## Section 1.1
                More content
                # Chapter 2
                Final content
            """.trimIndent()

            val toc = handler.extractToc(markdown)

            assertEquals(3, toc.size)
            assertEquals(1, toc[0].level)
            assertEquals("Chapter 1", toc[0].text)
            assertEquals(2, toc[1].level)
            assertEquals("Section 1.1", toc[1].text)
        }

        @Test
        fun `should process file content successfully`() = runBlocking {
            val markdown = "# Test\n\nParagraph with [link](http://example.com)"
            val content = FileContent(
                name = "test.md",
                extension = "md",
                content = markdown.toByteArray()
            )

            val result = handler.process(content)

            assertTrue(result.success)
            assertEquals("text/html", result.contentType)
            assertNotNull(result.processedContent)
            assertTrue(result.processedContent?.contains("<h1>") == true)
        }

        @Test
        fun `should extract metadata from markdown`() = runBlocking {
            val markdown = """
                # Heading 1
                ## Heading 2

                [Link](http://test.com)
                ![Image](image.png)

                ```kotlin
                fun main() {}
                ```
            """.trimIndent()

            val content = FileContent(
                name = "test.md",
                extension = "md",
                content = markdown.toByteArray()
            )

            val metadata = handler.extractMetadata(content)

            assertEquals("markdown", metadata["format"])
            assertEquals("flexmark", metadata["parser"])
            assertTrue(metadata["headingCount"]?.toInt()!! >= 2)
        }

        @Test
        fun `should validate markdown content`() = runBlocking {
            val validContent = FileContent(
                name = "test.md",
                extension = "md",
                content = "# Valid markdown".toByteArray()
            )

            assertTrue(handler.validate(validContent))
        }

        @Test
        fun `should handle GFM tables`() {
            val markdown = """
                | Column 1 | Column 2 |
                |----------|----------|
                | Cell 1   | Cell 2   |
            """.trimIndent()

            val html = handler.toHtml(markdown)
            assertTrue(html.contains("<table>") || html.contains("<table "))
        }

        @Test
        fun `should handle task lists`() {
            val markdown = """
                - [x] Completed task
                - [ ] Incomplete task
            """.trimIndent()

            val html = handler.toHtml(markdown)
            assertTrue(html.contains("checkbox") || html.contains("task"))
        }
    }

    @Nested
    inner class PdfBoxHandlerTest {
        private lateinit var handler: PdfBoxHandler

        @BeforeEach
        fun setup() {
            handler = PdfBoxHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("pdfbox-pdf", handler.handlerId)
            assertEquals("PDF (PDFBox)", handler.displayName)
            assertTrue(handler.supportedExtensions.contains("pdf"))
            assertEquals(MimeCategory.DOCUMENT, handler.category)
            assertEquals(20, handler.priority)
        }

        @Test
        fun `should handle invalid PDF gracefully`() = runBlocking {
            val content = FileContent(
                name = "invalid.pdf",
                extension = "pdf",
                content = "This is not a PDF".toByteArray()
            )

            val result = handler.process(content)
            assertFalse(result.success)
            assertNotNull(result.error)
        }

        @Test
        fun `should validate returns false for non-PDF`() = runBlocking {
            val content = FileContent(
                name = "test.pdf",
                extension = "pdf",
                content = "Not a PDF file".toByteArray()
            )

            assertFalse(handler.validate(content))
        }
    }

    @Nested
    inner class DocxHandlerTest {
        private lateinit var handler: DocxHandler

        @BeforeEach
        fun setup() {
            handler = DocxHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("docx4j-docx", handler.handlerId)
            assertEquals("Word Document (docx4j)", handler.displayName)
            assertTrue(handler.supportedExtensions.contains("docx"))
            assertEquals(MimeCategory.DOCUMENT, handler.category)
            assertEquals(20, handler.priority)
        }

        @Test
        fun `should handle invalid DOCX gracefully`() = runBlocking {
            val content = FileContent(
                name = "invalid.docx",
                extension = "docx",
                content = "This is not a DOCX".toByteArray()
            )

            val result = handler.process(content)
            assertFalse(result.success)
            assertNotNull(result.error)
        }

        @Test
        fun `should validate returns false for non-DOCX`() = runBlocking {
            val content = FileContent(
                name = "test.docx",
                extension = "docx",
                content = "Not a DOCX file".toByteArray()
            )

            assertFalse(handler.validate(content))
        }
    }

    @Nested
    inner class XlsxHandlerTest {
        private lateinit var handler: XlsxHandler

        @BeforeEach
        fun setup() {
            handler = XlsxHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("docx4j-xlsx", handler.handlerId)
            assertTrue(handler.supportedExtensions.contains("xlsx"))
            assertEquals(MimeCategory.DOCUMENT, handler.category)
        }

        @Test
        fun `should handle invalid XLSX gracefully`() = runBlocking {
            val content = FileContent(
                name = "invalid.xlsx",
                extension = "xlsx",
                content = "This is not an XLSX".toByteArray()
            )

            val result = handler.process(content)
            assertFalse(result.success)
        }
    }

    @Nested
    inner class PptxHandlerTest {
        private lateinit var handler: PptxHandler

        @BeforeEach
        fun setup() {
            handler = PptxHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("docx4j-pptx", handler.handlerId)
            assertTrue(handler.supportedExtensions.contains("pptx"))
            assertEquals(MimeCategory.DOCUMENT, handler.category)
        }

        @Test
        fun `should handle invalid PPTX gracefully`() = runBlocking {
            val content = FileContent(
                name = "invalid.pptx",
                extension = "pptx",
                content = "This is not a PPTX".toByteArray()
            )

            val result = handler.process(content)
            assertFalse(result.success)
        }
    }

    @Nested
    inner class TikaHandlerTest {
        private lateinit var handler: TikaHandler

        @BeforeEach
        fun setup() {
            handler = TikaHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("tika-universal", handler.handlerId)
            assertEquals("Universal (Apache Tika)", handler.displayName)
            assertEquals(1, handler.priority) // Low priority - fallback
        }

        @Test
        fun `should detect MIME type for text`() {
            val mimeType = handler.detectMimeType("Hello World".toByteArray(), "test.txt")
            assertTrue(mimeType.startsWith("text/"))
        }

        @Test
        fun `should detect MIME type from filename`() {
            val mimeType = handler.detectFromFilename("document.pdf")
            assertEquals("application/pdf", mimeType)
        }

        @Test
        fun `should process plain text successfully`() = runBlocking {
            val content = FileContent(
                name = "test.txt",
                extension = "txt",
                content = "Hello World".toByteArray()
            )

            val result = handler.process(content)
            assertTrue(result.success)
        }

        @Test
        fun `should extract content with metadata`() {
            val text = "This is a test document"
            val result = handler.detectAndExtract(text.toByteArray(), "test.txt")

            assertNotNull(result.text)
            assertNotNull(result.mimeType)
            assertNotNull(result.metadata)
        }

        @Test
        fun `should validate any content`() = runBlocking {
            val content = FileContent(
                name = "test.bin",
                extension = "bin",
                content = byteArrayOf(0x00, 0x01, 0x02, 0x03)
            )

            assertTrue(handler.validate(content))
        }

        @Test
        fun `should check content type`() {
            val htmlContent = "<html><body>Test</body></html>".toByteArray()
            assertTrue(handler.isType(htmlContent, "text/"))
        }
    }

    @Nested
    inner class FontHandlerTest {
        private lateinit var handler: FontHandler

        @BeforeEach
        fun setup() {
            handler = FontHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("fontbox-font", handler.handlerId)
            assertTrue(handler.supportedExtensions.contains("ttf"))
            assertTrue(handler.supportedExtensions.contains("otf"))
            assertTrue(handler.supportedExtensions.contains("woff"))
            assertEquals(MimeCategory.BINARY, handler.category)
        }

        @Test
        fun `should handle invalid font gracefully`() = runBlocking {
            val content = FileContent(
                name = "invalid.ttf",
                extension = "ttf",
                content = "Not a font".toByteArray()
            )

            val result = handler.process(content)
            // Should still return something, even if extraction fails
            assertNotNull(result)
        }
    }
}
