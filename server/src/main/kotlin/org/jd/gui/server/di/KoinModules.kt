package org.jd.gui.server.di

import io.ktor.server.application.*
import mu.KotlinLogging
import org.jd.gui.server.auth.VCardOrgManager
import org.jd.gui.server.bedework.BedeworkService
import org.jd.gui.server.config.ServerConfig
import org.jd.gui.server.matrix.MatrixSocialService
import org.jd.gui.server.puml.AntlrToPumlConverterRegistry
import org.jd.gui.server.puml.PlantUmlService
import org.jd.gui.server.svg.SvgService
import org.jd.gui.server.svg.SvgTranscoder
import org.jd.gui.server.sync.VCardSyncManager
import org.jd.gui.server.xmpp.OpenfireOrgManager
import org.jd.gui.server.handlers.*
import org.jd.gui.server.pipeline.pipelineModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

private val logger = KotlinLogging.logger {}

/**
 * Configuration module - provides ServerConfig
 */
fun configModule(environment: ApplicationEnvironment) = module {
    single { ServerConfig.load(environment.config) }
}

/**
 * Core services module - VCard and authentication
 */
val coreServicesModule = module {
    // VCard Manager - depends on config
    single {
        val config = get<ServerConfig>()
        VCardOrgManager(config.storage.basePath)
    }
}

/**
 * External services module - Openfire, Matrix, Bedework
 */
val externalServicesModule = module {
    // Openfire XMPP Manager
    single {
        val config = get<ServerConfig>()
        val vcardManager = get<VCardOrgManager>()
        OpenfireOrgManager(config.openfire, vcardManager)
    }

    // Matrix Social Service
    single {
        val config = get<ServerConfig>()
        MatrixSocialService(config.matrix)
    }

    // Bedework CalDAV/CardDAV Service
    single {
        val config = get<ServerConfig>()
        val vcardManager = get<VCardOrgManager>()
        BedeworkService(config.bedework, vcardManager)
    }
}

/**
 * Sync services module - VCard synchronization
 */
val syncServicesModule = module {
    // VCard Sync Manager
    single {
        val config = get<ServerConfig>()
        VCardSyncManager(
            vcardManager = get(),
            openfireManager = get(),
            matrixService = get(),
            bedeworkService = get(),
            basePath = config.storage.basePath
        )
    }
}

/**
 * Diagram services module - PlantUML, SVG
 */
val diagramServicesModule = module {
    // ANTLR to PlantUML converter registry
    single { AntlrToPumlConverterRegistry() }

    // PlantUML service
    singleOf(::PlantUmlService)

    // SVG transcoder
    singleOf(::SvgTranscoder)

    // SVG service (depends on PlantUML service)
    single { SvgService(get()) }
}

/**
 * MIME type handlers module - file extension to handler mapping
 */
val mimeTypeHandlersModule = module {
    // MIME type registry
    single { MimeTypeRegistry() }
}

/**
 * File extension handlers module - one service per file type
 */
val fileExtensionHandlersModule = module {
    // Source code handlers (core languages)
    single { JavaHandler() }
    single { KotlinHandler() }
    single { TypeScriptHandler() }
    single { JavaScriptHandler() }
    single { PythonHandler() }
    single { GoHandler() }
    single { RustHandler() }
    single { CppHandler() }
    single { CSharpHandler() }
    single { SwiftHandler() }

    // Scripting language handlers
    single { PhpHandler() }
    single { RubyHandler() }
    single { PerlHandler() }
    single { LuaHandler() }
    single { ScalaHandler() }
    single { GroovyHandler() }

    // Modern language handlers
    single { DartHandler() }
    single { ElixirHandler() }
    single { HaskellHandler() }
    single { ObjectiveCHandler() }

    // Specialized language handlers
    single { SolidityHandler() }
    single { GraphQLHandler() }
    single { ProtobufHandler() }
    single { TerraformHandler() }

    // Legacy language handlers
    single { CobolHandler() }
    single { FortranHandler() }
    single { AdaHandler() }
    single { ZigHandler() }

    // Hardware description language handlers
    single { VerilogHandler() }
    single { VhdlHandler() }

    // Functional language handlers
    single { ClojureHandler() }
    single { ErlangHandler() }

    // Data format handlers
    single { JsonHandler() }
    single { XmlHandler() }
    single { YamlHandler() }
    single { TomlHandler() }
    single { PropertiesHandler() }
    single { SqlHandler() }
    single { MarkdownHandler() }
    single { HtmlHandler() }
    single { CssHandler() }

    // Image handlers
    single { PngHandler() }
    single { JpegHandler() }
    single { GifHandler() }
    single { WebPHandler() }
    single { BmpHandler() }
    single { SvgHandler() }
    single { IcoHandler() }

    // Specialized parser handlers (higher priority)
    single { FlexmarkMarkdownHandler() }  // Enhanced Markdown with GFM support
    single { PdfBoxHandler() }            // PDF text extraction
    single { DocxHandler() }              // Word document processing
    single { XlsxHandler() }              // Excel spreadsheet processing
    single { PptxHandler() }              // PowerPoint presentation processing
    single { FontHandler() }              // Font file processing (basic)
    single { TikaHandler() }              // Universal content detection (fallback)

    // Enhanced media handlers
    single { FontBoxEnhancedHandler() }   // Font with Bootstrap char grid XHTML
    single { ImageMetadataHandler() }     // EXIF/IPTC/XMP image metadata
    single { FFmpegMediaHandler() }       // Audio/video with FFmpeg

    // XHTML Converter Service - Base backend handler (Tika-based)
    single { XhtmlConverterService() }    // Common XHTML output for all converters
    single { XhtmlHandler(get()) }        // Universal XHTML handler

    // Handler registry (depends on all handlers)
    single { HandlerRegistry() }
}

