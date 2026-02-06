package org.jd.gui.server.pipeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.ToXMLContentHandler
import org.jd.gui.server.di.MimeCategory
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayInputStream

private val logger = KotlinLogging.logger {}

/**
 * Staged Level-of-Detail (LOD) Pipeline for file processing
 *
 * Stages:
 * 1. PROBE: MIME type detection (Tika)
 * 2. METADATA: Basic metadata extraction
 * 3. SYNTAX: ANTLR parsing for syntax highlighting/structure
 * 4. MEDIA_INFO: Specialized handler for media types
 * 5. CONVERSION: Action matrix for available conversions
 */

/**
 * Pipeline stage enumeration
 */
enum class PipelineStage(val order: Int, val description: String) {
    PROBE(1, "MIME type detection"),
    METADATA(2, "Basic metadata extraction"),
    SYNTAX(3, "Syntax parsing with ANTLR"),
    MEDIA_INFO(4, "Specialized media info extraction"),
    CONVERSION(5, "Conversion actions matrix")
}

/**
 * Result from each pipeline stage
 */
@Serializable
sealed class StageResult {
    abstract val stage: String
    abstract val success: Boolean
    abstract val durationMs: Long
}

@Serializable
data class ProbeResult(
    override val stage: String = "PROBE",
    override val success: Boolean,
    override val durationMs: Long,
    val mimeType: String,
    val mediaType: String,       // e.g., "text", "image", "video"
    val subType: String,         // e.g., "plain", "png", "mp4"
    val charset: String?,
    val category: String         // MimeCategory enum name
) : StageResult()

@Serializable
data class MetadataResult(
    override val stage: String = "METADATA",
    override val success: Boolean,
    override val durationMs: Long,
    val metadata: Map<String, String>,
    val tikaContentType: String?,
    val encoding: String?,
    val language: String?
) : StageResult()

@Serializable
data class SyntaxResult(
    override val stage: String = "SYNTAX",
    override val success: Boolean,
    override val durationMs: Long,
    val language: String?,
    val parserType: String?,     // "antlr", "regex", "none"
    val declarations: List<String>,
    val imports: List<String>,
    val errors: List<String>,
    val hasAntlrGrammar: Boolean
) : StageResult()

@Serializable
data class MediaInfoResult(
    override val stage: String = "MEDIA_INFO",
    override val success: Boolean,
    override val durationMs: Long,
    val handlerId: String?,
    val handlerName: String?,
    val extractedInfo: Map<String, String>,
    val xhtmlPreview: String?
) : StageResult()

@Serializable
data class ConversionResult(
    override val stage: String = "CONVERSION",
    override val success: Boolean,
    override val durationMs: Long,
    val availableActions: List<ConversionAction>,
    val recommendedAction: String?
) : StageResult()

@Serializable
data class ConversionAction(
    val actionId: String,
    val displayName: String,
    val targetMimeType: String,
    val outputFormats: List<String>,
    val requiresHandler: String?,
    val isLossless: Boolean
)

@Serializable
data class ErrorResult(
    override val stage: String,
    override val success: Boolean = false,
    override val durationMs: Long,
    val error: String,
    val errorType: String
) : StageResult()

/**
 * Complete pipeline result containing all stages
 */
@Serializable
data class PipelineResult(
    val uri: String,
    val filename: String,
    val stages: List<StageResult>,
    val totalDurationMs: Long,
    val completedStages: Int,
    val requestedDepth: Int
)

/**
 * Pipeline request with Accept header negotiation
 */
@Serializable
data class PipelineRequest(
    val uri: String,
    val depth: Int = 5,                    // How many stages to run (1-5)
    val acceptHeaders: List<String> = listOf("application/json"),
    val preferStreaming: Boolean = false,  // SSE vs single response
    val includeXhtml: Boolean = true,      // Include XHTML preview in media info
    val credentials: Credentials? = null
)

