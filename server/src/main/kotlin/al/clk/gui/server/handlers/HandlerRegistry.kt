package al.clk.gui.server.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import al.clk.gui.server.di.MimeCategory
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

private val logger = KotlinLogging.logger {}

/**
 * Registry for file extension handlers
 * Uses Koin for dependency injection
 */
class HandlerRegistry : KoinComponent {
    private val handlers = mutableMapOf<String, FileExtensionHandler>()
    private val handlersByExtension = mutableMapOf<String, MutableList<FileExtensionHandler>>()

    // Inject all handlers from Koin
    // Core language handlers
    private val javaHandler: JavaHandler by inject()
    private val kotlinHandler: KotlinHandler by inject()
    private val typeScriptHandler: TypeScriptHandler by inject()
    private val javaScriptHandler: JavaScriptHandler by inject()
    private val pythonHandler: PythonHandler by inject()
    private val goHandler: GoHandler by inject()
    private val rustHandler: RustHandler by inject()
    private val cppHandler: CppHandler by inject()
    private val csharpHandler: CSharpHandler by inject()
    private val swiftHandler: SwiftHandler by inject()

    // Scripting language handlers
    private val phpHandler: PhpHandler by inject()
    private val rubyHandler: RubyHandler by inject()
    private val perlHandler: PerlHandler by inject()
    private val luaHandler: LuaHandler by inject()
    private val scalaHandler: ScalaHandler by inject()
    private val groovyHandler: GroovyHandler by inject()

    // Modern language handlers
    private val dartHandler: DartHandler by inject()
    private val elixirHandler: ElixirHandler by inject()
    private val haskellHandler: HaskellHandler by inject()
    private val objectiveCHandler: ObjectiveCHandler by inject()

    // Specialized language handlers
    private val solidityHandler: SolidityHandler by inject()
    private val graphqlHandler: GraphQLHandler by inject()
    private val protobufHandler: ProtobufHandler by inject()
    private val terraformHandler: TerraformHandler by inject()

    // Legacy language handlers
    private val cobolHandler: CobolHandler by inject()
    private val fortranHandler: FortranHandler by inject()
    private val adaHandler: AdaHandler by inject()
    private val zigHandler: ZigHandler by inject()

    // Hardware description language handlers
    private val verilogHandler: VerilogHandler by inject()
    private val vhdlHandler: VhdlHandler by inject()

    // Functional language handlers
    private val clojureHandler: ClojureHandler by inject()
    private val erlangHandler: ErlangHandler by inject()

    // Data format handlers
    private val jsonHandler: JsonHandler by inject()
    private val xmlHandler: XmlHandler by inject()
    private val yamlHandler: YamlHandler by inject()
    private val tomlHandler: TomlHandler by inject()
    private val propertiesHandler: PropertiesHandler by inject()
    private val sqlHandler: SqlHandler by inject()
    private val markdownHandler: MarkdownHandler by inject()
    private val htmlHandler: HtmlHandler by inject()
    private val cssHandler: CssHandler by inject()

    // Image handlers
    private val pngHandler: PngHandler by inject()
    private val jpegHandler: JpegHandler by inject()
    private val gifHandler: GifHandler by inject()
    private val webpHandler: WebPHandler by inject()
    private val bmpHandler: BmpHandler by inject()
    private val svgHandler: SvgHandler by inject()
    private val icoHandler: IcoHandler by inject()

    // Specialized parser handlers (higher priority)
    private val flexmarkMarkdownHandler: FlexmarkMarkdownHandler by inject()
    private val pdfBoxHandler: PdfBoxHandler by inject()
    private val docxHandler: DocxHandler by inject()
    private val xlsxHandler: XlsxHandler by inject()
    private val pptxHandler: PptxHandler by inject()
    private val fontHandler: FontHandler by inject()
    private val tikaHandler: TikaHandler by inject()

    // Enhanced media handlers
    private val fontBoxEnhancedHandler: FontBoxEnhancedHandler by inject()
    private val imageMetadataHandler: ImageMetadataHandler by inject()
    private val ffmpegMediaHandler: FFmpegMediaHandler by inject()

    // XHTML Converter - Base backend handler with Tika
    private val xhtmlHandler: XhtmlHandler by inject()

