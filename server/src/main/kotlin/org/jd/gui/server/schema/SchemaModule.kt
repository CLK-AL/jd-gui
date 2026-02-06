package org.jd.gui.server.schema

import mu.KotlinLogging
import org.jd.gui.server.schema.validators.*
import org.jd.gui.server.schema.inferrers.*
import org.koin.dsl.module

private val logger = KotlinLogging.logger {}

/**
 * Koin module for schema registry and validation.
 *
 * Provides:
 * - SchemaRegistry singleton
 * - Format-specific validators (JSON Schema, XSD, OpenAPI, etc.)
 * - Schema inferrers for ANTLR-based inference
 */
val schemaModule = module {

    // Format-specific validators
    single { JsonSchemaValidator() }
    single { XmlSchemaValidator() }
    single { GraphQLSchemaValidator() }
    single { ProtobufSchemaValidator() }
    single { OpenApiValidator() }
    single { AsyncApiValidator() }

    // Schema inferrers
    single { JsonSchemaInferrer() }
    single { TypeScriptSchemaInferrer() }
    single { KotlinSchemaInferrer() }
    single { ProtobufSchemaInferrer() }
    single { GraphQLSchemaInferrer() }

    // Central schema registry
    single {
        SchemaRegistry().apply {
            // Register validators
            registerValidator(SchemaFormat.JSON_SCHEMA, get<JsonSchemaValidator>())
            registerValidator(SchemaFormat.XSD, get<XmlSchemaValidator>())
            registerValidator(SchemaFormat.GRAPHQL_SCHEMA, get<GraphQLSchemaValidator>())
            registerValidator(SchemaFormat.PROTOBUF, get<ProtobufSchemaValidator>())
            registerValidator(SchemaFormat.OPENAPI, get<OpenApiValidator>())
            registerValidator(SchemaFormat.ASYNCAPI, get<AsyncApiValidator>())

            // Register inferrers
            registerInferrer(SchemaFormat.JSON_SCHEMA, get<JsonSchemaInferrer>())
            registerInferrer(SchemaFormat.TYPESCRIPT_DEFS, get<TypeScriptSchemaInferrer>())
            registerInferrer(SchemaFormat.KOTLIN_SERIAL, get<KotlinSchemaInferrer>())
            registerInferrer(SchemaFormat.PROTOBUF, get<ProtobufSchemaInferrer>())
            registerInferrer(SchemaFormat.GRAPHQL_SCHEMA, get<GraphQLSchemaInferrer>())

            logger.info { "SchemaRegistry initialized with ${SchemaFormat.values().size} formats" }
        }
    }

    // Schema validation service
    single { SchemaValidationService(get()) }
}

/**
 * Service for validating documents against schemas.
 */
