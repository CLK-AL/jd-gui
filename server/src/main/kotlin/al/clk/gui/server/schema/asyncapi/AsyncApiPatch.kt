package al.clk.gui.server.schema.asyncapi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.net.URI
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

/**
 * AsyncAPI patch operations on classpath URIs.
 *
 * Similar to JSON Patch (RFC 6902) but designed for AsyncAPI specs
 * loaded from classpath resources.
 *
 * Usage:
 * ```
 * val patch = AsyncApiPatch.builder()
 *     .base("classpath:/asyncapi/base-spec.yaml")
 *     .add("/channels/user~1events", channel)
 *     .replace("/info/version", "2.0.0")
 *     .remove("/components/schemas/Deprecated")
 *     .merge("classpath:/asyncapi/overlay.yaml")
 *     .build()
 *
 * val result = patch.apply()
 * ```
 */
class AsyncApiPatch private constructor(
    private val baseUri: String,
    private val operations: List<PatchOperation>
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * Apply all patch operations to base document.
     */
    fun apply(): AsyncApiPatchResult {
        val errors = mutableListOf<PatchError>()

        // Load base document
        val baseContent = resolveUri(baseUri)
            ?: return AsyncApiPatchResult(
                success = false,
                document = null,
                errors = listOf(PatchError("BASE", "Failed to load base: $baseUri"))
            )

        var document = try {
            json.parseToJsonElement(baseContent).jsonObject.toMutableMap()
        } catch (e: Exception) {
            // Try YAML conversion (simplified - in real impl use SnakeYAML)
            parseYamlToJson(baseContent)?.jsonObject?.toMutableMap()
                ?: return AsyncApiPatchResult(
                    success = false,
                    document = null,
                    errors = listOf(PatchError("BASE", "Failed to parse base: ${e.message}"))
                )
        }

        // Apply operations sequentially
        operations.forEach { op ->
            try {
                document = applyOperation(document, op)
            } catch (e: Exception) {
                errors.add(PatchError(op.op.name, "Path: ${op.path}, Error: ${e.message}"))
                if (op.op == OpType.TEST) {
                    // Test failures are non-fatal but recorded
                    logger.warn { "Test operation failed: ${op.path}" }
                }
            }
        }

        val result = JsonObject(document)
        return AsyncApiPatchResult(
            success = errors.isEmpty(),
            document = json.encodeToString(JsonElement.serializer(), result),
            errors = errors,
            appliedOps = operations.size - errors.size
        )
    }

    private fun applyOperation(doc: MutableMap<String, JsonElement>, op: PatchOperation): MutableMap<String, JsonElement> {
        return when (op.op) {
            OpType.ADD -> applyAdd(doc, op.path, op.value!!)
            OpType.REMOVE -> applyRemove(doc, op.path)
            OpType.REPLACE -> applyReplace(doc, op.path, op.value!!)
            OpType.MOVE -> applyMove(doc, op.from!!, op.path)
            OpType.COPY -> applyCopy(doc, op.from!!, op.path)
            OpType.TEST -> applyTest(doc, op.path, op.value!!)
            OpType.MERGE -> applyMerge(doc, op.value!!)
            OpType.MERGE_URI -> applyMergeUri(doc, op.from!!)
        }
    }

    private fun applyAdd(doc: MutableMap<String, JsonElement>, path: String, value: JsonElement): MutableMap<String, JsonElement> {
        setAtPath(doc, path, value)
        return doc
    }

    private fun applyRemove(doc: MutableMap<String, JsonElement>, path: String): MutableMap<String, JsonElement> {
        removeAtPath(doc, path)
        return doc
    }

    private fun applyReplace(doc: MutableMap<String, JsonElement>, path: String, value: JsonElement): MutableMap<String, JsonElement> {
        if (getAtPath(doc, path) == null) {
            throw IllegalStateException("Path does not exist: $path")
        }
        setAtPath(doc, path, value)
        return doc
    }

    private fun applyMove(doc: MutableMap<String, JsonElement>, from: String, to: String): MutableMap<String, JsonElement> {
        val value = getAtPath(doc, from) ?: throw IllegalStateException("Source path does not exist: $from")
        removeAtPath(doc, from)
        setAtPath(doc, to, value)
        return doc
    }

    private fun applyCopy(doc: MutableMap<String, JsonElement>, from: String, to: String): MutableMap<String, JsonElement> {
        val value = getAtPath(doc, from) ?: throw IllegalStateException("Source path does not exist: $from")
        setAtPath(doc, to, value)
        return doc
    }

    private fun applyTest(doc: MutableMap<String, JsonElement>, path: String, expected: JsonElement): MutableMap<String, JsonElement> {
        val actual = getAtPath(doc, path)
        if (actual != expected) {
            throw IllegalStateException("Test failed: expected $expected at $path, got $actual")
        }
        return doc
    }

    private fun applyMerge(doc: MutableMap<String, JsonElement>, overlay: JsonElement): MutableMap<String, JsonElement> {
        if (overlay is JsonObject) {
            deepMerge(doc, overlay)
        }
        return doc
    }

    private fun applyMergeUri(doc: MutableMap<String, JsonElement>, uri: String): MutableMap<String, JsonElement> {
        val content = resolveUri(uri) ?: throw IllegalStateException("Failed to load: $uri")
        val overlay = json.parseToJsonElement(content)
        return applyMerge(doc, overlay)
    }

    // JSON Pointer path operations (RFC 6901)
    private fun parsePath(path: String): List<String> {
        if (path.isEmpty() || path == "/") return emptyList()
        return path.removePrefix("/")
            .split("/")
            .map { it.replace("~1", "/").replace("~0", "~") }
    }

    private fun getAtPath(doc: Map<String, JsonElement>, path: String): JsonElement? {
        val segments = parsePath(path)
        var current: JsonElement = JsonObject(doc)

        for (segment in segments) {
            current = when (current) {
                is JsonObject -> current[segment] ?: return null
                is JsonArray -> {
                    val index = segment.toIntOrNull() ?: return null
                    current.getOrNull(index) ?: return null
                }
                else -> return null
            }
        }
        return current
    }

    private fun setAtPath(doc: MutableMap<String, JsonElement>, path: String, value: JsonElement) {
        val segments = parsePath(path)
        if (segments.isEmpty()) {
            if (value is JsonObject) {
                doc.putAll(value)
            }
            return
        }

        var current: Any = doc
        for (i in 0 until segments.size - 1) {
            val segment = segments[i]
            current = when (current) {
                is MutableMap<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val map = current as MutableMap<String, JsonElement>
                    val next = map[segment]
                    if (next == null) {
                        val newMap = mutableMapOf<String, JsonElement>()
                        map[segment] = JsonObject(newMap)
                        newMap
                    } else when (next) {
                        is JsonObject -> next.toMutableMap().also { map[segment] = JsonObject(it) }
                        is JsonArray -> next.toMutableList()
                        else -> throw IllegalStateException("Cannot traverse: $segment")
                    }
                }
                else -> throw IllegalStateException("Cannot traverse path")
            }
        }

        val lastSegment = segments.last()
        when (current) {
            is MutableMap<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (current as MutableMap<String, JsonElement>)[lastSegment] = value
            }
            is MutableList<*> -> {
                @Suppress("UNCHECKED_CAST")
                val list = current as MutableList<JsonElement>
                val index = if (lastSegment == "-") list.size else lastSegment.toInt()
                if (index >= list.size) list.add(value) else list[index] = value
            }
        }
    }

    private fun removeAtPath(doc: MutableMap<String, JsonElement>, path: String) {
        val segments = parsePath(path)
        if (segments.isEmpty()) return

        var current: Any = doc
        for (i in 0 until segments.size - 1) {
            val segment = segments[i]
            current = when (current) {
                is MutableMap<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val map = current as MutableMap<String, JsonElement>
                    when (val next = map[segment]) {
                        is JsonObject -> next.toMutableMap().also { map[segment] = JsonObject(it) }
                        is JsonArray -> next.toMutableList()
                        else -> return
                    }
                }
                else -> return
            }
        }

        val lastSegment = segments.last()
        when (current) {
            is MutableMap<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (current as MutableMap<String, JsonElement>).remove(lastSegment)
            }
            is MutableList<*> -> {
                @Suppress("UNCHECKED_CAST")
                val list = current as MutableList<JsonElement>
                val index = lastSegment.toIntOrNull() ?: return
                if (index < list.size) list.removeAt(index)
            }
        }
    }

    private fun deepMerge(target: MutableMap<String, JsonElement>, source: JsonObject) {
        source.forEach { (key, value) ->
            val existing = target[key]
            target[key] = when {
                existing is JsonObject && value is JsonObject -> {
                    val merged = existing.toMutableMap()
                    deepMerge(merged, value)
                    JsonObject(merged)
                }
                existing is JsonArray && value is JsonArray -> {
                    JsonArray(existing + value)
                }
                else -> value
            }
        }
    }

    /**
     * Resolve classpath:/ or file:/ or http:/ URIs
     */
    private fun resolveUri(uriString: String): String? {
        return try {
            when {
                uriString.startsWith("classpath:") -> {
                    val path = uriString.removePrefix("classpath:")
                    Thread.currentThread().contextClassLoader
                        .getResourceAsStream(path.removePrefix("/"))
                        ?.bufferedReader(StandardCharsets.UTF_8)
                        ?.readText()
                }
                uriString.startsWith("file:") -> {
                    URI(uriString).toURL().readText(StandardCharsets.UTF_8)
                }
                uriString.startsWith("http://") || uriString.startsWith("https://") -> {
                    URI(uriString).toURL().readText(StandardCharsets.UTF_8)
                }
                else -> {
                    // Treat as classpath resource
                    Thread.currentThread().contextClassLoader
                        .getResourceAsStream(uriString)
                        ?.bufferedReader(StandardCharsets.UTF_8)
                        ?.readText()
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to resolve URI: $uriString" }
            null
        }
    }

    private fun parseYamlToJson(yaml: String): JsonElement? {
        // Simplified YAML to JSON conversion for common patterns
        // In production, use SnakeYAML or similar
        return try {
            val lines = yaml.lines()
            val result = mutableMapOf<String, JsonElement>()
            var currentKey = ""
            var indent = 0

            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    if (trimmed.contains(":")) {
                        val (key, value) = trimmed.split(":", limit = 2)
                        val v = value.trim()
                        if (v.isNotEmpty()) {
                            result[key.trim()] = JsonPrimitive(v.removeSurrounding("\"").removeSurrounding("'"))
                        }
                    }
                }
            }
            JsonObject(result)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun builder() = Builder()
    }

    class Builder {
        private var baseUri: String = ""
        private val operations = mutableListOf<PatchOperation>()
        private val json = Json { ignoreUnknownKeys = true }

        fun base(uri: String) = apply { baseUri = uri }

        fun add(path: String, value: JsonElement) = apply {
            operations.add(PatchOperation(OpType.ADD, path, value = value))
        }

        fun add(path: String, value: String) = add(path, JsonPrimitive(value))
        fun add(path: String, value: Int) = add(path, JsonPrimitive(value))
        fun add(path: String, value: Boolean) = add(path, JsonPrimitive(value))

        fun addChannel(channelName: String, channel: AsyncApiChannel) = apply {
            val channelJson = buildJsonObject {
                channel.description?.let { put("description", it) }
                channel.subscribe?.let { op ->
                    putJsonObject("subscribe") {
                        op.operationId?.let { put("operationId", it) }
                        op.summary?.let { put("summary", it) }
                        op.message?.let { msg ->
                            putJsonObject("message") {
                                msg.name?.let { put("name", it) }
                                msg.contentType?.let { put("contentType", it) }
                                msg.schemaRef?.let { put("\$ref", it) }
                            }
                        }
                    }
                }
                channel.publish?.let { op ->
                    putJsonObject("publish") {
                        op.operationId?.let { put("operationId", it) }
                        op.summary?.let { put("summary", it) }
                    }
                }
            }
            add("/channels/${channelName.replace("/", "~1")}", channelJson)
        }

        fun addSchema(schemaName: String, schema: JsonElement) = apply {
            add("/components/schemas/$schemaName", schema)
        }

        fun addSchemaFromKotlin(schemaName: String, kotlinDataClass: String) = apply {
            // Use UnifiedSchemaGenerator to create JSON Schema
            val schemas = al.clk.gui.server.schema.UnifiedSchemaGenerator.generate(kotlinDataClass, "generated")
            val jsonSchema = json.parseToJsonElement(schemas.jsonSchema)
            if (jsonSchema is JsonObject) {
                val defs = jsonSchema["\$defs"] as? JsonObject
                defs?.forEach { (name, schema) ->
                    add("/components/schemas/$name", schema)
                }
            }
        }

        fun remove(path: String) = apply {
            operations.add(PatchOperation(OpType.REMOVE, path))
        }

        fun replace(path: String, value: JsonElement) = apply {
            operations.add(PatchOperation(OpType.REPLACE, path, value = value))
        }

        fun replace(path: String, value: String) = replace(path, JsonPrimitive(value))

        fun move(from: String, to: String) = apply {
            operations.add(PatchOperation(OpType.MOVE, to, from = from))
        }

        fun copy(from: String, to: String) = apply {
            operations.add(PatchOperation(OpType.COPY, to, from = from))
        }

        fun test(path: String, value: JsonElement) = apply {
            operations.add(PatchOperation(OpType.TEST, path, value = value))
        }

        fun merge(overlay: JsonElement) = apply {
            operations.add(PatchOperation(OpType.MERGE, "", value = overlay))
        }

        fun merge(uri: String) = apply {
            operations.add(PatchOperation(OpType.MERGE_URI, "", from = uri))
        }

        fun build(): AsyncApiPatch {
            require(baseUri.isNotEmpty()) { "Base URI is required" }
            return AsyncApiPatch(baseUri, operations.toList())
        }
    }
}