@Serializable
data class Credentials(
    val username: String,
    val password: String
)

/**
 * Main staged pipeline processor
 */
class StagedPipelineProcessor : KoinComponent {

    private val tika = Tika()
    private val parser = AutoDetectParser()

    // Injected services
    private val probeStage: ProbeStageProcessor by inject()
    private val metadataStage: MetadataStageProcessor by inject()
    private val syntaxStage: SyntaxStageProcessor by inject()
    private val mediaInfoStage: MediaInfoStageProcessor by inject()
    private val conversionStage: ConversionStageProcessor by inject()

    /**
     * Process file through pipeline stages as Flow (for SSE streaming)
     */
    fun processAsFlow(request: PipelineRequest): Flow<StageResult> = flow {
        val source = FileStreamSource.fromUri(request.uri)
        val content = source.openStream().use { it.readBytes() }
        val filename = source.filename

        var previousResults = mutableMapOf<PipelineStage, StageResult>()

        // Stage 1: PROBE
        if (request.depth >= 1) {
            val result = probeStage.process(content, filename)
            previousResults[PipelineStage.PROBE] = result
            emit(result)
        }

        // Stage 2: METADATA
        if (request.depth >= 2) {
            val probeResult = previousResults[PipelineStage.PROBE] as? ProbeResult
            val result = metadataStage.process(content, filename, probeResult)
            previousResults[PipelineStage.METADATA] = result
            emit(result)
        }

        // Stage 3: SYNTAX
        if (request.depth >= 3) {
            val probeResult = previousResults[PipelineStage.PROBE] as? ProbeResult
            val result = syntaxStage.process(content, filename, probeResult)
            previousResults[PipelineStage.SYNTAX] = result
            emit(result)
        }

        // Stage 4: MEDIA_INFO
        if (request.depth >= 4) {
            val probeResult = previousResults[PipelineStage.PROBE] as? ProbeResult
            val result = mediaInfoStage.process(content, filename, probeResult, request.includeXhtml)
            previousResults[PipelineStage.MEDIA_INFO] = result
            emit(result)
        }

        // Stage 5: CONVERSION
        if (request.depth >= 5) {
            val probeResult = previousResults[PipelineStage.PROBE] as? ProbeResult
            val syntaxResult = previousResults[PipelineStage.SYNTAX] as? SyntaxResult
            val mediaResult = previousResults[PipelineStage.MEDIA_INFO] as? MediaInfoResult
            val result = conversionStage.process(content, filename, probeResult, syntaxResult, mediaResult)
            emit(result)
        }
    }

    /**
     * Process file through all requested stages (single response)
     */
    suspend fun process(request: PipelineRequest): PipelineResult {
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<StageResult>()

        val source = FileStreamSource.fromUri(request.uri)
        val content = source.openStream().use { it.readBytes() }
        val filename = source.filename

        var probeResult: ProbeResult? = null
        var syntaxResult: SyntaxResult? = null
        var mediaResult: MediaInfoResult? = null

        // Stage 1: PROBE
        if (request.depth >= 1) {
            probeResult = probeStage.process(content, filename)
            results.add(probeResult)
        }

        // Stage 2: METADATA
        if (request.depth >= 2) {
            results.add(metadataStage.process(content, filename, probeResult))
        }

        // Stage 3: SYNTAX
        if (request.depth >= 3) {
            syntaxResult = syntaxStage.process(content, filename, probeResult)
            results.add(syntaxResult)
        }

        // Stage 4: MEDIA_INFO
        if (request.depth >= 4) {
            mediaResult = mediaInfoStage.process(content, filename, probeResult, request.includeXhtml)
            results.add(mediaResult)
        }

        // Stage 5: CONVERSION
        if (request.depth >= 5) {
            results.add(conversionStage.process(content, filename, probeResult, syntaxResult, mediaResult))
        }

        val totalDuration = System.currentTimeMillis() - startTime

        return PipelineResult(
            uri = request.uri,
            filename = filename,
            stages = results,
            totalDurationMs = totalDuration,
            completedStages = results.count { it.success },
            requestedDepth = request.depth
        )
    }
}

