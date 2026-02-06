package org.jd.gui.server.treenode

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.jd.gui.server.handlers.FileContent
import org.jd.gui.server.handlers.FileExtensionHandler
import org.jd.gui.server.handlers.ProcessingResult
import org.jd.gui.server.pipeline.FileStreamSource
import java.util.regex.Pattern

private val logger = KotlinLogging.logger {}

/**
 * Container type for archive entries
 * Matches Java legacy: jar, war, ear, jmod, zip, etc.
 */
enum class ContainerType(val extension: String) {
    JAR("jar"),
    WAR("war"),
    EAR("ear"),
    JMOD("jmod"),
    ZIP("zip"),
    TAR("tar"),
    DIRECTORY("dir"),  // Filesystem directory
    ANY("*");          // Wildcard

    companion object {
        fun fromExtension(ext: String): ContainerType {
            return values().find { it.extension.equals(ext, ignoreCase = true) } ?: ANY
        }

        fun fromPath(path: String): ContainerType {
            return when {
                path.endsWith(".jar", ignoreCase = true) -> JAR
                path.endsWith(".war", ignoreCase = true) -> WAR
                path.endsWith(".ear", ignoreCase = true) -> EAR
                path.endsWith(".jmod", ignoreCase = true) -> JMOD
                path.endsWith(".zip", ignoreCase = true) -> ZIP
                path.endsWith(".tar", ignoreCase = true) ||
                path.endsWith(".tar.gz", ignoreCase = true) ||
                path.endsWith(".tgz", ignoreCase = true) -> TAR
                else -> DIRECTORY
            }
        }
    }
}

/**
 * Entry type: file or directory
 */
enum class EntryType(val selector: String) {
    FILE("file"),
    DIRECTORY("dir");

    companion object {
        fun from(isDirectory: Boolean) = if (isDirectory) DIRECTORY else FILE
    }
}

/**
 * Container entry representing a file/directory within an archive
 * Mirrors Java legacy Container.Entry interface
 */
@Serializable
data class ContainerEntry(
    val path: String,                    // Full path within container
    val name: String,                    // Just the filename
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long? = null,
    val containerType: String = "*",      // jar, war, ear, etc.
    val containerPath: String? = null     // Path to parent container
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "")

    /**
     * Generate selector key for matching
     * Format: {containerType}:{fileOrDir}:{pathPattern}
     */
    fun toSelector(): String = "$containerType:${if (isDirectory) "dir" else "file"}:$path"

    /**
     * Generate extension-based selector
     */
    fun toExtensionSelector(): String = "$containerType:${if (isDirectory) "dir" else "file"}:*.$extension"
}

/**
 * Result of TreeNodeFactory processing
 */
@Serializable
data class TreeNodeResult(
    val entry: ContainerEntry,
    val factoryId: String,
    val displayName: String,
    val iconName: String? = null,
    val tooltip: String? = null,
    val data: Map<String, String> = emptyMap(),
    val xhtmlContent: String? = null,
    val children: List<TreeNodeResult>? = null  // For directories
)

/**
 * TreeNodeFactory interface - Kotlin adaptation of Java legacy SPI
 *
 * Selector format (matching Java legacy):
 *   {containerType}:{fileOrDir}:{pathPattern}
 *
 * Examples:
 *   - jar:file:*.class       -> Class files in JAR
 *   - jar:dir:META-INF       -> META-INF directory in JAR
 *   - *:file:*.java          -> Java files in any container
 *   - war:dir:WEB-INF        -> WEB-INF directory in WAR
 *   - ear:file:*.xml         -> XML files in EAR
 *   - *:file:META-INF/MANIFEST.MF  -> Manifest in any container
 *
 * Wildcards:
 *   - * as containerType matches any container
 *   - *.ext matches file extension
 *   - path/* matches directory prefix
 */
interface TreeNodeFactory {
    /**
     * Unique factory identifier
     */
    val factoryId: String

    /**
     * Human-readable display name
     */
    val displayName: String

    /**
     * Selectors for matching container entries
     * Format: {containerType}:{fileOrDir}:{pathPattern}
     */
    fun getSelectors(): Array<String>

    /**
     * Optional regex pattern for fine-grained path matching
     * Used when multiple factories match the same selector
     */
    fun getPathPattern(): Pattern? = null

    /**
     * Priority for when multiple factories match
     * Higher = preferred
     */
    val priority: Int get() = 0

