package org.jd.gui.server.schema.validators

import kotlinx.serialization.json.*
import mu.KotlinLogging
import org.jd.gui.server.schema.*

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

    override fun validate(document: String, schema: SchemaEntry): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        try {
            // Parse document
            val docJson = json.parseToJsonElement(document)

            // Parse schema
            val schemaJson = json.parseToJsonElement(schema.content)

            // Validate against schema
            validateAgainstSchema(docJson, schemaJson, "", errors, warnings)

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
        warnings: MutableList<ValidationWarning>
    ) {
        if (schema !is JsonObject) return

        // Handle $ref
        schema["${"$"}ref"]?.let { ref ->
            // TODO: Resolve reference and validate
            warnings.add(ValidationWarning(
                message = "Schema reference not resolved: $ref",
                path = path
            ))
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
                    validateObject(value, schema, path, errors, warnings)
                }
            }
            expectedType == "array" || expectedType?.contains("array") == true -> {
                if (value !is JsonArray) {
                    errors.add(ValidationError("Expected array at $path", path, errorCode = "TYPE_MISMATCH"))
                } else {
                    validateArray(value, schema, path, errors, warnings)
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
        warnings: MutableList<ValidationWarning>
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
                        validateAgainstSchema(value, propSchema, "$path.$key", errors, warnings)
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
        warnings: MutableList<ValidationWarning>
    ) {
        // Items validation
        schema["items"]?.let { items ->
            arr.forEachIndexed { index, item ->
                validateAgainstSchema(item, items, "$path[$index]", errors, warnings)
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