/**
 * Stage 1: MIME Type Probe
 */
class ProbeStageProcessor : KoinComponent {

    private val tika = Tika()

    fun process(content: ByteArray, filename: String): ProbeResult {
        val startTime = System.currentTimeMillis()

        return try {
            val mimeType = tika.detect(content, filename)
            val parts = mimeType.split("/")
            val mediaType = parts.getOrElse(0) { "application" }
            val subType = parts.getOrElse(1) { "octet-stream" }

            val category = when (mediaType) {
                "text" -> when {
                    subType.contains("java") || subType.contains("python") ||
                    subType.contains("kotlin") || subType.contains("javascript") ||
                    subType.contains("typescript") -> MimeCategory.PROGRAMMING
                    subType.contains("html") || subType.contains("css") -> MimeCategory.WEB
                    subType.contains("xml") || subType.contains("json") ||
                    subType.contains("yaml") -> MimeCategory.DATA
                    subType.contains("markdown") -> MimeCategory.DOCUMENT
                    else -> MimeCategory.DATA
                }
                "image" -> MimeCategory.IMAGE
                "audio" -> MimeCategory.AUDIO
                "video" -> MimeCategory.VIDEO
                "application" -> when {
                    subType.contains("pdf") -> MimeCategory.DOCUMENT
                    subType.contains("zip") || subType.contains("tar") ||
                    subType.contains("gzip") || subType.contains("jar") -> MimeCategory.ARCHIVE
                    subType.contains("json") -> MimeCategory.DATA
                    subType.contains("xml") -> MimeCategory.DATA
                    subType.contains("font") || subType.contains("ttf") ||
                    subType.contains("otf") -> MimeCategory.BINARY
                    else -> MimeCategory.BINARY
                }
                "font" -> MimeCategory.BINARY
                else -> MimeCategory.BINARY
            }

            ProbeResult(
                success = true,
                durationMs = System.currentTimeMillis() - startTime,
                mimeType = mimeType,
                mediaType = mediaType,
                subType = subType,
                charset = detectCharset(content),
                category = category.name
            )
        } catch (e: Exception) {
            logger.error(e) { "Probe stage failed for $filename" }
            ProbeResult(
                success = false,
                durationMs = System.currentTimeMillis() - startTime,
                mimeType = "application/octet-stream",
                mediaType = "application",
                subType = "octet-stream",
                charset = null,
                category = MimeCategory.BINARY.name
            )
        }
    }

    private fun detectCharset(content: ByteArray): String? {
        return try {
            // Simple BOM detection
            when {
                content.size >= 3 &&
                    content[0] == 0xEF.toByte() &&
                    content[1] == 0xBB.toByte() &&
                    content[2] == 0xBF.toByte() -> "UTF-8"
                content.size >= 2 &&
                    content[0] == 0xFE.toByte() &&
                    content[1] == 0xFF.toByte() -> "UTF-16BE"
                content.size >= 2 &&
                    content[0] == 0xFF.toByte() &&
                    content[1] == 0xFE.toByte() -> "UTF-16LE"
                else -> "UTF-8" // Assume UTF-8 for text
            }
        } catch (e: Exception) { null }
    }
}

/**
 * Stage 2: Metadata Extraction
 */
class MetadataStageProcessor : KoinComponent {

    private val parser = AutoDetectParser()

