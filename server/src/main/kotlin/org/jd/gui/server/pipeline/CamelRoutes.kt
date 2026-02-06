package org.jd.gui.server.pipeline

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.DefaultCamelContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = KotlinLogging.logger {}

/**
 * Apache Camel route definitions for the staged LOD pipeline
 *
 * Routes:
 * - direct:probe      -> MIME type detection
 * - direct:metadata   -> Tika metadata extraction
 * - direct:syntax     -> ANTLR syntax parsing
 * - direct:mediainfo  -> Specialized handler extraction
 * - direct:conversion -> Action matrix generation
 * - direct:pipeline   -> Full pipeline (orchestrates all stages)
 *
 * Integration endpoints:
 * - file:inbox        -> Local file watching
 * - sardine:webdav    -> WebDAV polling (custom component)
 * - websocket:stream  -> WebSocket output
 * - stream:out        -> Console/SSE output
 */

/**
 * Camel message headers for pipeline processing
 */
object PipelineHeaders {
    const val URI = "PipelineUri"
    const val FILENAME = "PipelineFilename"
    const val DEPTH = "PipelineDepth"
    const val CONTENT = "PipelineContent"
    const val PROBE_RESULT = "ProbeResult"
    const val METADATA_RESULT = "MetadataResult"
    const val SYNTAX_RESULT = "SyntaxResult"
    const val MEDIA_RESULT = "MediaInfoResult"
    const val ACCEPT_HEADER = "AcceptHeader"
    const val INCLUDE_XHTML = "IncludeXhtml"
}

/**
 * Main Camel route builder for pipeline stages
 */
class PipelineRouteBuilder : RouteBuilder(), KoinComponent {

    private val probeProcessor: ProbeStageProcessor by inject()
    private val metadataProcessor: MetadataStageProcessor by inject()
    private val syntaxProcessor: SyntaxStageProcessor by inject()
    private val mediaInfoProcessor: MediaInfoStageProcessor by inject()
    private val conversionProcessor: ConversionStageProcessor by inject()

    private val json = Json { prettyPrint = true }

    override fun configure() {
        // Error handler
        onException(Exception::class.java)
            .handled(true)
            .process { exchange ->
                val exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception::class.java)
                logger.error(exception) { "Pipeline error: ${exception.message}" }
                exchange.message.body = ErrorResult(
                    stage = exchange.message.getHeader("CurrentStage", "UNKNOWN", String::class.java),
                    durationMs = 0,
                    error = exception.message ?: "Unknown error",
                    errorType = exception.javaClass.simpleName
                )
            }

        // Stage 1: Probe route
        from("direct:probe")
            .routeId("pipeline-probe")
            .process(ProbeStageRouteProcessor(probeProcessor))
            .setHeader(PipelineHeaders.PROBE_RESULT, body())

        // Stage 2: Metadata route
        from("direct:metadata")
            .routeId("pipeline-metadata")
            .process(MetadataStageRouteProcessor(metadataProcessor))
            .setHeader(PipelineHeaders.METADATA_RESULT, body())

        // Stage 3: Syntax route
        from("direct:syntax")
            .routeId("pipeline-syntax")
            .process(SyntaxStageRouteProcessor(syntaxProcessor))
            .setHeader(PipelineHeaders.SYNTAX_RESULT, body())

        // Stage 4: Media info route
        from("direct:mediainfo")
            .routeId("pipeline-mediainfo")
            .process(MediaInfoStageRouteProcessor(mediaInfoProcessor))
            .setHeader(PipelineHeaders.MEDIA_RESULT, body())

        // Stage 5: Conversion route
        from("direct:conversion")
            .routeId("pipeline-conversion")
            .process(ConversionStageRouteProcessor(conversionProcessor))

