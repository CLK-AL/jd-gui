/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.puml

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader
import java.io.ByteArrayOutputStream
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * PlantUML Service for generating diagrams from source code
 * Uses ANTLR parsers to extract class information and generate UML
 */
class PlantUmlService {

    /**
     * Generate PlantUML diagram from source code
     */
    fun generateDiagramFromSource(
        source: String,
        languageId: String = "java",
        options: DiagramOptions = DiagramOptions()
    ): DiagramResult {
        return try {
            val converter = AntlrToPumlConverterRegistry.getConverter(languageId)
                ?: return DiagramResult(
                    success = false,
                    error = "Unsupported language: $languageId"
                )

            val diagram = converter.convert(source)
            val pumlSource = applyOptions(diagram, options).toPlantUml()

            DiagramResult(
                success = true,
                pumlSource = pumlSource,
                classCount = diagram.classifiers.size + diagram.packages.sumOf { it.classifiers.size },
                relationCount = diagram.relations.size
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate diagram" }
            DiagramResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Generate diagram image (PNG, SVG, etc.)
     */
    fun generateDiagramImage(
        pumlSource: String,
        format: ImageFormat = ImageFormat.SVG
    ): ByteArray? {
        return try {
            val reader = SourceStringReader(pumlSource)
            val outputStream = ByteArrayOutputStream()

            val fileFormat = when (format) {
                ImageFormat.PNG -> FileFormat.PNG
                ImageFormat.SVG -> FileFormat.SVG
                ImageFormat.EPS -> FileFormat.EPS
                ImageFormat.PDF -> FileFormat.PDF
                ImageFormat.ASCII -> FileFormat.ATXT
                ImageFormat.ASCII_UNICODE -> FileFormat.UTXT
            }

            reader.outputImage(outputStream, FileFormatOption(fileFormat))
            outputStream.toByteArray()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate diagram image" }
            null
        }
    }

    /**
     * Generate diagram from multiple source files
     */
    fun generateDiagramFromFiles(
        files: List<SourceFile>,
        options: DiagramOptions = DiagramOptions()
    ): DiagramResult {
        val builder = PumlClassDiagramBuilder()
        options.title?.let { builder.title(it) }
        options.direction?.let { builder.direction(it) }

        val allRelations = mutableListOf<PumlRelation>()

        files.forEach { file ->
            try {
                val converter = AntlrToPumlConverterRegistry.getConverter(file.languageId)
                    ?: return@forEach

                val diagram = converter.convert(file.source)

                // Collect classifiers by package
                diagram.packages.forEach { pkg ->
                    builder.addPackage(pkg)
                }
                diagram.classifiers.forEach { classifier ->
                    builder.addClassifier(classifier)
                }
                allRelations.addAll(diagram.relations)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to parse file: ${file.name}" }
            }
        }

        // Add all relations
        allRelations.forEach { builder.addRelation(it) }

        val diagram = builder.build()
        return DiagramResult(
            success = true,
            pumlSource = diagram.toPlantUml(),
            classCount = diagram.classifiers.size + diagram.packages.sumOf { it.classifiers.size },
            relationCount = diagram.relations.size
        )
    }

    /**
     * Generate diagram from directory
     */
    fun generateDiagramFromDirectory(
        directory: File,
        extensions: List<String> = listOf("java", "kt"),
        options: DiagramOptions = DiagramOptions()
    ): DiagramResult {
        if (!directory.exists() || !directory.isDirectory) {
            return DiagramResult(
                success = false,
                error = "Directory not found: ${directory.absolutePath}"
            )
        }

        val files = directory.walkTopDown()
            .filter { it.isFile && extensions.any { ext -> it.extension.equals(ext, ignoreCase = true) } }
            .map { file ->
                val languageId = when (file.extension.lowercase()) {
                    "java" -> "java"
                    "kt", "kts" -> "kotlin"
                    "ts" -> "typescript"
                    "js" -> "javascript"
                    else -> "java"
                }
                SourceFile(
                    name = file.name,
                    path = file.absolutePath,
                    source = file.readText(),
                    languageId = languageId
                )
            }
            .toList()

        return generateDiagramFromFiles(files, options)
    }

    /**
     * Apply options to diagram
     */
    private fun applyOptions(diagram: PumlClassDiagram, options: DiagramOptions): PumlClassDiagram {
        val builder = PumlClassDiagramBuilder()

        options.title?.let { builder.title(it) }
        options.direction?.let { builder.direction(it) }
        options.scale?.let { builder.scale(it) }

        // Apply skin params
        if (options.monochrome) {
            builder.skinParam("monochrome", "true")
        }
        if (options.shadowing) {
            builder.skinParam("shadowing", "true")
        }
        if (options.handwritten) {
            builder.skinParam("handwritten", "true")
        }

        // Hide options
        if (options.hideEmptyMembers) {
            builder.hide("empty members")
        }
        if (options.hideCircle) {
            builder.hide("circle")
        }
        if (options.hideMethods) {
            builder.hide("methods")
        }
        if (options.hideFields) {
            builder.hide("fields")
        }

        // Copy content
        diagram.packages.forEach { builder.addPackage(it) }
        diagram.classifiers.forEach { builder.addClassifier(it) }
        diagram.relations.forEach { builder.addRelation(it) }
        diagram.notes.forEach { builder.addNote(it) }

        return builder.build()
    }

    companion object {
        val INSTANCE = PlantUmlService()
    }
}

/**
 * Diagram generation options
 */
@Serializable
data class DiagramOptions(
    val title: String? = null,
    val direction: DiagramDirection? = DiagramDirection.TOP_TO_BOTTOM,
    val scale: Double? = null,
    val monochrome: Boolean = false,
    val shadowing: Boolean = false,
    val handwritten: Boolean = false,
    val hideEmptyMembers: Boolean = true,
    val hideCircle: Boolean = false,
    val hideMethods: Boolean = false,
    val hideFields: Boolean = false,
    val includePrivate: Boolean = true,
    val includeProtected: Boolean = true,
    val includePackagePrivate: Boolean = false
)

/**
 * Source file for multi-file diagram generation
 */
@Serializable
data class SourceFile(
    val name: String,
    val path: String? = null,
    val source: String,
    val languageId: String = "java"
)

/**
 * Diagram generation result
 */
@Serializable
data class DiagramResult(
    val success: Boolean,
    val pumlSource: String? = null,
    val error: String? = null,
    val classCount: Int = 0,
    val relationCount: Int = 0
)

/**
 * Image format for diagram export
 */
enum class ImageFormat(val contentType: String, val extension: String) {
    PNG("image/png", "png"),
    SVG("image/svg+xml", "svg"),
    EPS("application/postscript", "eps"),
    PDF("application/pdf", "pdf"),
    ASCII("text/plain", "txt"),
    ASCII_UNICODE("text/plain", "utxt")
}

/**
 * Request for diagram generation
 */
@Serializable
data class DiagramRequest(
    val source: String? = null,
    val files: List<SourceFile>? = null,
    val languageId: String = "java",
    val options: DiagramOptions = DiagramOptions(),
    val outputFormat: String = "puml"  // puml, png, svg
)

/**
 * Configure PlantUML routes
 */
fun Route.configurePlantUmlRoutes() {
    val service = PlantUmlService.INSTANCE

    route("/api/puml") {
        /**
         * Generate PlantUML from source code
         * POST /api/puml/generate
         */
        post("/generate") {
            val request = call.receive<DiagramRequest>()

            val result = if (request.files != null && request.files.isNotEmpty()) {
                service.generateDiagramFromFiles(request.files, request.options)
            } else if (request.source != null) {
                service.generateDiagramFromSource(request.source, request.languageId, request.options)
            } else {
                DiagramResult(success = false, error = "No source provided")
            }

            if (result.success) {
                when (request.outputFormat.lowercase()) {
                    "png" -> {
                        val image = service.generateDiagramImage(result.pumlSource!!, ImageFormat.PNG)
                        if (image != null) {
                            call.respondBytes(image, ContentType.Image.PNG)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to generate image"))
                        }
                    }
                    "svg" -> {
                        val image = service.generateDiagramImage(result.pumlSource!!, ImageFormat.SVG)
                        if (image != null) {
                            call.respondBytes(image, ContentType.Image.SVG)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to generate image"))
                        }
                    }
                    else -> {
                        call.respond(result)
                    }
                }
            } else {
                call.respond(HttpStatusCode.BadRequest, result)
            }
        }

        /**
         * Render PlantUML source to image
         * POST /api/puml/render
         */
        post("/render") {
            val pumlSource = call.receiveText()
            val format = call.request.queryParameters["format"]?.let {
                try { ImageFormat.valueOf(it.uppercase()) } catch (e: Exception) { ImageFormat.SVG }
            } ?: ImageFormat.SVG

            val image = service.generateDiagramImage(pumlSource, format)
            if (image != null) {
                call.respondBytes(image, ContentType.parse(format.contentType))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to render diagram"))
            }
        }

        /**
         * Get supported languages
         * GET /api/puml/languages
         */
        get("/languages") {
            call.respond(mapOf(
                "languages" to AntlrToPumlConverterRegistry.getSupportedLanguages()
            ))
        }

        /**
         * Parse source code and return structure
         * POST /api/puml/parse
         */
        post("/parse") {
            val request = call.receive<DiagramRequest>()

            if (request.source == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No source provided"))
                return@post
            }

            val converter = AntlrToPumlConverterRegistry.getConverter(request.languageId)
            if (converter == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unsupported language: ${request.languageId}"))
                return@post
            }

            try {
                val diagram = converter.convert(request.source)
                call.respond(mapOf(
                    "packages" to diagram.packages.map { pkg ->
                        mapOf(
                            "name" to pkg.name,
                            "classifiers" to pkg.classifiers.map { it.name }
                        )
                    },
                    "classifiers" to diagram.classifiers.map { classifier ->
                        mapOf(
                            "name" to classifier.name,
                            "type" to classifier.type.name,
                            "package" to classifier.packageName,
                            "fields" to classifier.fields.map { "${it.visibility.symbol}${it.name}: ${it.type}" },
                            "methods" to classifier.methods.map { "${it.visibility.symbol}${it.name}(${it.parameters.size}): ${it.returnType ?: "void"}" }
                        )
                    },
                    "relations" to diagram.relations.map { rel ->
                        mapOf(
                            "source" to rel.source,
                            "target" to rel.target,
                            "type" to rel.type.name
                        )
                    }
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
    }
}
