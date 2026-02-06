package org.jd.gui.server.pipeline

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin module for the staged LOD pipeline
 *
 * Provides:
 * - Stage processors (Probe, Metadata, Syntax, MediaInfo, Conversion)
 * - Main pipeline processor
 * - Camel context manager
 * - File stream source factory
 */
val pipelineModule = module {

    // Stage processors
    singleOf(::ProbeStageProcessor)
    singleOf(::MetadataStageProcessor)
    singleOf(::SyntaxStageProcessor)
    singleOf(::MediaInfoStageProcessor)
    singleOf(::ConversionStageProcessor)

    // Main pipeline processor
    singleOf(::StagedPipelineProcessor)

    // Camel context manager
    singleOf(::PipelineCamelContext)

    // File stream source factory
    single<FileStreamSourceFactory> { FileStreamSourceFactoryImpl() }
}

/**
 * Factory for creating FileStreamSource instances
 */
interface FileStreamSourceFactory {
    fun create(uri: String): FileStreamSource
    fun createWithCredentials(uri: String, username: String, password: String): FileStreamSource
}

class FileStreamSourceFactoryImpl : FileStreamSourceFactory {

    override fun create(uri: String): FileStreamSource {
        return FileStreamSource.fromUri(uri)
    }

    override fun createWithCredentials(uri: String, username: String, password: String): FileStreamSource {
        val parsedUri = java.net.URI(uri)
        return when (parsedUri.scheme?.lowercase()) {
            "http", "https" -> {
                if (parsedUri.path.contains("!")) {
                    WebDavArchiveSource(parsedUri, username, password)
                } else {
                    WebDavSource(parsedUri, username, password)
                }
            }
            else -> FileStreamSource.fromUri(uri)
        }
    }
}

/**
 * Pipeline configuration
 */
data class PipelineConfig(
    val maxDepth: Int = 5,
    val defaultIncludeXhtml: Boolean = true,
    val enableCamelRoutes: Boolean = true,
    val inboxDirectory: String = "inbox",
    val enableFileWatcher: Boolean = false,
    val maxContentSize: Long = 100 * 1024 * 1024, // 100MB
    val probeTimeout: Long = 5000,
    val metadataTimeout: Long = 10000,
    val syntaxTimeout: Long = 15000,
    val mediaInfoTimeout: Long = 30000,
    val conversionTimeout: Long = 60000
)

/**
 * Pipeline lifecycle manager
 */
class PipelineLifecycleManager(
    private val camelContext: PipelineCamelContext,
    private val config: PipelineConfig = PipelineConfig()
) {

    fun start() {
        if (config.enableCamelRoutes) {
            camelContext.start()
        }
    }

    fun stop() {
        camelContext.stop()
    }
}
