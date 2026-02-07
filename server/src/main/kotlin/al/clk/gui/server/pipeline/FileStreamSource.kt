package al.clk.gui.server.pipeline

import com.github.sardine.Sardine
import com.github.sardine.SardineFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import java.io.InputStream
import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private val logger = KotlinLogging.logger {}

/**
 * Unified file stream source abstraction using standard Java URL formats.
 *
 * ## Supported URI Formats (Java Standard)
 *
 * ### Local Files
 * - `file:/path/to/file.txt`
 * - `file:///absolute/path/to/file.txt`
 *
 * ### JAR Protocol (JarURLConnection standard)
 * - `jar:file:/path/to/archive.jar!/com/example/Class.class`
 * - `jar:http://server/lib.jar!/META-INF/MANIFEST.MF`
 * - `jar:https://webdav/repo/app.jar!/resources/config.xml`
 *
 * ### Nested JAR (JAR within JAR)
 * - `jar:jar:file:/outer.jar!/nested.jar!/path/inside`
 *
 * ### Java Runtime Modules (Java 9+)
 * - `jrt:/java.base/java/lang/Object.class`
 * - `jrt:/java.sql/java/sql/Connection.class`
 *
 * ### Classpath (Spring-compatible pseudo-protocol)
 * - `classpath:com/example/config.properties`
 * - `classpath:/META-INF/services/javax.xml.parsers.SAXParserFactory`
 *
 * ### WebDAV (via Sardine)
 * - `http://server/webdav/path/file.txt`
 * - `https://server/webdav/path/file.txt`
 *
 * @see java.net.JarURLConnection
 * @see java.lang.module.ModuleFinder
 */
sealed class FileStreamSource {
    abstract val uri: URI
    abstract val path: String
    abstract val filename: String
    abstract val size: Long?

    /**
     * Open input stream for reading content
     */
    abstract suspend fun openStream(): InputStream

    /**
     * Get metadata about the source
     */
    abstract suspend fun getMetadata(): SourceMetadata

    companion object {
        // Separator between JAR file URL and entry path (Java standard)
        const val JAR_SEPARATOR = "!/"

        // Regex to parse jar: URLs
        private val JAR_URL_PATTERN = Regex("""^jar:(.+)!/(.*)$""")

        // Regex to detect nested jar: URLs
        private val NESTED_JAR_PATTERN = Regex("""^jar:jar:(.+)!/(.+)!/(.*)$""")

        /**
         * Create appropriate FileStreamSource from URI string.
         *
         * Examples:
         * - `file:/path/to/file.txt`
         * - `jar:file:/lib/app.jar!/com/example/Main.class`
         * - `jar:jar:file:/boot.jar!/BOOT-INF/lib/dep.jar!/META-INF/MANIFEST.MF`
         * - `jrt:/java.base/java/lang/String.class`
         * - `classpath:application.properties`
         * - `https://webdav.server/files/doc.pdf`
         */
        fun fromUri(uriString: String): FileStreamSource {
            val trimmed = uriString.trim()

            return when {
                // Nested JAR: jar:jar:file:/outer.jar!/inner.jar!/path
                trimmed.startsWith("jar:jar:") -> NestedJarSource.parse(trimmed)

                // Standard JAR: jar:file:/archive.jar!/entry
                trimmed.startsWith("jar:") -> JarEntrySource.parse(trimmed)

                // Java Runtime Module: jrt:/module/path
                trimmed.startsWith("jrt:") -> JrtModuleSource.parse(trimmed)

                // Classpath pseudo-protocol: classpath:path/to/resource
                trimmed.startsWith("classpath:") -> ClasspathSource.parse(trimmed)

                // HTTP/HTTPS - could be WebDAV or remote JAR
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                    val uri = URI(trimmed)
                    WebDavSource(uri)
                }

                // Local file
                else -> {
                    val uri = if (trimmed.startsWith("file:")) {
                        URI(trimmed)
                    } else {
                        // Treat as local path
                        Path.of(trimmed).toUri()
                    }
                    LocalFileSource(uri)
                }
            }
        }