/**
 * Patch operation types (JSON Patch RFC 6902 + extensions)
 */
enum class OpType {
    ADD,        // Add value at path
    REMOVE,     // Remove value at path
    REPLACE,    // Replace value at path
    MOVE,       // Move value from one path to another
    COPY,       // Copy value from one path to another
    TEST,       // Test value at path equals expected
    MERGE,      // Deep merge overlay document
    MERGE_URI   // Load and merge from URI
}

@Serializable
data class PatchOperation(
    val op: OpType,
    val path: String,
    val from: String? = null,
    val value: JsonElement? = null
)

@Serializable
data class PatchError(
    val operation: String,
    val message: String
)

@Serializable
data class AsyncApiPatchResult(
    val success: Boolean,
    val document: String?,
    val errors: List<PatchError> = emptyList(),
    val appliedOps: Int = 0
)

// AsyncAPI model classes for type-safe channel/operation building
data class AsyncApiChannel(
    val description: String? = null,
    val subscribe: AsyncApiOperation? = null,
    val publish: AsyncApiOperation? = null,
    val parameters: Map<String, AsyncApiParameter>? = null
)

data class AsyncApiOperation(
    val operationId: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val message: AsyncApiMessage? = null,
    val traits: List<String>? = null
)

data class AsyncApiMessage(
    val name: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val contentType: String? = null,
    val schemaRef: String? = null,
    val payload: JsonElement? = null
)

data class AsyncApiParameter(
    val description: String? = null,
    val schema: JsonElement? = null,
    val location: String? = null
)

/**
 * DSL for building AsyncAPI patches.
 */
fun asyncApiPatch(baseUri: String, block: AsyncApiPatch.Builder.() -> Unit): AsyncApiPatch {
    return AsyncApiPatch.builder()
        .base(baseUri)
        .apply(block)
        .build()
}

/**
 * Create channel definition.
 */
fun channel(
    description: String? = null,
    subscribe: AsyncApiOperation? = null,
    publish: AsyncApiOperation? = null
) = AsyncApiChannel(description, subscribe, publish)

/**
 * Create operation definition.
 */
fun operation(
    operationId: String? = null,
    summary: String? = null,
    message: AsyncApiMessage? = null
) = AsyncApiOperation(operationId, summary, message = message)

/**
 * Create message definition.
 */
fun message(
    name: String? = null,
    contentType: String = "application/json",
    schemaRef: String? = null
) = AsyncApiMessage(name, contentType = contentType, schemaRef = schemaRef)
