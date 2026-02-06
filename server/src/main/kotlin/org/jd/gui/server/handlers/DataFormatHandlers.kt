package org.jd.gui.server.handlers

import kotlinx.serialization.json.*
import mu.KotlinLogging
import org.jd.gui.server.di.MimeCategory

private val logger = KotlinLogging.logger {}

/**
 * Base implementation for data format handlers
 */
abstract class AbstractDataFormatHandler : DataFormatHandler {
    override val category: MimeCategory = MimeCategory.DATA

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val validation = validateSchema(content, null)
            val formatted = format(content)

            ProcessingResult(
                success = validation.valid,
                contentType = primaryMimeType,
                data = formatted,
                metadata = extractMetadata(content),
                error = validation.errors.firstOrNull()?.message
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to process ${content.name}" }
            ProcessingResult(
                success = false,
                contentType = primaryMimeType,
                error = e.message
            )
        }
    }
}

/**
 * JSON file handler
 */
class JsonHandler : AbstractDataFormatHandler() {
    override val handlerId = "json"
    override val displayName = "JSON"
    override val supportedExtensions = setOf("json", "jsonc", "json5")
    override val mimeTypes = setOf("application/json")
    override val priority = 10

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    override suspend fun parse(content: FileContent): Any? {
        return try {
            val text = content.asString().let { s ->
                // Remove comments for JSONC support
                if (content.extension == "jsonc") {
                    s.lines().filter { !it.trim().startsWith("//") }.joinToString("\n")
                } else s
            }
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            logger.warn { "Failed to parse JSON: ${e.message}" }
            null
        }
    }

    override suspend fun validateSchema(content: FileContent, schema: String?): ValidationResult {
        return try {
            parse(content)
            ValidationResult(valid = true)
        } catch (e: Exception) {
            ValidationResult(
                valid = false,
                errors = listOf(ValidationError(message = e.message ?: "Invalid JSON"))
            )
        }
    }

    override suspend fun format(content: FileContent, indent: Int): String {
        return try {
            val element = Json.parseToJsonElement(content.asString())
            json.encodeToString(JsonElement.serializer(), element)
        } catch (e: Exception) {
            content.asString()
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val parsed = parse(content)
        return mapOf(
            "type" to when (parsed) {
                is JsonObject -> "object"
                is JsonArray -> "array"
                else -> "unknown"
            },
            "size" to content.size.toString()
        )
    }
}

/**
 * XML file handler
 */
class XmlHandler : AbstractDataFormatHandler() {
    override val handlerId = "xml"
    override val displayName = "XML"
    override val supportedExtensions = setOf("xml", "xsd", "xsl", "xslt", "xaml", "fxml", "svg")
    override val mimeTypes = setOf("application/xml", "text/xml")
    override val priority = 10

    override suspend fun parse(content: FileContent): Any? {
        return try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            builder.parse(content.asInputStream())
        } catch (e: Exception) {
            logger.warn { "Failed to parse XML: ${e.message}" }
            null
        }
    }

    override suspend fun validateSchema(content: FileContent, schema: String?): ValidationResult {
        return try {
            parse(content)
            ValidationResult(valid = true)
        } catch (e: Exception) {
            ValidationResult(
                valid = false,
                errors = listOf(ValidationError(message = e.message ?: "Invalid XML"))
            )
        }
    }

    override suspend fun format(content: FileContent, indent: Int): String {
        return try {
            val doc = parse(content) as? org.w3c.dom.Document ?: return content.asString()
            val transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", indent.toString())
            val writer = java.io.StringWriter()
            transformer.transform(javax.xml.transform.dom.DOMSource(doc), javax.xml.transform.stream.StreamResult(writer))
            writer.toString()
        } catch (e: Exception) {
            content.asString()
        }
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val doc = parse(content) as? org.w3c.dom.Document
        return mapOf(
            "rootElement" to (doc?.documentElement?.tagName ?: "unknown"),
            "encoding" to (doc?.xmlEncoding ?: "UTF-8"),
            "size" to content.size.toString()
        )
    }
}

/**
 * YAML file handler
 */
class YamlHandler : AbstractDataFormatHandler() {
    override val handlerId = "yaml"
    override val displayName = "YAML"
    override val supportedExtensions = setOf("yaml", "yml")
    override val mimeTypes = setOf("application/x-yaml", "text/yaml")
    override val priority = 10

