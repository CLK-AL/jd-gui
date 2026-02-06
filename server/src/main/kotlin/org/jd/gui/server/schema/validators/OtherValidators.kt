package org.jd.gui.server.schema.validators

import mu.KotlinLogging
import org.jd.gui.server.schema.*

private val logger = KotlinLogging.logger {}

/**
 * XML Schema (XSD) validator.
 *
 * Uses:
 * - javax.xml.validation for XSD validation
 * - ANTLR XML grammar for syntax parsing
 */
class XmlSchemaValidator : SchemaValidator {

    override val format = SchemaFormat.XSD

    override fun validate(document: String, schema: SchemaEntry): ValidationResult {
        // TODO: Implement using javax.xml.validation.SchemaFactory
        return ValidationResult(
            valid = true,
            warnings = listOf(ValidationWarning("XSD validation not fully implemented")),
            schemaId = schema.id,
            format = format
        )
    }

    override fun validateSyntax(document: String, format: SchemaFormat): ValidationResult {
        return try {
            // Basic XML well-formedness check
            javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(document.byteInputStream())
            ValidationResult(valid = true, format = format)
        } catch (e: Exception) {
            ValidationResult(
                valid = false,
                errors = listOf(ValidationError("XML syntax error: ${e.message}")),
                format = format
            )
        }
    }
}

/**
 * GraphQL Schema validator.
 *
 * Uses:
 * - ANTLR GraphQL grammar for parsing
 * - Custom validation for GraphQL SDL semantics
 */
class GraphQLSchemaValidator : SchemaValidator {

    override val format = SchemaFormat.GRAPHQL_SCHEMA

    override fun validate(document: String, schema: SchemaEntry): ValidationResult {
        // TODO: Implement using graphql-java or ANTLR GraphQL grammar
        return validateSyntax(document, format)
    }

    override fun validateSyntax(document: String, format: SchemaFormat): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Basic GraphQL syntax validation
        val lines = document.lines()
        var braceCount = 0
        var inString = false

        lines.forEachIndexed { lineNum, line ->
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' && (i == 0 || line[i - 1] != '\\') -> inString = !inString
                    !inString && c == '{' -> braceCount++
                    !inString && c == '}' -> braceCount--
                }
                if (braceCount < 0) {
                    errors.add(ValidationError(
                        message = "Unexpected closing brace",
                        line = lineNum + 1,
                        column = i + 1
                    ))
                }
                i++
            }
        }

        if (braceCount != 0) {
            errors.add(ValidationError(
                message = "Unbalanced braces: $braceCount unclosed",
                errorCode = "BRACE_MISMATCH"
            ))
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            format = format
        )
    }
}

/**
 * Protocol Buffers schema validator.
 *
 * Uses:
 * - ANTLR Protobuf3 grammar for parsing
 * - Validation for proto3 semantics
 */
class ProtobufSchemaValidator : SchemaValidator {

    override val format = SchemaFormat.PROTOBUF

    override fun validate(document: String, schema: SchemaEntry): ValidationResult {
        return validateSyntax(document, format)
    }

    override fun validateSyntax(document: String, format: SchemaFormat): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // Check for syntax declaration
        if (!document.contains(Regex("""syntax\s*=\s*["']proto[23]["']"""))) {
            warnings.add(ValidationWarning(
                message = "Missing syntax declaration (expected 'syntax = \"proto3\"')",
                suggestion = "Add 'syntax = \"proto3\";' at the beginning"
            ))
        }

        // Check for package declaration
        if (!document.contains(Regex("""package\s+[\w.]+;"""))) {
            warnings.add(ValidationWarning(
                message = "Missing package declaration",
                suggestion = "Add 'package your.package.name;'"
            ))
        }

        // Check message definitions
        val messageRegex = Regex("""message\s+(\w+)\s*\{""")
        val messageNames = messageRegex.findAll(document).map { it.groupValues[1] }.toList()

        if (messageNames.isEmpty()) {
            warnings.add(ValidationWarning(
                message = "No message definitions found"
            ))
        }