/**
 * All server modules combined
 */
fun allServerModules(environment: ApplicationEnvironment): List<Module> = listOf(
    configModule(environment),
    coreServicesModule,
    externalServicesModule,
    syncServicesModule,
    diagramServicesModule,
    mimeTypeHandlersModule,
    fileExtensionHandlersModule,
    pipelineModule  // Staged LOD pipeline (Camel routes, SSE/WebSocket)
)

/**
 * Install Koin in Ktor application
 */
fun Application.installKoin() {
    install(Koin) {
        slf4jLogger()
        modules(allServerModules(environment))
    }
    logger.info { "Koin dependency injection initialized" }
}

/**
 * MIME type registry for file extension handling
 */
class MimeTypeRegistry {
    private val extensionToMime = mutableMapOf<String, MimeTypeInfo>()
    private val mimeToExtensions = mutableMapOf<String, MutableSet<String>>()

    init {
        // Programming languages
        register("java", "text/x-java-source", "Java source file", MimeCategory.PROGRAMMING)
        register("kt", "text/x-kotlin", "Kotlin source file", MimeCategory.PROGRAMMING)
        register("kts", "text/x-kotlin", "Kotlin script file", MimeCategory.PROGRAMMING)
        register("ts", "application/typescript", "TypeScript file", MimeCategory.PROGRAMMING)
        register("tsx", "application/typescript", "TypeScript React file", MimeCategory.PROGRAMMING)
        register("js", "application/javascript", "JavaScript file", MimeCategory.PROGRAMMING)
        register("jsx", "application/javascript", "JavaScript React file", MimeCategory.PROGRAMMING)
        register("py", "text/x-python", "Python file", MimeCategory.PROGRAMMING)
        register("cpp", "text/x-c++src", "C++ source file", MimeCategory.PROGRAMMING)
        register("c", "text/x-csrc", "C source file", MimeCategory.PROGRAMMING)
        register("h", "text/x-chdr", "C/C++ header file", MimeCategory.PROGRAMMING)
        register("cs", "text/x-csharp", "C# source file", MimeCategory.PROGRAMMING)
        register("go", "text/x-go", "Go source file", MimeCategory.PROGRAMMING)
        register("rs", "text/x-rust", "Rust source file", MimeCategory.PROGRAMMING)
        register("swift", "text/x-swift", "Swift source file", MimeCategory.PROGRAMMING)
        register("rb", "text/x-ruby", "Ruby source file", MimeCategory.PROGRAMMING)
        register("php", "application/x-php", "PHP source file", MimeCategory.PROGRAMMING)
        register("scala", "text/x-scala", "Scala source file", MimeCategory.PROGRAMMING)
        register("groovy", "text/x-groovy", "Groovy source file", MimeCategory.PROGRAMMING)

        // Data formats
        register("json", "application/json", "JSON file", MimeCategory.DATA)
        register("jsonc", "application/json", "JSON with comments", MimeCategory.DATA)
        register("xml", "application/xml", "XML file", MimeCategory.DATA)
        register("yaml", "application/x-yaml", "YAML file", MimeCategory.DATA)
        register("yml", "application/x-yaml", "YAML file", MimeCategory.DATA)
        register("toml", "application/toml", "TOML file", MimeCategory.DATA)
        register("csv", "text/csv", "CSV file", MimeCategory.DATA)
        register("properties", "text/x-java-properties", "Java properties file", MimeCategory.DATA)
        register("ini", "text/plain", "INI config file", MimeCategory.DATA)

        // Web technologies
        register("html", "text/html", "HTML file", MimeCategory.WEB)
        register("htm", "text/html", "HTML file", MimeCategory.WEB)
        register("css", "text/css", "CSS stylesheet", MimeCategory.WEB)
        register("scss", "text/x-scss", "SCSS stylesheet", MimeCategory.WEB)
        register("sass", "text/x-sass", "SASS stylesheet", MimeCategory.WEB)
        register("less", "text/x-less", "LESS stylesheet", MimeCategory.WEB)
        register("md", "text/markdown", "Markdown file", MimeCategory.WEB)
        register("markdown", "text/markdown", "Markdown file", MimeCategory.WEB)

        // Binary/Archive
        register("class", "application/java-vm", "Java class file", MimeCategory.BINARY)
        register("jar", "application/java-archive", "Java archive", MimeCategory.ARCHIVE)
        register("war", "application/java-archive", "Web archive", MimeCategory.ARCHIVE)
        register("ear", "application/java-archive", "Enterprise archive", MimeCategory.ARCHIVE)
        register("zip", "application/zip", "ZIP archive", MimeCategory.ARCHIVE)
        register("gz", "application/gzip", "Gzip archive", MimeCategory.ARCHIVE)
        register("tar", "application/x-tar", "TAR archive", MimeCategory.ARCHIVE)

        // Images
        register("png", "image/png", "PNG image", MimeCategory.IMAGE)
        register("jpg", "image/jpeg", "JPEG image", MimeCategory.IMAGE)
        register("jpeg", "image/jpeg", "JPEG image", MimeCategory.IMAGE)
        register("gif", "image/gif", "GIF image", MimeCategory.IMAGE)
        register("bmp", "image/bmp", "BMP image", MimeCategory.IMAGE)
        register("webp", "image/webp", "WebP image", MimeCategory.IMAGE)
        register("ico", "image/x-icon", "Icon file", MimeCategory.IMAGE)
        register("svg", "image/svg+xml", "SVG image", MimeCategory.IMAGE)

        // Documents
        register("pdf", "application/pdf", "PDF document", MimeCategory.DOCUMENT)
        register("doc", "application/msword", "Word document (legacy)", MimeCategory.DOCUMENT)
        register("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word document", MimeCategory.DOCUMENT)
        register("xls", "application/vnd.ms-excel", "Excel spreadsheet (legacy)", MimeCategory.DOCUMENT)
        register("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel spreadsheet", MimeCategory.DOCUMENT)
        register("ppt", "application/vnd.ms-powerpoint", "PowerPoint presentation (legacy)", MimeCategory.DOCUMENT)
        register("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint presentation", MimeCategory.DOCUMENT)
        register("odt", "application/vnd.oasis.opendocument.text", "OpenDocument text", MimeCategory.DOCUMENT)
        register("ods", "application/vnd.oasis.opendocument.spreadsheet", "OpenDocument spreadsheet", MimeCategory.DOCUMENT)
        register("odp", "application/vnd.oasis.opendocument.presentation", "OpenDocument presentation", MimeCategory.DOCUMENT)
        register("rtf", "application/rtf", "Rich Text Format", MimeCategory.DOCUMENT)
        register("txt", "text/plain", "Plain text file", MimeCategory.DOCUMENT)
        register("epub", "application/epub+zip", "EPUB ebook", MimeCategory.DOCUMENT)

        // Fonts
        register("ttf", "font/ttf", "TrueType font", MimeCategory.BINARY)
        register("otf", "font/otf", "OpenType font", MimeCategory.BINARY)
        register("woff", "font/woff", "Web Open Font Format", MimeCategory.BINARY)
        register("woff2", "font/woff2", "Web Open Font Format 2", MimeCategory.BINARY)
        register("eot", "application/vnd.ms-fontobject", "Embedded OpenType font", MimeCategory.BINARY)

        // Audio
        register("mp3", "audio/mpeg", "MP3 audio", MimeCategory.AUDIO)
        register("wav", "audio/wav", "WAV audio", MimeCategory.AUDIO)
        register("flac", "audio/flac", "FLAC audio", MimeCategory.AUDIO)
        register("aac", "audio/aac", "AAC audio", MimeCategory.AUDIO)
        register("ogg", "audio/ogg", "OGG audio", MimeCategory.AUDIO)
        register("wma", "audio/x-ms-wma", "WMA audio", MimeCategory.AUDIO)
        register("m4a", "audio/mp4", "M4A audio", MimeCategory.AUDIO)
        register("opus", "audio/opus", "Opus audio", MimeCategory.AUDIO)
        register("aiff", "audio/aiff", "AIFF audio", MimeCategory.AUDIO)

        // Video
        register("mp4", "video/mp4", "MP4 video", MimeCategory.VIDEO)
        register("mkv", "video/x-matroska", "Matroska video", MimeCategory.VIDEO)
        register("avi", "video/x-msvideo", "AVI video", MimeCategory.VIDEO)
        register("mov", "video/quicktime", "QuickTime video", MimeCategory.VIDEO)
        register("wmv", "video/x-ms-wmv", "WMV video", MimeCategory.VIDEO)
        register("flv", "video/x-flv", "Flash video", MimeCategory.VIDEO)
        register("webm", "video/webm", "WebM video", MimeCategory.VIDEO)
        register("m4v", "video/x-m4v", "M4V video", MimeCategory.VIDEO)
        register("mpeg", "video/mpeg", "MPEG video", MimeCategory.VIDEO)
        register("mpg", "video/mpeg", "MPEG video", MimeCategory.VIDEO)
        register("3gp", "video/3gpp", "3GP video", MimeCategory.VIDEO)

        // vCard/Calendar
        register("vcf", "text/vcard", "vCard contact", MimeCategory.CONTACT)
        register("vcard", "text/vcard", "vCard contact", MimeCategory.CONTACT)
        register("ics", "text/calendar", "iCalendar file", MimeCategory.CALENDAR)
        register("ical", "text/calendar", "iCalendar file", MimeCategory.CALENDAR)

        // Diagrams
        register("puml", "text/x-plantuml", "PlantUML diagram", MimeCategory.DIAGRAM)
        register("plantuml", "text/x-plantuml", "PlantUML diagram", MimeCategory.DIAGRAM)
        register("wsd", "text/x-plantuml", "PlantUML diagram", MimeCategory.DIAGRAM)
        register("mmd", "text/x-mermaid", "Mermaid diagram", MimeCategory.DIAGRAM)
        register("dot", "text/vnd.graphviz", "Graphviz diagram", MimeCategory.DIAGRAM)

        // Build files
        register("gradle", "text/x-gradle", "Gradle build file", MimeCategory.BUILD)
        register("pom", "application/xml", "Maven POM file", MimeCategory.BUILD)
        register("sh", "application/x-sh", "Shell script", MimeCategory.BUILD)
        register("bash", "application/x-sh", "Bash script", MimeCategory.BUILD)
        register("dockerfile", "text/x-dockerfile", "Dockerfile", MimeCategory.BUILD)
        register("makefile", "text/x-makefile", "Makefile", MimeCategory.BUILD)
    }

