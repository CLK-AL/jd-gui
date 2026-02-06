package org.jd.gui.server.schema

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Schema format types supported by the registry.
 *
 * Each format has associated ANTLR grammar, validation tools, and inference capabilities.
 */
@Serializable
enum class SchemaFormat(
    val mimeType: String,
    val fileExtensions: Set<String>,
    val antlrGrammar: String?
) {
    // Data interchange formats
    JSON_SCHEMA("application/schema+json", setOf("json"), "json/json/JSON.g4"),
    XSD("application/xml", setOf("xsd"), "xml/xml/XMLParser.g4"),
    YAML_SCHEMA("application/x-yaml", setOf("yaml", "yml"), null),  // Uses JSON Schema

    // API specification formats
    OPENAPI("application/vnd.oai.openapi+json", setOf("json", "yaml"), null),  // Uses JSON/YAML parsers
    ASYNCAPI("application/vnd.aai.asyncapi+json", setOf("json", "yaml"), null),
    GRAPHQL_SCHEMA("application/graphql", setOf("graphql", "gql"), "graphql/spec/GraphQL.g4"),

    // Binary/RPC formats
    PROTOBUF("application/protobuf", setOf("proto"), "protobuf/3/Protobuf3.g4"),
    AVRO("application/avro", setOf("avsc"), "json/json/JSON.g4"),  // Avro schemas are JSON
    THRIFT("application/x-thrift", setOf("thrift"), null),
    MSGPACK("application/msgpack", setOf("msgpack"), null),  // No schema, binary only

    // Configuration formats
    TOML("application/toml", setOf("toml"), "toml/toml/TomlParser.g4"),
    INI("text/plain", setOf("ini", "cfg"), null),

    // Domain-specific
    SQL_DDL("application/sql", setOf("sql"), "sql/plsql/PlSqlParser.g4"),
    WSDL("application/wsdl+xml", setOf("wsdl"), "xml/xml/XMLParser.g4"),

    // Type definitions
    TYPESCRIPT_DEFS("application/typescript", setOf("d.ts"), "typescript/ts/TypeScriptParser.g4"),
    KOTLIN_SERIAL("text/x-kotlin", setOf("kt"), "kotlin/KotlinParser.g4"),

    // Unknown/Generic
    UNKNOWN("application/octet-stream", emptySet(), null);

    companion object {
        fun fromExtension(ext: String): SchemaFormat {
            val lowerExt = ext.lowercase().removePrefix(".")
            return values().find { lowerExt in it.fileExtensions } ?: UNKNOWN
        }

        fun fromMimeType(mimeType: String): SchemaFormat {
            return values().find { it.mimeType == mimeType } ?: UNKNOWN
        }
    }
}

/**
 * Represents a registered schema with metadata.
 */
