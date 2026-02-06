package org.jd.gui.server.schema

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Result containing all generated schema formats from a single Kotlin source.
 */
data class MultiFormatSchema(
    val protobuf: String,
    val jsonSchema: String,
    val xsd: String,
    val typescript: String,
    val graphql: String,
    val avro: String,
    val kotlinAnnotated: String,
    val types: List<InferredType>
)

/**
 * Unified schema generator that produces multiple output formats
 * from Kotlin data classes in a single recursive traversal.
 *
 * Usage:
 * ```
 * val source = """
 *     data class User(
 *         val id: Long,
 *         val name: String,
 *         val email: String?,
 *         val address: Address?,
 *         val roles: List<Role> = emptyList()
 *     )
 *
 *     data class Address(
 *         val street: String,
 *         val city: String,
 *         val zipCode: String
 *     )
 *
 *     enum class Role { ADMIN, USER, GUEST }
 * ```
 *
 * val schemas = UnifiedSchemaGenerator.generate(source, "com.example")
 * // schemas.protobuf  -> .proto file
 * // schemas.jsonSchema -> JSON Schema draft 2020-12
 * // schemas.xsd -> XML Schema Definition
 * // schemas.typescript -> TypeScript interfaces
 * // schemas.graphql -> GraphQL types
 * // schemas.avro -> Avro schema
 * ```
 */
object UnifiedSchemaGenerator {

    /**
     * Generate all schema formats from Kotlin source in one pass.
     */
    fun generate(
        kotlinSource: String,
        packageName: String = "generated",
        rootTypeName: String? = null
    ): MultiFormatSchema {
        // Parse Kotlin source into type graph
        val types = parseKotlinTypes(kotlinSource)
        val typeRegistry = types.associateBy { it.name }
        val rootType = rootTypeName?.let { typeRegistry[it] } ?: types.firstOrNull { it.kind == TypeKind.CLASS }

        // Build all formats in single traversal
        val builders = SchemaBuilders(packageName, typeRegistry)

        // Recursive traversal builds all formats simultaneously
        types.forEach { type ->
            builders.visitType(type)
        }

        return MultiFormatSchema(
            protobuf = builders.buildProtobuf(),
            jsonSchema = builders.buildJsonSchema(rootType?.name),
            xsd = builders.buildXsd(rootType?.name),
            typescript = builders.buildTypeScript(),
            graphql = builders.buildGraphQL(),
            avro = builders.buildAvro(rootType?.name),
            kotlinAnnotated = builders.buildKotlinAnnotated(),
            types = types
        )
    }

    /**
     * Parse Kotlin data classes into type graph.
     */
    private fun parseKotlinTypes(source: String): List<InferredType> {
        val types = mutableListOf<InferredType>()
        val knownTypes = mutableSetOf<String>()

        // First pass: collect type names
        val typeNameRegex = Regex("""(data|sealed|enum)\s+class\s+(\w+)""")
        typeNameRegex.findAll(source).forEach { match ->
            knownTypes.add(match.groupValues[2])
        }

        // Extract data classes
        val dataClassRegex = Regex("""data\s+class\s+(\w+)(?:<[^>]+>)?\s*\(([^)]+)\)""")
        dataClassRegex.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val params = match.groupValues[2]
            types.add(parseDataClass(name, params, knownTypes))
        }

        // Extract sealed classes
        val sealedRegex = Regex("""sealed\s+class\s+(\w+)""")
        sealedRegex.findAll(source).forEach { match ->
            types.add(InferredType(name = match.groupValues[1], kind = TypeKind.UNION))
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

        return types
    }

