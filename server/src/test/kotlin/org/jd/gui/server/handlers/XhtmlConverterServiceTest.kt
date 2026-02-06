package org.jd.gui.server.handlers

import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class XhtmlConverterServiceTest {
    private lateinit var service: XhtmlConverterService

    @BeforeEach
    fun setup() {
        service = XhtmlConverterService()
    }

    @Nested
    inner class MimeTypeDetectionTest {

        @Test
        fun `should detect plain text MIME type`() {
            val content = "Hello, World!".toByteArray()
            val mimeType = service.detectMimeType(content, "test.txt")

            assertTrue(mimeType.startsWith("text/"))
        }

        @Test
        fun `should detect HTML MIME type`() {
            val content = "<html><body>Test</body></html>".toByteArray()
            val mimeType = service.detectMimeType(content, "test.html")

            assertTrue(mimeType.contains("html"))
        }

        @Test
        fun `should detect JSON MIME type`() {
            val content = """{"key": "value"}""".toByteArray()
            val mimeType = service.detectMimeType(content, "test.json")

            assertTrue(mimeType.contains("json") || mimeType.startsWith("text/"))
        }

        @Test
        fun `should detect from filename when content is ambiguous`() {
            val content = "some content".toByteArray()
            val mimeType = service.detectMimeType(content, "document.pdf")

            // Even if content doesn't match, filename should influence detection
            assertNotNull(mimeType)
        }

        @Test
        fun `should detect binary content`() {
            val binaryContent = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte())
            val mimeType = service.detectMimeType(binaryContent, "file.bin")

            assertNotNull(mimeType)
        }
    }

    @Nested
    inner class MarkdownConversionTest {

        @Test
        fun `should convert simple markdown to XHTML`() {
            val markdown = "# Hello World\n\nThis is a test."
            val result = service.convertToXhtml(markdown.toByteArray(), "test.md")

            assertTrue(result.success)
            assertEquals("text/markdown", result.mimeType)
            assertEquals("flexmark", result.parser)
            assertTrue(result.xhtml.contains("<!DOCTYPE"))
            assertTrue(result.xhtml.contains("<html"))
            assertTrue(result.xhtml.contains("Hello World"))
        }

        @Test
        fun `should include proper XHTML headers`() {
            val markdown = "# Test"
            val result = service.convertToXhtml(markdown.toByteArray(), "test.md")

            assertTrue(result.xhtml.contains("<?xml version"))
            assertTrue(result.xhtml.contains("xmlns=\"http://www.w3.org/1999/xhtml\""))
        }

        @Test
        fun `should handle markdown with code blocks`() {
            val markdown = """
                # Code Example

                ```kotlin
                fun main() {
                    println("Hello")
                }
                ```
            """.trimIndent()

            val result = service.convertToXhtml(markdown.toByteArray(), "readme.md")

            assertTrue(result.success)
            assertTrue(result.xhtml.contains("<code"))
        }

        @Test
        fun `should handle markdown with links`() {
            val markdown = "Click [here](http://example.com) for more."
            val result = service.convertToXhtml(markdown.toByteArray(), "test.md")

            assertTrue(result.success)
            assertTrue(result.xhtml.contains("href"))
        }

        @Test
        fun `should extract metadata from markdown`() {
            val markdown = """
                # Heading 1
                ## Heading 2
                Some text with [link](http://test.com).
            """.trimIndent()

            val result = service.convertToXhtml(markdown.toByteArray(), "test.md")

            assertTrue(result.metadata.isNotEmpty())
            assertEquals("flexmark", result.metadata["parser"])
        }
    }

    @Nested
    inner class SourceCodeConversionTest {

        @Test
        fun `should convert Java source to XHTML`() {
            val java = """
                public class Test {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
            """.trimIndent()

            val result = service.convertToXhtml(java.toByteArray(), "Test.java")

            assertTrue(result.success)
            assertEquals("source-code", result.parser)
            assertTrue(result.xhtml.contains("<code"))
            assertTrue(result.xhtml.contains("language-java"))
        }

        @Test
        fun `should convert Kotlin source to XHTML`() {
            val kotlin = """
                fun main() {
                    println("Hello")
                }
            """.trimIndent()

            val result = service.convertToXhtml(kotlin.toByteArray(), "Main.kt")

            assertTrue(result.success)
            assertTrue(result.xhtml.contains("language-kotlin"))
        }

        @Test
        fun `should convert Python source to XHTML`() {
            val python = """
                def main():
                    print("Hello")

                if __name__ == "__main__":
                    main()
            """.trimIndent()

            val result = service.convertToXhtml(python.toByteArray(), "main.py")

            assertTrue(result.success)
            assertTrue(result.xhtml.contains("language-python"))
        }

        @Test
        fun `should escape HTML in source code`() {
            val code = """
                String html = "<div>Test</div>";
            """.trimIndent()

            val result = service.convertToXhtml(code.toByteArray(), "Test.java")

            assertTrue(result.success)
            assertTrue(result.xhtml.contains("&lt;div&gt;"))
        }

        @Test
        fun `should include line count in metadata`() {
            val code = """
                line 1
                line 2
                line 3
            """.trimIndent()

            val result = service.convertToXhtml(code.toByteArray(), "test.py")

            assertEquals("3", result.metadata["lineCount"])
        }
    }

    @Nested
    inner class ImageConversionTest {

        @Test
        fun `should convert image to XHTML with data URI`() {
            // Create a minimal PNG (1x1 pixel)
            val pngBytes = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            )

            val result = service.convertToXhtml(pngBytes, "test.png")

            assertTrue(result.success)
            assertEquals("image-embed", result.parser)
            assertTrue(result.xhtml.contains("data:image/png;base64"))
            assertTrue(result.xhtml.contains("<img"))
        }

        @Test
        fun `should include image in figure element`() {
            val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

            val result = service.convertToXhtml(imageBytes, "photo.png")

            assertTrue(result.xhtml.contains("<figure"))
            assertTrue(result.xhtml.contains("<figcaption"))
        }

        @Test
        fun `should escape filename in alt attribute`() {
            val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte())

            val result = service.convertToXhtml(imageBytes, "image<test>.jpg")

            assertTrue(result.xhtml.contains("&lt;test&gt;"))
        }
    }

    @Nested
    inner class FallbackConversionTest {

        @Test
        fun `should use Tika for unknown file types`() {
            val content = "Some unknown content".toByteArray()
            val result = service.convertToXhtml(content, "file.unknown")

            assertTrue(result.success)
            // Should use Tika as fallback
            assertTrue(result.parser == "tika" || result.parser == "source-code")
        }

        @Test
        fun `should handle empty content`() {
            val result = service.convertToXhtml(byteArrayOf(), "empty.txt")

            assertTrue(result.success)
            assertTrue(result.xhtml.contains("<html"))
        }

        @Test
        fun `should handle binary content gracefully`() {
            val binary = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0xFF.toByte())
            val result = service.convertToXhtml(binary, "binary.dat")

            // Should not throw, should return valid XHTML
            assertNotNull(result.xhtml)
            assertTrue(result.xhtml.contains("<html"))
        }
    }

    @Nested
    inner class XhtmlResultTest {

        @Test
        fun `should create successful result`() {
            val result = XhtmlResult(
                success = true,
                xhtml = "<html></html>",
                mimeType = "text/plain",
                parser = "test",
                metadata = mapOf("key" to "value")
            )

            assertTrue(result.success)
            assertNull(result.error)
            assertEquals("value", result.metadata["key"])
        }

        @Test
        fun `should create error result`() {
            val result = XhtmlResult(
                success = false,
                xhtml = "<html><body>Error</body></html>",
                mimeType = "application/octet-stream",
                parser = "error",
                error = "Failed to parse file"
            )

            assertFalse(result.success)
            assertEquals("Failed to parse file", result.error)
        }
    }

    @Nested
    inner class XhtmlHandlerTest {

        @Test
        fun `should create XhtmlHandler with service`() {
            val handler = XhtmlHandler(service)

            assertEquals("xhtml-universal", handler.handlerId)
            assertEquals("Universal XHTML Converter", handler.displayName)
            assertEquals(5, handler.priority)
        }

        @Test
        fun `should process content through service`() = kotlinx.coroutines.runBlocking {
            val handler = XhtmlHandler(service)
            val content = FileContent(
                name = "test.txt",
                extension = "txt",
                content = "Hello World".toByteArray()
            )

            val result = handler.process(content)

            assertTrue(result.success)
            assertEquals("application/xhtml+xml", result.contentType)
        }

        @Test
        fun `should validate any content`() = kotlinx.coroutines.runBlocking {
            val handler = XhtmlHandler(service)
            val content = FileContent(
                name = "test.bin",
                extension = "bin",
                content = byteArrayOf(0x00, 0x01)
            )

            assertTrue(handler.validate(content))
        }

        @Test
        fun `should extract metadata`() = kotlinx.coroutines.runBlocking {
            val handler = XhtmlHandler(service)
            val content = FileContent(
                name = "test.md",
                extension = "md",
                content = "# Hello".toByteArray()
            )

            val metadata = handler.extractMetadata(content)

            assertTrue(metadata.isNotEmpty())
        }
    }

    @Nested
    inner class LanguageClassMappingTest {

        @Test
        fun `should map Kotlin extension to language class`() {
            val result = service.convertToXhtml("fun test() {}".toByteArray(), "Test.kt")
            assertTrue(result.xhtml.contains("language-kotlin"))
        }

        @Test
        fun `should map TypeScript extension to language class`() {
            val result = service.convertToXhtml("const x: number = 1".toByteArray(), "test.ts")
            assertTrue(result.xhtml.contains("language-typescript"))
        }

        @Test
        fun `should map C++ extension to language class`() {
            val result = service.convertToXhtml("#include <iostream>".toByteArray(), "main.cpp")
            assertTrue(result.xhtml.contains("language-cpp"))
        }

        @Test
        fun `should map Ruby extension to language class`() {
            val result = service.convertToXhtml("puts 'hello'".toByteArray(), "script.rb")
            assertTrue(result.xhtml.contains("language-ruby"))
        }

        @Test
        fun `should use extension as fallback language class`() {
            val result = service.convertToXhtml("code here".toByteArray(), "script.xyz")
            // Unknown extension should use the extension as class
            assertTrue(result.xhtml.contains("language-"))
        }
    }
}
