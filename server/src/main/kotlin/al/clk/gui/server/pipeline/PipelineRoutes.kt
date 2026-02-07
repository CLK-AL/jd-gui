package al.clk.gui.server.pipeline

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.koin.ktor.ext.inject

private val logger = KotlinLogging.logger {}

private val json = Json {
    prettyPrint = true
    encodeDefaults = true
}

/**
 * Configure Ktor routes for the staged LOD pipeline
 *
 * Endpoints:
 * - POST /api/pipeline              - Full pipeline (single JSON response)
 * - POST /api/pipeline/stream       - SSE streaming (event-stream)
 * - WS   /api/pipeline/ws           - WebSocket streaming
 * - POST /api/pipeline/probe        - MIME type detection only
 * - POST /api/pipeline/metadata     - Metadata extraction only
 * - POST /api/pipeline/syntax       - Syntax analysis only
 * - POST /api/pipeline/mediainfo    - Media info extraction only
 * - POST /api/pipeline/conversion   - Conversion matrix only
 * - GET  /api/pipeline/actions/{mime} - Available actions for MIME type
 */
fun Route.configurePipelineRoutes() {
    val pipeline: StagedPipelineProcessor by inject()
    val camelContext: PipelineCamelContext by inject()

    route("/api/pipeline") {

        // Full pipeline - single JSON response
        post {
            try {
                val request = call.receive<PipelineRequest>()
                logger.info { "Processing pipeline request: ${request.uri}" }

                val result = pipeline.process(request)

                // Content negotiation based on Accept header
                when {
                    call.request.acceptItems().any { it.value.contains("xml") } -> {
                        call.respondText(
                            toXml(result),
                            ContentType.Application.Xml
                        )
                    }
                    else -> {
                        call.respond(result)
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Pipeline request failed" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Pipeline processing failed"))
                )
            }
        }

        // SSE streaming - Server-Sent Events
        post("/stream") {
            try {
                val request = call.receive<PipelineRequest>()
                logger.info { "Starting SSE stream for: ${request.uri}" }

                call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                call.response.headers.append(HttpHeaders.Connection, "keep-alive")

                call.respondTextWriter(ContentType.Text.EventStream) {
                    write("event: start\n")
                    write("data: {\"uri\": \"${request.uri}\", \"depth\": ${request.depth}}\n\n")
                    flush()

                    pipeline.processAsFlow(request).onEach { stageResult ->
                        val eventData = json.encodeToString(stageResult)
                        write("event: stage\n")
                        write("data: $eventData\n\n")
                        flush()
                    }.collect()

                    write("event: complete\n")
                    write("data: {\"status\": \"complete\"}\n\n")
                    flush()
                }
            } catch (e: Exception) {
                logger.error(e) { "SSE stream failed" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "SSE streaming failed"))
                )
            }
        }

        // Individual stage endpoints
        post("/probe") {
            processStage(call, pipeline, 1)
        }

        post("/metadata") {
            processStage(call, pipeline, 2)
        }

        post("/syntax") {
            processStage(call, pipeline, 3)
        }

        post("/mediainfo") {
            processStage(call, pipeline, 4)
        }

        post("/conversion") {
            processStage(call, pipeline, 5)
        }

        // Get available actions for a MIME type
        get("/actions/{mime}") {
            val mimeType = call.parameters["mime"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "MIME type required")
            )

            val decodedMime = mimeType.replace("_", "/")
            val actions = getActionsForMimeType(decodedMime)

            call.respond(mapOf(
                "mimeType" to decodedMime,
                "actions" to actions
            ))
        }

        // Pipeline info
        get("/info") {
            call.respond(mapOf(
                "stages" to PipelineStage.values().map { stage ->
                    mapOf(
                        "order" to stage.order,
                        "name" to stage.name,
                        "description" to stage.description
                    )
                },
                "sourceTypes" to SourceType.values().map { it.name },
                "supportedSchemes" to listOf("file", "http", "https", "zip", "jar", "tar")
            ))
        }

        // Camel route status
        get("/routes") {
            val context = camelContext.getContext()
            if (context != null) {
                val routes = context.routes.map { route ->
                    mapOf(
                        "id" to route.routeId,
                        "endpoint" to route.endpoint.endpointUri,
                        "status" to context.getRouteController().getRouteStatus(route.routeId).name
                    )
                }
                call.respond(mapOf(
                    "status" to "running",
                    "routes" to routes
                ))
            } else {
                call.respond(mapOf(
                    "status" to "stopped",
                    "routes" to emptyList<Any>()
                ))
            }
        }
    }

    // WebSocket endpoint for streaming
    webSocket("/api/pipeline/ws") {
        logger.info { "WebSocket connection opened" }

        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> {
                    try {
                        val text = frame.readText()
                        val request = json.decodeFromString<PipelineRequest>(text)

                        // Send start message
                        send(Frame.Text(json.encodeToString(mapOf(
                            "event" to "start",
                            "uri" to request.uri,
                            "depth" to request.depth
                        ))))

                        // Process and stream each stage
                        pipeline.processAsFlow(request).collect { stageResult ->
                            val message = json.encodeToString(mapOf(
                                "event" to "stage",
                                "data" to serializeStageResult(stageResult)
                            ))
                            send(Frame.Text(message))
                        }

                        // Send complete message
                        send(Frame.Text(json.encodeToString(mapOf(
                            "event" to "complete"
                        ))))

                    } catch (e: Exception) {
                        logger.error(e) { "WebSocket processing error" }
                        send(Frame.Text(json.encodeToString(mapOf(
                            "event" to "error",
                            "error" to (e.message ?: "Processing failed")
                        ))))
                    }
                }
                is Frame.Close -> {
                    logger.info { "WebSocket connection closed" }
                }
                else -> {}
            }
        }
    }
}