    private fun parseDataClass(name: String, params: String, knownTypes: Set<String>): InferredType {
        val properties = mutableListOf<InferredProperty>()
        val paramRegex = Regex("""(?:@ProtoNumber\((\d+)\)\s*)?(val|var)\s+(\w+):\s*([^,=]+)(?:\s*=\s*([^,]+))?""")

        var fieldNumber = 1
        paramRegex.findAll(params).forEach { match ->
            val explicitNumber = match.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull()
            val propName = match.groupValues[3]
            val propType = match.groupValues[4].trim()
            val defaultValue = match.groupValues[5].takeIf { it.isNotEmpty() }?.trim()

            val number = explicitNumber ?: fieldNumber++
            val isRecursive = propType.contains(name)
            val isReference = knownTypes.any { propType.contains(it) }

            properties.add(InferredProperty(
                name = propName,
                type = propType,
                nullable = propType.endsWith("?"),
                optional = defaultValue != null || isRecursive,
                defaultValue = defaultValue,
                annotations = listOf(
                    "@ProtoNumber($number)",
                    if (isReference) "@Reference" else "",
                    if (isRecursive) "@Recursive" else ""
                ).filter { it.isNotEmpty() }
            ))
        }

        return InferredType(name = name, kind = TypeKind.CLASS, properties = properties)
    }
}

/**
 * Builders for all schema formats - populated in single traversal.
 */
