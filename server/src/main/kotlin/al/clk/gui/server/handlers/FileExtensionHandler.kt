package al.clk.gui.server.handlers

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import al.clk.gui.server.di.MimeCategory
import java.io.InputStream

private val logger = KotlinLogging.logger {}

/**
 * Result of processing a file
 */
@Serializable
data class ProcessingResult(
    val success: Boolean,
    val contentType: String,
    val data: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val error: String? = null
)

/**
 * File content with metadata
 */
data class FileContent(
    val name: String,
    val extension: String,
    val content: ByteArray,
    val size: Long = content.size.toLong(),
    val metadata: Map<String, String> = emptyMap()
) {
    fun asString(): String = content.decodeToString()
    fun asInputStream(): InputStream = content.inputStream()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileContent) return false
        return name == other.name && extension == other.extension && content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + extension.hashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}

/**
 * Base interface for all file extension handlers
 */
interface FileExtensionHandler {
    /** Unique handler identifier */
    val handlerId: String

    /** Human-readable name */
    val displayName: String

    /** File extensions this handler supports (without dot) */
    val supportedExtensions: Set<String>

    /** MIME types this handler produces */
    val mimeTypes: Set<String>

    /** Primary MIME type */
    val primaryMimeType: String
        get() = mimeTypes.firstOrNull() ?: "application/octet-stream"

    /** Category of files this handler processes */
    val category: MimeCategory

    /** Priority (higher = preferred when multiple handlers match) */
    val priority: Int
        get() = 0

    /** Check if this handler supports the given extension */
    fun supports(extension: String): Boolean =
        supportedExtensions.any { it.equals(extension, ignoreCase = true) }

    /** Process file content */
    suspend fun process(content: FileContent): ProcessingResult

    /** Validate file content without full processing */
    suspend fun validate(content: FileContent): Boolean = true

    /** Get metadata about the file */
    suspend fun extractMetadata(content: FileContent): Map<String, String> = emptyMap()
}

/**
 * Handler for source code files with syntax highlighting
 */
interface SourceCodeHandler : FileExtensionHandler {
    /** Language identifier for syntax highlighting */
    val languageId: String

    /** Syntax style for editors */
    val syntaxStyle: String

    /** ANTLR grammar name if available */
    val antlrGrammar: String?
        get() = null

    /** Extract declarations (classes, functions, etc.) */
    suspend fun extractDeclarations(content: FileContent): List<Declaration>

    /** Extract references to other symbols */
    suspend fun extractReferences(content: FileContent): List<Reference>
}

/**
 * Handler for binary/archive files
 */
interface ArchiveHandler : FileExtensionHandler {
    /** List entries in the archive */
    suspend fun listEntries(content: FileContent): List<ArchiveEntry>

    /** Extract a specific entry */
    suspend fun extractEntry(content: FileContent, entryPath: String): ByteArray?

    /** Check if archive contains an entry */
    suspend fun containsEntry(content: FileContent, entryPath: String): Boolean
}

/**
 * Handler for image files
 */
interface ImageHandler : FileExtensionHandler {
    /** Get image dimensions */
    suspend fun getDimensions(content: FileContent): ImageDimensions?

    /** Convert to another format */
    suspend fun convert(content: FileContent, targetFormat: String): ByteArray?

    /** Generate thumbnail */
    suspend fun thumbnail(content: FileContent, maxWidth: Int, maxHeight: Int): ByteArray?
}

/**
 * Handler for structured data files (JSON, XML, YAML, etc.)
 */
interface DataFormatHandler : FileExtensionHandler {
    /** Parse to generic map structure */
    suspend fun parse(content: FileContent): Any?

    /** Validate against schema if available */
    suspend fun validateSchema(content: FileContent, schema: String?): ValidationResult

    /** Format/prettify the content */
    suspend fun format(content: FileContent, indent: Int = 2): String
}

/**
 * Handler for diagram files (PlantUML, Mermaid, etc.)
 */
interface DiagramHandler : FileExtensionHandler {
    /** Supported output formats */
    val outputFormats: Set<String>

    /** Render diagram to specified format */
    suspend fun render(content: FileContent, format: String): ByteArray?

    /** Get diagram source (may transform) */
    suspend fun getSource(content: FileContent): String
}

/**
 * Handler for contact/calendar files (vCard, iCalendar)
 */
interface ContactCalendarHandler : FileExtensionHandler {
    /** RFC specification this handler implements */
    val rfcSpec: String

    /** Parse to structured data */
    suspend fun parseToMap(content: FileContent): Map<String, Any?>

    /** Serialize from map to file format */
    suspend fun serializeFromMap(data: Map<String, Any?>): String
}

// Data classes for handler results

data class Declaration(
    val name: String,
    val kind: DeclarationKind,
    val startLine: Int,
    val endLine: Int,
    val signature: String? = null,
    val modifiers: Set<String> = emptySet()
)

enum class DeclarationKind {
    CLASS, INTERFACE, ENUM, FUNCTION, METHOD, PROPERTY, FIELD, CONSTANT, TYPE_ALIAS, MODULE
}

data class Reference(
    val name: String,
    val kind: ReferenceKind,
    val line: Int,
    val column: Int,
    val targetType: String? = null
)

enum class ReferenceKind {
    TYPE, METHOD, FIELD, VARIABLE, IMPORT, PACKAGE
}

data class ArchiveEntry(
    val path: String,
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val lastModified: Long? = null
)

data class ImageDimensions(
    val width: Int,
    val height: Int,
    val colorDepth: Int? = null,
    val hasAlpha: Boolean = false
)

data class ValidationResult(
    val valid: Boolean,
    val errors: List<ValidationError> = emptyList()
)

data class ValidationError(
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val path: String? = null
)
