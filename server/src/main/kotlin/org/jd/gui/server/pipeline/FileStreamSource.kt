package org.jd.gui.server.pipeline

import com.github.sardine.Sardine
import com.github.sardine.SardineFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private val logger = KotlinLogging.logger {}

/**
 * Unified file stream source abstraction for:
 * - Local filesystem (file://)
 * - WebDAV (http://, https://)
 * - Archive entries (zip://, jar://, tar://)
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
        /**
         * Create appropriate FileStreamSource from URI
         */
        fun fromUri(uriString: String): FileStreamSource {
            val uri = URI(uriString)
            return when (uri.scheme?.lowercase()) {
                "file" -> LocalFileSource(uri)
                "http", "https" -> {
                    if (uri.path.contains("!")) {
                        // Archive within WebDAV: https://server/path/archive.zip!/entry/path
                        WebDavArchiveSource(uri)
                    } else {
                        WebDavSource(uri)
                    }
                }
                "zip", "jar" -> ArchiveEntrySource(uri)
                "tar", "tgz", "gz" -> CompressedArchiveSource(uri)
                else -> LocalFileSource(uri)
            }
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
    val entryPath: String? = null
)

@Serializable
enum class SourceType {
    LOCAL_FILE,
    WEBDAV,
    ZIP_ENTRY,
    JAR_ENTRY,
    TAR_ENTRY,
    WEBDAV_ARCHIVE
}

/**
 * Local filesystem source
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
 * WebDAV remote source using Sardine
 */
class WebDavSource(
    override val uri: URI,
    private val username: String? = null,
    private val password: String? = null
) : FileStreamSource() {

    override val path: String = uri.path ?: ""
    override val filename: String = path.substringAfterLast('/')
    override val size: Long? = null // Retrieved from metadata

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
 * Archive entry source (zip://, jar://)
 * Format: zip:///path/to/archive.zip!/entry/path/file.txt
 */
class ArchiveEntrySource(override val uri: URI) : FileStreamSource() {
    private val archivePath: String
    private val entryPath: String

    init {
        val fullPath = uri.schemeSpecificPart
        val parts = fullPath.split("!/", limit = 2)
        archivePath = parts[0].removePrefix("//")
        entryPath = parts.getOrElse(1) { "" }
    }

    override val path: String = entryPath
    override val filename: String = entryPath.substringAfterLast('/')
    override val size: Long? = null

    override suspend fun openStream(): InputStream = withContext(Dispatchers.IO) {
        val zipFile = ZipFile(archivePath)
        val entry = zipFile.getEntry(entryPath)
            ?: throw IllegalArgumentException("Entry not found: $entryPath in $archivePath")
        zipFile.getInputStream(entry)
    }

    override suspend fun getMetadata(): SourceMetadata = withContext(Dispatchers.IO) {
        val zipFile = ZipFile(archivePath)
        val entry = zipFile.getEntry(entryPath)
        zipFile.close()

        SourceMetadata(
            uri = uri.toString(),
            filename = filename,
            size = entry?.size,
            lastModified = entry?.time,
            contentType = null,
            etag = null,
            sourceType = if (uri.scheme == "jar") SourceType.JAR_ENTRY else SourceType.ZIP_ENTRY,
            archivePath = archivePath,
            entryPath = entryPath
        )
    }
}

/**
 * Compressed archive source (tar, tgz, gz)
 */
class CompressedArchiveSource(override val uri: URI) : FileStreamSource() {
    private val archivePath: String
    private val entryPath: String

    init {
        val fullPath = uri.schemeSpecificPart
        val parts = fullPath.split("!/", limit = 2)
        archivePath = parts[0].removePrefix("//")
        entryPath = parts.getOrElse(1) { "" }
    }

    override val path: String = entryPath
    override val filename: String = entryPath.substringAfterLast('/')
    override val size: Long? = null

    override suspend fun openStream(): InputStream = withContext(Dispatchers.IO) {
        // For now, delegate to archive commons or similar
        // This is a placeholder - full implementation would use Apache Commons Compress
        throw UnsupportedOperationException("TAR/GZ support requires Apache Commons Compress")
    }

    override suspend fun getMetadata(): SourceMetadata = withContext(Dispatchers.IO) {
        SourceMetadata(
            uri = uri.toString(),
            filename = filename,
            size = null,
            lastModified = null,
            contentType = null,
            etag = null,
            sourceType = SourceType.TAR_ENTRY,
            archivePath = archivePath,
            entryPath = entryPath
        )
    }
}

/**
 * WebDAV archive source - archive file on WebDAV server
 * Format: https://server/path/archive.zip!/entry/path/file.txt
 */
class WebDavArchiveSource(
    override val uri: URI,
    private val username: String? = null,
    private val password: String? = null
) : FileStreamSource() {

    private val webdavUrl: String
    private val entryPath: String

    init {
        val fullPath = "${uri.scheme}://${uri.host}${uri.path}"
        val parts = fullPath.split("!/", limit = 2)
        webdavUrl = parts[0]
        entryPath = parts.getOrElse(1) { "" }
    }

    override val path: String = entryPath
    override val filename: String = entryPath.substringAfterLast('/')
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
        val archiveStream = sardine.get(webdavUrl)

        // Stream through ZIP to find entry
        val zipStream = ZipInputStream(archiveStream)
        var entry = zipStream.nextEntry
        while (entry != null) {
            if (entry.name == entryPath) {
                return@withContext zipStream
            }
            entry = zipStream.nextEntry
        }
        throw IllegalArgumentException("Entry not found: $entryPath in $webdavUrl")
    }

    override suspend fun getMetadata(): SourceMetadata = withContext(Dispatchers.IO) {
        SourceMetadata(
            uri = uri.toString(),
            filename = filename,
            size = null,
            lastModified = null,
            contentType = null,
            etag = null,
            sourceType = SourceType.WEBDAV_ARCHIVE,
            archivePath = webdavUrl,
            entryPath = entryPath
        )
    }
}