    override suspend fun parse(content: FileContent): Any? {
        // Simple YAML parsing without external library
        return try {
            parseYamlToMap(content.asString())
        } catch (e: Exception) {
            logger.warn { "Failed to parse YAML: ${e.message}" }
            null
        }
    }

    private fun parseYamlToMap(yaml: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        var currentKey: String? = null

        yaml.lines().forEach { line ->
            if (line.isBlank() || line.trim().startsWith("#")) return@forEach

            val keyMatch = Regex("""^(\w+):\s*(.*)$""").find(line.trim())
            if (keyMatch != null) {
                currentKey = keyMatch.groupValues[1]
                val value = keyMatch.groupValues[2].trim()
                result[currentKey!!] = if (value.isEmpty()) null else parseYamlValue(value)
            }
        }
        return result
    }

    private fun parseYamlValue(value: String): Any {
        return when {
            value == "true" || value == "yes" -> true
            value == "false" || value == "no" -> false
            value == "null" || value == "~" -> "null"
            value.toIntOrNull() != null -> value.toInt()
            value.toDoubleOrNull() != null -> value.toDouble()
            value.startsWith("\"") && value.endsWith("\"") -> value.drop(1).dropLast(1)
            value.startsWith("'") && value.endsWith("'") -> value.drop(1).dropLast(1)
            else -> value
        }
    }

    override suspend fun validateSchema(content: FileContent, schema: String?): ValidationResult {
        return try {
            parse(content)
            ValidationResult(valid = true)
        } catch (e: Exception) {
            ValidationResult(
                valid = false,
                errors = listOf(ValidationError(message = e.message ?: "Invalid YAML"))
            )
        }
    }

    override suspend fun format(content: FileContent, indent: Int): String {
        // YAML is already human-readable, just clean up
        return content.asString().lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val lines = content.asString().lines().filter { it.isNotBlank() && !it.trim().startsWith("#") }
        return mapOf(
            "lineCount" to lines.size.toString(),
            "size" to content.size.toString()
        )
    }
}

/**
 * TOML file handler
 */
class TomlHandler : AbstractDataFormatHandler() {
    override val handlerId = "toml"
    override val displayName = "TOML"
    override val supportedExtensions = setOf("toml")
    override val mimeTypes = setOf("application/toml")
    override val priority = 10

    override suspend fun parse(content: FileContent): Any? {
        return try {
            parseTomlToMap(content.asString())
        } catch (e: Exception) {
            logger.warn { "Failed to parse TOML: ${e.message}" }
            null
        }
    }

    private fun parseTomlToMap(toml: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        var currentSection = result

        toml.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach

            // Section header
            val sectionMatch = Regex("""^\[(.+)]$""").find(trimmed)
            if (sectionMatch != null) {
                val sectionName = sectionMatch.groupValues[1]
                val newSection = mutableMapOf<String, Any?>()
                result[sectionName] = newSection
                currentSection = newSection
                return@forEach
            }

            // Key-value pair
            val kvMatch = Regex("""^(\w+)\s*=\s*(.+)$""").find(trimmed)
            if (kvMatch != null) {
                val key = kvMatch.groupValues[1]
                val value = kvMatch.groupValues[2].trim()
                currentSection[key] = parseTomlValue(value)
            }
        }
        return result
    }

    private fun parseTomlValue(value: String): Any {
        return when {
            value == "true" -> true
            value == "false" -> false
            value.startsWith("\"") && value.endsWith("\"") -> value.drop(1).dropLast(1)
            value.startsWith("'") && value.endsWith("'") -> value.drop(1).dropLast(1)
            value.toIntOrNull() != null -> value.toInt()
            value.toDoubleOrNull() != null -> value.toDouble()
            else -> value
        }
    }

    override suspend fun validateSchema(content: FileContent, schema: String?): ValidationResult {
        return try {
            parse(content)
            ValidationResult(valid = true)
        } catch (e: Exception) {
            ValidationResult(
                valid = false,
                errors = listOf(ValidationError(message = e.message ?: "Invalid TOML"))
            )
        }
    }

    override suspend fun format(content: FileContent, indent: Int): String {
        return content.asString()
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val parsed = parse(content) as? Map<*, *>
        return mapOf(
            "sectionCount" to (parsed?.keys?.size ?: 0).toString(),
            "size" to content.size.toString()
        )
    }
}

/**
 * Properties file handler
 */