class SchemaValidationService(
    private val registry: SchemaRegistry
) {

    /**
     * Validate document against a schema by ID.
     */
    fun validate(document: String, schemaId: String): ValidationResult {
        return registry.validate(document, schemaId)
    }

    /**
     * Validate document with auto-detected format.
     */
    fun validateAuto(document: String, filename: String): ValidationResult {
        return registry.validateAuto(document, filename)
    }

    /**
     * Infer schema from source code.
     */
    fun inferSchema(source: String, targetFormat: SchemaFormat): InferredSchema? {
        return registry.infer(source, targetFormat)
    }

    /**
     * Infer schema with auto-detected source format.
     */
    fun inferSchemaAuto(source: String, filename: String): InferredSchema? {
        return registry.inferAuto(source, filename)
    }

    /**
     * Convert schema from one format to another.
     */
    fun convertSchema(
        source: String,
        sourceFormat: SchemaFormat,
        targetFormat: SchemaFormat
    ): String? {
        // Infer from source format
        val inferred = registry.infer(source, sourceFormat) ?: return null

        // Convert to target format
        return when (targetFormat) {
            SchemaFormat.JSON_SCHEMA -> convertToJsonSchema(inferred)
            SchemaFormat.TYPESCRIPT_DEFS -> convertToTypeScript(inferred)
            SchemaFormat.GRAPHQL_SCHEMA -> convertToGraphQL(inferred)
            SchemaFormat.PROTOBUF -> convertToProtobuf(inferred)
            else -> null
        }
    }

    private fun convertToJsonSchema(inferred: InferredSchema): String {
        return buildString {
            appendLine("{")
            appendLine("""  "${"$"}schema": "https://json-schema.org/draft/2020-12/schema",""")
            appendLine("""  "title": "Inferred Schema",""")
            appendLine("""  "type": "object",""")
            appendLine("""  "properties": {""")

            inferred.types.flatMap { it.properties }.forEachIndexed { index, prop ->
                if (index > 0) appendLine(",")
                append("""    "${prop.name}": ${propertyToJsonSchema(prop)}""")
            }

            appendLine()
            appendLine("  }")
            appendLine("}")
        }
    }

    private fun propertyToJsonSchema(prop: InferredProperty): String {
        val type = when (prop.type.lowercase()) {
            "string", "str" -> "string"
            "int", "integer", "long" -> "integer"
            "float", "double", "number" -> "number"
            "bool", "boolean" -> "boolean"
            "array", "list" -> "array"
            else -> "object"
        }
        return """{"type": "$type"}"""
    }

    private fun convertToTypeScript(inferred: InferredSchema): String {
        return buildString {
            inferred.types.forEach { type ->
                appendLine("interface ${type.name} {")
                type.properties.forEach { prop ->
                    val tsType = kotlinToTsType(prop.type)
                    val optional = if (prop.nullable || prop.optional) "?" else ""
                    appendLine("  ${prop.name}$optional: $tsType;")
                }
                appendLine("}")
                appendLine()
            }
        }
    }

    private fun kotlinToTsType(kotlinType: String): String = when (kotlinType.lowercase()) {
        "string" -> "string"
        "int", "long", "short", "byte", "float", "double" -> "number"
        "boolean" -> "boolean"
        "any" -> "any"
        else -> kotlinType
    }

    private fun convertToGraphQL(inferred: InferredSchema): String {
        return buildString {
            inferred.types.forEach { type ->
                val gqlKind = when (type.kind) {
                    TypeKind.INTERFACE -> "interface"
                    TypeKind.ENUM -> "enum"
                    TypeKind.UNION -> "union"
                    else -> "type"
                }
                appendLine("$gqlKind ${type.name} {")
                type.properties.forEach { prop ->
                    val gqlType = kotlinToGraphQLType(prop.type, prop.nullable)
                    appendLine("  ${prop.name}: $gqlType")
                }
                appendLine("}")
                appendLine()
            }
        }
    }

    private fun kotlinToGraphQLType(kotlinType: String, nullable: Boolean): String {
        val base = when (kotlinType.lowercase()) {
            "string" -> "String"
            "int" -> "Int"
            "long" -> "Int"  // GraphQL doesn't have Long
            "float", "double" -> "Float"
            "boolean" -> "Boolean"
            else -> kotlinType
        }
        return if (nullable) base else "$base!"
    }

    private fun convertToProtobuf(inferred: InferredSchema): String {
        return buildString {
            appendLine("syntax = \"proto3\";")
            appendLine()

            inferred.types.forEach { type ->
                appendLine("message ${type.name} {")
                type.properties.forEachIndexed { index, prop ->
                    val protoType = kotlinToProtoType(prop.type)
                    appendLine("  $protoType ${prop.name} = ${index + 1};")
                }
                appendLine("}")
                appendLine()
            }
        }
    }

    private fun kotlinToProtoType(kotlinType: String): String = when (kotlinType.lowercase()) {
        "string" -> "string"
        "int" -> "int32"
        "long" -> "int64"
        "float" -> "float"
        "double" -> "double"
        "boolean" -> "bool"
        "bytes", "bytearray" -> "bytes"
        else -> kotlinType
    }
}