        // Full pipeline orchestration
        from("direct:pipeline")
            .routeId("pipeline-full")
            .log("Starting pipeline for \${header.${PipelineHeaders.FILENAME}}")
            .choice()
                .`when`(header(PipelineHeaders.DEPTH).isGreaterThanOrEqualTo(1))
                    .to("direct:probe")
                .end()
            .choice()
                .`when`(header(PipelineHeaders.DEPTH).isGreaterThanOrEqualTo(2))
                    .to("direct:metadata")
                .end()
            .choice()
                .`when`(header(PipelineHeaders.DEPTH).isGreaterThanOrEqualTo(3))
                    .to("direct:syntax")
                .end()
            .choice()
                .`when`(header(PipelineHeaders.DEPTH).isGreaterThanOrEqualTo(4))
                    .to("direct:mediainfo")
                .end()
            .choice()
                .`when`(header(PipelineHeaders.DEPTH).isGreaterThanOrEqualTo(5))
                    .to("direct:conversion")
                .end()
            .process(PipelineAggregator())
            .log("Pipeline completed for \${header.${PipelineHeaders.FILENAME}}")

        // File watcher route (inbox folder)
        from("file:inbox?recursive=true&noop=true&idempotent=true")
            .routeId("file-watcher")
            .setHeader(PipelineHeaders.URI, simple("file://\${file:absolute.path}"))
            .setHeader(PipelineHeaders.FILENAME, simple("\${file:name}"))
            .setHeader(PipelineHeaders.DEPTH, constant(5))
            .setHeader(PipelineHeaders.INCLUDE_XHTML, constant(true))
            .process { exchange ->
                val content = exchange.message.getBody(ByteArray::class.java)
                exchange.message.setHeader(PipelineHeaders.CONTENT, content)
            }
            .to("direct:pipeline")
            .to("stream:out")

        // SSE output route (for streaming results)
        from("direct:sse-output")
            .routeId("sse-output")
            .process { exchange ->
                val result = exchange.message.body
                val jsonOutput = when (result) {
                    is StageResult -> json.encodeToString(serializeStageResult(result))
                    is PipelineResult -> json.encodeToString(result)
                    else -> result.toString()
                }
                exchange.message.body = "data: $jsonOutput\n\n"
            }
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
}

/**
 * Probe stage processor for Camel
 */
class ProbeStageRouteProcessor(
    private val processor: ProbeStageProcessor
) : Processor {
    override fun process(exchange: Exchange) {
        val content = exchange.message.getHeader(PipelineHeaders.CONTENT, ByteArray::class.java)
            ?: exchange.message.getBody(ByteArray::class.java)
        val filename = exchange.message.getHeader(PipelineHeaders.FILENAME, String::class.java)

        exchange.message.setHeader("CurrentStage", "PROBE")
        val result = processor.process(content, filename)
        exchange.message.body = result
    }
}

/**
 * Metadata stage processor for Camel
 */
class MetadataStageRouteProcessor(
    private val processor: MetadataStageProcessor
) : Processor {
    override fun process(exchange: Exchange) {
        val content = exchange.message.getHeader(PipelineHeaders.CONTENT, ByteArray::class.java)
            ?: exchange.message.getBody(ByteArray::class.java)
        val filename = exchange.message.getHeader(PipelineHeaders.FILENAME, String::class.java)
        val probeResult = exchange.message.getHeader(PipelineHeaders.PROBE_RESULT, ProbeResult::class.java)

        exchange.message.setHeader("CurrentStage", "METADATA")
        val result = processor.process(content, filename, probeResult)
        exchange.message.body = result
    }
}

/**
 * Syntax stage processor for Camel
 */
class SyntaxStageRouteProcessor(
    private val processor: SyntaxStageProcessor
) : Processor {
    override fun process(exchange: Exchange) {
        val content = exchange.message.getHeader(PipelineHeaders.CONTENT, ByteArray::class.java)
            ?: exchange.message.getBody(ByteArray::class.java)
        val filename = exchange.message.getHeader(PipelineHeaders.FILENAME, String::class.java)
        val probeResult = exchange.message.getHeader(PipelineHeaders.PROBE_RESULT, ProbeResult::class.java)

        exchange.message.setHeader("CurrentStage", "SYNTAX")
        val result = processor.process(content, filename, probeResult)
        exchange.message.body = result
    }
}

/**
 * Media info stage processor for Camel
 */
class MediaInfoStageRouteProcessor(
    private val processor: MediaInfoStageProcessor
) : Processor {
    override fun process(exchange: Exchange) {
        val content = exchange.message.getHeader(PipelineHeaders.CONTENT, ByteArray::class.java)
            ?: exchange.message.getBody(ByteArray::class.java)
        val filename = exchange.message.getHeader(PipelineHeaders.FILENAME, String::class.java)
        val probeResult = exchange.message.getHeader(PipelineHeaders.PROBE_RESULT, ProbeResult::class.java)
        val includeXhtml = exchange.message.getHeader(PipelineHeaders.INCLUDE_XHTML, true, Boolean::class.java)

        exchange.message.setHeader("CurrentStage", "MEDIA_INFO")
        val result = processor.process(content, filename, probeResult, includeXhtml)
        exchange.message.body = result
    }
}

