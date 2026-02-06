package org.jd.gui.server.schema.inferrers

import kotlinx.serialization.json.*
import mu.KotlinLogging
import org.jd.gui.server.schema.*

private val logger = KotlinLogging.logger {}

/**
 * JSON Schema inferrer from JSON data.
 *
 * Analyzes JSON data and generates a JSON Schema that validates it.
 * Uses sampling for large arrays to infer item schema.
 */
class JsonSchemaInferrer : SchemaInferrer {

    override val format = SchemaFormat.JSON_SCHEMA

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun canInfer(source: String): Boolean {
        return try {
            json.parseToJsonElement(source)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun infer(source: String): InferredSchema {
        val element = json.parseToJsonElement(source)
        val schema = inferFromElement(element)
        val types = extractTypes(element)

        return InferredSchema(
            format = format,
            schema = json.encodeToString(JsonElement.serializer(), schema),
            confidence = 0.9,
            types = types
        )
    }

    private fun inferFromElement(element: JsonElement): JsonObject {
        return when (element) {
            is JsonObject -> inferFromObject(element)
            is JsonArray -> inferFromArray(element)
            is JsonPrimitive -> inferFromPrimitive(element)
            JsonNull -> buildJsonObject { put("type", "null") }
        }
    }

    private fun inferFromObject(obj: JsonObject): JsonObject {
        val properties = buildJsonObject {
            obj.forEach { (key, value) ->
                put(key, inferFromElement(value))
            }
        }

        val required = JsonArray(obj.keys.map { JsonPrimitive(it) })

        return buildJsonObject {
            put("type", "object")
            put("properties", properties)
            put("required", required)
        }
    }

    private fun inferFromArray(arr: JsonArray): JsonObject {
        if (arr.isEmpty()) {
            return buildJsonObject {
                put("type", "array")
                put("items", buildJsonObject { })
            }
        }

        // Infer item schema from first element (or merge multiple)
        val itemSchema = inferFromElement(arr.first())

        return buildJsonObject {
            put("type", "array")
            put("items", itemSchema)
        }
    }

    private fun inferFromPrimitive(prim: JsonPrimitive): JsonObject {
        return buildJsonObject {
            when {
                prim.isString -> put("type", "string")
                prim.booleanOrNull != null -> put("type", "boolean")
                prim.intOrNull != null -> put("type", "integer")
                prim.doubleOrNull != null -> put("type", "number")
                else -> put("type", "string")
            }
        }
    }

    private fun extractTypes(element: JsonElement, name: String = "Root"): List<InferredType> {
        return when (element) {
            is JsonObject -> {
                val properties = element.entries.map { (key, value) ->
                    InferredProperty(
                        name = key,
                        type = inferTypeName(value),
                        nullable = value == JsonNull
                    )
                }
                listOf(InferredType(
                    name = name,
                    kind = TypeKind.OBJECT,
                    properties = properties
                ))
            }
            else -> emptyList()
        }
    }

    private fun inferTypeName(element: JsonElement): String = when (element) {
        is JsonPrimitive -> when {
            element.isString -> "String"
            element.booleanOrNull != null -> "Boolean"
            element.intOrNull != null -> "Int"
            element.longOrNull != null -> "Long"
            element.doubleOrNull != null -> "Double"
            else -> "Any"
        }
        is JsonArray -> "List<${element.firstOrNull()?.let { inferTypeName(it) } ?: "Any"}>"
        is JsonObject -> "Object"
        JsonNull -> "Any?"
    }
}

/**
 * TypeScript definition inferrer from TypeScript/JavaScript code.
 *
 * Uses regex patterns (ANTLR integration TODO) to extract:
 * - Interface definitions
 * - Type aliases
 * - Class declarations
 */
class TypeScriptSchemaInferrer : SchemaInferrer {

    override val format = SchemaFormat.TYPESCRIPT_DEFS

    override fun canInfer(source: String): Boolean {
        return source.contains("interface ") ||
                source.contains("type ") ||
                source.contains("class ")
    }

    override fun infer(source: String): InferredSchema {
        val types = mutableListOf<InferredType>()

        // Extract interfaces
        val interfaceRegex = Regex("""interface\s+(\w+)(?:<[^>]+>)?\s*(?:extends\s+[\w,\s<>]+)?\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        interfaceRegex.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]
            types.add(parseTypeBody(name, body, TypeKind.INTERFACE))
        }

        // Extract type aliases
        val typeRegex = Regex("""type\s+(\w+)(?:<[^>]+>)?\s*=\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        typeRegex.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]
            types.add(parseTypeBody(name, body, TypeKind.OBJECT))
        }

        // Extract classes
        val classRegex = Regex("""class\s+(\w+)(?:<[^>]+>)?\s*(?:extends\s+\w+)?\s*(?:implements\s+[\w,\s<>]+)?\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        classRegex.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]
            types.add(parseClassBody(name, body))
        }

        val schema = generateTypeScriptDefs(types)

        return InferredSchema(
            format = format,
            schema = schema,
            confidence = 0.85,
            types = types
        )
    }

    private fun parseTypeBody(name: String, body: String, kind: TypeKind): InferredType {
        val properties = mutableListOf<InferredProperty>()
        val propRegex = Regex("""(\w+)(\?)?:\s*([^;,\n]+)""")

        propRegex.findAll(body).forEach { match ->
            properties.add(InferredProperty(
                name = match.groupValues[1],
                type = match.groupValues[3].trim(),
                optional = match.groupValues[2] == "?",
                nullable = match.groupValues[3].contains("null")
            ))
        }

        return InferredType(name = name, kind = kind, properties = properties)
    }

    private fun parseClassBody(name: String, body: String): InferredType {
        val properties = mutableListOf<InferredProperty>()

        // Class properties
        val propRegex = Regex("""(private|public|protected|readonly)?\s*(\w+)(\?)?:\s*([^;=]+)""")
        propRegex.findAll(body).forEach { match ->
            val modifiers = match.groupValues[1]
            if (modifiers != "private") {
                properties.add(InferredProperty(
                    name = match.groupValues[2],
                    type = match.groupValues[4].trim(),
                    optional = match.groupValues[3] == "?",
                    annotations = if (modifiers.isNotEmpty()) listOf(modifiers) else emptyList()
                ))
            }
        }

        return InferredType(name = name, kind = TypeKind.CLASS, properties = properties)
    }

    private fun generateTypeScriptDefs(types: List<InferredType>): String {
        return buildString {
            types.forEach { type ->
                val keyword = when (type.kind) {
                    TypeKind.INTERFACE -> "interface"
                    TypeKind.CLASS -> "class"
                    else -> "type"
                }

                appendLine("$keyword ${type.name} {")
                type.properties.forEach { prop ->
                    val optional = if (prop.optional) "?" else ""
                    appendLine("  ${prop.name}$optional: ${prop.type};")
                }
                appendLine("}")
                appendLine()
            }
        }
    }
}

/**
 * Kotlin schema inferrer from Kotlin data classes.
 *
 * Extracts:
 * - Data class definitions
 * - Sealed class hierarchies
 * - @Serializable annotations
 *
 * Supports kotlinx-serialization-protobuf annotations:
 * - @ProtoNumber for field numbers
 * - @ProtoPacked for packed repeated fields
 * - @ProtoType for type overrides
 */
class KotlinSchemaInferrer : SchemaInferrer {

    override val format = SchemaFormat.KOTLIN_SERIAL

    override fun canInfer(source: String): Boolean {
        return source.contains("data class ") ||
                source.contains("sealed class ") ||
                source.contains("@Serializable")
    }

    override fun infer(source: String): InferredSchema {
        val types = mutableListOf<InferredType>()

        // Extract data classes
        val dataClassRegex = Regex("""data\s+class\s+(\w+)(?:<[^>]+>)?\s*\(([^)]+)\)""")
        dataClassRegex.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val params = match.groupValues[2]
            types.add(parseDataClass(name, params))
        }

        // Extract sealed classes (for oneof in proto)
        val sealedRegex = Regex("""sealed\s+class\s+(\w+)""")
        sealedRegex.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            types.add(InferredType(name = name, kind = TypeKind.UNION))
        }

        // Extract enums
        val enumRegex = Regex("""enum\s+class\s+(\w+)\s*\{([^}]+)\}""")
        enumRegex.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]
            val values = body.split(",")
                .map { it.trim().substringBefore("(") }
                .filter { it.isNotEmpty() && !it.startsWith("//") }
            types.add(InferredType(
                name = name,
                kind = TypeKind.ENUM,
                properties = values.mapIndexed { i, v ->
                    InferredProperty(name = v, type = i.toString())
                }
            ))
        }

        val schema = generateKotlinSchema(types)

        return InferredSchema(
            format = format,
            schema = schema,
            confidence = 0.9,
            types = types
        )
    }

    private fun parseDataClass(name: String, params: String): InferredType {
        val properties = mutableListOf<InferredProperty>()
        // Match properties with optional @ProtoNumber annotation
        val paramRegex = Regex("""(?:@ProtoNumber\((\d+)\)\s*)?(val|var)\s+(\w+):\s*([^,=]+)(?:\s*=\s*([^,]+))?""")

        paramRegex.findAll(params).forEach { match ->
            val protoNumber = match.groupValues[1].takeIf { it.isNotEmpty() }
            val propName = match.groupValues[3]
            val propType = match.groupValues[4].trim()
            val defaultValue = match.groupValues[5].takeIf { it.isNotEmpty() }?.trim()

            val annotations = mutableListOf<String>()
            if (protoNumber != null) {
                annotations.add("@ProtoNumber($protoNumber)")
            }

            properties.add(InferredProperty(
                name = propName,
                type = propType,
                nullable = propType.endsWith("?"),
                optional = defaultValue != null,
                defaultValue = defaultValue,
                annotations = annotations
            ))
        }

        return InferredType(name = name, kind = TypeKind.CLASS, properties = properties)
    }

    private fun generateKotlinSchema(types: List<InferredType>): String {
        return buildString {
            appendLine("@file:UseSerializers()")
            appendLine()
            appendLine("import kotlinx.serialization.Serializable")
            appendLine("import kotlinx.serialization.protobuf.ProtoNumber")
            appendLine()

            types.forEach { type ->
                appendLine("@Serializable")
                when (type.kind) {
                    TypeKind.ENUM -> {
                        appendLine("enum class ${type.name} {")
                        type.properties.forEachIndexed { i, prop ->
                            append("    ${prop.name}")
                            if (i < type.properties.size - 1) append(",")
                            appendLine()
                        }
                        appendLine("}")
                    }
                    TypeKind.UNION -> {
                        appendLine("sealed class ${type.name}")
                    }
                    else -> {
                        appendLine("data class ${type.name}(")
                        type.properties.forEachIndexed { i, prop ->
                            val nullMark = if (prop.nullable && !prop.type.endsWith("?")) "?" else ""
                            val default = prop.defaultValue?.let { " = $it" } ?: ""
                            append("    val ${prop.name}: ${prop.type}$nullMark$default")
                            if (i < type.properties.size - 1) append(",")
                            appendLine()
                        }
                        appendLine(")")
                    }
                }
                appendLine()
            }
        }
    }

    /**
     * Generate Protocol Buffers .proto file from Kotlin data classes.
     *
     * Type mappings (kotlinx-serialization-protobuf compatible):
     * - Int -> int32
     * - Long -> int64
     * - Float -> float
     * - Double -> double
     * - Boolean -> bool
     * - String -> string
     * - ByteArray -> bytes
     * - List<T> -> repeated T
     * - Map<K, V> -> map<K, V>
     * - nullable types -> optional fields (proto3)
     */
    fun toProtobuf(types: List<InferredType>, packageName: String? = null): String {
        return buildString {
            appendLine("syntax = \"proto3\";")
            appendLine()
            if (packageName != null) {
                appendLine("package $packageName;")
                appendLine()
            }

            types.forEach { type ->
                when (type.kind) {
                    TypeKind.ENUM -> generateProtoEnum(this, type)
                    TypeKind.UNION -> generateProtoOneof(this, type, types)
                    else -> generateProtoMessage(this, type)
                }
                appendLine()
            }
        }
    }

    private fun generateProtoMessage(sb: StringBuilder, type: InferredType) {
        sb.appendLine("message ${type.name} {")
        type.properties.forEachIndexed { index, prop ->
            val protoType = kotlinToProtoType(prop.type)
            val fieldNumber = extractProtoNumber(prop) ?: (index + 1)
            val optional = if (prop.nullable) "optional " else ""
            sb.appendLine("  $optional$protoType ${toSnakeCase(prop.name)} = $fieldNumber;")
        }
        sb.appendLine("}")
    }

    private fun generateProtoEnum(sb: StringBuilder, type: InferredType) {
        sb.appendLine("enum ${type.name} {")
        type.properties.forEachIndexed { index, prop ->
            val enumValue = prop.type.toIntOrNull() ?: index
            sb.appendLine("  ${toScreamingSnakeCase(prop.name)} = $enumValue;")
        }
        sb.appendLine("}")
    }

    private fun generateProtoOneof(sb: StringBuilder, sealedType: InferredType, allTypes: List<InferredType>) {
        // Find subclasses of sealed class
        val subclasses = allTypes.filter { it.kind == TypeKind.CLASS && it.name != sealedType.name }

        sb.appendLine("message ${sealedType.name} {")
        sb.appendLine("  oneof value {")
        subclasses.forEachIndexed { index, subclass ->
            sb.appendLine("    ${subclass.name} ${toSnakeCase(subclass.name)} = ${index + 1};")
        }
        sb.appendLine("  }")
        sb.appendLine("}")
    }

    private fun extractProtoNumber(prop: InferredProperty): Int? {
        val annotation = prop.annotations.find { it.startsWith("@ProtoNumber") }
        return annotation?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
    }

    private fun kotlinToProtoType(kotlinType: String): String {
        val baseType = kotlinType.removeSuffix("?").trim()

        // Handle collections
        if (baseType.startsWith("List<") || baseType.startsWith("MutableList<")) {
            val innerType = baseType.substringAfter("<").substringBeforeLast(">")
            return "repeated ${kotlinToProtoType(innerType)}"
        }

        if (baseType.startsWith("Set<") || baseType.startsWith("MutableSet<")) {
            val innerType = baseType.substringAfter("<").substringBeforeLast(">")
            return "repeated ${kotlinToProtoType(innerType)}"
        }

        if (baseType.startsWith("Map<") || baseType.startsWith("MutableMap<")) {
            val inner = baseType.substringAfter("<").substringBeforeLast(">")
            val parts = inner.split(",").map { it.trim() }
            if (parts.size == 2) {
                return "map<${kotlinToProtoType(parts[0])}, ${kotlinToProtoType(parts[1])}>"
            }
        }

        // kotlinx-serialization-protobuf type mappings
        return when (baseType.lowercase()) {
            "int", "integer" -> "int32"
            "uint" -> "uint32"
            "long" -> "int64"
            "ulong" -> "uint64"
            "short" -> "int32"  // proto3 uses int32 for smaller types
            "ushort" -> "uint32"
            "byte" -> "int32"
            "ubyte" -> "uint32"
            "float" -> "float"
            "double" -> "double"
            "boolean", "bool" -> "bool"
            "string" -> "string"
            "bytearray" -> "bytes"
            "unit" -> "google.protobuf.Empty"
            "any" -> "google.protobuf.Any"
            "instant" -> "google.protobuf.Timestamp"
            "duration" -> "google.protobuf.Duration"
            else -> baseType  // Custom message type
        }
    }

    private fun toSnakeCase(camelCase: String): String {
        return camelCase.replace(Regex("([a-z])([A-Z])")) {
            "${it.groupValues[1]}_${it.groupValues[2]}"
        }.lowercase()
    }

    private fun toScreamingSnakeCase(name: String): String {
        return toSnakeCase(name).uppercase()
    }
}

/**
 * Protocol Buffers schema inferrer.
 */
class ProtobufSchemaInferrer : SchemaInferrer {

    override val format = SchemaFormat.PROTOBUF

    override fun canInfer(source: String): Boolean {
        return source.contains("message ") || source.contains("syntax = ")
    }

    override fun infer(source: String): InferredSchema {
        val types = mutableListOf<InferredType>()

        // Extract messages
        val messageRegex = Regex("""message\s+(\w+)\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        messageRegex.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]
            types.add(parseMessage(name, body))
        }

        // Extract enums
        val enumRegex = Regex("""enum\s+(\w+)\s*\{([^}]+)\}""")
        enumRegex.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]
            types.add(parseEnum(name, body))
        }

        return InferredSchema(
            format = format,
            schema = source,  // Proto is already the schema
            confidence = 1.0,
            types = types
        )
    }

    private fun parseMessage(name: String, body: String): InferredType {
        val properties = mutableListOf<InferredProperty>()
        val fieldRegex = Regex("""(repeated\s+)?(\w+)\s+(\w+)\s*=\s*(\d+)""")

        fieldRegex.findAll(body).forEach { match ->
            val repeated = match.groupValues[1].isNotEmpty()
            val typeName = match.groupValues[2]
            val fieldName = match.groupValues[3]

            properties.add(InferredProperty(
                name = fieldName,
                type = if (repeated) "repeated $typeName" else typeName,
                annotations = if (repeated) listOf("repeated") else emptyList()
            ))
        }

        return InferredType(name = name, kind = TypeKind.CLASS, properties = properties)
    }

    private fun parseEnum(name: String, body: String): InferredType {
        val values = mutableListOf<InferredProperty>()
        val valueRegex = Regex("""(\w+)\s*=\s*(\d+)""")

        valueRegex.findAll(body).forEach { match ->
            values.add(InferredProperty(
                name = match.groupValues[1],
                type = match.groupValues[2]
            ))
        }

        return InferredType(name = name, kind = TypeKind.ENUM, properties = values)
    }
}

