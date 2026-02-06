package org.jd.gui.server.schema.validators

import kotlinx.serialization.json.*
import mu.KotlinLogging
import org.jd.gui.server.schema.*
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

private val logger = KotlinLogging.logger {}

/**
 * JSON Schema validator using kotlinx.serialization.
 *
 * Supports:
 * - JSON Schema Draft-07, Draft-2019-09, Draft-2020-12
 * - Syntax validation
 * - Schema validation with type checking
 * - Cross-references ($ref)
 *
 * Libraries:
 * - kotlinx.serialization.json for parsing
 * - Custom validation logic for schema compliance
 */
class JsonSchemaValidator : SchemaValidator {

    override val format = SchemaFormat.JSON_SCHEMA

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    /** Cache for resolved external schemas to avoid circular resolution */
    private val schemaCache = mutableMapOf<String, JsonElement>()

    /** Set to track refs currently being resolved (for circular reference detection) */
    private val resolvingRefs = mutableSetOf<String>()

    override fun validate(document: String, schema: SchemaEntry): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // Clear caches for each validation run
        schemaCache.clear()
        resolvingRefs.clear()

        try {
            // Parse document
            val docJson = json.parseToJsonElement(document)

            // Parse schema
            val schemaJson = json.parseToJsonElement(schema.content)

            // Determine base URI from schema location if available
            val baseUri = schema.location?.let { URI(it) }

            // Validate against schema
            validateAgainstSchema(docJson, schemaJson, "", errors, warnings, schemaJson, baseUri)

            return ValidationResult(
                valid = errors.isEmpty(),
                errors = errors,
                warnings = warnings,
                schemaId = schema.id,
                format = format
            )

        } catch (e: Exception) {
            return ValidationResult(
                valid = false,
                errors = listOf(ValidationError(
                    message = "JSON parsing error: ${e.message}",
                    errorCode = "PARSE_ERROR"
                )),
                schemaId = schema.id,
                format = format
            )
        }
    }

    override fun validateSyntax(document: String, format: SchemaFormat): ValidationResult {
        return try {
            json.parseToJsonElement(document)
            ValidationResult(valid = true, format = format)
        } catch (e: Exception) {
            val lineCol = extractLineColumn(e.message ?: "")
            ValidationResult(
                valid = false,
                errors = listOf(ValidationError(
                    message = "JSON syntax error: ${e.message}",
                    line = lineCol.first,
                    column = lineCol.second,
                    errorCode = "SYNTAX_ERROR"
                )),
                format = format
            )
        }
    }

    private fun validateAgainstSchema(
        value: JsonElement,
        schema: JsonElement,
        path: String,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>,
        rootSchema: JsonElement = schema,
        baseUri: URI? = null
    ) {
        if (schema !is JsonObject) return

        // Handle $ref
        schema["${"$"}ref"]?.let { refElement ->
            val ref = (refElement as? JsonPrimitive)?.contentOrNull ?: return@let

            try {
                val resolvedSchema = resolveRef(ref, rootSchema, baseUri, errors, warnings)
                if (resolvedSchema != null) {
                    // Determine the new root schema and base URI for the resolved reference
                    val (newRootSchema, newBaseUri) = when {
                        ref.startsWith("#") -> rootSchema to baseUri
                        ref.contains("#") -> {
                            val uriPart = ref.substringBefore("#")
                            resolvedSchema to resolveUri(uriPart, baseUri)
                        }
                        else -> resolvedSchema to resolveUri(ref, baseUri)
                    }
                    validateAgainstSchema(value, resolvedSchema, path, errors, warnings, newRootSchema, newBaseUri)
                }
            } catch (e: Exception) {
                errors.add(ValidationError(
                    message = "Failed to resolve reference '$ref': ${e.message}",
                    path = path,
                    errorCode = "REF_RESOLUTION_ERROR"
                ))
            }
            return
        }

        // Get expected type
        val typeNode = schema["type"]
        val expectedType = when (typeNode) {
            is JsonPrimitive -> typeNode.contentOrNull
            is JsonArray -> typeNode.map { (it as? JsonPrimitive)?.contentOrNull }
            else -> null
        }

        // Type validation
        when {
            expectedType == "object" || expectedType?.contains("object") == true -> {
                if (value !is JsonObject) {
                    errors.add(ValidationError("Expected object at $path", path, errorCode = "TYPE_MISMATCH"))
                } else {
                    validateObject(value, schema, path, errors, warnings, rootSchema, baseUri)
                }
            }
            expectedType == "array" || expectedType?.contains("array") == true -> {
                if (value !is JsonArray) {
                    errors.add(ValidationError("Expected array at $path", path, errorCode = "TYPE_MISMATCH"))
                } else {
                    validateArray(value, schema, path, errors, warnings, rootSchema, baseUri)
                }
            }
            expectedType == "string" || expectedType?.contains("string") == true -> {
                if (value !is JsonPrimitive || !value.isString) {
                    errors.add(ValidationError("Expected string at $path", path, errorCode = "TYPE_MISMATCH"))
                } else {
                    validateString(value.content, schema, path, errors)
                }
            }
            expectedType == "number" || expectedType == "integer" ||
                    expectedType?.contains("number") == true || expectedType?.contains("integer") == true -> {
                if (value !is JsonPrimitive) {
                    errors.add(ValidationError("Expected number at $path", path, errorCode = "TYPE_MISMATCH"))
                } else {
                    validateNumber(value, schema, path, errors)
                }
            }
            expectedType == "boolean" || expectedType?.contains("boolean") == true -> {
                if (value !is JsonPrimitive) {
                    errors.add(ValidationError("Expected boolean at $path", path, errorCode = "TYPE_MISMATCH"))
                }
            }
            expectedType == "null" || expectedType?.contains("null") == true -> {
                if (value != JsonNull) {
                    errors.add(ValidationError("Expected null at $path", path, errorCode = "TYPE_MISMATCH"))
                }
            }
        }

        // Enum validation
        schema["enum"]?.let { enumNode ->
            if (enumNode is JsonArray && value !in enumNode) {
                errors.add(ValidationError(
                    message = "Value not in enum at $path",
                    path = path,
                    errorCode = "ENUM_MISMATCH"
                ))
            }
        }

        // Const validation
        schema["const"]?.let { constNode ->
            if (value != constNode) {
                errors.add(ValidationError(
                    message = "Value does not match const at $path",
                    path = path,
                    errorCode = "CONST_MISMATCH"
                ))
            }
        }
    }

    private fun validateObject(
        obj: JsonObject,
        schema: JsonObject,
        path: String,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>,
        rootSchema: JsonElement,
        baseUri: URI?
    ) {
        // Required properties
        schema["required"]?.let { required ->
            if (required is JsonArray) {
                required.forEach { prop ->
                    val propName = (prop as? JsonPrimitive)?.contentOrNull
                    if (propName != null && propName !in obj) {
                        errors.add(ValidationError(
                            message = "Missing required property: $propName",
                            path = "$path.$propName",
                            errorCode = "REQUIRED"
                        ))
                    }
                }
            }
        }

        // Property validation
        schema["properties"]?.let { properties ->
            if (properties is JsonObject) {
                obj.forEach { (key, value) ->
                    val propSchema = properties[key]
                    if (propSchema != null) {
                        validateAgainstSchema(value, propSchema, "$path.$key", errors, warnings, rootSchema, baseUri)
                    }
                }
            }
        }

        // Additional properties
        val additionalProps = schema["additionalProperties"]
        if (additionalProps == JsonPrimitive(false)) {
            val properties = (schema["properties"] as? JsonObject)?.keys ?: emptySet()
            obj.keys.filter { it !in properties }.forEach { key ->
                errors.add(ValidationError(
                    message = "Additional property not allowed: $key",
                    path = "$path.$key",
                    errorCode = "ADDITIONAL_PROPERTY"
                ))
            }
        }

        // Min/max properties
        schema["minProperties"]?.let { min ->
            val minVal = (min as? JsonPrimitive)?.intOrNull ?: 0
            if (obj.size < minVal) {
                errors.add(ValidationError(
                    message = "Object has fewer than $minVal properties",
                    path = path,
                    errorCode = "MIN_PROPERTIES"
                ))
            }
        }

        schema["maxProperties"]?.let { max ->
            val maxVal = (max as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE
            if (obj.size > maxVal) {
                errors.add(ValidationError(
                    message = "Object has more than $maxVal properties",
                    path = path,
                    errorCode = "MAX_PROPERTIES"
                ))
            }
        }
    }

    private fun validateArray(
        arr: JsonArray,
        schema: JsonObject,
        path: String,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>,
        rootSchema: JsonElement,
        baseUri: URI?
    ) {
        // Items validation
        schema["items"]?.let { items ->
            arr.forEachIndexed { index, item ->
                validateAgainstSchema(item, items, "$path[$index]", errors, warnings, rootSchema, baseUri)
            }
        }

        // Min/max items
        schema["minItems"]?.let { min ->
            val minVal = (min as? JsonPrimitive)?.intOrNull ?: 0
            if (arr.size < minVal) {
                errors.add(ValidationError(
                    message = "Array has fewer than $minVal items",
                    path = path,
                    errorCode = "MIN_ITEMS"
                ))
            }
        }

        schema["maxItems"]?.let { max ->
            val maxVal = (max as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE
            if (arr.size > maxVal) {
                errors.add(ValidationError(
                    message = "Array has more than $maxVal items",
                    path = path,
                    errorCode = "MAX_ITEMS"
                ))
            }
        }

        // Unique items
        if (schema["uniqueItems"] == JsonPrimitive(true)) {
            val seen = mutableSetOf<JsonElement>()
            arr.forEachIndexed { index, item ->
                if (item in seen) {
                    errors.add(ValidationError(
                        message = "Duplicate item in array",
                        path = "$path[$index]",
                        errorCode = "UNIQUE_ITEMS"
                    ))
                }
                seen.add(item)
            }
        }
    }

    private fun validateString(
        value: String,
        schema: JsonObject,
        path: String,
        errors: MutableList<ValidationError>
    ) {
        // Min/max length
        schema["minLength"]?.let { min ->
            val minVal = (min as? JsonPrimitive)?.intOrNull ?: 0
            if (value.length < minVal) {
                errors.add(ValidationError(
                    message = "String shorter than $minVal characters",
                    path = path,
                    errorCode = "MIN_LENGTH"
                ))
            }
        }

        schema["maxLength"]?.let { max ->
            val maxVal = (max as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE
            if (value.length > maxVal) {
                errors.add(ValidationError(
                    message = "String longer than $maxVal characters",
                    path = path,
                    errorCode = "MAX_LENGTH"
                ))
            }
        }

        // Pattern
        schema["pattern"]?.let { pattern ->
            val regex = (pattern as? JsonPrimitive)?.contentOrNull
            if (regex != null && !Regex(regex).containsMatchIn(value)) {
                errors.add(ValidationError(
                    message = "String does not match pattern: $regex",
                    path = path,
                    errorCode = "PATTERN"
                ))
            }
        }

        // Format (basic validation)
        schema["format"]?.let { format ->
            val formatStr = (format as? JsonPrimitive)?.contentOrNull
            validateFormat(value, formatStr, path, errors)
        }
    }

    private fun validateNumber(
        value: JsonPrimitive,
        schema: JsonObject,
        path: String,
        errors: MutableList<ValidationError>
    ) {
        val num = value.doubleOrNull ?: return

        schema["minimum"]?.let { min ->
            val minVal = (min as? JsonPrimitive)?.doubleOrNull ?: Double.MIN_VALUE
            if (num < minVal) {
                errors.add(ValidationError(
                    message = "Number less than minimum $minVal",
                    path = path,
                    errorCode = "MINIMUM"
                ))
            }
        }

        schema["maximum"]?.let { max ->
            val maxVal = (max as? JsonPrimitive)?.doubleOrNull ?: Double.MAX_VALUE
            if (num > maxVal) {
                errors.add(ValidationError(
                    message = "Number greater than maximum $maxVal",
                    path = path,
                    errorCode = "MAXIMUM"
                ))
            }
        }

        schema["multipleOf"]?.let { mult ->
            val multVal = (mult as? JsonPrimitive)?.doubleOrNull ?: 1.0
            if (num % multVal != 0.0) {
                errors.add(ValidationError(
                    message = "Number not a multiple of $multVal",
                    path = path,
                    errorCode = "MULTIPLE_OF"
                ))
            }
        }
    }

    private fun validateFormat(
        value: String,
        format: String?,
        path: String,
        errors: MutableList<ValidationError>
    ) {
        when (format) {
            "email" -> {
                if (!value.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
                    errors.add(ValidationError("Invalid email format", path, errorCode = "FORMAT_EMAIL"))
                }
            }
            "uri", "uri-reference" -> {
                try {
                    java.net.URI(value)
                } catch (e: Exception) {
                    errors.add(ValidationError("Invalid URI format", path, errorCode = "FORMAT_URI"))
                }
            }
            "date" -> {
                if (!value.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                    errors.add(ValidationError("Invalid date format (expected YYYY-MM-DD)", path, errorCode = "FORMAT_DATE"))
                }
            }
            "date-time" -> {
                if (!value.matches(Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*$"))) {
                    errors.add(ValidationError("Invalid date-time format", path, errorCode = "FORMAT_DATETIME"))
                }
            }
            "uuid" -> {
                if (!value.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))) {
                    errors.add(ValidationError("Invalid UUID format", path, errorCode = "FORMAT_UUID"))
                }
            }
            "ipv4" -> {
                if (!value.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
                    errors.add(ValidationError("Invalid IPv4 format", path, errorCode = "FORMAT_IPV4"))
                }
            }
        }
    }

    /**
     * Resolves a JSON Schema $ref reference.
     *
     * Supports:
     * - Local references: "#/definitions/MyType"
     * - External references with fragment: "other-schema.json#/definitions/Type"
     * - External references without fragment: "other-schema.json"
     *
     * @param ref The reference string to resolve
     * @param rootSchema The root schema document for local references
     * @param baseUri The base URI for resolving relative external references
     * @param errors List to add resolution errors to
     * @param warnings List to add resolution warnings to
     * @return The resolved schema element, or null if resolution failed
     */
    private fun resolveRef(
        ref: String,
        rootSchema: JsonElement,
        baseUri: URI?,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ): JsonElement? {
        // Check for circular reference
        if (ref in resolvingRefs) {
            warnings.add(ValidationWarning(
                message = "Circular reference detected: $ref",
                path = ref
            ))
            return null
        }

        resolvingRefs.add(ref)
        try {
            return when {
                ref.startsWith("#/") -> {
                    // Local reference - navigate within same document using JSON Pointer
                    navigateToPointer(ref.substring(1), rootSchema)
                }
                ref == "#" -> {
                    // Reference to root of current document
                    rootSchema
                }
                ref.contains("#") -> {
                    // External reference with fragment
                    val (uriPart, fragment) = ref.split("#", limit = 2)
                    val externalSchema = loadExternalSchema(uriPart, baseUri)
                    if (externalSchema != null && fragment.isNotEmpty()) {
                        navigateToPointer("/$fragment", externalSchema)
                    } else {
                        externalSchema
                    }
                }
                else -> {
                    // External reference without fragment
                    loadExternalSchema(ref, baseUri)
                }
            }
        } finally {
            resolvingRefs.remove(ref)
        }
    }

    /**
     * Navigates to a location in a JSON document using a JSON Pointer (RFC 6901).
     *
     * @param pointer The JSON Pointer path (e.g., "/definitions/MyType")
     * @param document The JSON document to navigate
     * @return The element at the pointer location, or null if not found
     */
    private fun navigateToPointer(pointer: String, document: JsonElement): JsonElement? {
        if (pointer.isEmpty() || pointer == "/") {
            return document
        }

        val segments = pointer.trimStart('/').split("/")
        return segments.fold(document as JsonElement?) { current, segment ->
            if (current == null) return null

            // Decode JSON Pointer escape sequences (RFC 6901)
            val decodedSegment = segment
                .replace("~1", "/")
                .replace("~0", "~")

            when (current) {
                is JsonObject -> current[decodedSegment]
                is JsonArray -> {
                    val index = decodedSegment.toIntOrNull()
                    if (index != null && index >= 0 && index < current.size) {
                        current[index]
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
    }

    /**
     * Loads an external schema from a URI.
     *
     * @param uriString The URI string of the external schema
     * @param baseUri The base URI for resolving relative references
     * @return The parsed schema element, or null if loading failed
     */
    private fun loadExternalSchema(uriString: String, baseUri: URI?): JsonElement? {
        // Check cache first
        val cacheKey = if (baseUri != null) {
            baseUri.resolve(uriString).toString()
        } else {
            uriString
        }

        schemaCache[cacheKey]?.let { return it }

        try {
            val uri = if (baseUri != null) {
                baseUri.resolve(uriString)
            } else {
                URI(uriString)
            }

            val content = when (uri.scheme) {
                "file", null -> {
                    // File URI or relative path
                    val path = if (uri.scheme == "file") {
                        Paths.get(uri)
                    } else {
                        Paths.get(uri.toString())
                    }
                    Files.readString(path)
                }
                "classpath" -> {
                    // Classpath resource
                    val resourcePath = uri.schemeSpecificPart
                    this::class.java.getResourceAsStream(resourcePath)?.bufferedReader()?.readText()
                        ?: throw IllegalArgumentException("Classpath resource not found: $resourcePath")
                }
                else -> {
                    // HTTP/HTTPS or other schemes - log warning and skip
                    logger.warn { "External schema loading not supported for scheme: ${uri.scheme}" }
                    return null
                }
            }

            val schema = json.parseToJsonElement(content)
            schemaCache[cacheKey] = schema
            return schema
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load external schema: $uriString" }
            return null
        }
    }

    /**
     * Resolves a URI string against a base URI.
     *
     * @param uriString The URI string to resolve
     * @param baseUri The base URI for resolution
     * @return The resolved URI, or null if resolution failed
     */
    private fun resolveUri(uriString: String, baseUri: URI?): URI? {
        return try {
            if (baseUri != null) {
                baseUri.resolve(uriString)
            } else {
                URI(uriString)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to resolve URI: $uriString" }
            null
        }
    }

    private fun extractLineColumn(message: String): Pair<Int?, Int?> {
        // Try to extract line/column from error message
        val lineMatch = Regex("line (\\d+)").find(message)
        val colMatch = Regex("column (\\d+)").find(message)
        return Pair(
            lineMatch?.groupValues?.get(1)?.toIntOrNull(),
            colMatch?.groupValues?.get(1)?.toIntOrNull()
        )
    }
}