class PropertiesHandler : AbstractDataFormatHandler() {
    override val handlerId = "properties"
    override val displayName = "Properties"
    override val supportedExtensions = setOf("properties", "ini", "cfg", "conf")
    override val mimeTypes = setOf("text/x-java-properties")
    override val priority = 10

    override suspend fun parse(content: FileContent): Any? {
        return try {
            val props = java.util.Properties()
            props.load(content.asInputStream())
            props.toMap()
        } catch (e: Exception) {
            logger.warn { "Failed to parse properties: ${e.message}" }
            null
        }
    }

    override suspend fun validateSchema(content: FileContent, schema: String?): ValidationResult {
        return try {
            parse(content)
            ValidationResult(valid = true)
        } catch (e: Exception) {
            ValidationResult(
                valid = false,
                errors = listOf(ValidationError(message = e.message ?: "Invalid properties file"))
            )
        }
    }

    override suspend fun format(content: FileContent, indent: Int): String {
        return content.asString()
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val parsed = parse(content) as? Map<*, *>
        return mapOf(
            "propertyCount" to (parsed?.size ?: 0).toString(),
            "size" to content.size.toString()
        )
    }
}

/**
 * SQL file handler
 */
class SqlHandler : AbstractDataFormatHandler() {
    override val handlerId = "sql"
    override val displayName = "SQL"
    override val supportedExtensions = setOf("sql", "ddl", "dml")
    override val mimeTypes = setOf("application/sql", "text/x-sql")
    override val priority = 10

    override suspend fun parse(content: FileContent): Any? {
        // SQL doesn't have a standard object representation
        return content.asString()
    }

    override suspend fun validateSchema(content: FileContent, schema: String?): ValidationResult {
        // Basic SQL validation - check for common syntax issues
        val text = content.asString()
        val errors = mutableListOf<ValidationError>()

        text.lines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("--") && !trimmed.startsWith("/*")) {
                // Check for unclosed strings
                val singleQuotes = trimmed.count { it == '\'' }
                if (singleQuotes % 2 != 0) {
                    errors.add(ValidationError("Unclosed string literal", index + 1))
                }
            }
        }

        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    override suspend fun format(content: FileContent, indent: Int): String {
        // Basic SQL formatting
        return content.asString()
            .replace(Regex("""\bSELECT\b""", RegexOption.IGNORE_CASE), "\nSELECT")
            .replace(Regex("""\bFROM\b""", RegexOption.IGNORE_CASE), "\nFROM")
            .replace(Regex("""\bWHERE\b""", RegexOption.IGNORE_CASE), "\nWHERE")
            .replace(Regex("""\bAND\b""", RegexOption.IGNORE_CASE), "\n  AND")
            .replace(Regex("""\bOR\b""", RegexOption.IGNORE_CASE), "\n  OR")
            .replace(Regex("""\bORDER BY\b""", RegexOption.IGNORE_CASE), "\nORDER BY")
            .replace(Regex("""\bGROUP BY\b""", RegexOption.IGNORE_CASE), "\nGROUP BY")
            .replace(Regex("""\bJOIN\b""", RegexOption.IGNORE_CASE), "\nJOIN")
            .trim()
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val text = content.asString().uppercase()
        return mapOf(
            "hasSelect" to text.contains("SELECT").toString(),
            "hasInsert" to text.contains("INSERT").toString(),
            "hasUpdate" to text.contains("UPDATE").toString(),
            "hasDelete" to text.contains("DELETE").toString(),
            "hasCreateTable" to text.contains("CREATE TABLE").toString(),
            "size" to content.size.toString()
        )
    }
}

/**
 * Markdown file handler
 */
class MarkdownHandler : AbstractDataFormatHandler() {
    override val handlerId = "markdown"
    override val displayName = "Markdown"
    override val supportedExtensions = setOf("md", "markdown", "mdown")
    override val mimeTypes = setOf("text/markdown")
    override val category = MimeCategory.WEB
    override val priority = 10

    override suspend fun parse(content: FileContent): Any? {
        // Parse markdown structure
        val lines = content.asString().lines()
        val headers = mutableListOf<Pair<Int, String>>()

        lines.forEach { line ->
            val headerMatch = Regex("""^(#{1,6})\s+(.+)$""").find(line)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val title = headerMatch.groupValues[2]
                headers.add(level to title)
            }
        }