private class SchemaBuilders(
    private val packageName: String,
    private val typeRegistry: Map<String, InferredType>
) {
    // Proto builder
    private val protoMessages = StringBuilder()
    private val protoEnums = StringBuilder()
    private val protoImports = mutableSetOf<String>()

    // JSON Schema builder
    private val jsonDefinitions = mutableMapOf<String, String>()

    // XSD builder
    private val xsdTypes = StringBuilder()

    // TypeScript builder
    private val tsInterfaces = StringBuilder()

    // GraphQL builder
    private val gqlTypes = StringBuilder()

    // Avro builder
    private val avroTypes = mutableListOf<String>()

    // Kotlin with annotations
    private val kotlinTypes = StringBuilder()

    /**
     * Visit a type and build all format representations.
     */
    fun visitType(type: InferredType) {
        when (type.kind) {
            TypeKind.ENUM -> visitEnum(type)
            TypeKind.UNION -> visitUnion(type)
            else -> visitClass(type)
        }
    }

    private fun visitClass(type: InferredType) {
        // Check for well-known type imports
        type.properties.forEach { prop ->
            if (prop.type.contains("Instant")) protoImports.add("google/protobuf/timestamp.proto")
            if (prop.type.contains("Duration")) protoImports.add("google/protobuf/duration.proto")
        }

        // Proto message
        protoMessages.appendLine("message ${type.name} {")
        type.properties.forEachIndexed { index, prop ->
            val protoType = toProtoType(prop.type)
            val fieldNum = extractFieldNumber(prop) ?: (index + 1)
            val optional = if (prop.nullable || prop.annotations.contains("@Recursive")) "optional " else ""
            protoMessages.appendLine("  $optional$protoType ${toSnakeCase(prop.name)} = $fieldNum;")
        }
        protoMessages.appendLine("}")
        protoMessages.appendLine()

        // JSON Schema definition
        val jsonProps = type.properties.joinToString(",\n") { prop ->
            """      "${prop.name}": ${toJsonSchemaType(prop)}"""
        }
        val required = type.properties
            .filter { !it.nullable && !it.optional }
            .joinToString(", ") { "\"${it.name}\"" }

        jsonDefinitions[type.name] = """
    "${type.name}": {
      "type": "object",
      "properties": {
$jsonProps
      }${if (required.isNotEmpty()) """,
      "required": [$required]""" else ""}
    }"""

        // XSD complexType
        xsdTypes.appendLine("""  <xs:complexType name="${type.name}">""")
        xsdTypes.appendLine("""    <xs:sequence>""")
        type.properties.forEach { prop ->
            val xsdType = toXsdType(prop.type)
            val minOccurs = if (prop.nullable || prop.optional) """minOccurs="0" """ else ""
            val maxOccurs = if (isCollectionType(prop.type)) """maxOccurs="unbounded" """ else ""
            xsdTypes.appendLine("""      <xs:element name="${prop.name}" type="$xsdType" $minOccurs$maxOccurs/>""")
        }
        xsdTypes.appendLine("""    </xs:sequence>""")
        xsdTypes.appendLine("""  </xs:complexType>""")
        xsdTypes.appendLine()

        // TypeScript interface
        tsInterfaces.appendLine("export interface ${type.name} {")
        type.properties.forEach { prop ->
            val tsType = toTypeScriptType(prop.type)
            val optional = if (prop.nullable || prop.optional) "?" else ""
            tsInterfaces.appendLine("  ${prop.name}$optional: $tsType;")
        }
        tsInterfaces.appendLine("}")
        tsInterfaces.appendLine()

        // GraphQL type
        gqlTypes.appendLine("type ${type.name} {")
        type.properties.forEach { prop ->
            val gqlType = toGraphQLType(prop.type, prop.nullable)
            gqlTypes.appendLine("  ${prop.name}: $gqlType")
        }
        gqlTypes.appendLine("}")
        gqlTypes.appendLine()

        // Avro record
        val avroFields = type.properties.joinToString(",\n") { prop ->
            val avroType = toAvroType(prop.type, prop.nullable)
            """    {"name": "${prop.name}", "type": $avroType${prop.defaultValue?.let { ", \"default\": $it" } ?: ""}}"""
        }
        avroTypes.add("""  {
    "type": "record",
    "name": "${type.name}",
    "fields": [
$avroFields
    ]
  }""")

        // Kotlin with annotations
        kotlinTypes.appendLine("@Serializable")
        kotlinTypes.appendLine("data class ${type.name}(")
        type.properties.forEachIndexed { i, prop ->
            val protoNum = prop.annotations.find { it.startsWith("@ProtoNumber") } ?: "@ProtoNumber(${i + 1})"
            kotlinTypes.appendLine("    $protoNum")
            val default = prop.defaultValue?.let { " = $it" } ?: ""
            kotlinTypes.append("    val ${prop.name}: ${prop.type}$default")
            if (i < type.properties.size - 1) kotlinTypes.append(",")
            kotlinTypes.appendLine()
        }
        kotlinTypes.appendLine(")")
        kotlinTypes.appendLine()
    }

    private fun visitEnum(type: InferredType) {
        // Proto enum
        protoEnums.appendLine("enum ${type.name} {")
        type.properties.forEachIndexed { index, prop ->
            val value = prop.type.toIntOrNull() ?: index
            protoEnums.appendLine("  ${toScreamingSnake(prop.name)} = $value;")
        }
        protoEnums.appendLine("}")
        protoEnums.appendLine()

        // JSON Schema enum
        val values = type.properties.joinToString(", ") { "\"${it.name}\"" }
        jsonDefinitions[type.name] = """
    "${type.name}": {
      "type": "string",
      "enum": [$values]
    }"""

        // XSD simpleType
        xsdTypes.appendLine("""  <xs:simpleType name="${type.name}">""")
        xsdTypes.appendLine("""    <xs:restriction base="xs:string">""")
        type.properties.forEach { prop ->
            xsdTypes.appendLine("""      <xs:enumeration value="${prop.name}"/>""")
        }
        xsdTypes.appendLine("""    </xs:restriction>""")
        xsdTypes.appendLine("""  </xs:simpleType>""")
        xsdTypes.appendLine()

        // TypeScript enum
        tsInterfaces.appendLine("export enum ${type.name} {")
        type.properties.forEachIndexed { i, prop ->
            tsInterfaces.append("  ${prop.name} = \"${prop.name}\"")
            if (i < type.properties.size - 1) tsInterfaces.append(",")
            tsInterfaces.appendLine()
        }
        tsInterfaces.appendLine("}")
        tsInterfaces.appendLine()

        // GraphQL enum
        gqlTypes.appendLine("enum ${type.name} {")
        type.properties.forEach { prop ->
            gqlTypes.appendLine("  ${prop.name}")
        }
        gqlTypes.appendLine("}")
        gqlTypes.appendLine()

        // Avro enum
        val avroSymbols = type.properties.joinToString(", ") { "\"${it.name}\"" }
        avroTypes.add("""  {
    "type": "enum",
    "name": "${type.name}",
    "symbols": [$avroSymbols]
  }""")

        // Kotlin enum
        kotlinTypes.appendLine("@Serializable")
        kotlinTypes.appendLine("enum class ${type.name} {")
        type.properties.forEachIndexed { i, prop ->
            kotlinTypes.append("    ${prop.name}")
            if (i < type.properties.size - 1) kotlinTypes.append(",")
            kotlinTypes.appendLine()
        }
        kotlinTypes.appendLine("}")
        kotlinTypes.appendLine()
    }

    private fun visitUnion(type: InferredType) {
        // Proto oneof wrapper
        val subclasses = typeRegistry.values.filter {
            it.kind == TypeKind.CLASS && it.name != type.name
        }
        protoMessages.appendLine("message ${type.name} {")
        protoMessages.appendLine("  oneof value {")
        subclasses.forEachIndexed { index, subclass ->
            protoMessages.appendLine("    ${subclass.name} ${toSnakeCase(subclass.name)} = ${index + 1};")
        }
        protoMessages.appendLine("  }")
        protoMessages.appendLine("}")
        protoMessages.appendLine()

        // JSON Schema oneOf
        val oneOf = subclasses.joinToString(", ") { """{"$$ref": "#/$$defs/${it.name}"}""" }
        jsonDefinitions[type.name] = """
    "${type.name}": {
      "oneOf": [$oneOf]
    }"""

        // TypeScript union
        val union = subclasses.joinToString(" | ") { it.name }
        tsInterfaces.appendLine("export type ${type.name} = $union;")
        tsInterfaces.appendLine()

        // GraphQL union
        gqlTypes.appendLine("union ${type.name} = ${subclasses.joinToString(" | ") { it.name }}")
        gqlTypes.appendLine()

        // Kotlin sealed
        kotlinTypes.appendLine("@Serializable")
        kotlinTypes.appendLine("sealed class ${type.name}")
        kotlinTypes.appendLine()
    }

    // Build final outputs
    fun buildProtobuf(): String = buildString {
        appendLine("syntax = \"proto3\";")
        appendLine()
        appendLine("package $packageName;")
        appendLine()
        protoImports.forEach { appendLine("import \"$it\";") }
        if (protoImports.isNotEmpty()) appendLine()
        append(protoEnums)
        append(protoMessages)
    }

    fun buildJsonSchema(rootType: String?): String = buildString {
        appendLine("{")
        appendLine("""  "$$schema": "https://json-schema.org/draft/2020-12/schema",""")
        appendLine("""  "$$id": "https://$packageName/schema.json",""")
        if (rootType != null) {
            appendLine("""  "$$ref": "#/$$defs/$rootType",""")
        }
        appendLine("""  "$$defs": {""")
        append(jsonDefinitions.values.joinToString(",\n"))
        appendLine()
        appendLine("  }")
        appendLine("}")
    }

    fun buildXsd(rootType: String?): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" """)
        appendLine("""           targetNamespace="https://$packageName" """)
        appendLine("""           xmlns:tns="https://$packageName">""")
        appendLine()
        append(xsdTypes)
        if (rootType != null) {
            appendLine("""  <xs:element name="$rootType" type="tns:$rootType"/>""")
        }
        appendLine("</xs:schema>")
    }

    fun buildTypeScript(): String = buildString {
        appendLine("// Generated TypeScript definitions")
        appendLine("// Package: $packageName")
        appendLine()
        append(tsInterfaces)
    }

    fun buildGraphQL(): String = buildString {
        appendLine("# Generated GraphQL schema")
        appendLine("# Package: $packageName")
        appendLine()
        append(gqlTypes)
    }

    fun buildAvro(rootType: String?): String = buildString {
        appendLine("{")
        appendLine("""  "namespace": "$packageName",""")
        if (rootType != null) {
            appendLine("""  "name": "$rootType",""")
        }
        appendLine("""  "type": "record",""")
        appendLine("""  "types": [""")
        append(avroTypes.joinToString(",\n"))
        appendLine()
        appendLine("  ]")
        appendLine("}")
    }

    fun buildKotlinAnnotated(): String = buildString {
        appendLine("package $packageName")
        appendLine()
        appendLine("import kotlinx.serialization.Serializable")
        appendLine("import kotlinx.serialization.protobuf.ProtoNumber")
        appendLine()
        append(kotlinTypes)
    }

    // Type conversion helpers
    private fun extractFieldNumber(prop: InferredProperty): Int? {
        val annotation = prop.annotations.find { it.startsWith("@ProtoNumber") }
        return annotation?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
    }

    /**
     * Collection type patterns:
     * - List<T>, MutableList<T>, ArrayList<T>
     * - Set<T>, MutableSet<T>, HashSet<T>, LinkedHashSet<T>
     * - Map<K, V>, MutableMap<K, V>, HashMap<K, V>, LinkedHashMap<K, V>
     * - Multimap<K, V> -> Map<K, List<V>> (Guava-style)
     * - SetMultimap<K, V> -> Map<K, Set<V>>
     *
     * Key types: String, Int, Long, UUID, Instant (Timestamp)
     * Proto map keys must be: int32, int64, uint32, uint64, sint32, sint64, bool, string
     */
    private fun toProtoType(kotlinType: String): String {
        val base = kotlinType.removeSuffix("?").trim()
        return when {
            // List types
            isListType(base) -> {
                val inner = extractGenericType(base)
                "repeated ${toProtoType(inner)}"
            }
            // Set types (treated as repeated with unique constraint - proto has no native set)
            isSetType(base) -> {
                val inner = extractGenericType(base)
                "repeated ${toProtoType(inner)}"
            }
            // Multimap<K, V> -> map<K, repeated V> (proto doesn't support, use wrapper message)
            isMultimapType(base) -> {
                val (keyType, valueType) = extractMapTypes(base)
                // Proto can't have repeated values in maps, so we use a wrapper
                "map<${toProtoKeyType(keyType)}, ${valueType}Values>"
            }
            // Map types
            isMapType(base) -> {
                val (keyType, valueType) = extractMapTypes(base)
                "map<${toProtoKeyType(keyType)}, ${toProtoType(valueType)}>"
            }
            typeRegistry.containsKey(base) -> base
            else -> toProtoScalarType(base)
        }
    }

    private fun toProtoKeyType(kotlinType: String): String {
        // Proto map keys: int32, int64, uint32, uint64, sint32, sint64, bool, string
        // UUID and Timestamp keys must be string
        return when (kotlinType.lowercase()) {
            "int", "integer" -> "int32"
            "long" -> "int64"
            "uint" -> "uint32"
            "ulong" -> "uint64"
            "boolean", "bool" -> "bool"
            "string" -> "string"
            "uuid" -> "string"  // UUID as string key
            "instant", "timestamp" -> "string"  // Timestamp as ISO string key
            else -> "string"
        }
    }

    private fun toProtoScalarType(type: String): String = when (type.lowercase()) {
        "int", "integer" -> "int32"
        "uint" -> "uint32"
        "long" -> "int64"
        "ulong" -> "uint64"
        "short" -> "int32"
        "byte" -> "int32"
        "float" -> "float"
        "double" -> "double"
        "boolean", "bool" -> "bool"
        "string" -> "string"
        "bytearray", "bytes" -> "bytes"
        "uuid" -> "string"  // UUID as string
        "instant", "timestamp" -> "google.protobuf.Timestamp"
        "duration" -> "google.protobuf.Duration"
        "localdate" -> "string"  // ISO date string
        "localdatetime" -> "string"  // ISO datetime string
        "zoneddatetime" -> "google.protobuf.Timestamp"
        else -> type
    }

    private fun toJsonSchemaType(prop: InferredProperty): String {
        val base = prop.type.removeSuffix("?").trim()
        val isNullable = prop.nullable

        val typeObj = when {
            isListType(base) || isSetType(base) -> {
                val inner = extractGenericType(base)
                val itemType = toJsonSchemaTypeSimple(inner)
                val uniqueItems = if (isSetType(base)) """, "uniqueItems": true""" else ""
                """{"type": "array", "items": $itemType$uniqueItems}"""
            }
            isMultimapType(base) -> {
                val (keyType, valueType) = extractMapTypes(base)
                val keyFormat = getJsonSchemaKeyFormat(keyType)
                """{"type": "object", "additionalProperties": {"type": "array", "items": ${toJsonSchemaTypeSimple(valueType)}}$keyFormat}"""
            }
            isMapType(base) -> {
                val (keyType, valueType) = extractMapTypes(base)
                val keyFormat = getJsonSchemaKeyFormat(keyType)
                """{"type": "object", "additionalProperties": ${toJsonSchemaTypeSimple(valueType)}$keyFormat}"""
            }
            typeRegistry.containsKey(base) -> """{"$$ref": "#/$$defs/$base"}"""
            else -> toJsonSchemaTypeSimple(base)
        }

        return if (isNullable) {
            typeObj.replace("\"type\":", "\"type\": [").replace("}", ", \"null\"]}")
                .takeIf { it.contains("\"type\":") } ?: typeObj
        } else typeObj
    }

    private fun getJsonSchemaKeyFormat(keyType: String): String = when (keyType.lowercase()) {
        "uuid" -> """, "propertyNames": {"format": "uuid"}"""
        "instant", "timestamp" -> """, "propertyNames": {"format": "date-time"}"""
        "int", "integer", "long" -> """, "propertyNames": {"pattern": "^-?[0-9]+$"}"""
        else -> ""
    }

    private fun toJsonSchemaTypeSimple(type: String): String = when (type.lowercase()) {
        "int", "integer", "long", "short", "byte" -> """{"type": "integer"}"""
        "uint", "ulong", "ushort", "ubyte" -> """{"type": "integer", "minimum": 0}"""
        "float", "double" -> """{"type": "number"}"""
        "boolean", "bool" -> """{"type": "boolean"}"""
        "string" -> """{"type": "string"}"""
        "uuid" -> """{"type": "string", "format": "uuid"}"""
        "instant", "timestamp", "zoneddatetime" -> """{"type": "string", "format": "date-time"}"""
        "localdate" -> """{"type": "string", "format": "date"}"""
        "localdatetime" -> """{"type": "string", "format": "date-time"}"""
        "duration" -> """{"type": "string", "format": "duration"}"""
        "uri", "url" -> """{"type": "string", "format": "uri"}"""
        "email" -> """{"type": "string", "format": "email"}"""
        "bytearray", "bytes" -> """{"type": "string", "contentEncoding": "base64"}"""
        else -> if (typeRegistry.containsKey(type)) """{"$$ref": "#/$$defs/$type"}""" else """{"type": "string"}"""
    }

    private fun toXsdType(kotlinType: String): String {
        val base = kotlinType.removeSuffix("?").trim()
        return when {
            isListType(base) || isSetType(base) -> {
                val inner = extractGenericType(base)
                toXsdType(inner)
            }
            isMapType(base) || isMultimapType(base) -> "tns:MapEntry"  // Custom XSD type
            typeRegistry.containsKey(base) -> "tns:$base"
            else -> toXsdScalarType(base)
        }
    }

    private fun toXsdScalarType(type: String): String = when (type.lowercase()) {
        "int", "integer" -> "xs:int"
        "long" -> "xs:long"
        "short" -> "xs:short"
        "byte" -> "xs:byte"
        "uint" -> "xs:unsignedInt"
        "ulong" -> "xs:unsignedLong"
        "ushort" -> "xs:unsignedShort"
        "ubyte" -> "xs:unsignedByte"
        "float" -> "xs:float"
        "double" -> "xs:double"
        "boolean", "bool" -> "xs:boolean"
        "string" -> "xs:string"
        "uuid" -> "xs:string"  // Pattern: [a-f0-9]{8}-...
        "instant", "timestamp", "zoneddatetime" -> "xs:dateTime"
        "localdate" -> "xs:date"
        "localdatetime" -> "xs:dateTime"
        "duration" -> "xs:duration"
        "uri", "url" -> "xs:anyURI"
        "bytearray", "bytes" -> "xs:base64Binary"
        else -> "xs:string"
    }

    private fun toTypeScriptType(kotlinType: String): String {
        val base = kotlinType.removeSuffix("?").trim()
        return when {
            isListType(base) -> {
                val inner = extractGenericType(base)
                "${toTypeScriptType(inner)}[]"
            }
            isSetType(base) -> {
                val inner = extractGenericType(base)
                "Set<${toTypeScriptType(inner)}>"
            }
            isMultimapType(base) -> {
                val (keyType, valueType) = extractMapTypes(base)
                "Map<${toTypeScriptKeyType(keyType)}, ${toTypeScriptType(valueType)}[]>"
            }
            isMapType(base) -> {
                val (keyType, valueType) = extractMapTypes(base)
                if (keyType.lowercase() in listOf("string", "int", "integer", "long")) {
                    "Record<${toTypeScriptKeyType(keyType)}, ${toTypeScriptType(valueType)}>"
                } else {
                    "Map<${toTypeScriptKeyType(keyType)}, ${toTypeScriptType(valueType)}>"
                }
            }
            typeRegistry.containsKey(base) -> base
            else -> toTypeScriptScalarType(base)
        }
    }

    private fun toTypeScriptKeyType(type: String): String = when (type.lowercase()) {
        "int", "integer", "long", "short", "byte" -> "number"
        "string" -> "string"
        "uuid" -> "string"  // UUID as string
        "instant", "timestamp" -> "string"  // ISO string
        else -> "string"
    }

    private fun toTypeScriptScalarType(type: String): String = when (type.lowercase()) {
        "int", "integer", "long", "short", "byte", "float", "double" -> "number"
        "uint", "ulong", "ushort", "ubyte" -> "number"
        "boolean", "bool" -> "boolean"
        "string" -> "string"
        "uuid" -> "string"  // or use UUID type with uuid package
        "instant", "timestamp", "zoneddatetime", "localdatetime" -> "Date"
        "localdate" -> "string"
        "duration" -> "string"
        "any" -> "any"
        "bytearray", "bytes" -> "Uint8Array"
        else -> type
    }

    private fun toGraphQLType(kotlinType: String, nullable: Boolean): String {
        val base = kotlinType.removeSuffix("?").trim()
        val gqlType = when {
            isListType(base) || isSetType(base) -> {
                val inner = extractGenericType(base)
                "[${toGraphQLType(inner, false)}]"
            }
            isMapType(base) || isMultimapType(base) -> {
                // GraphQL has no native map, use JSON scalar or custom type
                "JSON"
            }
            typeRegistry.containsKey(base) -> base
            else -> toGraphQLScalarType(base)
        }
        return if (nullable) gqlType else "$gqlType!"
    }

    private fun toGraphQLScalarType(type: String): String = when (type.lowercase()) {
        "int", "integer", "short", "byte" -> "Int"
        "long" -> "Int"  // GraphQL has no Long, use custom scalar
        "uint", "ulong" -> "Int"
        "float", "double" -> "Float"
        "boolean", "bool" -> "Boolean"
        "string" -> "String"
        "uuid" -> "ID"  // UUID often maps to ID
        "instant", "timestamp", "zoneddatetime" -> "DateTime"  // Custom scalar
        "localdate" -> "Date"  // Custom scalar
        "localdatetime" -> "DateTime"
        "duration" -> "String"
        else -> type
    }

    private fun toAvroType(kotlinType: String, nullable: Boolean): String {
        val base = kotlinType.removeSuffix("?").trim()
        val avroType = when {
            isListType(base) || isSetType(base) -> {
                val inner = extractGenericType(base)
                """{"type": "array", "items": ${toAvroType(inner, false)}}"""
            }
            isMultimapType(base) -> {
                val (_, valueType) = extractMapTypes(base)
                """{"type": "map", "values": {"type": "array", "items": ${toAvroType(valueType, false)}}}"""
            }
            isMapType(base) -> {
                val (_, valueType) = extractMapTypes(base)
                // Avro maps always have string keys
                """{"type": "map", "values": ${toAvroType(valueType, false)}}"""
            }
            typeRegistry.containsKey(base) -> "\"$base\""
            else -> toAvroScalarType(base)
        }
        return if (nullable) """["null", $avroType]""" else avroType
    }

    private fun toAvroScalarType(type: String): String = when (type.lowercase()) {
        "int", "integer" -> "\"int\""
        "long" -> "\"long\""
        "float" -> "\"float\""
        "double" -> "\"double\""
        "boolean", "bool" -> "\"boolean\""
        "string" -> "\"string\""
        "uuid" -> """{"type": "string", "logicalType": "uuid"}"""
        "instant", "timestamp" -> """{"type": "long", "logicalType": "timestamp-millis"}"""
        "localdate" -> """{"type": "int", "logicalType": "date"}"""
        "localdatetime" -> """{"type": "long", "logicalType": "local-timestamp-millis"}"""
        "duration" -> """{"type": "fixed", "size": 12, "logicalType": "duration"}"""
        "bytearray", "bytes" -> "\"bytes\""
        else -> "\"string\""
    }

    // Collection type detection helpers
    private fun isListType(type: String): Boolean =
        type.startsWith("List<") || type.startsWith("MutableList<") ||
        type.startsWith("ArrayList<") || type.startsWith("LinkedList<")

    private fun isSetType(type: String): Boolean =
        type.startsWith("Set<") || type.startsWith("MutableSet<") ||
        type.startsWith("HashSet<") || type.startsWith("LinkedHashSet<") ||
        type.startsWith("TreeSet<") || type.startsWith("SortedSet<")

    private fun isMapType(type: String): Boolean =
        type.startsWith("Map<") || type.startsWith("MutableMap<") ||
        type.startsWith("HashMap<") || type.startsWith("LinkedHashMap<") ||
        type.startsWith("TreeMap<") || type.startsWith("SortedMap<") ||
        type.startsWith("ConcurrentHashMap<")

    private fun isMultimapType(type: String): Boolean =
        type.startsWith("Multimap<") || type.startsWith("SetMultimap<") ||
        type.startsWith("ListMultimap<") || type.startsWith("HashMultimap<") ||
        type.startsWith("ArrayListMultimap<") || type.startsWith("LinkedHashMultimap<")

    private fun isCollectionType(type: String): Boolean =
        isListType(type) || isSetType(type) || isMapType(type) || isMultimapType(type)

    private fun extractGenericType(type: String): String =
        type.substringAfter("<").substringBeforeLast(">").trim()

    private fun extractMapTypes(type: String): Pair<String, String> {
        val inner = extractGenericType(type)
        val parts = splitGenericParams(inner)
        return Pair(parts.getOrElse(0) { "String" }.trim(), parts.getOrElse(1) { "Any" }.trim())
    }

    private fun splitGenericParams(params: String): List<String> {
        // Handle nested generics: Map<String, List<Int>>
        val result = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        for (c in params) {
            when {
                c == '<' -> { depth++; current.append(c) }
                c == '>' -> { depth--; current.append(c) }
                c == ',' && depth == 0 -> { result.add(current.toString()); current = StringBuilder() }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun toSnakeCase(camelCase: String): String =
        camelCase.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }.lowercase()

    private fun toScreamingSnake(name: String): String = toSnakeCase(name).uppercase()
}

/**
 * Extension to generate all schema formats from Kotlin source.
 */
fun String.toAllSchemas(packageName: String = "generated"): MultiFormatSchema =
    UnifiedSchemaGenerator.generate(this, packageName)
