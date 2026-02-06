package org.jd.gui.server.svg

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.jd.gui.server.puml.ImageFormat
import org.jd.gui.server.puml.PlantUmlService
import java.awt.Color
import java.util.Base64

private val logger = KotlinLogging.logger {}

/**
 * SVG conversion request
 */
@Serializable
data class SvgConversionRequest(
    val svg: String,
    val format: String = "png",
    val width: Float? = null,
    val height: Float? = null,
    val backgroundColor: String? = null,
    val quality: Float = 0.95f,
    val dpi: Float = 96f
)

/**
 * SVG conversion response
 */
@Serializable
data class SvgConversionResult(
    val success: Boolean,
    val format: String,
    val data: String? = null,
    val error: String? = null,
    val contentType: String? = null
)

/**
 * SVG manipulation request
 */
@Serializable
data class SvgManipulationRequest(
    val svg: String,
    val operations: List<SvgOperation>
)

/**
 * SVG operation types
 */
@Serializable
data class SvgOperation(
    val type: String,
    val params: Map<String, String> = emptyMap()
)

/**
 * SVG merge request
 */
@Serializable
data class SvgMergeRequest(
    val documents: List<String>,
    val layout: String = "vertical"
)

/**
 * PlantUML to SVG request
 */
@Serializable
data class PumlToSvgRequest(
    val puml: String,
    val optimize: Boolean = false,
    val convertTo: String? = null,
    val width: Float? = null,
    val height: Float? = null
)

/**
 * SVG service integrating Batik with PlantUML
 */
class SvgService(
    private val plantUmlService: PlantUmlService = PlantUmlService()
) {
    private val transcoder = SvgTranscoder()

    /**
     * Generate SVG from PlantUML
     */
    fun generateSvgFromPuml(puml: String): String {
        val result = plantUmlService.renderDiagram(puml, ImageFormat.SVG)
        return String(result, Charsets.UTF_8)
    }

    /**
     * Convert SVG to another format
     */
    fun convertSvg(
        svgContent: String,
        format: SvgOutputFormat,
        options: SvgManipulationOptions = SvgManipulationOptions()
    ): ByteArray {
        return transcoder.transcode(svgContent, format, options)
    }

    /**
     * Manipulate SVG
     */
    fun manipulateSvg(svgContent: String, operations: List<SvgOperation>): String {
        var document = SvgManipulator.parse(svgContent)

        operations.forEach { op ->
            document = when (op.type.lowercase()) {
                "dimensions" -> {
                    val width = op.params["width"] ?: "100%"
                    val height = op.params["height"] ?: "100%"
                    SvgManipulator.setDimensions(document, width, height)
                }
                "viewbox" -> {
                    val minX = op.params["minX"]?.toDoubleOrNull() ?: 0.0
                    val minY = op.params["minY"]?.toDoubleOrNull() ?: 0.0
                    val width = op.params["width"]?.toDoubleOrNull() ?: 100.0
                    val height = op.params["height"]?.toDoubleOrNull() ?: 100.0
                    SvgManipulator.setViewBox(document, minX, minY, width, height)
                }
                "style" -> {
                    val css = op.params["css"] ?: ""
                    SvgManipulator.addStyle(document, css)
                }
                "transform" -> {
                    val transform = op.params["value"] ?: ""
                    SvgManipulator.addRootTransform(document, transform)
                }
                "watermark" -> {
                    val text = op.params["text"] ?: "Watermark"
                    val opacity = op.params["opacity"]?.toDoubleOrNull() ?: 0.3
                    SvgManipulator.addWatermark(document, text, opacity)
                }
                "optimize" -> {
                    SvgManipulator.optimize(document)
                }
                else -> document
            }
        }

        return SvgManipulator.serialize(document)
    }

    /**
     * Merge multiple SVG documents
     */
    fun mergeSvg(svgContents: List<String>, layout: SvgManipulator.MergeLayout): String {
        val documents = svgContents.map { SvgManipulator.parse(it) }
        val merged = SvgManipulator.merge(documents, layout)
        return SvgManipulator.serialize(merged)
    }

    /**
     * Generate and optionally convert PlantUML diagram
     */
    fun generateAndConvert(
        puml: String,
        targetFormat: SvgOutputFormat?,
        optimize: Boolean = false,
        options: SvgManipulationOptions = SvgManipulationOptions()
    ): Pair<ByteArray, String> {
        var svg = generateSvgFromPuml(puml)

        if (optimize) {
            val document = SvgManipulator.parse(svg)
            svg = SvgManipulator.serialize(SvgManipulator.optimize(document))
        }

        return if (targetFormat != null) {
            val converted = convertSvg(svg, targetFormat, options)
            val contentType = when (targetFormat) {
                SvgOutputFormat.PNG -> "image/png"
                SvgOutputFormat.JPEG -> "image/jpeg"
                SvgOutputFormat.TIFF -> "image/tiff"
                SvgOutputFormat.PDF -> "application/pdf"
            }
            converted to contentType
        } else {
            svg.toByteArray(Charsets.UTF_8) to "image/svg+xml"
        }
    }
}

/**
 * Parse color string to Color object
 */