        return mapOf(
            "headers" to headers,
            "content" to content.asString()
        )
    }

    override suspend fun validateSchema(content: FileContent, schema: String?): ValidationResult {
        return ValidationResult(valid = true) // Markdown is always valid
    }

    override suspend fun format(content: FileContent, indent: Int): String {
        return content.asString()
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val text = content.asString()
        val lines = text.lines()

        val codeBlocks = Regex("""```[\s\S]*?```""").findAll(text).count()
        val links = Regex("""\[.+?\]\(.+?\)""").findAll(text).count()
        val images = Regex("""!\[.*?\]\(.+?\)""").findAll(text).count()
        val headers = lines.count { it.startsWith("#") }

        return mapOf(
            "lineCount" to lines.size.toString(),
            "headerCount" to headers.toString(),
            "codeBlockCount" to codeBlocks.toString(),
            "linkCount" to links.toString(),
            "imageCount" to images.toString(),
            "size" to content.size.toString()
        )
    }
}

/**
 * HTML file handler
 */
class HtmlHandler : AbstractDataFormatHandler() {
    override val handlerId = "html"
    override val displayName = "HTML"
    override val supportedExtensions = setOf("html", "htm", "xhtml")
    override val mimeTypes = setOf("text/html")
    override val category = MimeCategory.WEB
    override val priority = 10

    override suspend fun parse(content: FileContent): Any? {
        return content.asString()
    }

    override suspend fun validateSchema(content: FileContent, schema: String?): ValidationResult {
        val text = content.asString()
        val errors = mutableListOf<ValidationError>()

        // Basic HTML validation
        val openTags = Regex("""<(\w+)(?:\s[^>]*)?>""").findAll(text).map { it.groupValues[1].lowercase() }.toList()
        val closeTags = Regex("""</(\w+)>""").findAll(text).map { it.groupValues[1].lowercase() }.toList()
        val selfClosing = setOf("br", "hr", "img", "input", "meta", "link", "area", "base", "col", "embed", "param", "source", "track", "wbr")

        val needsClosing = openTags.filter { it !in selfClosing }
        if (needsClosing.size != closeTags.size) {
            errors.add(ValidationError("Mismatched open/close tags"))
        }

        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    override suspend fun format(content: FileContent, indent: Int): String {
        // Basic HTML formatting
        var result = content.asString()
        result = result.replace(Regex(""">\s*<"""), ">\n<")
        return result
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val text = content.asString()
        val titleMatch = Regex("""<title>(.+?)</title>""", RegexOption.IGNORE_CASE).find(text)
        val scriptCount = Regex("""<script""", RegexOption.IGNORE_CASE).findAll(text).count()
        val styleCount = Regex("""<style""", RegexOption.IGNORE_CASE).findAll(text).count()

        return mapOf(
            "title" to (titleMatch?.groupValues?.get(1) ?: ""),
            "scriptCount" to scriptCount.toString(),
            "styleCount" to styleCount.toString(),
            "size" to content.size.toString()
        )
    }
}

/**
 * CSS file handler
 */
class CssHandler : AbstractDataFormatHandler() {
    override val handlerId = "css"
    override val displayName = "CSS"
    override val supportedExtensions = setOf("css", "scss", "sass", "less")
    override val mimeTypes = setOf("text/css")
    override val category = MimeCategory.WEB
    override val priority = 10

    override suspend fun parse(content: FileContent): Any? {
        return content.asString()
    }

    override suspend fun validateSchema(content: FileContent, schema: String?): ValidationResult {
        val text = content.asString()
        val errors = mutableListOf<ValidationError>()

        // Check for balanced braces
        val openBraces = text.count { it == '{' }
        val closeBraces = text.count { it == '}' }
        if (openBraces != closeBraces) {
            errors.add(ValidationError("Unbalanced braces: $openBraces open, $closeBraces close"))
        }

        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    override suspend fun format(content: FileContent, indent: Int): String {
        var result = content.asString()
        // Basic CSS formatting
        result = result.replace("{", " {\n  ")
        result = result.replace("}", "\n}\n")
        result = result.replace(";", ";\n  ")
        return result.trim()
    }

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val text = content.asString()
        val ruleCount = Regex("""\{""").findAll(text).count()
        val selectorCount = text.split("{").size - 1
        val hasVariables = text.contains("--") || text.contains("$")

        return mapOf(
            "ruleCount" to ruleCount.toString(),
            "selectorCount" to selectorCount.toString(),
            "hasVariables" to hasVariables.toString(),
            "size" to content.size.toString()
        )
    }
}