    fun process(content: ByteArray, filename: String, probeResult: ProbeResult?): MetadataResult {
        val startTime = System.currentTimeMillis()

        return try {
            val metadata = Metadata()
            metadata.set("resourceName", filename)

            val handler = ToXMLContentHandler()
            parser.parse(ByteArrayInputStream(content), handler, metadata)

            val metadataMap = metadata.names().associateWith { name ->
                metadata.get(name) ?: ""
            }

            MetadataResult(
                success = true,
                durationMs = System.currentTimeMillis() - startTime,
                metadata = metadataMap,
                tikaContentType = metadata.get("Content-Type"),
                encoding = metadata.get("Content-Encoding") ?: probeResult?.charset,
                language = metadata.get("dc:language") ?: metadata.get("language")
            )
        } catch (e: Exception) {
            logger.error(e) { "Metadata stage failed for $filename" }
            MetadataResult(
                success = false,
                durationMs = System.currentTimeMillis() - startTime,
                metadata = mapOf("error" to (e.message ?: "Unknown error")),
                tikaContentType = probeResult?.mimeType,
                encoding = probeResult?.charset,
                language = null
            )
        }
    }
}

/**
 * Stage 3: Syntax Analysis
 */
class SyntaxStageProcessor : KoinComponent {

    // Map of extensions to ANTLR grammar availability
    private val antlrGrammars = setOf(
        "java", "kt", "kts", "scala", "groovy",      // JVM
        "ts", "tsx", "js", "jsx",                     // JavaScript/TypeScript
        "py", "pyw",                                  // Python
        "go", "rs", "c", "cpp", "h", "hpp",          // Systems
        "cs", "swift", "m", "mm",                     // C#, Swift, Objective-C
        "rb", "php", "pl", "lua",                     // Scripting
        "dart", "ex", "exs", "hs", "clj", "erl",     // Modern/Functional
        "sol", "graphql", "proto",                    // Specialized
        "sql", "html", "css", "xml", "json", "yaml"  // Data/Web
    )

    fun process(content: ByteArray, filename: String, probeResult: ProbeResult?): SyntaxResult {
        val startTime = System.currentTimeMillis()

        val extension = filename.substringAfterLast('.', "").lowercase()
        val hasGrammar = antlrGrammars.contains(extension)

        return try {
            val text = String(content, Charsets.UTF_8)
            val lines = text.lines()

            // Simple extraction (real impl uses ANTLR parsers)
            val declarations = extractDeclarations(text, extension)
            val imports = extractImports(text, extension)

            SyntaxResult(
                success = true,
                durationMs = System.currentTimeMillis() - startTime,
                language = mapExtensionToLanguage(extension),
                parserType = if (hasGrammar) "antlr" else "regex",
                declarations = declarations.take(50), // Limit for response size
                imports = imports.take(50),
                errors = emptyList(),
                hasAntlrGrammar = hasGrammar
            )
        } catch (e: Exception) {
            logger.error(e) { "Syntax stage failed for $filename" }
            SyntaxResult(
                success = false,
                durationMs = System.currentTimeMillis() - startTime,
                language = mapExtensionToLanguage(extension),
                parserType = null,
                declarations = emptyList(),
                imports = emptyList(),
                errors = listOf(e.message ?: "Parse error"),
                hasAntlrGrammar = hasGrammar
            )
        }
    }

    private fun extractDeclarations(text: String, extension: String): List<String> {
        val patterns = when (extension) {
            "java", "kt", "scala", "groovy" ->
                listOf(
                    Regex("""(?:public\s+)?(?:class|interface|enum)\s+(\w+)"""),
                    Regex("""(?:public\s+|private\s+|protected\s+)?(?:fun|def|void|static)\s+(\w+)\s*\(""")
                )
            "ts", "tsx", "js", "jsx" ->
                listOf(
                    Regex("""(?:export\s+)?(?:class|interface|type)\s+(\w+)"""),
                    Regex("""(?:export\s+)?(?:function|const|let|var)\s+(\w+)"""),
                    Regex("""(\w+)\s*:\s*\(.*\)\s*=>""")
                )
            "py" ->
                listOf(
                    Regex("""class\s+(\w+)"""),
                    Regex("""def\s+(\w+)\s*\(""")
                )
            "go" ->
                listOf(
                    Regex("""type\s+(\w+)\s+(?:struct|interface)"""),
                    Regex("""func\s+(?:\([^)]+\)\s+)?(\w+)\s*\(""")
                )
            "rs" ->
                listOf(
                    Regex("""(?:pub\s+)?(?:struct|enum|trait)\s+(\w+)"""),
                    Regex("""(?:pub\s+)?fn\s+(\w+)""")
                )
            else -> emptyList()
        }

        return patterns.flatMap { pattern ->
            pattern.findAll(text).mapNotNull { it.groupValues.getOrNull(1) }
        }.distinct()
    }