@Serializable
data class SchemaEntry(
    val id: String,                      // Unique schema identifier
    val name: String,                    // Human-readable name
    val format: SchemaFormat,            // Schema format type
    val version: String = "1.0.0",       // Schema version
    val content: String,                 // Raw schema content
    val namespace: String? = null,       // Optional namespace/package
    val dependencies: List<String> = emptyList(),  // Other schema IDs this depends on
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Result of schema validation.
 */
@Serializable
data class ValidationResult(
    val valid: Boolean,
    val errors: List<ValidationError> = emptyList(),
    val warnings: List<ValidationWarning> = emptyList(),
    val schemaId: String? = null,
    val format: SchemaFormat? = null
)

@Serializable
data class ValidationError(
    val message: String,
    val path: String? = null,           // JSONPath, XPath, etc.
    val line: Int? = null,
    val column: Int? = null,
    val errorCode: String? = null
)

@Serializable
data class ValidationWarning(
    val message: String,
    val path: String? = null,
    val suggestion: String? = null
)

/**
 * Inferred schema from source code or data.
 */
@Serializable
data class InferredSchema(
    val format: SchemaFormat,
    val schema: String,                  // Generated schema content
    val confidence: Double = 1.0,        // 0.0 to 1.0 confidence score
    val types: List<InferredType> = emptyList(),
    val sourceInfo: Map<String, String> = emptyMap()
)

@Serializable
data class InferredType(
    val name: String,
    val kind: TypeKind,
    val properties: List<InferredProperty> = emptyList(),
    val annotations: List<String> = emptyList()
)

@Serializable
enum class TypeKind {
    CLASS, INTERFACE, ENUM, UNION, SCALAR, OBJECT, ARRAY, MAP
}

@Serializable
data class InferredProperty(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val optional: Boolean = false,
    val defaultValue: String? = null,
    val annotations: List<String> = emptyList()
)

/**
 * Central schema registry for managing and validating schemas.
 *
 * Features:
 * - Register/retrieve schemas by ID, format, or namespace
 * - Validate documents against registered schemas
 * - Infer schemas from code using ANTLR parsers
 * - Cross-reference and dependency resolution
 */
class SchemaRegistry {
    private val schemas = mutableMapOf<String, SchemaEntry>()
    private val schemasByFormat = mutableMapOf<SchemaFormat, MutableList<String>>()
    private val schemasByNamespace = mutableMapOf<String, MutableList<String>>()

    // Format-specific validators (injected via Koin)
    private val validators = mutableMapOf<SchemaFormat, SchemaValidator>()

    // Schema inferrers (injected via Koin)
    private val inferrers = mutableMapOf<SchemaFormat, SchemaInferrer>()

    /**
     * Register a schema.
     */
    fun register(schema: SchemaEntry) {
        schemas[schema.id] = schema
        schemasByFormat.getOrPut(schema.format) { mutableListOf() }.add(schema.id)
        schema.namespace?.let { ns ->
            schemasByNamespace.getOrPut(ns) { mutableListOf() }.add(schema.id)
        }
        logger.info { "Registered schema: ${schema.id} (${schema.format})" }
    }

    /**
     * Register a validator for a format.
     */
    fun registerValidator(format: SchemaFormat, validator: SchemaValidator) {
        validators[format] = validator
        logger.debug { "Registered validator for $format" }
    }

    /**
     * Register an inferrer for a format.
     */
    fun registerInferrer(format: SchemaFormat, inferrer: SchemaInferrer) {
        inferrers[format] = inferrer
        logger.debug { "Registered inferrer for $format" }
    }

    /**
     * Get schema by ID.
     */
    fun get(id: String): SchemaEntry? = schemas[id]

    /**
     * Get all schemas by format.
     */
    fun getByFormat(format: SchemaFormat): List<SchemaEntry> {
        return schemasByFormat[format]?.mapNotNull { schemas[it] } ?: emptyList()
    }

    /**
     * Get all schemas in namespace.
     */
    fun getByNamespace(namespace: String): List<SchemaEntry> {
        return schemasByNamespace[namespace]?.mapNotNull { schemas[it] } ?: emptyList()
    }

    /**
     * Validate document against a schema.
     */
    fun validate(document: String, schemaId: String): ValidationResult {
        val schema = schemas[schemaId]
            ?: return ValidationResult(
                valid = false,
                errors = listOf(ValidationError("Schema not found: $schemaId"))
            )

        val validator = validators[schema.format]
            ?: return ValidationResult(
                valid = false,
                errors = listOf(ValidationError("No validator for format: ${schema.format}"))
            )

        return try {
            validator.validate(document, schema)
        } catch (e: Exception) {
            ValidationResult(
                valid = false,
                errors = listOf(ValidationError("Validation error: ${e.message}")),
                schemaId = schemaId,
                format = schema.format
            )
        }
    }

    /**
     * Validate document against auto-detected format.
     */
    fun validateAuto(document: String, filename: String): ValidationResult {
        val ext = filename.substringAfterLast('.', "")
        val format = SchemaFormat.fromExtension(ext)

        if (format == SchemaFormat.UNKNOWN) {
            return ValidationResult(
                valid = false,
                errors = listOf(ValidationError("Unknown file format: $ext"))
            )
        }

        val validator = validators[format]
            ?: return ValidationResult(
                valid = false,
                errors = listOf(ValidationError("No validator for format: $format"))
            )

        return try {
            validator.validateSyntax(document, format)
        } catch (e: Exception) {
            ValidationResult(
                valid = false,
                errors = listOf(ValidationError("Validation error: ${e.message}")),
                format = format
            )
        }
    }

    /**
     * Infer schema from source code or data.
     */
    fun infer(source: String, format: SchemaFormat): InferredSchema? {
        val inferrer = inferrers[format] ?: return null
        return try {
            inferrer.infer(source)
        } catch (e: Exception) {
            logger.error(e) { "Schema inference failed for $format" }
            null
        }
    }

    /**
     * Infer schema from source with auto-detected format.
     */
    fun inferAuto(source: String, filename: String): InferredSchema? {
        val ext = filename.substringAfterLast('.', "")
        val format = SchemaFormat.fromExtension(ext)
        return if (format != SchemaFormat.UNKNOWN) infer(source, format) else null
    }

    /**
     * Get all registered schema IDs.
     */
    fun getAllIds(): Set<String> = schemas.keys

    /**
     * Get count of schemas by format.
     */
    fun countByFormat(): Map<SchemaFormat, Int> {
        return schemasByFormat.mapValues { it.value.size }
    }

    /**
     * Clear all schemas.
     */
    fun clear() {
        schemas.clear()
        schemasByFormat.clear()
        schemasByNamespace.clear()
    }
}

/**
 * Interface for format-specific schema validators.
 */
interface SchemaValidator {
    val format: SchemaFormat

    /**
     * Validate document against a schema.
     */
    fun validate(document: String, schema: SchemaEntry): ValidationResult

    /**
     * Validate document syntax only (no schema).
     */
    fun validateSyntax(document: String, format: SchemaFormat): ValidationResult
}

/**
 * Interface for schema inference from source code.
 */
interface SchemaInferrer {
    val format: SchemaFormat

    /**
     * Infer schema from source code or data.
     */
    fun infer(source: String): InferredSchema

    /**
     * Check if this inferrer can handle the given source.
     */
    fun canInfer(source: String): Boolean
}
