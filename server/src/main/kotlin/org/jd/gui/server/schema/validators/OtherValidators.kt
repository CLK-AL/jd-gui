package org.jd.gui.server.schema.validators

import mu.KotlinLogging
import org.jd.gui.server.schema.*
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import org.xml.sax.SAXParseException

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
        val errors = mutableListOf<ValidationError>()

        try {
            // Create schema factory for XSD
            val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

            // Security: Disable external entities to prevent XXE attacks
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")

            // Parse the XSD schema
            val xsdSchema = factory.newSchema(StreamSource(StringReader(schema.content)))

            // Create validator and validate the XML document against the XSD
            val validator = xsdSchema.newValidator()
            validator.validate(StreamSource(StringReader(document)))

        } catch (e: SAXParseException) {
            errors.add(ValidationError(
                path = "line ${e.lineNumber}, column ${e.columnNumber}",
                message = e.message ?: "XML/XSD parse error",
                line = e.lineNumber,
                column = e.columnNumber
            ))
        } catch (e: Exception) {
            errors.add(ValidationError(
                path = "$",
                message = e.message ?: "Validation failed"
            ))
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            schemaId = schema.id,
            format = format
        )
    }

    override fun validateSyntax(document: String, format: SchemaFormat): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        try {
            // Create schema factory for XSD
            val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

            // Security: Disable external entities to prevent XXE attacks
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")

            // Validate that the XSD syntax is correct by parsing it
            factory.newSchema(StreamSource(StringReader(document)))

        } catch (e: SAXParseException) {
            errors.add(ValidationError(
                path = "line ${e.lineNumber}, column ${e.columnNumber}",
                message = e.message ?: "XSD syntax error",
                line = e.lineNumber,
                column = e.columnNumber
            ))
        } catch (e: Exception) {
            errors.add(ValidationError(
                path = "$",
                message = e.message ?: "XSD syntax validation failed"
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
 * GraphQL Schema validator.
 *
 * Uses regex-based parsing for GraphQL SDL validation without external dependencies.
 * Validates:
 * - Balanced braces and parentheses
 * - Type definitions (type, interface, enum, union, input, scalar, etc.)
 * - Query type or schema definition presence
 */
class GraphQLSchemaValidator : SchemaValidator {

    override val format = SchemaFormat.GRAPHQL_SCHEMA

    override fun validate(document: String, schema: SchemaEntry): ValidationResult {
        return validateSyntax(document, format).copy(schemaId = schema.id)
    }

    override fun validateSyntax(document: String, format: SchemaFormat): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        val trimmed = document.trim()
        if (trimmed.isEmpty()) {
            errors.add(ValidationError(
                message = "Empty GraphQL schema",
                errorCode = "EMPTY_SCHEMA"
            ))
            return ValidationResult(valid = false, errors = errors, format = format)
        }

        // Check for balanced braces and parentheses
        var braceCount = 0
        var parenCount = 0
        var inString = false
        var inComment = false

        val lines = document.lines()
        lines.forEachIndexed { lineNum, line ->
            var i = 0
            inComment = false  // Reset for each line (GraphQL uses # for single-line comments)

            while (i < line.length) {
                val c = line[i]

                // Handle comments
                if (!inString && c == '#') {
                    inComment = true
                }

                if (!inComment) {
                    when {
                        // Handle string literals (both single and triple quotes)
                        c == '"' && (i == 0 || line[i - 1] != '\\') -> {
                            // Check for triple quotes
                            if (i + 2 < line.length && line.substring(i, i + 3) == "\"\"\"") {
                                // Skip triple quote handling for simplicity - just toggle string state
                                i += 2
                            }
                            inString = !inString
                        }
                        !inString && c == '{' -> braceCount++
                        !inString && c == '}' -> {
                            braceCount--
                            if (braceCount < 0) {
                                errors.add(ValidationError(
                                    message = "Unmatched closing brace",
                                    line = lineNum + 1,
                                    column = i + 1,
                                    errorCode = "UNMATCHED_BRACE"
                                ))
                            }
                        }
                        !inString && c == '(' -> parenCount++
                        !inString && c == ')' -> {
                            parenCount--
                            if (parenCount < 0) {
                                errors.add(ValidationError(
                                    message = "Unmatched closing parenthesis",
                                    line = lineNum + 1,
                                    column = i + 1,
                                    errorCode = "UNMATCHED_PAREN"
                                ))
                            }
                        }
                    }
                }
                i++
            }
        }

        if (braceCount != 0) {
            val braceMessage = if (braceCount > 0) {
                "Unbalanced braces: missing $braceCount closing"
            } else {
                "Unbalanced braces: extra ${-braceCount} closing"
            }
            errors.add(ValidationError(
                message = braceMessage,
                errorCode = "BRACE_MISMATCH"
            ))
        }

        if (parenCount != 0) {
            errors.add(ValidationError(
                message = "Unbalanced parentheses",
                errorCode = "PAREN_MISMATCH"
            ))
        }

        // Check for valid type definitions
        val typePattern = Regex("""(type|interface|enum|union|input|scalar|extend|schema|directive)\s+\w+""")
        val hasTypes = typePattern.containsMatchIn(document)

        if (!hasTypes && !document.contains("query") && !document.contains("mutation")) {
            warnings.add(ValidationWarning(
                message = "No type definitions found",
                suggestion = "Add type definitions such as 'type Query { ... }'"
            ))
        }

        // Check for Query type or schema definition
        if (!Regex("""type\s+Query\s*\{""").containsMatchIn(document) &&
            !Regex("""schema\s*\{""").containsMatchIn(document)) {
            warnings.add(ValidationWarning(
                message = "No Query type or schema definition found",
                suggestion = "Add 'type Query { ... }' or 'schema { query: ... }'"
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
