package org.jd.gui.server.schema.asyncapi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Unified URI resolver supporting multiple schemes:
 * - classpath:/path/to/resource
 * - file:/path/to/file
 * - jar:file:/path/to/jar!/entry
 * - http:// or https://
 * - resource: (alias for classpath)
 *
 * Features:
 * - Caching with TTL
 * - Async loading
 * - Content type detection
 * - Fallback resolution
 */
object ClasspathUriResolver {

    private val cache = ConcurrentHashMap<String, CachedResource>()
    private val defaultTtlMs = 5 * 60 * 1000L  // 5 minutes

    /**
     * Resolve URI to content string.
     */
    fun resolve(uri: String, charset: Charset = StandardCharsets.UTF_8): String? {
        return resolveToBytes(uri)?.toString(charset)
    }

    /**
     * Resolve URI to byte array with caching.
     */
    fun resolveToBytes(uri: String): ByteArray? {
        // Check cache
        cache[uri]?.let { cached ->
            if (!cached.isExpired()) {
                logger.debug { "Cache hit: $uri" }
                return cached.content
            }
            cache.remove(uri)
        }

        // Resolve fresh
        val content = doResolve(uri)
        if (content != null) {
            cache[uri] = CachedResource(content, System.currentTimeMillis() + defaultTtlMs)
        }
        return content
    }

    /**
     * Resolve URI asynchronously.
     */
    suspend fun resolveAsync(uri: String, charset: Charset = StandardCharsets.UTF_8): String? {
        return withContext(Dispatchers.IO) {
            resolve(uri, charset)
        }
    }