fun parseColor(colorString: String): Color? {
    return try {
        when {
            colorString.startsWith("#") -> {
                val hex = colorString.substring(1)
                when (hex.length) {
                    6 -> Color(
                        hex.substring(0, 2).toInt(16),
                        hex.substring(2, 4).toInt(16),
                        hex.substring(4, 6).toInt(16)
                    )
                    8 -> Color(
                        hex.substring(0, 2).toInt(16),
                        hex.substring(2, 4).toInt(16),
                        hex.substring(4, 6).toInt(16),
                        hex.substring(6, 8).toInt(16)
                    )
                    else -> null
                }
            }
            colorString.startsWith("rgb(") -> {
                val parts = colorString.removePrefix("rgb(").removeSuffix(")")
                    .split(",").map { it.trim().toInt() }
                Color(parts[0], parts[1], parts[2])
            }
            colorString.startsWith("rgba(") -> {
                val parts = colorString.removePrefix("rgba(").removeSuffix(")")
                    .split(",").map { it.trim() }
                Color(
                    parts[0].toInt(),
                    parts[1].toInt(),
                    parts[2].toInt(),
                    (parts[3].toFloat() * 255).toInt()
                )
            }
            else -> {
                // Try named colors
                val field = Color::class.java.getField(colorString.uppercase())
                field.get(null) as Color
            }
        }
    } catch (e: Exception) {
        logger.warn { "Failed to parse color: $colorString" }
        null
    }
}

/**
 * Configure SVG routes for Ktor
 */
fun Route.configureSvgRoutes() {
    val svgService = SvgService()

    route("/api/svg") {
        // Convert SVG to other formats
        post("/convert") {
            try {
                val request = call.receive<SvgConversionRequest>()
                val format = when (request.format.lowercase()) {
                    "png" -> SvgOutputFormat.PNG
                    "jpeg", "jpg" -> SvgOutputFormat.JPEG
                    "tiff", "tif" -> SvgOutputFormat.TIFF
                    "pdf" -> SvgOutputFormat.PDF
                    else -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            SvgConversionResult(
                                success = false,
                                format = request.format,
                                error = "Unsupported format: ${request.format}"
                            )
                        )
                        return@post
                    }
                }

                val options = SvgManipulationOptions(
                    width = request.width,
                    height = request.height,
                    backgroundColor = request.backgroundColor?.let { parseColor(it) },
                    quality = request.quality,
                    dpi = request.dpi
                )

                val result = svgService.convertSvg(request.svg, format, options)
                val base64 = Base64.getEncoder().encodeToString(result)
                val contentType = when (format) {
                    SvgOutputFormat.PNG -> "image/png"
                    SvgOutputFormat.JPEG -> "image/jpeg"
                    SvgOutputFormat.TIFF -> "image/tiff"
                    SvgOutputFormat.PDF -> "application/pdf"
                }

                call.respond(
                    SvgConversionResult(
                        success = true,
                        format = request.format,
                        data = base64,
                        contentType = contentType
                    )
                )
            } catch (e: Exception) {
                logger.error(e) { "SVG conversion failed" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    SvgConversionResult(
                        success = false,
                        format = "unknown",
                        error = e.message ?: "Conversion failed"
                    )
                )
            }
        }

        // Manipulate SVG
        post("/manipulate") {
            try {
                val request = call.receive<SvgManipulationRequest>()
                val result = svgService.manipulateSvg(request.svg, request.operations)
                call.respondText(result, ContentType.Image.SVG)
            } catch (e: Exception) {
                logger.error(e) { "SVG manipulation failed" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Manipulation failed"))
                )
            }
        }

        // Merge SVG documents
        post("/merge") {
            try {
                val request = call.receive<SvgMergeRequest>()
                val layout = when (request.layout.lowercase()) {
                    "horizontal" -> SvgManipulator.MergeLayout.HORIZONTAL
                    "vertical" -> SvgManipulator.MergeLayout.VERTICAL
                    "grid" -> SvgManipulator.MergeLayout.GRID
                    else -> SvgManipulator.MergeLayout.VERTICAL
                }
                val result = svgService.mergeSvg(request.documents, layout)
                call.respondText(result, ContentType.Image.SVG)
            } catch (e: Exception) {
                logger.error(e) { "SVG merge failed" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Merge failed"))
                )
            }
        }

        // Generate SVG from PlantUML
        post("/from-puml") {
            try {
                val request = call.receive<PumlToSvgRequest>()
                val targetFormat = request.convertTo?.let { format ->
                    when (format.lowercase()) {
                        "png" -> SvgOutputFormat.PNG
                        "jpeg", "jpg" -> SvgOutputFormat.JPEG
                        "tiff", "tif" -> SvgOutputFormat.TIFF
                        "pdf" -> SvgOutputFormat.PDF
                        else -> null
                    }
                }

                val options = SvgManipulationOptions(
                    width = request.width,
                    height = request.height
                )

                val (data, contentType) = svgService.generateAndConvert(
                    request.puml,
                    targetFormat,
                    request.optimize,
                    options
                )

                if (contentType == "image/svg+xml") {
                    call.respondText(String(data, Charsets.UTF_8), ContentType.Image.SVG)
                } else {
                    call.respondBytes(data, ContentType.parse(contentType))
                }
            } catch (e: Exception) {
                logger.error(e) { "PlantUML to SVG conversion failed" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Conversion failed"))
                )
            }
        }

        // Optimize SVG
        post("/optimize") {
            try {
                val svg = call.receiveText()
                val document = SvgManipulator.parse(svg)
                val optimized = SvgManipulator.optimize(document)
                call.respondText(SvgManipulator.serialize(optimized), ContentType.Image.SVG)
            } catch (e: Exception) {
                logger.error(e) { "SVG optimization failed" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Optimization failed"))
                )
            }
        }

        // Get supported formats
        get("/formats") {
            call.respond(
                mapOf(
                    "input" to listOf("svg"),
                    "output" to SvgOutputFormat.values().map { it.name.lowercase() }
                )
            )
        }
    }
}