    private fun extractImports(text: String, extension: String): List<String> {
        val pattern = when (extension) {
            "java" -> Regex("""import\s+([\w.]+);""")
            "kt", "kts" -> Regex("""import\s+([\w.]+)""")
            "ts", "tsx", "js", "jsx" -> Regex("""import\s+.*from\s+['"]([^'"]+)['"]""")
            "py" -> Regex("""(?:from\s+([\w.]+)\s+import|import\s+([\w.]+))""")
            "go" -> Regex("""import\s+(?:\(\s*)?"([^"]+)"""")
            "rs" -> Regex("""use\s+([\w:]+)""")
            else -> null
        } ?: return emptyList()

        return pattern.findAll(text).mapNotNull {
            it.groupValues.drop(1).firstOrNull { g -> g.isNotEmpty() }
        }.distinct().toList()
    }

    private fun mapExtensionToLanguage(extension: String): String? {
        return when (extension) {
            "java" -> "Java"
            "kt", "kts" -> "Kotlin"
            "scala" -> "Scala"
            "groovy" -> "Groovy"
            "ts", "tsx" -> "TypeScript"
            "js", "jsx" -> "JavaScript"
            "py", "pyw" -> "Python"
            "go" -> "Go"
            "rs" -> "Rust"
            "c", "h" -> "C"
            "cpp", "hpp", "cxx" -> "C++"
            "cs" -> "C#"
            "swift" -> "Swift"
            "m", "mm" -> "Objective-C"
            "rb" -> "Ruby"
            "php" -> "PHP"
            "pl", "pm" -> "Perl"
            "lua" -> "Lua"
            else -> null
        }
    }
}

/**
 * Stage 4: Media Info Extraction
 */
class MediaInfoStageProcessor : KoinComponent {

    fun process(
        content: ByteArray,
        filename: String,
        probeResult: ProbeResult?,
        includeXhtml: Boolean
    ): MediaInfoResult {
        val startTime = System.currentTimeMillis()

        return try {
            val category = probeResult?.category ?: MimeCategory.BINARY.name
            val extension = filename.substringAfterLast('.', "").lowercase()

            // Determine appropriate handler based on category
            val (handlerId, handlerName, info, xhtml) = when (category) {
                MimeCategory.IMAGE.name -> extractImageInfo(content, filename, includeXhtml)
                MimeCategory.AUDIO.name, MimeCategory.VIDEO.name -> extractMediaInfo(content, filename, includeXhtml)
                MimeCategory.BINARY.name -> when {
                    extension in listOf("ttf", "otf", "woff", "woff2", "eot") ->
                        extractFontInfo(content, filename, includeXhtml)
                    else -> Quadruple(null, null, emptyMap(), null)
                }
                MimeCategory.DOCUMENT.name -> extractDocumentInfo(content, filename, probeResult?.mimeType)
                else -> Quadruple(null, null, emptyMap(), null)
            }

            MediaInfoResult(
                success = true,
                durationMs = System.currentTimeMillis() - startTime,
                handlerId = handlerId,
                handlerName = handlerName,
                extractedInfo = info,
                xhtmlPreview = if (includeXhtml) xhtml else null
            )
        } catch (e: Exception) {
            logger.error(e) { "MediaInfo stage failed for $filename" }
            MediaInfoResult(
                success = false,
                durationMs = System.currentTimeMillis() - startTime,
                handlerId = null,
                handlerName = null,
                extractedInfo = mapOf("error" to (e.message ?: "Unknown")),
                xhtmlPreview = null
            )
        }
    }