    fun register(extension: String, mimeType: String, description: String, category: MimeCategory) {
        val info = MimeTypeInfo(extension, mimeType, description, category)
        extensionToMime[extension.lowercase()] = info
        mimeToExtensions.getOrPut(mimeType) { mutableSetOf() }.add(extension.lowercase())
    }

    fun getMimeType(extension: String): String? {
        return extensionToMime[extension.lowercase()]?.mimeType
    }

    fun getInfo(extension: String): MimeTypeInfo? {
        return extensionToMime[extension.lowercase()]
    }

    fun getExtensions(mimeType: String): Set<String> {
        return mimeToExtensions[mimeType] ?: emptySet()
    }

    fun getByCategory(category: MimeCategory): List<MimeTypeInfo> {
        return extensionToMime.values.filter { it.category == category }
    }

    fun getAllExtensions(): Set<String> = extensionToMime.keys

    fun getAllMimeTypes(): Set<String> = mimeToExtensions.keys
}

/**
 * MIME type information
 */
data class MimeTypeInfo(
    val extension: String,
    val mimeType: String,
    val description: String,
    val category: MimeCategory
)

/**
 * MIME type categories
 */
enum class MimeCategory {
    PROGRAMMING,
    DATA,
    WEB,
    BINARY,
    ARCHIVE,
    IMAGE,
    DOCUMENT,
    CONTACT,
    CALENDAR,
    DIAGRAM,
    BUILD,
    AUDIO,
    VIDEO
}
