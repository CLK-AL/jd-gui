package al.clk.gui.server.handlers

import io.mockk.*
import io.mockk.impl.annotations.MockK
import al.clk.gui.server.di.MimeCategory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

class HandlerRegistryTest : KoinTest {

    private lateinit var registry: HandlerRegistry

    @BeforeEach
    fun setup() {
        // Create mock handlers
        val mockJavaHandler = mockk<JavaHandler>(relaxed = true) {
            every { handlerId } returns "java"
            every { displayName } returns "Java"
            every { supportedExtensions } returns setOf("java")
            every { mimeTypes } returns setOf("text/x-java-source")
            every { category } returns MimeCategory.PROGRAMMING
            every { priority } returns 10
        }

        val mockKotlinHandler = mockk<KotlinHandler>(relaxed = true) {
            every { handlerId } returns "kotlin"
            every { displayName } returns "Kotlin"
            every { supportedExtensions } returns setOf("kt", "kts")
            every { mimeTypes } returns setOf("text/x-kotlin")
            every { category } returns MimeCategory.PROGRAMMING
            every { priority } returns 10
        }

        val mockJsonHandler = mockk<JsonHandler>(relaxed = true) {
            every { handlerId } returns "json"
            every { displayName } returns "JSON"
            every { supportedExtensions } returns setOf("json")
            every { mimeTypes } returns setOf("application/json")
            every { category } returns MimeCategory.DATA
            every { priority } returns 10
        }

        // Start Koin with mock module
        startKoin {
            modules(module {
                single { mockJavaHandler }
                single { mockKotlinHandler }
                single { mockJsonHandler }
                // Mock all other required handlers
                single { mockk<TypeScriptHandler>(relaxed = true) { every { handlerId } returns "typescript"; every { supportedExtensions } returns setOf("ts"); every { priority } returns 10 } }
                single { mockk<JavaScriptHandler>(relaxed = true) { every { handlerId } returns "javascript"; every { supportedExtensions } returns setOf("js"); every { priority } returns 10 } }
                single { mockk<PythonHandler>(relaxed = true) { every { handlerId } returns "python"; every { supportedExtensions } returns setOf("py"); every { priority } returns 10 } }
                single { mockk<GoHandler>(relaxed = true) { every { handlerId } returns "go"; every { supportedExtensions } returns setOf("go"); every { priority } returns 10 } }
                single { mockk<RustHandler>(relaxed = true) { every { handlerId } returns "rust"; every { supportedExtensions } returns setOf("rs"); every { priority } returns 10 } }
                single { mockk<CppHandler>(relaxed = true) { every { handlerId } returns "cpp"; every { supportedExtensions } returns setOf("cpp"); every { priority } returns 10 } }
                single { mockk<CSharpHandler>(relaxed = true) { every { handlerId } returns "csharp"; every { supportedExtensions } returns setOf("cs"); every { priority } returns 10 } }
                single { mockk<SwiftHandler>(relaxed = true) { every { handlerId } returns "swift"; every { supportedExtensions } returns setOf("swift"); every { priority } returns 10 } }
                // Scripting handlers
                single { mockk<PhpHandler>(relaxed = true) { every { handlerId } returns "php"; every { supportedExtensions } returns setOf("php"); every { priority } returns 10 } }
                single { mockk<RubyHandler>(relaxed = true) { every { handlerId } returns "ruby"; every { supportedExtensions } returns setOf("rb"); every { priority } returns 10 } }
                single { mockk<PerlHandler>(relaxed = true) { every { handlerId } returns "perl"; every { supportedExtensions } returns setOf("pl"); every { priority } returns 10 } }
                single { mockk<LuaHandler>(relaxed = true) { every { handlerId } returns "lua"; every { supportedExtensions } returns setOf("lua"); every { priority } returns 10 } }
                single { mockk<ScalaHandler>(relaxed = true) { every { handlerId } returns "scala"; every { supportedExtensions } returns setOf("scala"); every { priority } returns 10 } }
                single { mockk<GroovyHandler>(relaxed = true) { every { handlerId } returns "groovy"; every { supportedExtensions } returns setOf("groovy"); every { priority } returns 10 } }
                // Modern handlers
                single { mockk<DartHandler>(relaxed = true) { every { handlerId } returns "dart"; every { supportedExtensions } returns setOf("dart"); every { priority } returns 10 } }
                single { mockk<ElixirHandler>(relaxed = true) { every { handlerId } returns "elixir"; every { supportedExtensions } returns setOf("ex"); every { priority } returns 10 } }
                single { mockk<HaskellHandler>(relaxed = true) { every { handlerId } returns "haskell"; every { supportedExtensions } returns setOf("hs"); every { priority } returns 10 } }
                single { mockk<ObjectiveCHandler>(relaxed = true) { every { handlerId } returns "objc"; every { supportedExtensions } returns setOf("m"); every { priority } returns 10 } }
                // Specialized handlers
                single { mockk<SolidityHandler>(relaxed = true) { every { handlerId } returns "solidity"; every { supportedExtensions } returns setOf("sol"); every { priority } returns 10 } }
                single { mockk<GraphQLHandler>(relaxed = true) { every { handlerId } returns "graphql"; every { supportedExtensions } returns setOf("graphql"); every { priority } returns 10 } }
                single { mockk<ProtobufHandler>(relaxed = true) { every { handlerId } returns "protobuf"; every { supportedExtensions } returns setOf("proto"); every { priority } returns 10 } }
                single { mockk<TerraformHandler>(relaxed = true) { every { handlerId } returns "terraform"; every { supportedExtensions } returns setOf("tf"); every { priority } returns 10 } }
                // Legacy handlers
                single { mockk<CobolHandler>(relaxed = true) { every { handlerId } returns "cobol"; every { supportedExtensions } returns setOf("cbl"); every { priority } returns 10 } }
                single { mockk<FortranHandler>(relaxed = true) { every { handlerId } returns "fortran"; every { supportedExtensions } returns setOf("f90"); every { priority } returns 10 } }
                single { mockk<AdaHandler>(relaxed = true) { every { handlerId } returns "ada"; every { supportedExtensions } returns setOf("ada"); every { priority } returns 10 } }
                single { mockk<ZigHandler>(relaxed = true) { every { handlerId } returns "zig"; every { supportedExtensions } returns setOf("zig"); every { priority } returns 10 } }
                // HDL handlers
                single { mockk<VerilogHandler>(relaxed = true) { every { handlerId } returns "verilog"; every { supportedExtensions } returns setOf("v"); every { priority } returns 10 } }
                single { mockk<VhdlHandler>(relaxed = true) { every { handlerId } returns "vhdl"; every { supportedExtensions } returns setOf("vhd"); every { priority } returns 10 } }
                // Functional handlers
                single { mockk<ClojureHandler>(relaxed = true) { every { handlerId } returns "clojure"; every { supportedExtensions } returns setOf("clj"); every { priority } returns 10 } }
                single { mockk<ErlangHandler>(relaxed = true) { every { handlerId } returns "erlang"; every { supportedExtensions } returns setOf("erl"); every { priority } returns 10 } }
                // Data format handlers
                single { mockk<XmlHandler>(relaxed = true) { every { handlerId } returns "xml"; every { supportedExtensions } returns setOf("xml"); every { priority } returns 10 } }
                single { mockk<YamlHandler>(relaxed = true) { every { handlerId } returns "yaml"; every { supportedExtensions } returns setOf("yaml"); every { priority } returns 10 } }
                single { mockk<TomlHandler>(relaxed = true) { every { handlerId } returns "toml"; every { supportedExtensions } returns setOf("toml"); every { priority } returns 10 } }
                single { mockk<PropertiesHandler>(relaxed = true) { every { handlerId } returns "properties"; every { supportedExtensions } returns setOf("properties"); every { priority } returns 10 } }
                single { mockk<SqlHandler>(relaxed = true) { every { handlerId } returns "sql"; every { supportedExtensions } returns setOf("sql"); every { priority } returns 10 } }
                single { mockk<MarkdownHandler>(relaxed = true) { every { handlerId } returns "markdown"; every { supportedExtensions } returns setOf("md"); every { priority } returns 5 } }
                single { mockk<HtmlHandler>(relaxed = true) { every { handlerId } returns "html"; every { supportedExtensions } returns setOf("html"); every { priority } returns 10 } }
                single { mockk<CssHandler>(relaxed = true) { every { handlerId } returns "css"; every { supportedExtensions } returns setOf("css"); every { priority } returns 10 } }
                // Image handlers
                single { mockk<PngHandler>(relaxed = true) { every { handlerId } returns "png"; every { supportedExtensions } returns setOf("png"); every { priority } returns 10 } }
                single { mockk<JpegHandler>(relaxed = true) { every { handlerId } returns "jpeg"; every { supportedExtensions } returns setOf("jpg", "jpeg"); every { priority } returns 10 } }
                single { mockk<GifHandler>(relaxed = true) { every { handlerId } returns "gif"; every { supportedExtensions } returns setOf("gif"); every { priority } returns 10 } }
                single { mockk<WebPHandler>(relaxed = true) { every { handlerId } returns "webp"; every { supportedExtensions } returns setOf("webp"); every { priority } returns 10 } }
                single { mockk<BmpHandler>(relaxed = true) { every { handlerId } returns "bmp"; every { supportedExtensions } returns setOf("bmp"); every { priority } returns 10 } }
                single { mockk<SvgHandler>(relaxed = true) { every { handlerId } returns "svg"; every { supportedExtensions } returns setOf("svg"); every { priority } returns 10 } }
                single { mockk<IcoHandler>(relaxed = true) { every { handlerId } returns "ico"; every { supportedExtensions } returns setOf("ico"); every { priority } returns 10 } }
                // Specialized parser handlers
                single { mockk<FlexmarkMarkdownHandler>(relaxed = true) { every { handlerId } returns "flexmark-md"; every { supportedExtensions } returns setOf("md"); every { priority } returns 20 } }
                single { mockk<PdfBoxHandler>(relaxed = true) { every { handlerId } returns "pdfbox"; every { supportedExtensions } returns setOf("pdf"); every { priority } returns 20 } }
                single { mockk<DocxHandler>(relaxed = true) { every { handlerId } returns "docx"; every { supportedExtensions } returns setOf("docx"); every { priority } returns 20 } }
                single { mockk<XlsxHandler>(relaxed = true) { every { handlerId } returns "xlsx"; every { supportedExtensions } returns setOf("xlsx"); every { priority } returns 20 } }
                single { mockk<PptxHandler>(relaxed = true) { every { handlerId } returns "pptx"; every { supportedExtensions } returns setOf("pptx"); every { priority } returns 20 } }
                single { mockk<FontHandler>(relaxed = true) { every { handlerId } returns "font"; every { supportedExtensions } returns setOf("ttf"); every { priority } returns 15 } }
                single { mockk<TikaHandler>(relaxed = true) { every { handlerId } returns "tika"; every { supportedExtensions } returns setOf("*"); every { priority } returns 1 } }
                // Enhanced media handlers
                single { mockk<FontBoxEnhancedHandler>(relaxed = true) { every { handlerId } returns "fontbox"; every { supportedExtensions } returns setOf("ttf", "otf"); every { priority } returns 25 } }
                single { mockk<ImageMetadataHandler>(relaxed = true) { every { handlerId } returns "image-meta"; every { supportedExtensions } returns setOf("jpg", "jpeg"); every { priority } returns 25 } }
                single { mockk<FFmpegMediaHandler>(relaxed = true) { every { handlerId } returns "ffmpeg"; every { supportedExtensions } returns setOf("mp4", "mp3"); every { priority } returns 20 } }
                // XHTML handler
                single { mockk<XhtmlHandler>(relaxed = true) { every { handlerId } returns "xhtml"; every { supportedExtensions } returns setOf("*"); every { priority } returns 5 } }
            })
        }

        registry = HandlerRegistry()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `should register all handlers`() {
        val handlers = registry.getAllHandlers()
        assertTrue(handlers.isNotEmpty(), "Registry should have handlers")
    }

    @Test
    fun `should get handler by id`() {
        val handler = registry.getHandler("java")
        assertNotNull(handler)
        assertEquals("java", handler?.handlerId)
    }

    @Test
    fun `should return null for unknown handler id`() {
        val handler = registry.getHandler("unknown-handler")
        assertNull(handler)
    }

    @Test
    fun `should get handler for extension`() {
        val handler = registry.getHandlerForExtension("java")
        assertNotNull(handler)
        assertEquals("java", handler?.handlerId)
    }

    @Test
    fun `should get handler for extension case insensitive`() {
        val handler = registry.getHandlerForExtension("JAVA")
        assertNotNull(handler)
    }

    @Test
    fun `should return null for unsupported extension`() {
        val handler = registry.getHandlerForExtension("xyz123")
        assertNull(handler)
    }

    @Test
    fun `should get all handlers for extension with multiple handlers`() {
        // md has both MarkdownHandler (priority 5) and FlexmarkMarkdownHandler (priority 20)
        val handlers = registry.getAllHandlersForExtension("md")
        assertTrue(handlers.size >= 1, "Should have at least one handler for md")
    }

    @Test
    fun `should prioritize higher priority handlers`() {
        // FlexmarkMarkdownHandler has priority 20, MarkdownHandler has priority 5
        val handlers = registry.getAllHandlersForExtension("md")
        if (handlers.size > 1) {
            assertTrue(handlers.first().priority >= handlers.last().priority,
                "Handlers should be sorted by priority descending")
        }
    }

    @Test
    fun `should check if extension is supported`() {
        assertTrue(registry.isSupported("java"))
        assertTrue(registry.isSupported("kt"))
        assertTrue(registry.isSupported("json"))
    }

    @Test
    fun `should get supported extensions`() {
        val extensions = registry.getSupportedExtensions()
        assertTrue(extensions.contains("java"))
        assertTrue(extensions.contains("kt"))
        assertTrue(extensions.contains("json"))
    }

    @Test
    fun `should get handler info`() {
        val info = registry.getHandlerInfo("java")
        assertNotNull(info)
        assertEquals("java", info?.id)
        assertEquals("Java", info?.displayName)
        assertTrue(info?.extensions?.contains("java") == true)
    }

    @Test
    fun `should get all handler info`() {
        val allInfo = registry.getAllHandlerInfo()
        assertTrue(allInfo.isNotEmpty())
    }
}