    init {
        // Register core language handlers
        registerHandler(javaHandler)
        registerHandler(kotlinHandler)
        registerHandler(typeScriptHandler)
        registerHandler(javaScriptHandler)
        registerHandler(pythonHandler)
        registerHandler(goHandler)
        registerHandler(rustHandler)
        registerHandler(cppHandler)
        registerHandler(csharpHandler)
        registerHandler(swiftHandler)

        // Register scripting language handlers
        registerHandler(phpHandler)
        registerHandler(rubyHandler)
        registerHandler(perlHandler)
        registerHandler(luaHandler)
        registerHandler(scalaHandler)
        registerHandler(groovyHandler)

        // Register modern language handlers
        registerHandler(dartHandler)
        registerHandler(elixirHandler)
        registerHandler(haskellHandler)
        registerHandler(objectiveCHandler)

        // Register specialized language handlers
        registerHandler(solidityHandler)
        registerHandler(graphqlHandler)
        registerHandler(protobufHandler)
        registerHandler(terraformHandler)

        // Register legacy language handlers
        registerHandler(cobolHandler)
        registerHandler(fortranHandler)
        registerHandler(adaHandler)
        registerHandler(zigHandler)

        // Register hardware description language handlers
        registerHandler(verilogHandler)
        registerHandler(vhdlHandler)

        // Register functional language handlers
        registerHandler(clojureHandler)
        registerHandler(erlangHandler)

        // Register data format handlers
        registerHandler(jsonHandler)
        registerHandler(xmlHandler)
        registerHandler(yamlHandler)
        registerHandler(tomlHandler)
        registerHandler(propertiesHandler)
        registerHandler(sqlHandler)
        registerHandler(markdownHandler)
        registerHandler(htmlHandler)
        registerHandler(cssHandler)

        // Register image handlers
        registerHandler(pngHandler)
        registerHandler(jpegHandler)
        registerHandler(gifHandler)
        registerHandler(webpHandler)
        registerHandler(bmpHandler)
        registerHandler(svgHandler)
        registerHandler(icoHandler)

        // Register specialized parser handlers (higher priority)
        registerHandler(flexmarkMarkdownHandler)  // Enhanced Markdown
        registerHandler(pdfBoxHandler)            // PDF processing
        registerHandler(docxHandler)              // Word documents
        registerHandler(xlsxHandler)              // Excel spreadsheets
        registerHandler(pptxHandler)              // PowerPoint presentations
        registerHandler(fontHandler)              // Font files (basic)
        registerHandler(tikaHandler)              // Universal fallback

        // Register enhanced media handlers (highest priority for media)
        registerHandler(fontBoxEnhancedHandler)   // Font with Bootstrap char grid
        registerHandler(imageMetadataHandler)     // EXIF/IPTC/XMP extraction
        registerHandler(ffmpegMediaHandler)       // Audio/video with FFmpeg

        // Register XHTML base handler (Tika-based, common output format)
        registerHandler(xhtmlHandler)             // Universal XHTML converter

        logger.info { "HandlerRegistry initialized with ${handlers.size} handlers" }
    }

    /**
     * Register a handler
     */
    fun registerHandler(handler: FileExtensionHandler) {
        handlers[handler.handlerId] = handler
        handler.supportedExtensions.forEach { ext ->
            handlersByExtension.getOrPut(ext.lowercase()) { mutableListOf() }.add(handler)
            // Sort by priority (highest first)
            handlersByExtension[ext.lowercase()]?.sortByDescending { it.priority }
        }
        logger.debug { "Registered handler: ${handler.handlerId} for extensions: ${handler.supportedExtensions}" }
    }

    /**
     * Get handler by ID
     */
    fun getHandler(handlerId: String): FileExtensionHandler? = handlers[handlerId]

    /**
     * Get handler for file extension
     */
    fun getHandlerForExtension(extension: String): FileExtensionHandler? {
        return handlersByExtension[extension.lowercase()]?.firstOrNull()
    }

    /**
     * Get all handlers for file extension
     */
    fun getAllHandlersForExtension(extension: String): List<FileExtensionHandler> {
        return handlersByExtension[extension.lowercase()] ?: emptyList()
    }

    /**
     * Get all handlers
     */
    fun getAllHandlers(): List<FileExtensionHandler> = handlers.values.toList()

    /**
     * Get handlers by category
     */
    fun getHandlersByCategory(category: MimeCategory): List<FileExtensionHandler> {
        return handlers.values.filter { it.category == category }
    }

    /**
     * Get all supported extensions
     */
    fun getSupportedExtensions(): Set<String> = handlersByExtension.keys

    /**
     * Check if extension is supported
     */
    fun isSupported(extension: String): Boolean {
        return handlersByExtension.containsKey(extension.lowercase())
    }

    /**
     * Process a file using appropriate handler
     */
    suspend fun processFile(content: FileContent): ProcessingResult {
        val handler = getHandlerForExtension(content.extension)
        return if (handler != null) {
            handler.process(content)
        } else {
            ProcessingResult(
                success = false,
                contentType = "application/octet-stream",
                error = "No handler found for extension: ${content.extension}"
            )
        }
    }

    /**
     * Get handler info
     */
    fun getHandlerInfo(handlerId: String): HandlerInfo? {
        val handler = handlers[handlerId] ?: return null
        return HandlerInfo(
            id = handler.handlerId,
            displayName = handler.displayName,
            extensions = handler.supportedExtensions,
            mimeTypes = handler.mimeTypes,
            category = handler.category.name,
            priority = handler.priority
        )
    }

    /**
     * Get all handler info
     */
    fun getAllHandlerInfo(): List<HandlerInfo> {
        return handlers.values.map { handler ->
            HandlerInfo(
                id = handler.handlerId,
                displayName = handler.displayName,
                extensions = handler.supportedExtensions,
                mimeTypes = handler.mimeTypes,
                category = handler.category.name,
                priority = handler.priority
            )
        }
    }
}