    private fun extractImageInfo(content: ByteArray, filename: String, includeXhtml: Boolean):
            Quadruple<String?, String?, Map<String, String>, String?> {
        // Placeholder - real impl uses ImageMetadataHandler
        return Quadruple(
            "image-metadata",
            "Image Metadata (EXIF/IPTC/XMP)",
            mapOf("handler" to "ImageMetadataHandler"),
            if (includeXhtml) "<html><body>Image preview</body></html>" else null
        )
    }

    private fun extractMediaInfo(content: ByteArray, filename: String, includeXhtml: Boolean):
            Quadruple<String?, String?, Map<String, String>, String?> {
        // Placeholder - real impl uses FFmpegMediaHandler
        return Quadruple(
            "ffmpeg-media",
            "Media (FFmpeg)",
            mapOf("handler" to "FFmpegMediaHandler"),
            null
        )
    }

    private fun extractFontInfo(content: ByteArray, filename: String, includeXhtml: Boolean):
            Quadruple<String?, String?, Map<String, String>, String?> {
        // Placeholder - real impl uses FontBoxEnhancedHandler
        return Quadruple(
            "fontbox-enhanced",
            "Font (FontBox Enhanced)",
            mapOf("handler" to "FontBoxEnhancedHandler"),
            if (includeXhtml) "<html><body>Font preview</body></html>" else null
        )
    }