    /**
     * Process the entry and create result
     */
    suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult

    /**
     * Check if this factory can handle the entry
     */
    fun accepts(entry: ContainerEntry): Boolean {
        return getSelectors().any { selector ->
            matchesSelector(selector, entry)
        }
    }

    /**
     * Match a single selector against an entry
     */
    fun matchesSelector(selector: String, entry: ContainerEntry): Boolean {
        val parts = selector.split(":", limit = 3)
        if (parts.size != 3) return false

        val (containerPattern, typePattern, pathPattern) = parts

        // Container type match
        if (containerPattern != "*" && !containerPattern.equals(entry.containerType, ignoreCase = true)) {
            return false
        }

        // File/directory match
        val isDir = entry.isDirectory
        if (typePattern == "file" && isDir) return false
        if (typePattern == "dir" && !isDir) return false

        // Path pattern match
        return matchesPathPattern(pathPattern, entry.path, entry.name)
    }

    private fun matchesPathPattern(pattern: String, path: String, name: String): Boolean {
        return when {
            // Exact path match
            pattern == path -> true

            // Wildcard extension: *.ext
            pattern.startsWith("*.") -> {
                val ext = pattern.substring(2)
                name.endsWith(".$ext", ignoreCase = true)
            }

            // Directory prefix: path/*
            pattern.endsWith("/*") -> {
                val prefix = pattern.dropLast(2)
                path.startsWith("$prefix/") || path == prefix
            }

            // Wildcard filename in any directory: */name
            pattern.startsWith("*/") -> {
                val targetName = pattern.substring(2)
                name == targetName
            }

            // Any match: *
            pattern == "*" -> true

            // Glob pattern match (simple)
            pattern.contains("*") -> {
                val regex = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
                path.matches(Regex(regex))
            }

            else -> false
        }
    }
}

/**
 * Abstract base implementation with common functionality
 */
abstract class AbstractTreeNodeFactory : TreeNodeFactory {

    protected open val externalSelectors: List<String>? = null
    protected open val externalPathPattern: Pattern? = null

    override fun getPathPattern(): Pattern? = externalPathPattern

    protected fun appendSelectors(vararg selectors: String): Array<String> {
        return if (externalSelectors != null) {
            (externalSelectors!! + selectors.toList()).toTypedArray()
        } else {
            selectors.toList().toTypedArray()
        }
    }

    protected fun buildTooltip(entry: ContainerEntry, extraInfo: Map<String, String> = emptyMap()): String {
        return buildString {
            append("Path: ${entry.path}")
            if (entry.containerPath != null) {
                append("\nContainer: ${entry.containerPath}")
            }
            if (entry.size > 0) {
                append("\nSize: ${formatSize(entry.size)}")
            }
            extraInfo.forEach { (key, value) ->
                append("\n$key: $value")
            }
        }
    }

    protected fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}

/**
 * Factory that adapts a FileExtensionHandler to TreeNodeFactory
 */
class HandlerAdapterFactory(
    private val handler: FileExtensionHandler,
    private val containerTypes: List<String> = listOf("*")
) : AbstractTreeNodeFactory() {

    override val factoryId: String = "adapter-${handler.handlerId}"
    override val displayName: String = handler.displayName
    override val priority: Int = handler.priority

    override fun getSelectors(): Array<String> {
        return containerTypes.flatMap { container ->
            handler.supportedExtensions.map { ext ->
                "$container:file:*.$ext"
            }
        }.toTypedArray()
    }

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        val fileContent = content?.let {
            FileContent(
                name = entry.name,
                extension = entry.extension,
                content = it,
                size = entry.size
            )
        }

        val processingResult = if (fileContent != null) {
            try {
                handler.process(fileContent)
            } catch (e: Exception) {
                logger.error(e) { "Handler ${handler.handlerId} failed for ${entry.path}" }
                ProcessingResult(false, handler.primaryMimeType, error = e.message)
            }
        } else {
            ProcessingResult(false, handler.primaryMimeType, error = "No content")
        }

        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "${handler.category.name.lowercase()}_icon",
            tooltip = buildTooltip(entry, mapOf("Handler" to handler.displayName)),
            data = processingResult.metadata + mapOf(
                "mimeType" to processingResult.contentType,
                "handlerId" to handler.handlerId
            ),
            xhtmlContent = processingResult.data
        )
    }
}
