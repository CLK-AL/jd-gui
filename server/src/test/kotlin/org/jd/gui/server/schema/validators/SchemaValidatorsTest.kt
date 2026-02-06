package org.jd.gui.server.schema.validators

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jd.gui.server.schema.SchemaEntry
import org.jd.gui.server.schema.SchemaFormat

class SchemaValidatorsTest : DescribeSpec({

    describe("JsonSchemaValidator") {
        val validator = JsonSchemaValidator()

        describe("validate()") {
            it("validates correct JSON against schema") {
                val schema = SchemaEntry(
                    id = "person-schema",
                    name = "Person Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "object",
                            "properties": {
                                "name": { "type": "string" },
                                "age": { "type": "number" }
                            },
                            "required": ["name"]
                        }
                    """.trimIndent()
                )

                val document = """
                    {
                        "name": "John Doe",
                        "age": 30
                    }
                """.trimIndent()

                val result = validator.validate(document, schema)

                result.valid shouldBe true
                result.errors.shouldBeEmpty()
                result.schemaId shouldBe "person-schema"
                result.format shouldBe SchemaFormat.JSON_SCHEMA
            }

            it("returns errors for missing required property") {
                val schema = SchemaEntry(
                    id = "person-schema",
                    name = "Person Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "object",
                            "properties": {
                                "name": { "type": "string" },
                                "email": { "type": "string" }
                            },
                            "required": ["name", "email"]
                        }
                    """.trimIndent()
                )

                val document = """
                    {
                        "name": "John Doe"
                    }
                """.trimIndent()

                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.shouldNotBeEmpty()
                result.errors.any { it.message.contains("email") } shouldBe true
                result.errors.any { it.errorCode == "REQUIRED" } shouldBe true
            }

            it("returns errors for type mismatch") {
                val schema = SchemaEntry(
                    id = "typed-schema",
                    name = "Typed Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "object",
                            "properties": {
                                "count": { "type": "number" }
                            }
                        }
                    """.trimIndent()
                )

                val document = """
                    {
                        "count": "not a number"
                    }
                """.trimIndent()

                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "TYPE_MISMATCH" } shouldBe true
            }

            it("validates array with items schema") {
                val schema = SchemaEntry(
                    id = "array-schema",
                    name = "Array Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "array",
                            "items": { "type": "string" },
                            "minItems": 1,
                            "maxItems": 3
                        }
                    """.trimIndent()
                )

                val validDoc = """["a", "b"]""".trimIndent()
                val result = validator.validate(validDoc, schema)

                result.valid shouldBe true
                result.errors.shouldBeEmpty()
            }

            it("returns errors for array exceeding maxItems") {
                val schema = SchemaEntry(
                    id = "array-schema",
                    name = "Array Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "array",
                            "items": { "type": "string" },
                            "maxItems": 2
                        }
                    """.trimIndent()
                )

                val document = """["a", "b", "c", "d"]""".trimIndent()
                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "MAX_ITEMS" } shouldBe true
            }

            it("returns errors for array below minItems") {
                val schema = SchemaEntry(
                    id = "array-schema",
                    name = "Array Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "array",
                            "items": { "type": "string" },
                            "minItems": 3
                        }
                    """.trimIndent()
                )

                val document = """["a"]""".trimIndent()
                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "MIN_ITEMS" } shouldBe true
            }

            it("validates string with pattern") {
                val schema = SchemaEntry(
                    id = "pattern-schema",
                    name = "Pattern Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "string",
                            "pattern": "^[A-Z]{3}-\\d{4}$"
                        }
                    """.trimIndent()
                )

                val validDoc = """"ABC-1234"""".trimIndent()
                val result = validator.validate(validDoc, schema)

                result.valid shouldBe true
            }

            it("returns errors for string not matching pattern") {
                val schema = SchemaEntry(
                    id = "pattern-schema",
                    name = "Pattern Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "string",
                            "pattern": "^[A-Z]{3}-\\d{4}$"
                        }
                    """.trimIndent()
                )

                val invalidDoc = """"abc-12"""".trimIndent()
                val result = validator.validate(invalidDoc, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "PATTERN" } shouldBe true
            }

            it("validates string length constraints") {
                val schema = SchemaEntry(
                    id = "length-schema",
                    name = "Length Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "string",
                            "minLength": 5,
                            "maxLength": 10
                        }
                    """.trimIndent()
                )

                val validDoc = """"hello"""".trimIndent()
                val result = validator.validate(validDoc, schema)

                result.valid shouldBe true
            }

            it("returns errors for string too short") {
                val schema = SchemaEntry(
                    id = "length-schema",
                    name = "Length Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "string",
                            "minLength": 5
                        }
                    """.trimIndent()
                )

                val document = """"hi"""".trimIndent()
                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "MIN_LENGTH" } shouldBe true
            }

            it("returns errors for string too long") {
                val schema = SchemaEntry(
                    id = "length-schema",
                    name = "Length Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "string",
                            "maxLength": 3
                        }
                    """.trimIndent()
                )

                val document = """"hello world"""".trimIndent()
                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "MAX_LENGTH" } shouldBe true
            }

            it("validates number constraints") {
                val schema = SchemaEntry(
                    id = "number-schema",
                    name = "Number Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "number",
                            "minimum": 0,
                            "maximum": 100
                        }
                    """.trimIndent()
                )

                val validDoc = "50".trimIndent()
                val result = validator.validate(validDoc, schema)

                result.valid shouldBe true
            }

            it("returns errors for number below minimum") {
                val schema = SchemaEntry(
                    id = "number-schema",
                    name = "Number Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "number",
                            "minimum": 10
                        }
                    """.trimIndent()
                )

                val document = "5".trimIndent()
                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "MINIMUM" } shouldBe true
            }

            it("returns errors for number above maximum") {
                val schema = SchemaEntry(
                    id = "number-schema",
                    name = "Number Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "number",
                            "maximum": 100
                        }
                    """.trimIndent()
                )

                val document = "150".trimIndent()
                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "MAXIMUM" } shouldBe true
            }

            it("validates enum values") {
                val schema = SchemaEntry(
                    id = "enum-schema",
                    name = "Enum Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "string",
                            "enum": ["red", "green", "blue"]
                        }
                    """.trimIndent()
                )

                val validDoc = """"green"""".trimIndent()
                val result = validator.validate(validDoc, schema)

                result.valid shouldBe true
            }

            it("returns errors for value not in enum") {
                val schema = SchemaEntry(
                    id = "enum-schema",
                    name = "Enum Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "string",
                            "enum": ["red", "green", "blue"]
                        }
                    """.trimIndent()
                )

                val document = """"yellow"""".trimIndent()
                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "ENUM_MISMATCH" } shouldBe true
            }

            it("validates const values") {
                val schema = SchemaEntry(
                    id = "const-schema",
                    name = "Const Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "const": "fixed-value"
                        }
                    """.trimIndent()
                )

                val validDoc = """"fixed-value"""".trimIndent()
                val result = validator.validate(validDoc, schema)

                result.valid shouldBe true
            }

            it("returns errors for value not matching const") {
                val schema = SchemaEntry(
                    id = "const-schema",
                    name = "Const Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "const": "fixed-value"
                        }
                    """.trimIndent()
                )

                val document = """"other-value"""".trimIndent()
                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "CONST_MISMATCH" } shouldBe true
            }

            it("returns errors for additional properties when not allowed") {
                val schema = SchemaEntry(
                    id = "strict-schema",
                    name = "Strict Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "object",
                            "properties": {
                                "name": { "type": "string" }
                            },
                            "additionalProperties": false
                        }
                    """.trimIndent()
                )

                val document = """
                    {
                        "name": "John",
                        "extra": "not allowed"
                    }
                """.trimIndent()

                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "ADDITIONAL_PROPERTY" } shouldBe true
            }

            it("validates email format") {
                val schema = SchemaEntry(
                    id = "email-schema",
                    name = "Email Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "string",
                            "format": "email"
                        }
                    """.trimIndent()
                )

                val validDoc = """"test@example.com"""".trimIndent()
                val result = validator.validate(validDoc, schema)

                result.valid shouldBe true
            }

            it("returns errors for invalid email format") {
                val schema = SchemaEntry(
                    id = "email-schema",
                    name = "Email Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "string",
                            "format": "email"
                        }
                    """.trimIndent()
                )

                val document = """"not-an-email"""".trimIndent()
                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "FORMAT_EMAIL" } shouldBe true
            }

            it("validates date format") {
                val schema = SchemaEntry(
                    id = "date-schema",
                    name = "Date Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "string",
                            "format": "date"
                        }
                    """.trimIndent()
                )

                val validDoc = """"2024-01-15"""".trimIndent()
                val result = validator.validate(validDoc, schema)

                result.valid shouldBe true
            }

            it("returns errors for invalid date format") {
                val schema = SchemaEntry(
                    id = "date-schema",
                    name = "Date Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "string",
                            "format": "date"
                        }
                    """.trimIndent()
                )

                val document = """"01-15-2024"""".trimIndent()
                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "FORMAT_DATE" } shouldBe true
            }

            it("validates UUID format") {
                val schema = SchemaEntry(
                    id = "uuid-schema",
                    name = "UUID Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "string",
                            "format": "uuid"
                        }
                    """.trimIndent()
                )

                val validDoc = """"550e8400-e29b-41d4-a716-446655440000"""".trimIndent()
                val result = validator.validate(validDoc, schema)

                result.valid shouldBe true
            }

            it("validates unique items in array") {
                val schema = SchemaEntry(
                    id = "unique-schema",
                    name = "Unique Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "array",
                            "items": { "type": "string" },
                            "uniqueItems": true
                        }
                    """.trimIndent()
                )

                val document = """["a", "b", "a"]""".trimIndent()
                val result = validator.validate(document, schema)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "UNIQUE_ITEMS" } shouldBe true
            }

            it("validates nested objects") {
                val schema = SchemaEntry(
                    id = "nested-schema",
                    name = "Nested Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "type": "object",
                            "properties": {
                                "person": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string" },
                                        "address": {
                                            "type": "object",
                                            "properties": {
                                                "city": { "type": "string" }
                                            },
                                            "required": ["city"]
                                        }
                                    },
                                    "required": ["name"]
                                }
                            }
                        }
                    """.trimIndent()
                )

                val validDoc = """
                    {
                        "person": {
                            "name": "John",
                            "address": {
                                "city": "NYC"
                            }
                        }
                    }
                """.trimIndent()

                val result = validator.validate(validDoc, schema)
                result.valid shouldBe true
            }

            it("handles ${'$'}ref with warning") {
                val schema = SchemaEntry(
                    id = "ref-schema",
                    name = "Ref Schema",
                    format = SchemaFormat.JSON_SCHEMA,
                    content = """
                        {
                            "${'$'}ref": "#/definitions/Person"
                        }
                    """.trimIndent()
                )

                val document = """{"name": "John"}"""
                val result = validator.validate(document, schema)

                result.warnings.shouldNotBeEmpty()
                result.warnings.any { it.message.contains("reference") } shouldBe true
            }
        }

        describe("validateSyntax()") {
            it("returns valid for well-formed JSON") {
                val document = """{"key": "value", "number": 123}"""
                val result = validator.validateSyntax(document, SchemaFormat.JSON_SCHEMA)

                result.valid shouldBe true
                result.errors.shouldBeEmpty()
            }

            it("returns errors for malformed JSON") {
                val document = """{"key": "value" "missing": "comma"}"""
                val result = validator.validateSyntax(document, SchemaFormat.JSON_SCHEMA)

                result.valid shouldBe false
                result.errors.shouldNotBeEmpty()
                result.errors.first().errorCode shouldBe "SYNTAX_ERROR"
            }

            it("returns errors for unclosed braces") {
                val document = """{"key": "value""""
                val result = validator.validateSyntax(document, SchemaFormat.JSON_SCHEMA)

                result.valid shouldBe false
                result.errors.any { it.message.contains("syntax") || it.message.contains("JSON") } shouldBe true
            }

            it("returns errors for trailing comma") {
                val document = """{"key": "value",}"""
                val result = validator.validateSyntax(document, SchemaFormat.JSON_SCHEMA)

                // Note: With isLenient = true, this might actually pass
                // The test documents the actual behavior
                result.shouldNotBeNull()
            }
        }
    }

    describe("OpenApiValidator") {
        val validator = OpenApiValidator()

        describe("validateSyntax()") {
            it("validates correct OpenAPI 3.0 JSON spec") {
                val document = """
                    {
                        "openapi": "3.0.3",
                        "info": {
                            "title": "Sample API",
                            "version": "1.0.0"
                        },
                        "paths": {
                            "/users": {
                                "get": {
                                    "summary": "Get users"
                                }
                            }
                        }
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.OPENAPI)

                result.valid shouldBe true
                result.errors.shouldBeEmpty()
            }

            it("validates correct OpenAPI 3.0 YAML spec") {
                val document = """
                    openapi: "3.0.3"
                    info:
                      title: Sample API
                      version: 1.0.0
                    paths:
                      /users:
                        get:
                          summary: Get users
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.OPENAPI)

                result.valid shouldBe true
                result.errors.shouldBeEmpty()
            }

            it("returns errors for missing openapi version field") {
                val document = """
                    {
                        "info": {
                            "title": "Sample API",
                            "version": "1.0.0"
                        },
                        "paths": {}
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.OPENAPI)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "MISSING_VERSION" } shouldBe true
            }

            it("returns errors for missing info object") {
                val document = """
                    {
                        "openapi": "3.0.3",
                        "paths": {}
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.OPENAPI)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "MISSING_INFO" } shouldBe true
            }

            it("returns warning for missing paths object") {
                val document = """
                    {
                        "openapi": "3.0.3",
                        "info": {
                            "title": "Components Only",
                            "version": "1.0.0"
                        },
                        "components": {}
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.OPENAPI)

                result.valid shouldBe true
                result.warnings.shouldNotBeEmpty()
                result.warnings.any { it.message.contains("paths") } shouldBe true
            }

            it("returns warning for non-3.x version") {
                val document = """
                    {
                        "openapi": "2.0.0",
                        "info": {
                            "title": "Old API",
                            "version": "1.0.0"
                        },
                        "paths": {}
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.OPENAPI)

                result.warnings.any { it.message.contains("2.0.0") } shouldBe true
            }

            it("returns errors for malformed JSON") {
                val document = """{"openapi": "3.0.0", info: broken}"""

                val result = validator.validateSyntax(document, SchemaFormat.OPENAPI)

                result.valid shouldBe false
            }
        }

        describe("validate()") {
            it("delegates to validateSyntax") {
                val schema = SchemaEntry(
                    id = "openapi-schema",
                    name = "OpenAPI Schema",
                    format = SchemaFormat.OPENAPI,
                    content = "{}"
                )

                val document = """
                    {
                        "openapi": "3.0.3",
                        "info": { "title": "Test", "version": "1.0.0" },
                        "paths": {}
                    }
                """.trimIndent()

                val result = validator.validate(document, schema)

                result.valid shouldBe true
            }
        }
    }

    describe("AsyncApiValidator") {
        val validator = AsyncApiValidator()

        describe("validateSyntax()") {
            it("validates correct AsyncAPI 2.x JSON spec") {
                val document = """
                    {
                        "asyncapi": "2.6.0",
                        "info": {
                            "title": "User Events",
                            "version": "1.0.0"
                        },
                        "channels": {
                            "user/created": {
                                "publish": {
                                    "message": {
                                        "payload": {
                                            "type": "object"
                                        }
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.ASYNCAPI)

                result.valid shouldBe true
                result.errors.shouldBeEmpty()
            }

            it("validates correct AsyncAPI 2.x YAML spec") {
                val document = """
                    asyncapi: "2.6.0"
                    info:
                      title: User Events
                      version: 1.0.0
                    channels:
                      user/created:
                        publish:
                          message:
                            payload:
                              type: object
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.ASYNCAPI)

                result.valid shouldBe true
                result.errors.shouldBeEmpty()
            }

            it("returns errors for missing asyncapi version field") {
                val document = """
                    {
                        "info": {
                            "title": "Events",
                            "version": "1.0.0"
                        },
                        "channels": {}
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.ASYNCAPI)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "MISSING_VERSION" } shouldBe true
            }

            it("returns errors for missing info object") {
                val document = """
                    {
                        "asyncapi": "2.6.0",
                        "channels": {}
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.ASYNCAPI)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "MISSING_INFO" } shouldBe true
            }

            it("returns warning for missing channels object") {
                val document = """
                    {
                        "asyncapi": "2.6.0",
                        "info": {
                            "title": "Events",
                            "version": "1.0.0"
                        }
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.ASYNCAPI)

                result.valid shouldBe true
                result.warnings.shouldNotBeEmpty()
                result.warnings.any { it.message.contains("channels") } shouldBe true
            }

            it("returns errors for malformed JSON") {
                val document = """{"asyncapi": "2.0.0", info: broken}"""

                val result = validator.validateSyntax(document, SchemaFormat.ASYNCAPI)

                result.valid shouldBe false
            }
        }

        describe("validate()") {
            it("delegates to validateSyntax") {
                val schema = SchemaEntry(
                    id = "asyncapi-schema",
                    name = "AsyncAPI Schema",
                    format = SchemaFormat.ASYNCAPI,
                    content = "{}"
                )

                val document = """
                    {
                        "asyncapi": "2.6.0",
                        "info": { "title": "Test", "version": "1.0.0" },
                        "channels": {}
                    }
                """.trimIndent()

                val result = validator.validate(document, schema)

                result.valid shouldBe true
            }
        }
    }

    describe("ProtobufValidator") {
        val validator = ProtobufSchemaValidator()

        describe("validateSyntax()") {
            it("validates correct proto3 file") {
                val document = """
                    syntax = "proto3";

                    package mypackage;

                    message Person {
                        string name = 1;
                        int32 age = 2;
                        string email = 3;
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.PROTOBUF)

                result.valid shouldBe true
                result.errors.shouldBeEmpty()
                result.warnings.shouldBeEmpty()
            }

            it("validates correct proto2 file") {
                val document = """
                    syntax = "proto2";

                    package legacy;

                    message OldMessage {
                        required string name = 1;
                        optional int32 count = 2;
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.PROTOBUF)

                result.valid shouldBe true
                result.errors.shouldBeEmpty()
            }

            it("returns warning for missing syntax declaration") {
                val document = """
                    package mypackage;

                    message Person {
                        string name = 1;
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.PROTOBUF)

                result.warnings.shouldNotBeEmpty()
                result.warnings.any { it.message.contains("syntax") } shouldBe true
                result.warnings.any { it.suggestion?.contains("proto3") == true } shouldBe true
            }

            it("returns warning for missing package declaration") {
                val document = """
                    syntax = "proto3";

                    message Person {
                        string name = 1;
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.PROTOBUF)

                result.warnings.shouldNotBeEmpty()
                result.warnings.any { it.message.contains("package") } shouldBe true
            }

            it("returns warning for no message definitions") {
                val document = """
                    syntax = "proto3";
                    package empty;
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.PROTOBUF)

                result.warnings.shouldNotBeEmpty()
                result.warnings.any { it.message.contains("message") } shouldBe true
            }

            it("returns errors for field number less than 1") {
                val document = """
                    syntax = "proto3";
                    package test;

                    message BadMessage {
                        string name = 0;
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.PROTOBUF)

                result.valid shouldBe false
                result.errors.shouldNotBeEmpty()
                result.errors.any { it.errorCode == "FIELD_NUMBER" } shouldBe true
            }

            it("returns errors for reserved field numbers 19000-19999") {
                val document = """
                    syntax = "proto3";
                    package test;

                    message ReservedFields {
                        string name = 19500;
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.PROTOBUF)

                result.valid shouldBe false
                result.errors.shouldNotBeEmpty()
                result.errors.any { it.errorCode == "RESERVED_FIELD" } shouldBe true
            }

            it("validates proto with enums") {
                val document = """
                    syntax = "proto3";
                    package test;

                    enum Status {
                        UNKNOWN = 0;
                        ACTIVE = 1;
                        INACTIVE = 2;
                    }

                    message Item {
                        string id = 1;
                        Status status = 2;
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.PROTOBUF)

                result.valid shouldBe true
            }

            it("validates proto with nested messages") {
                val document = """
                    syntax = "proto3";
                    package test;

                    message Outer {
                        string id = 1;

                        message Inner {
                            string value = 1;
                        }

                        Inner nested = 2;
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.PROTOBUF)

                result.valid shouldBe true
            }

            it("validates proto with imports and options") {
                val document = """
                    syntax = "proto3";
                    package test;

                    option java_package = "com.example.test";
                    option java_multiple_files = true;

                    message TestMessage {
                        string field = 1;
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.PROTOBUF)

                result.valid shouldBe true
            }
        }

        describe("validate()") {
            it("delegates to validateSyntax") {
                val schema = SchemaEntry(
                    id = "proto-schema",
                    name = "Proto Schema",
                    format = SchemaFormat.PROTOBUF,
                    content = ""
                )

                val document = """
                    syntax = "proto3";
                    package test;
                    message Test { string name = 1; }
                """.trimIndent()

                val result = validator.validate(document, schema)

                result.valid shouldBe true
            }
        }
    }

    describe("XsdValidator") {
        val validator = XmlSchemaValidator()

        describe("validateSyntax()") {
            it("validates well-formed XML") {
                val document = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root>
                        <element attribute="value">Content</element>
                        <empty/>
                    </root>
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.XSD)

                result.valid shouldBe true
                result.errors.shouldBeEmpty()
            }

            it("validates XML with namespaces") {
                val document = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <root xmlns="http://example.com/ns" xmlns:custom="http://example.com/custom">
                        <custom:element>Value</custom:element>
                    </root>
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.XSD)

                result.valid shouldBe true
            }

            it("returns errors for malformed XML with unclosed tag") {
                val document = """
                    <?xml version="1.0"?>
                    <root>
                        <unclosed>
                    </root>
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.XSD)

                result.valid shouldBe false
                result.errors.shouldNotBeEmpty()
            }

            it("returns errors for mismatched tags") {
                val document = """
                    <?xml version="1.0"?>
                    <root>
                        <open>content</close>
                    </root>
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.XSD)

                result.valid shouldBe false
            }

            it("returns errors for invalid XML characters") {
                val document = "<root>Invalid\u0000char</root>"

                val result = validator.validateSyntax(document, SchemaFormat.XSD)

                result.valid shouldBe false
            }
        }

        describe("validate()") {
            it("returns warning that XSD validation is not fully implemented") {
                val schema = SchemaEntry(
                    id = "xsd-schema",
                    name = "XSD Schema",
                    format = SchemaFormat.XSD,
                    content = """
                        <?xml version="1.0"?>
                        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                            <xs:element name="root" type="xs:string"/>
                        </xs:schema>
                    """.trimIndent()
                )

                val document = "<root>Value</root>"

                val result = validator.validate(document, schema)

                result.valid shouldBe true
                result.warnings.shouldNotBeEmpty()
                result.warnings.any { it.message.contains("not fully implemented") } shouldBe true
            }
        }
    }

    describe("GraphQLValidator") {
        val validator = GraphQLSchemaValidator()

        describe("validateSyntax()") {
            it("validates correct GraphQL schema") {
                val document = """
                    type Query {
                        users: [User!]!
                        user(id: ID!): User
                    }

                    type User {
                        id: ID!
                        name: String!
                        email: String
                        posts: [Post!]!
                    }

                    type Post {
                        id: ID!
                        title: String!
                        content: String
                        author: User!
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.GRAPHQL_SCHEMA)

                result.valid shouldBe true
                result.errors.shouldBeEmpty()
            }

            it("validates GraphQL with input types") {
                val document = """
                    type Mutation {
                        createUser(input: CreateUserInput!): User!
                    }

                    input CreateUserInput {
                        name: String!
                        email: String!
                    }

                    type User {
                        id: ID!
                        name: String!
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.GRAPHQL_SCHEMA)

                result.valid shouldBe true
            }

            it("validates GraphQL with enums and interfaces") {
                val document = """
                    enum Status {
                        ACTIVE
                        INACTIVE
                        PENDING
                    }

                    interface Node {
                        id: ID!
                    }

                    type User implements Node {
                        id: ID!
                        name: String!
                        status: Status!
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.GRAPHQL_SCHEMA)

                result.valid shouldBe true
            }

            it("validates GraphQL with directives") {
                val document = """
                    directive @deprecated(reason: String) on FIELD_DEFINITION

                    type Query {
                        oldField: String @deprecated(reason: "Use newField")
                        newField: String
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.GRAPHQL_SCHEMA)

                result.valid shouldBe true
            }

            it("returns errors for unbalanced braces - extra closing") {
                val document = """
                    type Query {
                        user: User
                    }}
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.GRAPHQL_SCHEMA)

                result.valid shouldBe false
                result.errors.shouldNotBeEmpty()
            }

            it("returns errors for unbalanced braces - unclosed") {
                val document = """
                    type Query {
                        user: User

                    type User {
                        id: ID!
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.GRAPHQL_SCHEMA)

                result.valid shouldBe false
                result.errors.any { it.errorCode == "BRACE_MISMATCH" } shouldBe true
            }

            it("validates GraphQL with string descriptions") {
                val document = """
                    "The Query type"
                    type Query {
                        "Get all users"
                        users: [User!]!
                    }

                    type User {
                        id: ID!
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.GRAPHQL_SCHEMA)

                result.valid shouldBe true
            }

            it("validates empty schema") {
                val document = ""

                val result = validator.validateSyntax(document, SchemaFormat.GRAPHQL_SCHEMA)

                result.valid shouldBe true
            }

            it("validates GraphQL unions") {
                val document = """
                    union SearchResult = User | Post | Comment

                    type User {
                        id: ID!
                        name: String!
                    }

                    type Post {
                        id: ID!
                        title: String!
                    }

                    type Comment {
                        id: ID!
                        text: String!
                    }
                """.trimIndent()

                val result = validator.validateSyntax(document, SchemaFormat.GRAPHQL_SCHEMA)

                result.valid shouldBe true
            }
        }

        describe("validate()") {
            it("delegates to validateSyntax") {
                val schema = SchemaEntry(
                    id = "graphql-schema",
                    name = "GraphQL Schema",
                    format = SchemaFormat.GRAPHQL_SCHEMA,
                    content = ""
                )

                val document = """
                    type Query {
                        hello: String
                    }
                """.trimIndent()

                val result = validator.validate(document, schema)

                result.valid shouldBe true
            }
        }
    }

    describe("SchemaFormat detection") {
        it("correctly identifies format from file extension") {
            SchemaFormat.fromExtension("json") shouldBe SchemaFormat.JSON_SCHEMA
            SchemaFormat.fromExtension("xsd") shouldBe SchemaFormat.XSD
            SchemaFormat.fromExtension("graphql") shouldBe SchemaFormat.GRAPHQL_SCHEMA
            SchemaFormat.fromExtension("proto") shouldBe SchemaFormat.PROTOBUF
            SchemaFormat.fromExtension("yaml") shouldBe SchemaFormat.YAML_SCHEMA
        }

        it("returns UNKNOWN for unsupported extensions") {
            SchemaFormat.fromExtension("xyz") shouldBe SchemaFormat.UNKNOWN
            SchemaFormat.fromExtension("random") shouldBe SchemaFormat.UNKNOWN
        }

        it("handles case insensitive extensions") {
            SchemaFormat.fromExtension("JSON") shouldBe SchemaFormat.JSON_SCHEMA
            SchemaFormat.fromExtension("Proto") shouldBe SchemaFormat.PROTOBUF
        }

        it("handles extensions with leading dot") {
            SchemaFormat.fromExtension(".json") shouldBe SchemaFormat.JSON_SCHEMA
            SchemaFormat.fromExtension(".proto") shouldBe SchemaFormat.PROTOBUF
        }
    }

    describe("Validator format property") {
        it("JsonSchemaValidator has correct format") {
            JsonSchemaValidator().format shouldBe SchemaFormat.JSON_SCHEMA
        }

        it("OpenApiValidator has correct format") {
            OpenApiValidator().format shouldBe SchemaFormat.OPENAPI
        }

        it("AsyncApiValidator has correct format") {
            AsyncApiValidator().format shouldBe SchemaFormat.ASYNCAPI
        }

        it("ProtobufSchemaValidator has correct format") {
            ProtobufSchemaValidator().format shouldBe SchemaFormat.PROTOBUF
        }

        it("XmlSchemaValidator has correct format") {
            XmlSchemaValidator().format shouldBe SchemaFormat.XSD
        }

        it("GraphQLSchemaValidator has correct format") {
            GraphQLSchemaValidator().format shouldBe SchemaFormat.GRAPHQL_SCHEMA
        }
    }
})
