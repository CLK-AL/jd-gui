package org.jd.gui.server.schema

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.nulls.shouldBeNull
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class UnifiedSchemaGeneratorTest : DescribeSpec({

    describe("SchemaVersion") {

        describe("fromFileCreated()") {
            it("creates version from file creation time") {
                val tempFile = Files.createTempFile("test-schema", ".kt")
                try {
                    val version = SchemaVersion.fromFileCreated(tempFile)

                    version.shouldNotBeNull()
                    version.source shouldBe SchemaVersion.VersionSource.FILE_CREATED
                    version.timestamp.shouldNotBeNull()
                    version.version.shouldNotBeNull()
                    // Version format should be yyyy.mm.dd.hh.mm.ss.nnnnnnnnn
                    version.version.split(".").size shouldBe 7
                } finally {
                    Files.deleteIfExists(tempFile)
                }
            }

            it("version format matches full precision pattern") {
                val tempFile = Files.createTempFile("test-precision", ".kt")
                try {
                    val version = SchemaVersion.fromFileCreated(tempFile)
                    val parts = version.version.split(".")

                    // Year should be 4 digits
                    parts[0].length shouldBe 4
                    // Month should be 2 digits
                    parts[1].length shouldBe 2
                    // Day should be 2 digits
                    parts[2].length shouldBe 2
                    // Hour should be 2 digits
                    parts[3].length shouldBe 2
                    // Minute should be 2 digits
                    parts[4].length shouldBe 2
                    // Second should be 2 digits
                    parts[5].length shouldBe 2
                    // Nanoseconds should be 9 digits
                    parts[6].length shouldBe 9
                } finally {
                    Files.deleteIfExists(tempFile)
                }
            }
        }

        describe("fromFileModified()") {
            it("creates version from file modification time") {
                val tempFile = Files.createTempFile("test-modified", ".kt")
                try {
                    Files.writeString(tempFile, "test content")
                    val version = SchemaVersion.fromFileModified(tempFile)

                    version.shouldNotBeNull()
                    version.source shouldBe SchemaVersion.VersionSource.FILE_MODIFIED
                    version.timestamp.shouldNotBeNull()
                } finally {
                    Files.deleteIfExists(tempFile)
                }
            }

            it("modification time updates after file write") {
                val tempFile = Files.createTempFile("test-update", ".kt")
                try {
                    val version1 = SchemaVersion.fromFileModified(tempFile)

                    // Add a small delay and modify file
                    Thread.sleep(10)
                    Files.writeString(tempFile, "modified content")

                    val version2 = SchemaVersion.fromFileModified(tempFile)

                    // The modification time should be different after write
                    version2.timestamp.shouldNotBeNull()
                } finally {
                    Files.deleteIfExists(tempFile)
                }
            }
        }

        describe("parse()") {
            it("parses yyyy.mm.dd format correctly") {
                val version = SchemaVersion.parse("2025.02.06")

                version.shouldNotBeNull()
                version.source shouldBe SchemaVersion.VersionSource.EXPLICIT
                version.timestamp.atZone(ZoneOffset.UTC).year shouldBe 2025
                version.timestamp.atZone(ZoneOffset.UTC).monthValue shouldBe 2
                version.timestamp.atZone(ZoneOffset.UTC).dayOfMonth shouldBe 6
            }

            it("parses yyyy.mm.dd.hh.mm.ss.nnnnnnnnn format correctly") {
                val version = SchemaVersion.parse("2025.02.06.14.30.22.123456789")

                version.shouldNotBeNull()
                version.source shouldBe SchemaVersion.VersionSource.EXPLICIT
                val dt = version.timestamp.atZone(ZoneOffset.UTC)
                dt.year shouldBe 2025
                dt.monthValue shouldBe 2
                dt.dayOfMonth shouldBe 6
                dt.hour shouldBe 14
                dt.minute shouldBe 30
                dt.second shouldBe 22
                dt.nano shouldBe 123456789
            }

            it("returns null for invalid format") {
                SchemaVersion.parse("invalid").shouldBeNull()
                SchemaVersion.parse("2025").shouldBeNull()
                SchemaVersion.parse("2025.02").shouldBeNull()
                SchemaVersion.parse("2025.02.06.14").shouldBeNull()
                SchemaVersion.parse("2025.02.06.14.30").shouldBeNull()
            }

            it("returns null for invalid date values") {
                // Invalid month
                SchemaVersion.parse("2025.13.01").shouldBeNull()
                // Invalid day
                SchemaVersion.parse("2025.02.30").shouldBeNull()
            }
        }

        describe("full property") {
            it("returns correct full precision format") {
                val instant = ZonedDateTime.of(2025, 2, 6, 14, 30, 22, 123456789, ZoneOffset.UTC).toInstant()
                val version = SchemaVersion(
                    version = "test",
                    timestamp = instant,
                    source = SchemaVersion.VersionSource.GENERATED
                )

                version.full shouldBe "2025.02.06.14.30.22.123456789"
            }

            it("pads single digit values with zeros") {
                val instant = ZonedDateTime.of(2025, 1, 5, 4, 3, 2, 1, ZoneOffset.UTC).toInstant()
                val version = SchemaVersion(
                    version = "test",
                    timestamp = instant,
                    source = SchemaVersion.VersionSource.GENERATED
                )

                version.full shouldBe "2025.01.05.04.03.02.000000001"
            }
        }

        describe("short property") {
            it("returns yyyy.mm.dd format") {
                val instant = ZonedDateTime.of(2025, 2, 6, 14, 30, 22, 123456789, ZoneOffset.UTC).toInstant()
                val version = SchemaVersion(
                    version = "test",
                    timestamp = instant,
                    source = SchemaVersion.VersionSource.GENERATED
                )

                version.short shouldBe "2025.02.06"
            }

            it("ignores time components") {
                val instant1 = ZonedDateTime.of(2025, 2, 6, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
                val instant2 = ZonedDateTime.of(2025, 2, 6, 23, 59, 59, 999999999, ZoneOffset.UTC).toInstant()

                val version1 = SchemaVersion("v1", instant1, SchemaVersion.VersionSource.GENERATED)
                val version2 = SchemaVersion("v2", instant2, SchemaVersion.VersionSource.GENERATED)

                version1.short shouldBe version2.short
                version1.short shouldBe "2025.02.06"
            }
        }

        describe("calver property") {
            it("returns YYYY.MM.DD format") {
                val instant = ZonedDateTime.of(2025, 2, 6, 14, 30, 22, 0, ZoneOffset.UTC).toInstant()
                val version = SchemaVersion(
                    version = "test",
                    timestamp = instant,
                    source = SchemaVersion.VersionSource.GENERATED
                )

                version.calver shouldBe "2025.02.06"
            }

            it("pads month and day with leading zeros") {
                val instant = ZonedDateTime.of(2025, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
                val version = SchemaVersion("test", instant, SchemaVersion.VersionSource.GENERATED)

                version.calver shouldBe "2025.01.05"
            }
        }

        describe("epochNano property") {
            it("returns seconds.nanos format") {
                val instant = Instant.ofEpochSecond(1738855822L, 123456789L)
                val version = SchemaVersion(
                    version = "test",
                    timestamp = instant,
                    source = SchemaVersion.VersionSource.GENERATED
                )

                version.epochNano shouldBe "1738855822.123456789"
            }

            it("handles zero nanoseconds") {
                val instant = Instant.ofEpochSecond(1738855822L, 0L)
                val version = SchemaVersion("test", instant, SchemaVersion.VersionSource.GENERATED)

                version.epochNano shouldBe "1738855822.0"
            }
        }

        describe("epoch property") {
            it("returns Unix epoch seconds") {
                val instant = Instant.ofEpochSecond(1738855822L, 123456789L)
                val version = SchemaVersion("test", instant, SchemaVersion.VersionSource.GENERATED)

                version.epoch shouldBe 1738855822L
            }
        }

        describe("timestampStr property") {
            it("returns ISO timestamp format") {
                val instant = ZonedDateTime.of(2025, 2, 6, 14, 30, 22, 0, ZoneOffset.UTC).toInstant()
                val version = SchemaVersion("test", instant, SchemaVersion.VersionSource.GENERATED)

                version.timestampStr shouldBe "20250206T143022Z"
            }
        }

        describe("now()") {
            it("creates version with current timestamp") {
                val before = Instant.now()
                val version = SchemaVersion.now()
                val after = Instant.now()

                version.source shouldBe SchemaVersion.VersionSource.GENERATED
                version.timestamp.isAfter(before.minusMillis(1)) shouldBe true
                version.timestamp.isBefore(after.plusMillis(1)) shouldBe true
            }
        }

        describe("explicit()") {
            it("creates version with explicit string") {
                val version = SchemaVersion.explicit("1.0.0")

                version.version shouldBe "1.0.0"
                version.source shouldBe SchemaVersion.VersionSource.EXPLICIT
            }
        }

        describe("fromContentHash()") {
            it("creates version from content hash") {
                val version = SchemaVersion.fromContentHash("some content")

                version.source shouldBe SchemaVersion.VersionSource.CONTENT_HASH
                version.version.length shouldBe 8
            }

            it("produces different hash for different content") {
                val version1 = SchemaVersion.fromContentHash("content A")
                val version2 = SchemaVersion.fromContentHash("content B")

                version1.version shouldNotBe version2.version
            }

            it("produces same hash for same content") {
                val version1 = SchemaVersion.fromContentHash("identical content")
                val version2 = SchemaVersion.fromContentHash("identical content")

                version1.version shouldBe version2.version
            }
        }
    }

    describe("UnifiedSchemaGenerator") {

        val simpleDataClass = """
            data class User(
                val id: Long,
                val name: String,
                val email: String?
            )
        """.trimIndent()

        val dataClassWithEnum = """
            data class User(
                val id: Long,
                val name: String,
                val role: Role
            )

            enum class Role { ADMIN, USER, GUEST }
        """.trimIndent()

        val nestedDataClass = """
            data class User(
                val id: Long,
                val name: String,
                val address: Address?
            )

            data class Address(
                val street: String,
                val city: String,
                val zipCode: String
            )
        """.trimIndent()

        val recursiveDataClass = """
            data class TreeNode(
                val value: String,
                val children: List<TreeNode> = emptyList()
            )
        """.trimIndent()

        val collectionDataClass = """
            data class Container(
                val items: List<String>,
                val tags: Set<String>,
                val metadata: Map<String, Int>
            )
        """.trimIndent()

        describe("generate() for Proto schema") {

            it("generates Proto schema from simple data class") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.protobuf shouldContain "syntax = \"proto3\""
                result.protobuf shouldContain "package com.example"
                result.protobuf shouldContain "message User {"
                result.protobuf shouldContain "int64 id"
                result.protobuf shouldContain "string name"
                result.protobuf shouldContain "optional string email"
            }

            it("generates Proto enum from enum class") {
                val result = UnifiedSchemaGenerator.generate(dataClassWithEnum, "com.example")

                result.protobuf shouldContain "enum Role {"
                result.protobuf shouldContain "ADMIN"
                result.protobuf shouldContain "USER"
                result.protobuf shouldContain "GUEST"
            }

            it("handles nested types in Proto") {
                val result = UnifiedSchemaGenerator.generate(nestedDataClass, "com.example")

                result.protobuf shouldContain "message User {"
                result.protobuf shouldContain "message Address {"
                result.protobuf shouldContain "optional Address address"
            }
        }

        describe("generate() for JSON Schema") {

            it("generates JSON Schema from data class") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.jsonSchema shouldContain "\"${'$'}schema\": \"https://json-schema.org/draft/2020-12/schema\""
                result.jsonSchema shouldContain "\"${'$'}defs\""
                result.jsonSchema shouldContain "\"User\""
                result.jsonSchema shouldContain "\"type\": \"object\""
                result.jsonSchema shouldContain "\"properties\""
            }

            it("handles nullable types in JSON Schema") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                // email is nullable, so it should allow null
                result.jsonSchema shouldContain "\"email\""
            }

            it("includes required fields in JSON Schema") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.jsonSchema shouldContain "\"required\""
                result.jsonSchema shouldContain "\"id\""
                result.jsonSchema shouldContain "\"name\""
            }

            it("generates enum as string type with enum values") {
                val result = UnifiedSchemaGenerator.generate(dataClassWithEnum, "com.example")

                result.jsonSchema shouldContain "\"enum\""
                result.jsonSchema shouldContain "\"ADMIN\""
                result.jsonSchema shouldContain "\"USER\""
                result.jsonSchema shouldContain "\"GUEST\""
            }
        }

        describe("generate() for XSD") {

            it("generates XSD from data class") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.xsd shouldContain "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                result.xsd shouldContain "<xs:schema"
                result.xsd shouldContain "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
                result.xsd shouldContain "complexType name=\"User\""
            }

            it("handles nullable types with minOccurs in XSD") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.xsd shouldContain "minOccurs=\"0\""
            }

            it("maps types correctly to XSD types") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.xsd shouldContain "xs:long"  // Long -> xs:long
                result.xsd shouldContain "xs:string" // String -> xs:string
            }

            it("generates enum as simpleType with restriction") {
                val result = UnifiedSchemaGenerator.generate(dataClassWithEnum, "com.example")

                result.xsd shouldContain "simpleType name=\"Role\""
                result.xsd shouldContain "restriction base=\"xs:string\""
                result.xsd shouldContain "enumeration value=\"ADMIN\""
            }
        }

        describe("handles recursive types") {

            it("marks recursive fields as optional in Proto") {
                val result = UnifiedSchemaGenerator.generate(recursiveDataClass, "com.example")

                result.protobuf shouldContain "message TreeNode {"
                result.protobuf shouldContain "repeated TreeNode children"
            }

            it("handles recursive references in JSON Schema") {
                val result = UnifiedSchemaGenerator.generate(recursiveDataClass, "com.example")

                result.jsonSchema shouldContain "TreeNode"
                result.jsonSchema shouldContain "${'$'}ref"
            }

            it("generates valid TypeScript for recursive types") {
                val result = UnifiedSchemaGenerator.generate(recursiveDataClass, "com.example")

                result.typescript shouldContain "interface TreeNode {"
                result.typescript shouldContain "children"
                result.typescript shouldContain "TreeNode[]"
            }
        }

        describe("handles collection types") {

            it("generates repeated for List in Proto") {
                val result = UnifiedSchemaGenerator.generate(collectionDataClass, "com.example")

                result.protobuf shouldContain "repeated string items"
            }

            it("generates repeated for Set in Proto (no native set)") {
                val result = UnifiedSchemaGenerator.generate(collectionDataClass, "com.example")

                result.protobuf shouldContain "repeated string tags"
            }

            it("generates map for Map in Proto") {
                val result = UnifiedSchemaGenerator.generate(collectionDataClass, "com.example")

                result.protobuf shouldContain "map<string, int32> metadata"
            }

            it("generates array type for List in JSON Schema") {
                val result = UnifiedSchemaGenerator.generate(collectionDataClass, "com.example")

                result.jsonSchema shouldContain "\"type\": \"array\""
            }

            it("generates array type with uniqueItems for Set in JSON Schema") {
                val result = UnifiedSchemaGenerator.generate(collectionDataClass, "com.example")

                result.jsonSchema shouldContain "uniqueItems"
            }

            it("generates object with additionalProperties for Map in JSON Schema") {
                val result = UnifiedSchemaGenerator.generate(collectionDataClass, "com.example")

                result.jsonSchema shouldContain "additionalProperties"
            }

            it("generates array type for List in TypeScript") {
                val result = UnifiedSchemaGenerator.generate(collectionDataClass, "com.example")

                result.typescript shouldContain "string[]"
            }

            it("generates Set type for Set in TypeScript") {
                val result = UnifiedSchemaGenerator.generate(collectionDataClass, "com.example")

                result.typescript shouldContain "Set<string>"
            }

            it("generates Record type for Map in TypeScript") {
                val result = UnifiedSchemaGenerator.generate(collectionDataClass, "com.example")

                result.typescript shouldContain "Record<string, number>"
            }
        }

        describe("version is included in output") {

            it("includes version in Proto output") {
                val version = SchemaVersion.explicit("1.0.0")
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example", version = version)

                result.protobuf shouldContain "Schema version: 1.0.0"
            }

            it("includes version in JSON Schema output") {
                val version = SchemaVersion.explicit("2.0.0")
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example", version = version)

                result.jsonSchema shouldContain "\"version\": \"2.0.0\""
            }

            it("includes version in XSD output") {
                val version = SchemaVersion.explicit("3.0.0")
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example", version = version)

                result.xsd shouldContain "version=\"3.0.0\""
            }

            it("includes version in TypeScript output") {
                val version = SchemaVersion.explicit("4.0.0")
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example", version = version)

                result.typescript shouldContain "Version: 4.0.0"
            }

            it("includes version in GraphQL output") {
                val version = SchemaVersion.explicit("5.0.0")
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example", version = version)

                result.graphql shouldContain "Version: 5.0.0"
            }

            it("includes version in Avro output") {
                val version = SchemaVersion.explicit("6.0.0")
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example", version = version)

                result.avro shouldContain "\"version\": \"6.0.0\""
            }

            it("includes version in Kotlin annotated output") {
                val version = SchemaVersion.explicit("7.0.0")
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example", version = version)

                result.kotlinAnnotated shouldContain "Schema version: 7.0.0"
            }

            it("includes generated timestamp in output") {
                val instant = ZonedDateTime.of(2025, 2, 6, 14, 30, 22, 0, ZoneOffset.UTC).toInstant()
                val version = SchemaVersion("test", instant, SchemaVersion.VersionSource.GENERATED)
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example", version = version)

                result.protobuf shouldContain "20250206T143022Z"
            }

            it("version in result matches provided version") {
                val version = SchemaVersion.explicit("custom-version")
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example", version = version)

                result.version shouldBe version
            }
        }

        describe("generateFromFile()") {

            it("generates schema from file with creation time version") {
                val tempFile = Files.createTempFile("test-schema", ".kt")
                try {
                    Files.writeString(tempFile, simpleDataClass)
                    val result = UnifiedSchemaGenerator.generateFromFile(
                        tempFile,
                        "com.example",
                        useCreationTime = true
                    )

                    result.protobuf shouldContain "message User {"
                    result.version.source shouldBe SchemaVersion.VersionSource.FILE_CREATED
                } finally {
                    Files.deleteIfExists(tempFile)
                }
            }

            it("generates schema from file with modification time version") {
                val tempFile = Files.createTempFile("test-schema", ".kt")
                try {
                    Files.writeString(tempFile, simpleDataClass)
                    val result = UnifiedSchemaGenerator.generateFromFile(
                        tempFile,
                        "com.example",
                        useCreationTime = false
                    )

                    result.protobuf shouldContain "message User {"
                    result.version.source shouldBe SchemaVersion.VersionSource.FILE_MODIFIED
                } finally {
                    Files.deleteIfExists(tempFile)
                }
            }
        }

        describe("GraphQL schema generation") {

            it("generates GraphQL types from data class") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.graphql shouldContain "type User {"
                result.graphql shouldContain "id: Int!"
                result.graphql shouldContain "name: String!"
                result.graphql shouldContain "email: String"  // nullable, so no !
            }

            it("generates GraphQL enums") {
                val result = UnifiedSchemaGenerator.generate(dataClassWithEnum, "com.example")

                result.graphql shouldContain "enum Role {"
                result.graphql shouldContain "ADMIN"
                result.graphql shouldContain "USER"
                result.graphql shouldContain "GUEST"
            }
        }

        describe("Avro schema generation") {

            it("generates Avro record from data class") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.avro shouldContain "\"type\": \"record\""
                result.avro shouldContain "\"name\": \"User\""
                result.avro shouldContain "\"fields\""
            }

            it("generates Avro enum from enum class") {
                val result = UnifiedSchemaGenerator.generate(dataClassWithEnum, "com.example")

                result.avro shouldContain "\"type\": \"enum\""
                result.avro shouldContain "\"symbols\""
                result.avro shouldContain "\"ADMIN\""
            }

            it("handles nullable types with union in Avro") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                // Nullable fields should be union with null
                result.avro shouldContain "[\"null\""
            }
        }

        describe("TypeScript generation") {

            it("generates TypeScript interfaces") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.typescript shouldContain "export interface User {"
                result.typescript shouldContain "id: number;"
                result.typescript shouldContain "name: string;"
                result.typescript shouldContain "email?: string;"  // nullable -> optional
            }

            it("generates TypeScript enums") {
                val result = UnifiedSchemaGenerator.generate(dataClassWithEnum, "com.example")

                result.typescript shouldContain "export enum Role {"
                result.typescript shouldContain "ADMIN = \"ADMIN\""
                result.typescript shouldContain "USER = \"USER\""
                result.typescript shouldContain "GUEST = \"GUEST\""
            }
        }

        describe("Kotlin annotated generation") {

            it("generates Kotlin with Serializable annotation") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.kotlinAnnotated shouldContain "@Serializable"
                result.kotlinAnnotated shouldContain "data class User("
            }

            it("generates Kotlin with ProtoNumber annotations") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.kotlinAnnotated shouldContain "@ProtoNumber"
            }

            it("includes package declaration") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.kotlinAnnotated shouldStartWith "package com.example"
            }

            it("includes necessary imports") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.kotlinAnnotated shouldContain "import kotlinx.serialization.Serializable"
                result.kotlinAnnotated shouldContain "import kotlinx.serialization.protobuf.ProtoNumber"
            }
        }

        describe("types list in result") {

            it("contains parsed types from source") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                result.types.shouldNotBeEmpty()
                result.types.any { it.name == "User" } shouldBe true
            }

            it("includes enum types") {
                val result = UnifiedSchemaGenerator.generate(dataClassWithEnum, "com.example")

                result.types.any { it.name == "Role" && it.kind == TypeKind.ENUM } shouldBe true
            }

            it("includes nested types") {
                val result = UnifiedSchemaGenerator.generate(nestedDataClass, "com.example")

                result.types.any { it.name == "User" } shouldBe true
                result.types.any { it.name == "Address" } shouldBe true
            }

            it("properties are correctly parsed") {
                val result = UnifiedSchemaGenerator.generate(simpleDataClass, "com.example")

                val userType = result.types.find { it.name == "User" }
                userType.shouldNotBeNull()
                userType.properties.any { it.name == "id" && it.type.contains("Long") } shouldBe true
                userType.properties.any { it.name == "email" && it.nullable } shouldBe true
            }
        }

        describe("extension functions") {

            it("String.toAllSchemas() generates schemas") {
                val result = simpleDataClass.toAllSchemas("com.example")

                result.protobuf shouldContain "message User"
                result.version.source shouldBe SchemaVersion.VersionSource.CONTENT_HASH
            }

            it("Path.toAllSchemas() generates schemas from file") {
                val tempFile = Files.createTempFile("test-ext", ".kt")
                try {
                    Files.writeString(tempFile, simpleDataClass)
                    val result = tempFile.toAllSchemas("com.example")

                    result.protobuf shouldContain "message User"
                } finally {
                    Files.deleteIfExists(tempFile)
                }
            }
        }

        describe("sealed class handling") {

            val sealedClassSource = """
                sealed class Event

                data class ClickEvent(
                    val x: Int,
                    val y: Int
                )

                data class KeyEvent(
                    val key: String
                )
            """.trimIndent()

            it("generates Proto oneof for sealed class") {
                val result = UnifiedSchemaGenerator.generate(sealedClassSource, "com.example")

                result.protobuf shouldContain "message Event {"
                result.protobuf shouldContain "oneof value {"
            }

            it("generates TypeScript union for sealed class") {
                val result = UnifiedSchemaGenerator.generate(sealedClassSource, "com.example")

                result.typescript shouldContain "export type Event ="
            }

            it("generates JSON Schema oneOf for sealed class") {
                val result = UnifiedSchemaGenerator.generate(sealedClassSource, "com.example")

                result.jsonSchema shouldContain "oneOf"
            }
        }

        describe("special type mappings") {

            val specialTypesSource = """
                data class SpecialTypes(
                    val uuid: UUID,
                    val timestamp: Instant,
                    val duration: Duration,
                    val bytes: ByteArray
                )
            """.trimIndent()

            it("maps UUID to string in Proto") {
                val result = UnifiedSchemaGenerator.generate(specialTypesSource, "com.example")

                result.protobuf shouldContain "string uuid"
            }

            it("maps Instant to google.protobuf.Timestamp in Proto") {
                val result = UnifiedSchemaGenerator.generate(specialTypesSource, "com.example")

                result.protobuf shouldContain "google.protobuf.Timestamp"
                result.protobuf shouldContain "google/protobuf/timestamp.proto"
            }

            it("maps Duration to google.protobuf.Duration in Proto") {
                val result = UnifiedSchemaGenerator.generate(specialTypesSource, "com.example")

                result.protobuf shouldContain "google.protobuf.Duration"
                result.protobuf shouldContain "google/protobuf/duration.proto"
            }

            it("maps ByteArray to bytes in Proto") {
                val result = UnifiedSchemaGenerator.generate(specialTypesSource, "com.example")

                result.protobuf shouldContain "bytes"
            }

            it("maps UUID to string with format in JSON Schema") {
                val result = UnifiedSchemaGenerator.generate(specialTypesSource, "com.example")

                result.jsonSchema shouldContain "\"format\": \"uuid\""
            }

            it("maps Instant to date-time format in JSON Schema") {
                val result = UnifiedSchemaGenerator.generate(specialTypesSource, "com.example")

                result.jsonSchema shouldContain "\"format\": \"date-time\""
            }
        }

        describe("field numbering with @ProtoNumber") {

            val annotatedSource = """
                data class Annotated(
                    @ProtoNumber(10) val first: String,
                    @ProtoNumber(20) val second: Int,
                    val third: Boolean
                )
            """.trimIndent()

            it("respects explicit ProtoNumber annotations") {
                val result = UnifiedSchemaGenerator.generate(annotatedSource, "com.example")

                result.protobuf shouldContain "first = 10"
                result.protobuf shouldContain "second = 20"
            }

            it("assigns sequential numbers to non-annotated fields") {
                val result = UnifiedSchemaGenerator.generate(annotatedSource, "com.example")

                // third doesn't have explicit number, should get a sequential one
                result.protobuf shouldContain "third ="
            }
        }

        describe("default values handling") {

            val defaultValuesSource = """
                data class WithDefaults(
                    val name: String = "default",
                    val count: Int = 0,
                    val active: Boolean = true
                )
            """.trimIndent()

            it("marks fields with defaults as optional in Proto") {
                val result = UnifiedSchemaGenerator.generate(defaultValuesSource, "com.example")

                // Fields with defaults should be marked optional
                result.protobuf.shouldNotBeNull()
            }

            it("fields with defaults are not in required list in JSON Schema") {
                val result = UnifiedSchemaGenerator.generate(defaultValuesSource, "com.example")

                // Fields with defaults should not be required
                result.jsonSchema.shouldNotBeNull()
            }

            it("fields with defaults are optional in TypeScript") {
                val result = UnifiedSchemaGenerator.generate(defaultValuesSource, "com.example")

                result.typescript shouldContain "name?: string"
                result.typescript shouldContain "count?: number"
                result.typescript shouldContain "active?: boolean"
            }
        }
    }
})