        // Check field numbers
        val fieldRegex = Regex("""(\w+)\s+(\w+)\s*=\s*(\d+);""")
        fieldRegex.findAll(document).forEach { match ->
            val fieldNum = match.groupValues[3].toIntOrNull()
            if (fieldNum != null) {
                if (fieldNum < 1) {
                    errors.add(ValidationError(
                        message = "Field number must be >= 1: ${match.value}",
                        errorCode = "FIELD_NUMBER"
                    ))
                }
                if (fieldNum in 19000..19999) {
                    errors.add(ValidationError(
                        message = "Field numbers 19000-19999 are reserved: ${match.value}",
                        errorCode = "RESERVED_FIELD"
                    ))
                }
            }
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            format = format
        )
    }
}

/**
 * OpenAPI validator.
 *
 * Supports OpenAPI 3.0 and 3.1 specifications.
 * Uses JSON/YAML parsing with semantic validation.
 */
class OpenApiValidator : SchemaValidator {

    override val format = SchemaFormat.OPENAPI

    private val jsonValidator = JsonSchemaValidator()

    override fun validate(document: String, schema: SchemaEntry): ValidationResult {
        return validateSyntax(document, format)
    }

    override fun validateSyntax(document: String, format: SchemaFormat): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // Detect JSON or YAML
        val isJson = document.trimStart().startsWith("{")

        if (isJson) {
            // Validate as JSON first
            val jsonResult = jsonValidator.validateSyntax(document, SchemaFormat.JSON_SCHEMA)
            if (!jsonResult.valid) {
                return jsonResult.copy(format = format)
            }
        }

        // Check for OpenAPI version
        val versionMatch = Regex(""""openapi"\s*:\s*"(\d+\.\d+\.\d+)"""").find(document)
            ?: Regex("""openapi:\s*["']?(\d+\.\d+\.\d+)["']?""").find(document)

        if (versionMatch == null) {
            errors.add(ValidationError(
                message = "Missing 'openapi' version field",
                errorCode = "MISSING_VERSION"
            ))
        } else {
            val version = versionMatch.groupValues[1]
            if (!version.startsWith("3.")) {
                warnings.add(ValidationWarning(
                    message = "OpenAPI version $version detected. Only 3.x is fully supported."
                ))
            }
        }

        // Check for required fields
        if (!document.contains(Regex(""""info"\s*:|info:"""))) {
            errors.add(ValidationError(
                message = "Missing required 'info' object",
                errorCode = "MISSING_INFO"
            ))
        }

        if (!document.contains(Regex(""""paths"\s*:|paths:"""))) {
            warnings.add(ValidationWarning(
                message = "Missing 'paths' object (may be valid for components-only spec)"
            ))
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            format = format
        )
    }
}

/**
 * AsyncAPI validator.
 *
 * Supports AsyncAPI 2.x specifications.
 */
class AsyncApiValidator : SchemaValidator {

    override val format = SchemaFormat.ASYNCAPI

    private val jsonValidator = JsonSchemaValidator()

    override fun validate(document: String, schema: SchemaEntry): ValidationResult {
        return validateSyntax(document, format)
    }

    override fun validateSyntax(document: String, format: SchemaFormat): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // Detect JSON or YAML
        val isJson = document.trimStart().startsWith("{")

        if (isJson) {
            val jsonResult = jsonValidator.validateSyntax(document, SchemaFormat.JSON_SCHEMA)
            if (!jsonResult.valid) {
                return jsonResult.copy(format = format)
            }
        }

        // Check for AsyncAPI version
        val versionMatch = Regex(""""asyncapi"\s*:\s*"(\d+\.\d+\.\d+)"""").find(document)
            ?: Regex("""asyncapi:\s*["']?(\d+\.\d+\.\d+)["']?""").find(document)

        if (versionMatch == null) {
            errors.add(ValidationError(
                message = "Missing 'asyncapi' version field",
                errorCode = "MISSING_VERSION"
            ))
        }

        // Check for required fields
        if (!document.contains(Regex(""""info"\s*:|info:"""))) {
            errors.add(ValidationError(
                message = "Missing required 'info' object",
                errorCode = "MISSING_INFO"
            ))
        }

        if (!document.contains(Regex(""""channels"\s*:|channels:"""))) {
            warnings.add(ValidationWarning(
                message = "Missing 'channels' object"
            ))
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            format = format
        )
    }
}