        /**
         * Construct a JAR URL from archive path and entry path.
         *
         * @param archiveUrl URL to the JAR file (file: or http:)
         * @param entryPath Path within the JAR
         * @return Properly formatted jar: URL string
         *
         * Example:
         * ```
         * buildJarUrl("file:/libs/app.jar", "com/example/Main.class")
         * // Returns: "jar:file:/libs/app.jar!/com/example/Main.class"
         * ```
         */
        fun buildJarUrl(archiveUrl: String, entryPath: String): String {
            val normalizedEntry = entryPath.removePrefix("/")
            return "jar:$archiveUrl$JAR_SEPARATOR$normalizedEntry"
        }

        /**
         * Construct a nested JAR URL.
         *
         * @param outerJarUrl URL to the outer JAR
         * @param innerJarPath Path to the inner JAR within the outer
         * @param entryPath Path within the inner JAR
         * @return Properly formatted nested jar: URL string
         *
         * Example:
         * ```
         * buildNestedJarUrl("file:/boot.jar", "BOOT-INF/lib/dep.jar", "META-INF/MANIFEST.MF")
         * // Returns: "jar:jar:file:/boot.jar!/BOOT-INF/lib/dep.jar!/META-INF/MANIFEST.MF"
         * ```
         */
        fun buildNestedJarUrl(outerJarUrl: String, innerJarPath: String, entryPath: String): String {
            return "jar:jar:$outerJarUrl$JAR_SEPARATOR$innerJarPath$JAR_SEPARATOR$entryPath"
        }
    }
}

/**
 * Source metadata
 */
@Serializable
data class SourceMetadata(
    val uri: String,
    val filename: String,
    val size: Long?,
    val lastModified: Long?,
    val contentType: String?,
    val etag: String?,
    val sourceType: SourceType,
    val archivePath: String? = null,
    val entryPath: String? = null,
    val moduleName: String? = null,      // For jrt: URIs
    val classLoaderName: String? = null  // For classpath: URIs
)

@Serializable
enum class SourceType {
    LOCAL_FILE,      // file:
    WEBDAV,          // http:/https: without JAR
    JAR_ENTRY,       // jar:file: or jar:http:
    NESTED_JAR,      // jar:jar:
    JRT_MODULE,      // jrt:/module
    CLASSPATH,       // classpath:
    TAR_ENTRY,       // tar: (future)
    WEBDAV_ARCHIVE   // WebDAV JAR entries
}

/**
 * Local filesystem source (file: protocol)
 */
class LocalFileSource(override val uri: URI) : FileStreamSource() {
    private val filePath: Path = Path.of(uri.path ?: uri.schemeSpecificPart)

    override val path: String = filePath.toString()
    override val filename: String = filePath.fileName?.toString() ?: ""
    override val size: Long? = try { Files.size(filePath) } catch (e: Exception) { null }

    override suspend fun openStream(): InputStream = withContext(Dispatchers.IO) {
        Files.newInputStream(filePath)
    }

    override suspend fun getMetadata(): SourceMetadata = withContext(Dispatchers.IO) {
        SourceMetadata(
            uri = uri.toString(),
            filename = filename,
            size = size,
            lastModified = try { Files.getLastModifiedTime(filePath).toMillis() } catch (e: Exception) { null },
            contentType = try { Files.probeContentType(filePath) } catch (e: Exception) { null },
            etag = null,
            sourceType = SourceType.LOCAL_FILE
        )
    }
}

/**
 * JAR entry source using standard Java jar: protocol.
 *
 * Format: `jar:<url>!/<entry>`
 *
 * Examples:
 * - `jar:file:/path/to/lib.jar!/com/example/Class.class`
 * - `jar:http://server/repo/lib.jar!/META-INF/MANIFEST.MF`
 *
 * Uses [java.net.JarURLConnection] for proper JAR handling.
 */