    private fun extractDocumentInfo(content: ByteArray, filename: String, mimeType: String?):
            Quadruple<String?, String?, Map<String, String>, String?> {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> Quadruple("pdfbox", "PDF (PDFBox)", mapOf("pages" to "N/A"), null)
            "docx" -> Quadruple("docx", "Word (docx4j)", emptyMap(), null)
            "xlsx" -> Quadruple("xlsx", "Excel (docx4j)", emptyMap(), null)
            "pptx" -> Quadruple("pptx", "PowerPoint (docx4j)", emptyMap(), null)
            else -> Quadruple(null, null, emptyMap(), null)
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

/**
 * Stage 5: Conversion Actions Matrix
 */
class ConversionStageProcessor : KoinComponent {

    fun process(
        content: ByteArray,
        filename: String,
        probeResult: ProbeResult?,
        syntaxResult: SyntaxResult?,
        mediaResult: MediaInfoResult?
    ): ConversionResult {
        val startTime = System.currentTimeMillis()

        val category = probeResult?.category ?: MimeCategory.BINARY.name
        val mimeType = probeResult?.mimeType ?: "application/octet-stream"
        val extension = filename.substringAfterLast('.', "").lowercase()

        val actions = mutableListOf<ConversionAction>()

        // Universal actions
        actions.add(ConversionAction(
            actionId = "to-xhtml",
            displayName = "Convert to XHTML",
            targetMimeType = "application/xhtml+xml",
            outputFormats = listOf("xhtml", "html"),
            requiresHandler = "xhtml-universal",
            isLossless = true
        ))

        // Category-specific actions
        when (category) {
            MimeCategory.PROGRAMMING.name -> {
                actions.addAll(listOf(
                    ConversionAction(
                        actionId = "to-puml",
                        displayName = "Generate PlantUML Diagram",
                        targetMimeType = "text/x-plantuml",
                        outputFormats = listOf("puml", "svg", "png"),
                        requiresHandler = "antlr-parser",
                        isLossless = false
                    ),
                    ConversionAction(
                        actionId = "to-svg-syntax",
                        displayName = "Syntax Highlight to SVG",
                        targetMimeType = "image/svg+xml",
                        outputFormats = listOf("svg"),
                        requiresHandler = "batik-svg",
                        isLossless = true
                    ),
                    ConversionAction(
                        actionId = "extract-structure",
                        displayName = "Extract Code Structure",
                        targetMimeType = "application/json",
                        outputFormats = listOf("json"),
                        requiresHandler = null,
                        isLossless = true
                    )
                ))
            }

            MimeCategory.IMAGE.name -> {
                actions.addAll(listOf(
                    ConversionAction(
                        actionId = "extract-exif",
                        displayName = "Extract EXIF/IPTC/XMP",
                        targetMimeType = "application/json",
                        outputFormats = listOf("json", "xhtml"),
                        requiresHandler = "image-metadata",
                        isLossless = true
                    ),
                    ConversionAction(
                        actionId = "to-thumbnail",
                        displayName = "Generate Thumbnail",
                        targetMimeType = "image/png",
                        outputFormats = listOf("png", "jpg", "webp"),
                        requiresHandler = null,
                        isLossless = false
                    )
                ))
            }

            MimeCategory.AUDIO.name, MimeCategory.VIDEO.name -> {
                actions.addAll(listOf(
                    ConversionAction(
                        actionId = "extract-mediainfo",
                        displayName = "Extract Media Info",
                        targetMimeType = "application/json",
                        outputFormats = listOf("json", "xhtml"),
                        requiresHandler = "ffmpeg-media",
                        isLossless = true
                    ),
                    ConversionAction(
                        actionId = "extract-audio",
                        displayName = "Extract Audio Track",
                        targetMimeType = "audio/mpeg",
                        outputFormats = listOf("mp3", "aac", "wav"),
                        requiresHandler = "ffmpeg-media",
                        isLossless = false
                    )
                ))
            }

            MimeCategory.DOCUMENT.name -> {
                actions.addAll(listOf(
                    ConversionAction(
                        actionId = "to-text",
                        displayName = "Extract Plain Text",
                        targetMimeType = "text/plain",
                        outputFormats = listOf("txt"),
                        requiresHandler = "tika",
                        isLossless = false
                    ),
                    ConversionAction(
                        actionId = "to-html",
                        displayName = "Convert to HTML",
                        targetMimeType = "text/html",
                        outputFormats = listOf("html"),
                        requiresHandler = when (extension) {
                            "pdf" -> "pdfbox"
                            "docx" -> "docx"
                            "md", "markdown" -> "flexmark-md"
                            else -> "tika"
                        },
                        isLossless = true
                    )
                ))
            }

            MimeCategory.DATA.name -> {
                actions.add(ConversionAction(
                    actionId = "validate",
                    displayName = "Validate Format",
                    targetMimeType = "application/json",
                    outputFormats = listOf("json"),
                    requiresHandler = null,
                    isLossless = true
                ))
            }

            else -> {}
        }

        // Font-specific actions
        if (extension in listOf("ttf", "otf", "woff", "woff2", "eot")) {
            actions.addAll(listOf(
                ConversionAction(
                    actionId = "font-preview",
                    displayName = "Generate Font Preview",
                    targetMimeType = "application/xhtml+xml",
                    outputFormats = listOf("xhtml"),
                    requiresHandler = "fontbox-enhanced",
                    isLossless = true
                ),
                ConversionAction(
                    actionId = "font-info",
                    displayName = "Extract Font Info",
                    targetMimeType = "application/json",
                    outputFormats = listOf("json"),
                    requiresHandler = "fontbox-enhanced",
                    isLossless = true
                )
            ))
        }

        // Determine recommended action
        val recommended = when (category) {
            MimeCategory.PROGRAMMING.name -> "to-puml"
            MimeCategory.IMAGE.name -> "extract-exif"
            MimeCategory.AUDIO.name, MimeCategory.VIDEO.name -> "extract-mediainfo"
            MimeCategory.DOCUMENT.name -> "to-html"
            else -> "to-xhtml"
        }

        return ConversionResult(
            success = true,
            durationMs = System.currentTimeMillis() - startTime,
            availableActions = actions,
            recommendedAction = recommended
        )
    }
}