private suspend fun processStage(
    call: ApplicationCall,
    pipeline: StagedPipelineProcessor,
    depth: Int
) {
    try {
        val request = call.receive<PipelineRequest>().copy(depth = depth)
        val result = pipeline.process(request)

        // Get only the requested stage result
        val stageResult = result.stages.lastOrNull()
        if (stageResult != null) {
            call.respond(stageResult)
        } else {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Stage processing failed")
            )
        }
    } catch (e: Exception) {
        logger.error(e) { "Stage processing failed" }
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to (e.message ?: "Stage processing failed"))
        )
    }
}

private fun getActionsForMimeType(mimeType: String): List<ConversionAction> {
    val mediaType = mimeType.substringBefore("/")
    val subType = mimeType.substringAfter("/")

    val actions = mutableListOf<ConversionAction>()

    // Universal XHTML conversion
    actions.add(ConversionAction(
        actionId = "to-xhtml",
        displayName = "Convert to XHTML",
        targetMimeType = "application/xhtml+xml",
        outputFormats = listOf("xhtml", "html"),
        requiresHandler = "xhtml-universal",
        isLossless = true
    ))

    when (mediaType) {
        "text" -> {
            if (subType.contains("java") || subType.contains("python") ||
                subType.contains("kotlin") || subType.contains("javascript")) {
                actions.addAll(listOf(
                    ConversionAction(
                        actionId = "to-puml",
                        displayName = "Generate PlantUML",
                        targetMimeType = "text/x-plantuml",
                        outputFormats = listOf("puml", "svg", "png"),
                        requiresHandler = "antlr-parser",
                        isLossless = false
                    ),
                    ConversionAction(
                        actionId = "to-svg-syntax",
                        displayName = "Syntax Highlight SVG",
                        targetMimeType = "image/svg+xml",
                        outputFormats = listOf("svg"),
                        requiresHandler = "batik-svg",
                        isLossless = true
                    )
                ))
            }
            if (subType == "markdown") {
                actions.add(ConversionAction(
                    actionId = "to-html",
                    displayName = "Render Markdown",
                    targetMimeType = "text/html",
                    outputFormats = listOf("html"),
                    requiresHandler = "flexmark-md",
                    isLossless = true
                ))
            }
        }
        "image" -> {
            actions.addAll(listOf(
                ConversionAction(
                    actionId = "extract-exif",
                    displayName = "Extract EXIF Metadata",
                    targetMimeType = "application/json",
                    outputFormats = listOf("json"),
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
        "audio", "video" -> {
            actions.addAll(listOf(
                ConversionAction(
                    actionId = "extract-mediainfo",
                    displayName = "Extract Media Info",
                    targetMimeType = "application/json",
                    outputFormats = listOf("json"),
                    requiresHandler = "ffmpeg-media",
                    isLossless = true
                )
            ))
        }
        "application" -> {
            if (subType == "pdf") {
                actions.add(ConversionAction(
                    actionId = "to-text",
                    displayName = "Extract Text",
                    targetMimeType = "text/plain",
                    outputFormats = listOf("txt"),
                    requiresHandler = "pdfbox",
                    isLossless = false
                ))
            }
            if (subType.contains("word") || subType.contains("document")) {
                actions.add(ConversionAction(
                    actionId = "to-html",
                    displayName = "Convert to HTML",
                    targetMimeType = "text/html",
                    outputFormats = listOf("html"),
                    requiresHandler = "docx",
                    isLossless = true
                ))
            }
        }
        "font" -> {
            actions.addAll(listOf(
                ConversionAction(
                    actionId = "font-preview",
                    displayName = "Font Preview Grid",
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
    }

    return actions
}

@Suppress("UNCHECKED_CAST")
private fun serializeStageResult(result: StageResult): Map<String, Any?> {
    return when (result) {
        is ProbeResult -> mapOf(
            "stage" to result.stage,
            "success" to result.success,
            "durationMs" to result.durationMs,
            "mimeType" to result.mimeType,
            "mediaType" to result.mediaType,
            "subType" to result.subType,
            "charset" to result.charset,
            "category" to result.category
        )
        is MetadataResult -> mapOf(
            "stage" to result.stage,
            "success" to result.success,
            "durationMs" to result.durationMs,
            "metadata" to result.metadata,
            "tikaContentType" to result.tikaContentType,
            "encoding" to result.encoding,
            "language" to result.language
        )
        is SyntaxResult -> mapOf(
            "stage" to result.stage,
            "success" to result.success,
            "durationMs" to result.durationMs,
            "language" to result.language,
            "parserType" to result.parserType,
            "declarations" to result.declarations,
            "imports" to result.imports,
            "errors" to result.errors,
            "hasAntlrGrammar" to result.hasAntlrGrammar
        )
        is MediaInfoResult -> mapOf(
            "stage" to result.stage,
            "success" to result.success,
            "durationMs" to result.durationMs,
            "handlerId" to result.handlerId,
            "handlerName" to result.handlerName,
            "extractedInfo" to result.extractedInfo,
            "xhtmlPreview" to result.xhtmlPreview
        )
        is ConversionResult -> mapOf(
            "stage" to result.stage,
            "success" to result.success,
            "durationMs" to result.durationMs,
            "availableActions" to result.availableActions.map { action ->
                mapOf(
                    "actionId" to action.actionId,
                    "displayName" to action.displayName,
                    "targetMimeType" to action.targetMimeType,
                    "outputFormats" to action.outputFormats,
                    "requiresHandler" to action.requiresHandler,
                    "isLossless" to action.isLossless
                )
            },
            "recommendedAction" to result.recommendedAction
        )
        is ErrorResult -> mapOf(
            "stage" to result.stage,
            "success" to result.success,
            "durationMs" to result.durationMs,
            "error" to result.error,
            "errorType" to result.errorType
        )
    }
}

private fun toXml(result: PipelineResult): String {
    return buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<pipelineResult xmlns="http://gui.org/pipeline">""")
        appendLine("""  <uri>${escapeXml(result.uri)}</uri>""")
        appendLine("""  <filename>${escapeXml(result.filename)}</filename>""")
        appendLine("""  <totalDurationMs>${result.totalDurationMs}</totalDurationMs>""")
        appendLine("""  <completedStages>${result.completedStages}</completedStages>""")
        appendLine("""  <requestedDepth>${result.requestedDepth}</requestedDepth>""")
        appendLine("""  <stages>""")
        result.stages.forEach { stage ->
            appendLine("""    <stage name="${stage.stage}" success="${stage.success}" durationMs="${stage.durationMs}"/>""")
        }
        appendLine("""  </stages>""")
        appendLine("""</pipelineResult>""")
    }
}

private fun escapeXml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