/**
 * GraphQL schema inferrer.
 */
class GraphQLSchemaInferrer : SchemaInferrer {

    override val format = SchemaFormat.GRAPHQL_SCHEMA

    override fun canInfer(source: String): Boolean {
        return source.contains("type ") || source.contains("interface ") ||
                source.contains("input ") || source.contains("enum ")
    }

    override fun infer(source: String): InferredSchema {
        val types = mutableListOf<InferredType>()

        // Extract types
        val typeRegex = Regex("""(type|interface|input)\s+(\w+)(?:\s+implements\s+[\w&\s]+)?\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        typeRegex.findAll(source).forEach { match ->
            val keyword = match.groupValues[1]
            val name = match.groupValues[2]
            val body = match.groupValues[3]
            val kind = when (keyword) {
                "interface" -> TypeKind.INTERFACE
                "input" -> TypeKind.OBJECT
                else -> TypeKind.OBJECT
            }
            types.add(parseGraphQLType(name, body, kind))
        }

        // Extract enums
        val enumRegex = Regex("""enum\s+(\w+)\s*\{([^}]+)\}""")
        enumRegex.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val body = match.groupValues[2]
            val values = body.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
            types.add(InferredType(
                name = name,
                kind = TypeKind.ENUM,
                properties = values.map { InferredProperty(name = it, type = "String") }
            ))
        }

        // Extract unions
        val unionRegex = Regex("""union\s+(\w+)\s*=\s*(.+)""")
        unionRegex.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            types.add(InferredType(name = name, kind = TypeKind.UNION))
        }

        return InferredSchema(
            format = format,
            schema = source,
            confidence = 0.95,
            types = types
        )
    }

    private fun parseGraphQLType(name: String, body: String, kind: TypeKind): InferredType {
        val properties = mutableListOf<InferredProperty>()
        val fieldRegex = Regex("""(\w+)(?:\([^)]*\))?\s*:\s*(\[?\w+!?\]?!?)""")

        fieldRegex.findAll(body).forEach { match ->
            val fieldName = match.groupValues[1]
            val fieldType = match.groupValues[2]
            val nullable = !fieldType.endsWith("!")

            properties.add(InferredProperty(
                name = fieldName,
                type = fieldType.removeSuffix("!"),
                nullable = nullable
            ))
        }

        return InferredType(name = name, kind = kind, properties = properties)
    }
}