/**
 * Handler information for API responses
 */
@Serializable
data class HandlerInfo(
    val id: String,
    val displayName: String,
    val extensions: Set<String>,
    val mimeTypes: Set<String>,
    val category: String,
    val priority: Int
)

/**
 * File processing request
 */
@Serializable
data class FileProcessRequest(
    val filename: String,
    val content: String, // Base64 encoded
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Configure handler routes for Ktor
 */
fun Route.configureHandlerRoutes(registry: HandlerRegistry) {
    route("/api/handlers") {
        // List all handlers
        get {
            call.respond(mapOf(
                "count" to registry.getAllHandlers().size,
                "handlers" to registry.getAllHandlerInfo()
            ))
        }

        // Get handler by ID
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Handler ID required")
            )
            val info = registry.getHandlerInfo(id)
            if (info != null) {
                call.respond(info)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Handler not found: $id"))
            }
        }

        // Get handler for extension
        get("/extension/{ext}") {
            val ext = call.parameters["ext"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Extension required")
            )
            val handlers = registry.getAllHandlersForExtension(ext)
            if (handlers.isNotEmpty()) {
                call.respond(mapOf(
                    "extension" to ext,
                    "handlers" to handlers.map { handler ->
                        HandlerInfo(
                            id = handler.handlerId,
                            displayName = handler.displayName,
                            extensions = handler.supportedExtensions,
                            mimeTypes = handler.mimeTypes,
                            category = handler.category.name,
                            priority = handler.priority
                        )
                    }
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "No handler for extension: $ext"))
            }
        }

        // Get handlers by category
        get("/category/{category}") {
            val categoryName = call.parameters["category"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Category required")
            )
            val category = try {
                MimeCategory.valueOf(categoryName.uppercase())
            } catch (e: IllegalArgumentException) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid category: $categoryName")
                )
            }
            val handlers = registry.getHandlersByCategory(category)
            call.respond(mapOf(
                "category" to categoryName,
                "count" to handlers.size,
                "handlers" to handlers.map { handler ->
                    HandlerInfo(
                        id = handler.handlerId,
                        displayName = handler.displayName,
                        extensions = handler.supportedExtensions,
                        mimeTypes = handler.mimeTypes,
                        category = handler.category.name,
                        priority = handler.priority
                    )
                }
            ))
        }

        // Get supported extensions
        get("/extensions") {
            call.respond(mapOf(
                "count" to registry.getSupportedExtensions().size,
                "extensions" to registry.getSupportedExtensions().sorted()
            ))
        }

        // Process a file
        post("/process") {
            try {
                val request = call.receive<FileProcessRequest>()
                val extension = request.filename.substringAfterLast('.', "")
                val content = try {
                    java.util.Base64.getDecoder().decode(request.content)
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid base64 content")
                    )
                }

                val fileContent = FileContent(
                    name = request.filename,
                    extension = extension,
                    content = content,
                    metadata = request.metadata
                )

                val result = registry.processFile(fileContent)
                call.respond(result)
            } catch (e: Exception) {
                logger.error(e) { "Failed to process file" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Processing failed"))
                )
            }
        }

        // Validate a file
        post("/validate") {
            try {
                val request = call.receive<FileProcessRequest>()
                val extension = request.filename.substringAfterLast('.', "")
                val handler = registry.getHandlerForExtension(extension)

                if (handler == null) {
                    call.respond(mapOf(
                        "valid" to false,
                        "error" to "No handler for extension: $extension"
                    ))
                    return@post
                }

                val content = try {
                    java.util.Base64.getDecoder().decode(request.content)
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid base64 content")
                    )
                }

                val fileContent = FileContent(
                    name = request.filename,
                    extension = extension,
                    content = content,
                    metadata = request.metadata
                )

                val valid = handler.validate(fileContent)
                call.respond(mapOf(
                    "valid" to valid,
                    "handler" to handler.handlerId
                ))
            } catch (e: Exception) {
                logger.error(e) { "Failed to validate file" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Validation failed"))
                )
            }
        }

        // Extract metadata from a file
        post("/metadata") {
            try {
                val request = call.receive<FileProcessRequest>()
                val extension = request.filename.substringAfterLast('.', "")
                val handler = registry.getHandlerForExtension(extension)

                if (handler == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "No handler for extension: $extension"
                    ))
                    return@post
                }

                val content = try {
                    java.util.Base64.getDecoder().decode(request.content)
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid base64 content")
                    )
                }

                val fileContent = FileContent(
                    name = request.filename,
                    extension = extension,
                    content = content,
                    metadata = request.metadata
                )

                val metadata = handler.extractMetadata(fileContent)
                call.respond(mapOf(
                    "handler" to handler.handlerId,
                    "metadata" to metadata
                ))
            } catch (e: Exception) {
                logger.error(e) { "Failed to extract metadata" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Metadata extraction failed"))
                )
            }
        }
    }
}