    /**
     * Resolve URI to input stream (no caching).
     */
    fun resolveToStream(uri: String): InputStream? {
        return try {
            when {
                uri.startsWith("classpath:") || uri.startsWith("resource:") -> {
                    val path = uri.substringAfter(":").removePrefix("/")
                    getClassLoader().getResourceAsStream(path)
                }
                uri.startsWith("jar:") -> {
                    URI(uri).toURL().openStream()
                }
                uri.startsWith("file:") -> {
                    URI(uri).toURL().openStream()
                }
                uri.startsWith("http://") || uri.startsWith("https://") -> {
                    URI(uri).toURL().openStream()
                }
                else -> {
                    // Try as classpath resource
                    getClassLoader().getResourceAsStream(uri)
                        ?: getClassLoader().getResourceAsStream("/$uri")
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to resolve stream: $uri" }
            null
        }
    }

    /**
     * Check if URI exists/is resolvable.
     */
    fun exists(uri: String): Boolean {
        return try {
            when {
                uri.startsWith("classpath:") || uri.startsWith("resource:") -> {
                    val path = uri.substringAfter(":").removePrefix("/")
                    getClassLoader().getResource(path) != null
                }
                uri.startsWith("file:") -> {
                    java.io.File(URI(uri)).exists()
                }
                else -> {
                    getClassLoader().getResource(uri) != null
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get URL for URI (for further processing).
     */
    fun toUrl(uri: String): URL? {
        return try {
            when {
                uri.startsWith("classpath:") || uri.startsWith("resource:") -> {
                    val path = uri.substringAfter(":").removePrefix("/")
                    getClassLoader().getResource(path)
                }
                uri.startsWith("jar:") || uri.startsWith("file:") ||
                    uri.startsWith("http://") || uri.startsWith("https://") -> {
                    URI(uri).toURL()
                }
                else -> getClassLoader().getResource(uri)
            }
        } catch (e: Exception) {
            logger.warn { "Failed to convert to URL: $uri" }
            null
        }
    }

    /**
     * Detect content type from URI.
     */
    fun detectContentType(uri: String): ContentType {
        val lower = uri.lowercase()
        return when {
            lower.endsWith(".json") -> ContentType.JSON
            lower.endsWith(".yaml") || lower.endsWith(".yml") -> ContentType.YAML
            lower.endsWith(".xml") || lower.endsWith(".xsd") -> ContentType.XML
            lower.endsWith(".proto") -> ContentType.PROTOBUF
            lower.endsWith(".graphql") || lower.endsWith(".gql") -> ContentType.GRAPHQL
            lower.endsWith(".avsc") -> ContentType.AVRO
            lower.endsWith(".kt") -> ContentType.KOTLIN
            lower.endsWith(".java") -> ContentType.JAVA
            lower.endsWith(".ts") || lower.endsWith(".d.ts") -> ContentType.TYPESCRIPT
            else -> ContentType.UNKNOWN
        }
    }

    /**
     * Clear cache.
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Clear expired cache entries.
     */
    fun cleanExpired() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { it.value.isExpired(now) }
    }

    private fun doResolve(uri: String): ByteArray? {
        return try {
            resolveToStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            logger.error(e) { "Failed to resolve: $uri" }
            null
        }
    }

    private fun getClassLoader(): ClassLoader {
        return Thread.currentThread().contextClassLoader
            ?: ClasspathUriResolver::class.java.classLoader
    }

    private data class CachedResource(
        val content: ByteArray,
        val expiresAt: Long
    ) {
        fun isExpired(now: Long = System.currentTimeMillis()) = now > expiresAt
    }
}

enum class ContentType(val mimeType: String) {
    JSON("application/json"),
    YAML("application/yaml"),
    XML("application/xml"),
    PROTOBUF("application/protobuf"),
    GRAPHQL("application/graphql"),
    AVRO("application/avro"),
    KOTLIN("text/x-kotlin"),
    JAVA("text/x-java"),
    TYPESCRIPT("application/typescript"),
    UNKNOWN("application/octet-stream")
}

/**
 * AsyncAPI specification registry.
 *
 * Manages base specs and patches for building final AsyncAPI documents.
 */
class AsyncApiRegistry {
    private val specs = ConcurrentHashMap<String, RegisteredSpec>()
    private val patches = ConcurrentHashMap<String, MutableList<AsyncApiPatch>>()

    /**
     * Register a base spec from URI.
     */
    fun register(id: String, uri: String, description: String? = null): AsyncApiRegistry {
        specs[id] = RegisteredSpec(
            id = id,
            uri = uri,
            description = description,
            registeredAt = System.currentTimeMillis()
        )
        return this
    }

    /**
     * Register a patch for a spec.
     */
    fun addPatch(specId: String, patch: AsyncApiPatch): AsyncApiRegistry {
        patches.getOrPut(specId) { mutableListOf() }.add(patch)
        return this
    }

    /**
     * Build final spec by applying all patches.
     */
    fun build(specId: String): AsyncApiPatchResult? {
        val spec = specs[specId] ?: return null
        val specPatches = patches[specId] ?: emptyList()

        if (specPatches.isEmpty()) {
            // Return base spec as-is
            val content = ClasspathUriResolver.resolve(spec.uri)
                ?: return AsyncApiPatchResult(false, null, listOf(PatchError("LOAD", "Failed to load: ${spec.uri}")))
            return AsyncApiPatchResult(true, content, emptyList(), 0)
        }

        // Chain patches
        var result: AsyncApiPatchResult? = null
        specPatches.forEach { patch ->
            result = patch.apply()
            if (result?.success != true) {
                return result
            }
        }
        return result
    }

    /**
     * List all registered specs.
     */
    fun list(): List<RegisteredSpec> = specs.values.toList()

    /**
     * Get spec metadata.
     */
    fun get(specId: String): RegisteredSpec? = specs[specId]

    /**
     * Remove spec and its patches.
     */
    fun remove(specId: String) {
        specs.remove(specId)
        patches.remove(specId)
    }

    data class RegisteredSpec(
        val id: String,
        val uri: String,
        val description: String?,
        val registeredAt: Long
    )
}

/**
 * DSL for building AsyncAPI specs with patches.
 */
fun asyncApiSpec(id: String, baseUri: String, block: AsyncApiSpecBuilder.() -> Unit): AsyncApiPatchResult {
    return AsyncApiSpecBuilder(id, baseUri).apply(block).build()
}

class AsyncApiSpecBuilder(
    private val id: String,
    private val baseUri: String
) {
    private val patchBuilder = AsyncApiPatch.builder().base(baseUri)

    fun info(version: String? = null, title: String? = null, description: String? = null) {
        version?.let { patchBuilder.replace("/info/version", it) }
        title?.let { patchBuilder.replace("/info/title", it) }
        description?.let { patchBuilder.replace("/info/description", it) }
    }

    fun channel(name: String, block: ChannelBuilder.() -> Unit) {
        val channel = ChannelBuilder().apply(block).build()
        patchBuilder.addChannel(name, channel)
    }

    fun schema(name: String, kotlinDataClass: String) {
        patchBuilder.addSchemaFromKotlin(name, kotlinDataClass)
    }

    fun merge(overlayUri: String) {
        patchBuilder.merge(overlayUri)
    }

    fun remove(path: String) {
        patchBuilder.remove(path)
    }

    fun build(): AsyncApiPatchResult = patchBuilder.build().apply()
}

class ChannelBuilder {
    private var description: String? = null
    private var subscribeOp: AsyncApiOperation? = null
    private var publishOp: AsyncApiOperation? = null

    fun description(desc: String) {
        description = desc
    }

    fun subscribe(operationId: String, summary: String? = null, messageBuilder: (MessageBuilder.() -> Unit)? = null) {
        val msg = messageBuilder?.let { MessageBuilder().apply(it).build() }
        subscribeOp = AsyncApiOperation(operationId, summary, message = msg)
    }

    fun publish(operationId: String, summary: String? = null, messageBuilder: (MessageBuilder.() -> Unit)? = null) {
        val msg = messageBuilder?.let { MessageBuilder().apply(it).build() }
        publishOp = AsyncApiOperation(operationId, summary, message = msg)
    }

    fun build() = AsyncApiChannel(description, subscribeOp, publishOp)
}

class MessageBuilder {
    private var name: String? = null
    private var contentType: String = "application/json"
    private var schemaRef: String? = null

    fun name(n: String) { name = n }
    fun contentType(ct: String) { contentType = ct }
    fun schema(ref: String) { schemaRef = ref }

    fun build() = AsyncApiMessage(name, contentType = contentType, schemaRef = schemaRef)
}