class JarEntrySource private constructor(
    override val uri: URI,
    private val jarFileUrl: String,
    private val entryPath: String
) : FileStreamSource() {

    override val path: String = entryPath
    override val filename: String = entryPath.substringAfterLast('/')
    override val size: Long? = null

    override suspend fun openStream(): InputStream = withContext(Dispatchers.IO) {
        val url = URL(uri.toString())
        val connection = url.openConnection() as JarURLConnection
        connection.jarEntry?.let { entry ->
            connection.jarFile.getInputStream(entry)
        } ?: throw IllegalStateException("JAR entry not found: $entryPath")
    }

    override suspend fun getMetadata(): SourceMetadata = withContext(Dispatchers.IO) {
        try {
            val url = URL(uri.toString())
            val connection = url.openConnection() as JarURLConnection
            val entry = connection.jarEntry

            SourceMetadata(
                uri = uri.toString(),
                filename = filename,
                size = entry?.size,
                lastModified = entry?.time,
                contentType = null,
                etag = null,
                sourceType = SourceType.JAR_ENTRY,
                archivePath = jarFileUrl,
                entryPath = entryPath
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get JAR metadata for $uri" }
            SourceMetadata(
                uri = uri.toString(),
                filename = filename,
                size = null,
                lastModified = null,
                contentType = null,
                etag = null,
                sourceType = SourceType.JAR_ENTRY,
                archivePath = jarFileUrl,
                entryPath = entryPath
            )
        }
    }

    companion object {
        /**
         * Parse a jar: URL string into JarEntrySource.
         *
         * @throws IllegalArgumentException if the URL format is invalid
         */
        fun parse(urlString: String): JarEntrySource {
            require(urlString.startsWith("jar:")) { "Not a jar: URL: $urlString" }

            val withoutPrefix = urlString.removePrefix("jar:")
            val separatorIndex = withoutPrefix.indexOf("!/")
            require(separatorIndex > 0) { "Invalid jar: URL format (missing !/): $urlString" }

            val jarFileUrl = withoutPrefix.substring(0, separatorIndex)
            val entryPath = withoutPrefix.substring(separatorIndex + 2)

            return JarEntrySource(
                uri = URI(urlString),
                jarFileUrl = jarFileUrl,
                entryPath = entryPath
            )
        }
    }
}

/**
 * Nested JAR source for accessing entries within JARs inside other JARs.
 *
 * Format: `jar:jar:<outer-url>!/<inner-jar-path>!/<entry-path>`
 *
 * Example:
 * - `jar:jar:file:/boot.jar!/BOOT-INF/lib/dependency.jar!/com/example/Class.class`
 *
 * Common use cases:
 * - Spring Boot fat JARs (BOOT-INF/lib/)
 * - WAR files with embedded JARs (WEB-INF/lib/)
 * - OSGi bundles
 */
class NestedJarSource private constructor(
    override val uri: URI,
    private val outerJarUrl: String,
    private val innerJarPath: String,
    private val entryPath: String
) : FileStreamSource() {

    override val path: String = entryPath
    override val filename: String = entryPath.substringAfterLast('/')
    override val size: Long? = null

    override suspend fun openStream(): InputStream = withContext(Dispatchers.IO) {
        // Open outer JAR
        val outerUrl = URL(outerJarUrl)
        val outerJarFile = when {
            outerJarUrl.startsWith("file:") -> {
                JarFile(Path.of(URI(outerJarUrl)).toFile())
            }
            else -> {
                // Download to temp file for remote JARs
                val tempFile = Files.createTempFile("nested-jar-", ".jar")
                outerUrl.openStream().use { input ->
                    Files.copy(input, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
                JarFile(tempFile.toFile())
            }
        }

        // Get inner JAR entry
        val innerEntry = outerJarFile.getEntry(innerJarPath)
            ?: throw IllegalArgumentException("Inner JAR not found: $innerJarPath in $outerJarUrl")

        // Create temp file for inner JAR
        val innerTempFile = Files.createTempFile("inner-jar-", ".jar")
        outerJarFile.getInputStream(innerEntry).use { input ->
            Files.copy(input, innerTempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }

        // Open inner JAR and get entry
        val innerJarFile = JarFile(innerTempFile.toFile())
        val targetEntry = innerJarFile.getEntry(entryPath)
            ?: throw IllegalArgumentException("Entry not found: $entryPath in $innerJarPath")

        innerJarFile.getInputStream(targetEntry)
    }

    override suspend fun getMetadata(): SourceMetadata = withContext(Dispatchers.IO) {
        SourceMetadata(
            uri = uri.toString(),
            filename = filename,
            size = null,
            lastModified = null,
            contentType = null,
            etag = null,
            sourceType = SourceType.NESTED_JAR,
            archivePath = "$outerJarUrl!/$innerJarPath",
            entryPath = entryPath
        )
    }

    companion object {
        /**
         * Parse a nested jar: URL string.
         */
        fun parse(urlString: String): NestedJarSource {
            require(urlString.startsWith("jar:jar:")) { "Not a nested jar: URL: $urlString" }

            val withoutPrefix = urlString.removePrefix("jar:jar:")
            val parts = withoutPrefix.split("!/")
            require(parts.size >= 3) { "Invalid nested jar: URL format: $urlString" }

            return NestedJarSource(
                uri = URI(urlString),
                outerJarUrl = parts[0],
                innerJarPath = parts[1],
                entryPath = parts.drop(2).joinToString("!/")
            )
        }
    }
}

/**
 * Java Runtime Module source (jrt: protocol, Java 9+).
 *
 * Format: `jrt:/<module-name>/<path>`
 *
 * Examples:
 * - `jrt:/java.base/java/lang/Object.class`
 * - `jrt:/java.sql/java/sql/Connection.class`
 *
 * @see java.lang.module.ModuleFinder
 */
class JrtModuleSource private constructor(
    override val uri: URI,
    private val moduleName: String,
    private val resourcePath: String
) : FileStreamSource() {

    override val path: String = resourcePath
    override val filename: String = resourcePath.substringAfterLast('/')
    override val size: Long? = null

    override suspend fun openStream(): InputStream = withContext(Dispatchers.IO) {
        // Use the jrt: FileSystem
        val jrtPath = java.nio.file.FileSystems.getFileSystem(URI.create("jrt:/"))
            .getPath("modules", moduleName, resourcePath)
        Files.newInputStream(jrtPath)
    }

    override suspend fun getMetadata(): SourceMetadata = withContext(Dispatchers.IO) {
        val jrtPath = java.nio.file.FileSystems.getFileSystem(URI.create("jrt:/"))
            .getPath("modules", moduleName, resourcePath)

        SourceMetadata(
            uri = uri.toString(),
            filename = filename,
            size = try { Files.size(jrtPath) } catch (e: Exception) { null },
            lastModified = try { Files.getLastModifiedTime(jrtPath).toMillis() } catch (e: Exception) { null },
            contentType = null,
            etag = null,
            sourceType = SourceType.JRT_MODULE,
            moduleName = moduleName
        )
    }

    companion object {
        fun parse(urlString: String): JrtModuleSource {
            require(urlString.startsWith("jrt:/")) { "Not a jrt: URL: $urlString" }

            val path = urlString.removePrefix("jrt:/")
            val parts = path.split("/", limit = 2)
            val moduleName = parts[0]
            val resourcePath = parts.getOrElse(1) { "" }

            return JrtModuleSource(
                uri = URI(urlString),
                moduleName = moduleName,
                resourcePath = resourcePath
            )
        }
    }
}

/**
 * Classpath resource source (Spring-compatible pseudo-protocol).
 *
 * Format: `classpath:<path>`
 *
 * Examples:
 * - `classpath:application.properties`
 * - `classpath:/META-INF/spring.factories`
 * - `classpath:com/example/config.xml`
 *
 * Note: This is a pseudo-protocol, not part of standard Java.
 * It's compatible with Spring's ResourceLoader.
 */
class ClasspathSource private constructor(
    override val uri: URI,
    private val resourcePath: String,
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader
        ?: ClasspathSource::class.java.classLoader
) : FileStreamSource() {

    override val path: String = resourcePath
    override val filename: String = resourcePath.substringAfterLast('/')
    override val size: Long? = null

    override suspend fun openStream(): InputStream = withContext(Dispatchers.IO) {
        classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Classpath resource not found: $resourcePath")
    }

    override suspend fun getMetadata(): SourceMetadata = withContext(Dispatchers.IO) {
        val resource = classLoader.getResource(resourcePath)

        SourceMetadata(
            uri = uri.toString(),
            filename = filename,
            size = null,
            lastModified = null,
            contentType = null,
            etag = null,
            sourceType = SourceType.CLASSPATH,
            classLoaderName = classLoader.name ?: classLoader.javaClass.simpleName
        )
    }

    companion object {
        fun parse(urlString: String): ClasspathSource {
            require(urlString.startsWith("classpath:")) { "Not a classpath: URL: $urlString" }

            val path = urlString.removePrefix("classpath:").removePrefix("/")

            return ClasspathSource(
                uri = URI(urlString),
                resourcePath = path
            )
        }
    }
}

/**
 * WebDAV remote source using Sardine.
 *
 * For accessing files on WebDAV servers (http/https without jar: prefix).
 */
class WebDavSource(
    override val uri: URI,
    private val username: String? = null,
    private val password: String? = null
) : FileStreamSource() {

    override val path: String = uri.path ?: ""
    override val filename: String = path.substringAfterLast('/')
    override val size: Long? = null

    private fun createSardine(): Sardine {
        return if (username != null && password != null) {
            SardineFactory.begin(username, password)
        } else {
            SardineFactory.begin()
        }
    }

    override suspend fun openStream(): InputStream = withContext(Dispatchers.IO) {
        val sardine = createSardine()
        sardine.get(uri.toString())
    }

    override suspend fun getMetadata(): SourceMetadata = withContext(Dispatchers.IO) {
        val sardine = createSardine()
        val resources = sardine.list(uri.toString(), 0)
        val resource = resources.firstOrNull()

        SourceMetadata(
            uri = uri.toString(),
            filename = filename,
            size = resource?.contentLength,
            lastModified = resource?.modified?.time,
            contentType = resource?.contentType,
            etag = resource?.etag,
            sourceType = SourceType.WEBDAV
        )
    }
}

/**
 * URL Format Examples and Usage Guide
 *
 * ```kotlin
 * // Local files
 * FileStreamSource.fromUri("file:/home/user/file.txt")
 * FileStreamSource.fromUri("/home/user/file.txt")  // Implicit file:
 *
 * // JAR entries (standard Java format)
 * FileStreamSource.fromUri("jar:file:/libs/app.jar!/com/example/Main.class")
 * FileStreamSource.fromUri("jar:file:/libs/app.jar!/META-INF/MANIFEST.MF")
 *
 * // Remote JAR entries (via HTTP)
 * FileStreamSource.fromUri("jar:https://repo.maven.apache.org/maven2/org/example/lib/1.0/lib-1.0.jar!/META-INF/MANIFEST.MF")
 *
 * // Nested JARs (Spring Boot fat JARs)
 * FileStreamSource.fromUri("jar:jar:file:/app.jar!/BOOT-INF/lib/dependency.jar!/com/dep/Class.class")
 *
 * // Java runtime modules (Java 9+)
 * FileStreamSource.fromUri("jrt:/java.base/java/lang/Object.class")
 *
 * // Classpath resources
 * FileStreamSource.fromUri("classpath:application.properties")
 * FileStreamSource.fromUri("classpath:META-INF/services/javax.xml.parsers.SAXParserFactory")
 *
 * // WebDAV remote files
 * FileStreamSource.fromUri("https://webdav.server/files/document.pdf")
 *
 * // Build URLs programmatically
 * val jarUrl = FileStreamSource.buildJarUrl("file:/libs/app.jar", "com/example/Main.class")
 * // Result: "jar:file:/libs/app.jar!/com/example/Main.class"
 *
 * val nestedUrl = FileStreamSource.buildNestedJarUrl("file:/boot.jar", "BOOT-INF/lib/dep.jar", "META-INF/MANIFEST.MF")
 * // Result: "jar:jar:file:/boot.jar!/BOOT-INF/lib/dep.jar!/META-INF/MANIFEST.MF"
 * ```
 */
object FileStreamSourceExamples