/**
 * Conversion stage processor for Camel
 */
class ConversionStageRouteProcessor(
    private val processor: ConversionStageProcessor
) : Processor {
    override fun process(exchange: Exchange) {
        val content = exchange.message.getHeader(PipelineHeaders.CONTENT, ByteArray::class.java)
            ?: exchange.message.getBody(ByteArray::class.java)
        val filename = exchange.message.getHeader(PipelineHeaders.FILENAME, String::class.java)
        val probeResult = exchange.message.getHeader(PipelineHeaders.PROBE_RESULT, ProbeResult::class.java)
        val syntaxResult = exchange.message.getHeader(PipelineHeaders.SYNTAX_RESULT, SyntaxResult::class.java)
        val mediaResult = exchange.message.getHeader(PipelineHeaders.MEDIA_RESULT, MediaInfoResult::class.java)

        exchange.message.setHeader("CurrentStage", "CONVERSION")
        val result = processor.process(content, filename, probeResult, syntaxResult, mediaResult)
        exchange.message.body = result
    }
}

/**
 * Aggregates all stage results into a PipelineResult
 */
class PipelineAggregator : Processor {
    override fun process(exchange: Exchange) {
        val uri = exchange.message.getHeader(PipelineHeaders.URI, "", String::class.java)
        val filename = exchange.message.getHeader(PipelineHeaders.FILENAME, "", String::class.java)
        val depth = exchange.message.getHeader(PipelineHeaders.DEPTH, 5, Int::class.java)

        val stages = mutableListOf<StageResult>()

        exchange.message.getHeader(PipelineHeaders.PROBE_RESULT, ProbeResult::class.java)?.let { stages.add(it) }
        exchange.message.getHeader(PipelineHeaders.METADATA_RESULT, MetadataResult::class.java)?.let { stages.add(it) }
        exchange.message.getHeader(PipelineHeaders.SYNTAX_RESULT, SyntaxResult::class.java)?.let { stages.add(it) }
        exchange.message.getHeader(PipelineHeaders.MEDIA_RESULT, MediaInfoResult::class.java)?.let { stages.add(it) }

        // Current body should be ConversionResult if depth >= 5
        val currentBody = exchange.message.body
        if (currentBody is ConversionResult) {
            stages.add(currentBody)
        }

        val totalDuration = stages.sumOf { it.durationMs }

        exchange.message.body = PipelineResult(
            uri = uri,
            filename = filename,
            stages = stages,
            totalDurationMs = totalDuration,
            completedStages = stages.count { it.success },
            requestedDepth = depth
        )
    }
}

/**
 * Camel context manager for the pipeline
 */
class PipelineCamelContext : KoinComponent {

    private var camelContext: CamelContext? = null

    fun start() {
        camelContext = DefaultCamelContext().apply {
            addRoutes(PipelineRouteBuilder())
            start()
        }
        logger.info { "Pipeline Camel context started" }
    }

    fun stop() {
        camelContext?.stop()
        camelContext = null
        logger.info { "Pipeline Camel context stopped" }
    }

    fun getContext(): CamelContext? = camelContext

    /**
     * Process a file through the pipeline using Camel
     */
    fun processFile(uri: String, depth: Int = 5, includeXhtml: Boolean = true): PipelineResult? {
        val context = camelContext ?: return null
        val producer = context.createProducerTemplate()

        val source = FileStreamSource.fromUri(uri)
        val content = runBlocking { source.openStream().use { it.readBytes() } }

        val headers = mapOf(
            PipelineHeaders.URI to uri,
            PipelineHeaders.FILENAME to source.filename,
            PipelineHeaders.DEPTH to depth,
            PipelineHeaders.CONTENT to content,
            PipelineHeaders.INCLUDE_XHTML to includeXhtml
        )

        return producer.requestBodyAndHeaders("direct:pipeline", content, headers, PipelineResult::class.java)
    }
}
