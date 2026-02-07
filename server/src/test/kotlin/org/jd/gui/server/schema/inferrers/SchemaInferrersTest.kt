package org.jd.gui.server.schema.inferrers

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jd.gui.server.schema.SchemaFormat
import org.jd.gui.server.schema.TypeKind

class SchemaInferrersTest : DescribeSpec({

    describe("JsonSchemaInferrer") {
        val inferrer = JsonSchemaInferrer()

        describe("format") {
            it("returns JSON_SCHEMA format") {
                inferrer.format shouldBe SchemaFormat.JSON_SCHEMA
            }
        }

        describe("canInfer()") {
            it("returns true for valid JSON object") {
                val json = """{"name": "test", "value": 42}"""
                inferrer.canInfer(json) shouldBe true
            }

            it("returns true for valid JSON array") {
                val json = """[1, 2, 3]"""
                inferrer.canInfer(json) shouldBe true
            }

            it("returns true for JSON primitive") {
                inferrer.canInfer(""""hello"""") shouldBe true
                inferrer.canInfer("42") shouldBe true
                inferrer.canInfer("true") shouldBe true
            }

            it("returns false for invalid JSON") {
                inferrer.canInfer("{not valid json}") shouldBe false
                inferrer.canInfer("random text") shouldBe false
            }

            it("returns false for malformed JSON with missing brackets") {
                inferrer.canInfer("""{"key": "value"""") shouldBe false
            }
        }

        describe("infer()") {
            describe("simple objects") {
                it("infers schema for simple object with string and number") {
                    val json = """{"name": "John", "age": 30}"""
                    val result = inferrer.infer(json)

                    result.format shouldBe SchemaFormat.JSON_SCHEMA
                    result.schema shouldContain "\"type\":\"object\""
                    result.schema shouldContain "\"properties\""
                    result.schema shouldContain "\"name\""
                    result.schema shouldContain "\"age\""
                    result.confidence shouldBe 0.9
                }

                it("infers correct types for primitives") {
                    val json = """{"str": "hello", "num": 123, "bool": true, "float": 3.14}"""
                    val result = inferrer.infer(json)

                    result.schema shouldContain "\"string\""
                    result.schema shouldContain "\"integer\""
                    result.schema shouldContain "\"boolean\""
                    result.schema shouldContain "\"number\""
                }

                it("marks all object properties as required") {
                    val json = """{"id": 1, "name": "test"}"""
                    val result = inferrer.infer(json)

                    result.schema shouldContain "\"required\""
                    result.schema shouldContain "\"id\""
                    result.schema shouldContain "\"name\""
                }
            }

            describe("arrays") {
                it("infers schema for array of strings") {
                    val json = """["apple", "banana", "cherry"]"""
                    val result = inferrer.infer(json)

                    result.schema shouldContain "\"type\":\"array\""
                    result.schema shouldContain "\"items\""
                    result.schema shouldContain "\"string\""
                }

                it("infers schema for array of objects") {
                    val json = """[{"id": 1, "name": "first"}, {"id": 2, "name": "second"}]"""
                    val result = inferrer.infer(json)

                    result.schema shouldContain "\"type\":\"array\""
                    result.schema shouldContain "\"items\""
                    result.schema shouldContain "\"object\""
                }

                it("handles empty arrays") {
                    val json = """[]"""
                    val result = inferrer.infer(json)

                    result.schema shouldContain "\"type\":\"array\""
                    result.schema shouldContain "\"items\""
                }

                it("infers schema for array of numbers") {
                    val json = """[1, 2, 3, 4, 5]"""
                    val result = inferrer.infer(json)

                    result.schema shouldContain "\"type\":\"array\""
                    result.schema shouldContain "\"integer\""
                }
            }

            describe("nested structures") {
                it("infers schema for nested objects") {
                    val json = """{
                        "user": {
                            "name": "John",
                            "address": {
                                "city": "NYC",
                                "zip": "10001"
                            }
                        }
                    }"""
                    val result = inferrer.infer(json)

                    result.schema shouldContain "\"user\""
                    result.schema shouldContain "\"address\""
                    result.schema shouldContain "\"city\""
                    result.schema shouldContain "\"zip\""
                }

                it("infers schema for objects containing arrays") {
                    val json = """{"items": [1, 2, 3], "tags": ["a", "b"]}"""
                    val result = inferrer.infer(json)

                    result.schema shouldContain "\"items\""
                    result.schema shouldContain "\"tags\""
                    result.schema shouldContain "\"array\""
                }
            }

            describe("null handling") {
                it("infers null type correctly") {
                    val json = """{"value": null}"""
                    val result = inferrer.infer(json)

                    result.schema shouldContain "\"null\""
                }
            }

            describe("extracted types") {
                it("extracts type information from object") {
                    val json = """{"id": 1, "name": "test", "active": true}"""
                    val result = inferrer.infer(json)

                    result.types.shouldNotBeEmpty()
                    result.types.first().name shouldBe "Root"
                    result.types.first().kind shouldBe TypeKind.OBJECT
                    result.types.first().properties.shouldNotBeEmpty()
                }

                it("extracts property names and types") {
                    val json = """{"id": 123, "name": "test"}"""
                    val result = inferrer.infer(json)

                    val properties = result.types.first().properties
                    properties.any { it.name == "id" && it.type == "Int" } shouldBe true
                    properties.any { it.name == "name" && it.type == "String" } shouldBe true
                }

                it("marks null properties as nullable") {
                    val json = """{"value": null}"""
                    val result = inferrer.infer(json)

                    val props = result.types.first().properties
                    props.any { it.name == "value" && it.nullable } shouldBe true
                }

                it("infers List type for array properties") {
                    val json = """{"items": [1, 2, 3]}"""
                    val result = inferrer.infer(json)

                    val props = result.types.first().properties
                    props.any { it.name == "items" && it.type.startsWith("List<") } shouldBe true
                }
            }
        }
    }

    describe("TypeScriptSchemaInferrer") {
        val inferrer = TypeScriptSchemaInferrer()

        describe("format") {
            it("returns TYPESCRIPT_DEFS format") {
                inferrer.format shouldBe SchemaFormat.TYPESCRIPT_DEFS
            }
        }

        describe("canInfer()") {
            it("returns true for interface definition") {
                val ts = "interface User { name: string; }"
                inferrer.canInfer(ts) shouldBe true
            }

            it("returns true for type alias") {
                val ts = "type Status = { active: boolean; }"
                inferrer.canInfer(ts) shouldBe true
            }

            it("returns true for class definition") {
                val ts = "class UserService { private client: Client; }"
                inferrer.canInfer(ts) shouldBe true
            }

            it("returns false for plain JavaScript") {
                val js = "const x = 5; function foo() {}"
                inferrer.canInfer(js) shouldBe false
            }

            it("returns false for random text") {
                inferrer.canInfer("random text without keywords") shouldBe false
            }
        }

        describe("infer()") {
            describe("interfaces") {
                it("extracts interface with simple properties") {
                    val ts = """
                        interface User {
                            id: number;
                            name: string;
                            email: string;
                        }
                    """.trimIndent()

                    val result = inferrer.infer(ts)

                    result.types.shouldNotBeEmpty()
                    result.types.first().name shouldBe "User"
                    result.types.first().kind shouldBe TypeKind.INTERFACE
                    result.types.first().properties.shouldHaveSize(3)
                }

                it("handles optional properties") {
                    val ts = """
                        interface Config {
                            host: string;
                            port?: number;
                        }
                    """.trimIndent()

                    val result = inferrer.infer(ts)

                    val props = result.types.first().properties
                    props.find { it.name == "host" }?.optional shouldBe false
                    props.find { it.name == "port" }?.optional shouldBe true
                }

                it("handles nullable types") {
                    val ts = """
                        interface Nullable {
                            value: string | null;
                        }
                    """.trimIndent()

                    val result = inferrer.infer(ts)

                    val props = result.types.first().properties
                    props.find { it.name == "value" }?.nullable shouldBe true
                }

                it("extracts multiple interfaces") {
                    val ts = """
                        interface User {
                            id: number;
                        }
                        interface Post {
                            title: string;
                        }
                    """.trimIndent()

                    val result = inferrer.infer(ts)

                    result.types.shouldHaveSize(2)
                    result.types.map { it.name } shouldBe listOf("User", "Post")
                }
            }

            describe("type aliases") {
                it("extracts type alias with object shape") {
                    val ts = """
                        type Person = {
                            name: string;
                            age: number;
                        }
                    """.trimIndent()

                    val result = inferrer.infer(ts)

                    result.types.shouldNotBeEmpty()
                    result.types.first().name shouldBe "Person"
                    result.types.first().kind shouldBe TypeKind.OBJECT
                }
            }

            describe("classes") {
                it("extracts class with public properties") {
                    val ts = """
                        class UserEntity {
                            public id: number;
                            public name: string;
                            private password: string;
                        }
                    """.trimIndent()

                    val result = inferrer.infer(ts)

                    result.types.shouldNotBeEmpty()
                    val classType = result.types.find { it.name == "UserEntity" }
                    classType.shouldNotBeNull()
                    classType.kind shouldBe TypeKind.CLASS
                    // Private properties should be excluded
                    classType.properties.none { it.name == "password" } shouldBe true
                }

                it("handles readonly modifier") {
                    val ts = """
                        class Config {
                            readonly apiKey: string;
                        }
                    """.trimIndent()

                    val result = inferrer.infer(ts)

                    val props = result.types.first().properties
                    props.any { it.name == "apiKey" && it.annotations.contains("readonly") } shouldBe true
                }
            }

            describe("schema generation") {
                it("generates TypeScript definition output") {
                    val ts = """
                        interface User {
                            id: number;
                            name: string;
                        }
                    """.trimIndent()

                    val result = inferrer.infer(ts)

                    result.schema shouldContain "interface User"
                    result.schema shouldContain "id"
                    result.schema shouldContain "name"
                }
            }

            describe("confidence") {
                it("returns confidence of 0.85") {
                    val ts = "interface Test { value: string; }"
                    val result = inferrer.infer(ts)

                    result.confidence shouldBe 0.85
                }
            }
        }
    }

    describe("KotlinSchemaInferrer") {
        val inferrer = KotlinSchemaInferrer()

        describe("format") {
            it("returns KOTLIN_SERIAL format") {
                inferrer.format shouldBe SchemaFormat.KOTLIN_SERIAL
            }
        }

        describe("canInfer()") {
            it("returns true for data class") {
                val kotlin = "data class User(val name: String)"
                inferrer.canInfer(kotlin) shouldBe true
            }

            it("returns true for sealed class") {
                val kotlin = "sealed class Result"
                inferrer.canInfer(kotlin) shouldBe true
            }

            it("returns true for @Serializable annotation") {
                val kotlin = "@Serializable class Config(val value: String)"
                inferrer.canInfer(kotlin) shouldBe true
            }

            it("returns false for regular class without markers") {
                val kotlin = "class SimpleClass { fun foo() {} }"
                inferrer.canInfer(kotlin) shouldBe false
            }

            it("returns false for random text") {
                inferrer.canInfer("random text") shouldBe false
            }
        }

        describe("infer()") {
            describe("data classes") {
                it("extracts simple data class") {
                    val kotlin = """
                        data class User(
                            val id: Long,
                            val name: String,
                            val email: String?
                        )
                    """.trimIndent()

                    val result = inferrer.infer(kotlin)

                    result.types.shouldNotBeEmpty()
                    val userType = result.types.find { it.name == "User" }
                    userType.shouldNotBeNull()
                    userType.kind shouldBe TypeKind.CLASS
                    userType.properties.shouldHaveSize(3)
                }

                it("handles nullable types") {
                    val kotlin = """
                        data class Contact(
                            val phone: String?,
                            val address: String?
                        )
                    """.trimIndent()

                    val result = inferrer.infer(kotlin)

                    val props = result.types.first().properties
                    props.all { it.nullable } shouldBe true
                }

                it("handles default values") {
                    val kotlin = """
                        data class Config(
                            val timeout: Int = 30,
                            val retries: Int = 3
                        )
                    """.trimIndent()

                    val result = inferrer.infer(kotlin)

                    val props = result.types.first().properties
                    props.find { it.name == "timeout" }?.defaultValue shouldBe "30"
                    props.find { it.name == "retries" }?.defaultValue shouldBe "3"
                }

                it("auto-generates ProtoNumber annotations") {
                    val kotlin = """
                        data class Simple(
                            val first: String,
                            val second: Int,
                            val third: Boolean
                        )
                    """.trimIndent()

                    val result = inferrer.infer(kotlin)

                    val props = result.types.first().properties
                    props[0].annotations.any { it.contains("@ProtoNumber(1)") } shouldBe true
                    props[1].annotations.any { it.contains("@ProtoNumber(2)") } shouldBe true
                    props[2].annotations.any { it.contains("@ProtoNumber(3)") } shouldBe true
                }

                it("preserves explicit @ProtoNumber annotations") {
                    val kotlin = """
                        data class Order(
                            @ProtoNumber(10) val orderId: String,
                            @ProtoNumber(20) val amount: Double
                        )
                    """.trimIndent()

                    val result = inferrer.infer(kotlin)

                    val props = result.types.first().properties
                    props.find { it.name == "orderId" }?.annotations?.any { it.contains("10") } shouldBe true
                    props.find { it.name == "amount" }?.annotations?.any { it.contains("20") } shouldBe true
                }
            }

            describe("nested and recursive types") {
                it("detects recursive type references") {
                    val kotlin = """
                        data class TreeNode(
                            val value: Int,
                            val children: List<TreeNode>
                        )
                    """.trimIndent()

                    val result = inferrer.infer(kotlin)

                    val props = result.types.first().properties
                    // Recursive fields should be marked as optional
                    props.find { it.name == "children" }?.optional shouldBe true
                }

                it("detects direct self-reference") {
                    val kotlin = """
                        data class LinkedNode(
                            val value: String,
                            val next: LinkedNode?
                        )
                    """.trimIndent()

                    val result = inferrer.infer(kotlin)

                    val props = result.types.first().properties
                    props.find { it.name == "next" }?.optional shouldBe true
                }
            }

            describe("sealed classes") {
                it("extracts sealed class as UNION type") {
                    val kotlin = """
                        sealed class Result
                        data class Success(val value: String) : Result()
                        data class Error(val message: String) : Result()
                    """.trimIndent()

                    val result = inferrer.infer(kotlin)

                    val sealedType = result.types.find { it.name == "Result" }
                    sealedType.shouldNotBeNull()
                    sealedType.kind shouldBe TypeKind.UNION
                }
            }

            describe("enums") {
                it("extracts enum class") {
                    val kotlin = """
                        enum class Status {
                            PENDING,
                            ACTIVE,
                            COMPLETED
                        }
                    """.trimIndent()

                    val result = inferrer.infer(kotlin)

                    val enumType = result.types.find { it.name == "Status" }
                    enumType.shouldNotBeNull()
                    enumType.kind shouldBe TypeKind.ENUM
                    enumType.properties.map { it.name } shouldBe listOf("PENDING", "ACTIVE", "COMPLETED")
                }
            }

            describe("schema generation") {
                it("generates Kotlin serializable schema output") {
                    val kotlin = """
                        data class User(
                            val id: Int,
                            val name: String
                        )
                    """.trimIndent()

                    val result = inferrer.infer(kotlin)

                    result.schema shouldContain "@Serializable"
                    result.schema shouldContain "data class User"
                    result.schema shouldContain "@ProtoNumber"
                }
            }

            describe("confidence") {
                it("returns confidence of 0.9") {
                    val kotlin = "data class Test(val value: String)"
                    val result = inferrer.infer(kotlin)

                    result.confidence shouldBe 0.9
                }
            }
        }

        describe("toProtobuf()") {
            it("converts simple data class to proto") {
                val kotlin = """
                    data class User(
                        val id: Long,
                        val name: String,
                        val email: String?
                    )
                """.trimIndent()

                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types)

                proto shouldContain "syntax = \"proto3\""
                proto shouldContain "message User"
                proto shouldContain "int64 id = 1"
                proto shouldContain "string name = 2"
                proto shouldContain "optional string email = 3"
            }

            it("handles @ProtoNumber annotations") {
                val kotlin = """
                    data class Order(
                        @ProtoNumber(10) val orderId: String,
                        @ProtoNumber(20) val amount: Double
                    )
                """.trimIndent()

                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types)

                proto shouldContain "string order_id = 10"
                proto shouldContain "double amount = 20"
            }

            it("auto-generates field numbers when not present") {
                val kotlin = """
                    data class Product(
                        val sku: String,
                        val price: Int,
                        val inStock: Boolean
                    )
                """.trimIndent()

                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types)

                proto shouldContain "= 1"
                proto shouldContain "= 2"
                proto shouldContain "= 3"
            }

            it("handles nested/recursive types") {
                val kotlin = """
                    data class TreeNode(
                        val value: Int,
                        val children: List<TreeNode>
                    )
                """.trimIndent()

                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types)

                proto shouldContain "message TreeNode"
                proto shouldContain "repeated TreeNode children"
            }

            it("maps Kotlin types to proto types correctly") {
                val kotlin = """
                    data class AllTypes(
                        val b: Boolean,
                        val i: Int,
                        val l: Long,
                        val f: Float,
                        val d: Double,
                        val s: String,
                        val bytes: ByteArray
                    )
                """.trimIndent()

                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types)

                proto shouldContain "bool b"
                proto shouldContain "int32 i"
                proto shouldContain "int64 l"
                proto shouldContain "float f"
                proto shouldContain "double d"
                proto shouldContain "string s"
                proto shouldContain "bytes bytes"
            }

            it("handles List and Set as repeated") {
                val kotlin = """
                    data class Container(
                        val items: List<String>,
                        val tags: Set<Int>
                    )
                """.trimIndent()

                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types)

                proto shouldContain "repeated string items"
                proto shouldContain "repeated int32 tags"
            }

            it("handles Map types") {
                val kotlin = """
                    data class Lookup(
                        val mapping: Map<String, Int>
                    )
                """.trimIndent()

                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types)

                proto shouldContain "map<string, int32> mapping"
            }

            it("converts camelCase to snake_case for field names") {
                val kotlin = """
                    data class CamelCase(
                        val firstName: String,
                        val lastName: String,
                        val phoneNumber: Int
                    )
                """.trimIndent()

                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types)

                proto shouldContain "first_name"
                proto shouldContain "last_name"
                proto shouldContain "phone_number"
            }

            it("adds package when provided") {
                val kotlin = "data class Simple(val value: String)"
                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types, "com.example")

                proto shouldContain "package com.example"
            }

            it("generates enum in proto format") {
                val kotlin = """
                    enum class Status {
                        UNKNOWN,
                        ACTIVE,
                        INACTIVE
                    }
                """.trimIndent()

                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types)

                proto shouldContain "enum Status"
                proto shouldContain "UNKNOWN = 0"
                proto shouldContain "ACTIVE = 1"
                proto shouldContain "INACTIVE = 2"
            }

            it("generates oneof for sealed class") {
                val kotlin = """
                    sealed class Result
                    data class Success(val value: String) : Result()
                    data class Failure(val error: String) : Result()
                """.trimIndent()

                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types)

                proto shouldContain "message Result"
                proto shouldContain "oneof value"
            }

            it("handles well-known types like Instant") {
                val kotlin = """
                    data class Event(
                        val timestamp: Instant,
                        val message: String
                    )
                """.trimIndent()

                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types)

                proto shouldContain "google.protobuf.Timestamp"
                proto shouldContain "import \"google/protobuf/timestamp.proto\""
            }

            it("handles Duration type") {
                val kotlin = """
                    data class Timer(
                        val elapsed: Duration
                    )
                """.trimIndent()

                val result = inferrer.infer(kotlin)
                val proto = inferrer.toProtobuf(result.types)

                proto shouldContain "google.protobuf.Duration"
                proto shouldContain "import \"google/protobuf/duration.proto\""
            }
        }

        describe("generateGradleConfig()") {
            it("generates Gradle protobuf plugin config") {
                val config = inferrer.generateGradleConfig("com.example")

                config shouldContain "plugins {"
                config shouldContain "com.google.protobuf"
                config shouldContain "protobuf {"
                config shouldContain "protoc {"
                config shouldContain "protobuf-kotlin"
            }

            it("includes gRPC plugin configuration") {
                val config = inferrer.generateGradleConfig("com.example")

                config shouldContain "grpc"
                config shouldContain "grpckt"
                config shouldContain "io.grpc"
            }
        }

        describe("generateWireConfig()") {
            it("generates Wire (Square) config") {
                val config = inferrer.generateWireConfig("com.example")

                config shouldContain "plugins {"
                config shouldContain "com.squareup.wire"
                config shouldContain "wire {"
                config shouldContain "kotlin {"
            }

            it("includes kotlin output configuration") {
                val config = inferrer.generateWireConfig("com.example")

                config shouldContain "out ="
                config shouldContain "rpcRole"
                config shouldContain "suspending"
            }

            it("includes source path configuration") {
                val config = inferrer.generateWireConfig("com.example")

                config shouldContain "sourcePath {"
                config shouldContain "srcDir"
                config shouldContain "proto"
            }
        }

        describe("generateKotlinxConfig()") {
            it("generates kotlinx-serialization-protobuf config") {
                val config = inferrer.generateKotlinxConfig()

                config shouldContain "plugins {"
                config shouldContain "kotlin(\"plugin.serialization\")"
                config shouldContain "kotlinx-serialization-protobuf"
            }

            it("includes usage examples") {
                val config = inferrer.generateKotlinxConfig()

                config shouldContain "ProtoBuf.encodeToByteArray"
                config shouldContain "ProtoBuf.decodeFromByteArray"
            }
        }
    }

    describe("ProtobufSchemaInferrer") {
        val inferrer = ProtobufSchemaInferrer()

        describe("format") {
            it("returns PROTOBUF format") {
                inferrer.format shouldBe SchemaFormat.PROTOBUF
            }
        }

        describe("canInfer()") {
            it("returns true for message definition") {
                val proto = "message User { string name = 1; }"
                inferrer.canInfer(proto) shouldBe true
            }

            it("returns true for syntax declaration") {
                val proto = "syntax = \"proto3\";"
                inferrer.canInfer(proto) shouldBe true
            }

            it("returns false for non-protobuf content") {
                inferrer.canInfer("random text") shouldBe false
                inferrer.canInfer("class User {}") shouldBe false
            }
        }

        describe("infer()") {
            describe("messages") {
                it("extracts message with fields") {
                    val proto = """
                        syntax = "proto3";
                        message User {
                            string name = 1;
                            int32 age = 2;
                        }
                    """.trimIndent()

                    val result = inferrer.infer(proto)

                    result.types.shouldNotBeEmpty()
                    val userType = result.types.find { it.name == "User" }
                    userType.shouldNotBeNull()
                    userType.kind shouldBe TypeKind.CLASS
                    userType.properties.shouldHaveSize(2)
                }

                it("handles repeated fields") {
                    val proto = """
                        message Container {
                            repeated string items = 1;
                        }
                    """.trimIndent()

                    val result = inferrer.infer(proto)

                    val props = result.types.first().properties
                    props.first().type shouldContain "repeated"
                    props.first().annotations shouldContain "repeated"
                }

                it("extracts multiple messages") {
                    val proto = """
                        message User { string name = 1; }
                        message Post { string title = 1; }
                    """.trimIndent()

                    val result = inferrer.infer(proto)

                    result.types.filter { it.kind == TypeKind.CLASS }.shouldHaveSize(2)
                }
            }

            describe("enums") {
                it("extracts enum definitions") {
                    val proto = """
                        enum Status {
                            UNKNOWN = 0;
                            ACTIVE = 1;
                            INACTIVE = 2;
                        }
                    """.trimIndent()

                    val result = inferrer.infer(proto)

                    val enumType = result.types.find { it.name == "Status" }
                    enumType.shouldNotBeNull()
                    enumType.kind shouldBe TypeKind.ENUM
                    enumType.properties.map { it.name } shouldBe listOf("UNKNOWN", "ACTIVE", "INACTIVE")
                }

                it("preserves enum values") {
                    val proto = """
                        enum Priority {
                            LOW = 0;
                            MEDIUM = 5;
                            HIGH = 10;
                        }
                    """.trimIndent()

                    val result = inferrer.infer(proto)

                    val props = result.types.first().properties
                    props.find { it.name == "LOW" }?.type shouldBe "0"
                    props.find { it.name == "MEDIUM" }?.type shouldBe "5"
                    props.find { it.name == "HIGH" }?.type shouldBe "10"
                }
            }

            describe("schema and confidence") {
                it("returns original proto as schema") {
                    val proto = "message Test { string value = 1; }"
                    val result = inferrer.infer(proto)

                    result.schema shouldBe proto
                }

                it("returns confidence of 1.0 for proto") {
                    val proto = "message Test { string value = 1; }"
                    val result = inferrer.infer(proto)

                    result.confidence shouldBe 1.0
                }
            }
        }
    }

    describe("GraphQLSchemaInferrer") {
        val inferrer = GraphQLSchemaInferrer()

        describe("format") {
            it("returns GRAPHQL_SCHEMA format") {
                inferrer.format shouldBe SchemaFormat.GRAPHQL_SCHEMA
            }
        }

        describe("canInfer()") {
            it("returns true for type definition") {
                val gql = "type User { name: String }"
                inferrer.canInfer(gql) shouldBe true
            }

            it("returns true for interface definition") {
                val gql = "interface Node { id: ID! }"
                inferrer.canInfer(gql) shouldBe true
            }

            it("returns true for input type") {
                val gql = "input CreateUserInput { name: String! }"
                inferrer.canInfer(gql) shouldBe true
            }

            it("returns true for enum definition") {
                val gql = "enum Status { ACTIVE INACTIVE }"
                inferrer.canInfer(gql) shouldBe true
            }

            it("returns false for non-GraphQL content") {
                inferrer.canInfer("random text") shouldBe false
                inferrer.canInfer("class User {}") shouldBe false
            }
        }

        describe("infer()") {
            describe("types") {
                it("extracts type with fields") {
                    val gql = """
                        type User {
                            id: ID!
                            name: String!
                            email: String
                        }
                    """.trimIndent()

                    val result = inferrer.infer(gql)

                    result.types.shouldNotBeEmpty()
                    val userType = result.types.find { it.name == "User" }
                    userType.shouldNotBeNull()
                    userType.kind shouldBe TypeKind.OBJECT
                    userType.properties.shouldHaveSize(3)
                }

                it("handles nullable and non-nullable fields") {
                    val gql = """
                        type Item {
                            required: String!
                            optional: String
                        }
                    """.trimIndent()

                    val result = inferrer.infer(gql)

                    val props = result.types.first().properties
                    props.find { it.name == "required" }?.nullable shouldBe false
                    props.find { it.name == "optional" }?.nullable shouldBe true
                }

                it("handles list types") {
                    val gql = """
                        type Container {
                            items: [String!]!
                            nullableItems: [Int]
                        }
                    """.trimIndent()

                    val result = inferrer.infer(gql)

                    val props = result.types.first().properties
                    props.find { it.name == "items" }?.type shouldContain "String"
                    props.find { it.name == "nullableItems" }?.type shouldContain "Int"
                }
            }

            describe("interfaces") {
                it("extracts interface definition") {
                    val gql = """
                        interface Node {
                            id: ID!
                        }
                    """.trimIndent()

                    val result = inferrer.infer(gql)

                    val nodeType = result.types.find { it.name == "Node" }
                    nodeType.shouldNotBeNull()
                    nodeType.kind shouldBe TypeKind.INTERFACE
                }
            }

            describe("input types") {
                it("extracts input type") {
                    val gql = """
                        input CreateUserInput {
                            name: String!
                            email: String!
                        }
                    """.trimIndent()

                    val result = inferrer.infer(gql)

                    val inputType = result.types.find { it.name == "CreateUserInput" }
                    inputType.shouldNotBeNull()
                    inputType.kind shouldBe TypeKind.OBJECT
                }
            }

            describe("enums") {
                it("extracts enum definition") {
                    val gql = """
                        enum Status {
                            ACTIVE
                            INACTIVE
                            PENDING
                        }
                    """.trimIndent()

                    val result = inferrer.infer(gql)

                    val enumType = result.types.find { it.name == "Status" }
                    enumType.shouldNotBeNull()
                    enumType.kind shouldBe TypeKind.ENUM
                    enumType.properties.map { it.name } shouldBe listOf("ACTIVE", "INACTIVE", "PENDING")
                }
            }

            describe("unions") {
                it("extracts union definition") {
                    val gql = """
                        union SearchResult = User | Post | Comment
                    """.trimIndent()

                    val result = inferrer.infer(gql)

                    val unionType = result.types.find { it.name == "SearchResult" }
                    unionType.shouldNotBeNull()
                    unionType.kind shouldBe TypeKind.UNION
                }
            }

            describe("schema and confidence") {
                it("returns original GraphQL as schema") {
                    val gql = "type Test { value: String }"
                    val result = inferrer.infer(gql)

                    result.schema shouldBe gql
                }

                it("returns confidence of 0.95") {
                    val gql = "type Test { value: String }"
                    val result = inferrer.infer(gql)

                    result.confidence shouldBe 0.95
                }
            }

            describe("complex schemas") {
                it("handles multiple type definitions") {
                    val gql = """
                        type Query {
                            users: [User!]!
                        }

                        type User {
                            id: ID!
                            posts: [Post!]!
                        }

                        type Post {
                            id: ID!
                            author: User!
                        }
                    """.trimIndent()

                    val result = inferrer.infer(gql)

                    result.types.shouldHaveSize(3)
                    result.types.map { it.name }.toSet() shouldBe setOf("Query", "User", "Post")
                }
            }
        }
    }

    describe("SchemaFormat detection") {
        it("detects format from file extension") {
            SchemaFormat.fromExtension("json") shouldBe SchemaFormat.JSON_SCHEMA
            SchemaFormat.fromExtension("xsd") shouldBe SchemaFormat.XSD
            SchemaFormat.fromExtension("graphql") shouldBe SchemaFormat.GRAPHQL_SCHEMA
            SchemaFormat.fromExtension("gql") shouldBe SchemaFormat.GRAPHQL_SCHEMA
            SchemaFormat.fromExtension("proto") shouldBe SchemaFormat.PROTOBUF
            SchemaFormat.fromExtension("yaml") shouldBe SchemaFormat.YAML_SCHEMA
            SchemaFormat.fromExtension("yml") shouldBe SchemaFormat.YAML_SCHEMA
            SchemaFormat.fromExtension("avsc") shouldBe SchemaFormat.AVRO
            SchemaFormat.fromExtension("thrift") shouldBe SchemaFormat.THRIFT
            SchemaFormat.fromExtension("toml") shouldBe SchemaFormat.TOML
            SchemaFormat.fromExtension("sql") shouldBe SchemaFormat.SQL_DDL
            SchemaFormat.fromExtension("wsdl") shouldBe SchemaFormat.WSDL
            SchemaFormat.fromExtension("kt") shouldBe SchemaFormat.KOTLIN_SERIAL
            SchemaFormat.fromExtension("d.ts") shouldBe SchemaFormat.TYPESCRIPT_DEFS
        }

        it("returns UNKNOWN for unsupported extensions") {
            SchemaFormat.fromExtension("xyz") shouldBe SchemaFormat.UNKNOWN
            SchemaFormat.fromExtension("random") shouldBe SchemaFormat.UNKNOWN
        }

        it("handles case insensitive extensions") {
            SchemaFormat.fromExtension("JSON") shouldBe SchemaFormat.JSON_SCHEMA
            SchemaFormat.fromExtension("Proto") shouldBe SchemaFormat.PROTOBUF
            SchemaFormat.fromExtension("GRAPHQL") shouldBe SchemaFormat.GRAPHQL_SCHEMA
        }

        it("handles extensions with leading dot") {
            SchemaFormat.fromExtension(".json") shouldBe SchemaFormat.JSON_SCHEMA
            SchemaFormat.fromExtension(".proto") shouldBe SchemaFormat.PROTOBUF
        }

        it("detects format from MIME type") {
            SchemaFormat.fromMimeType("application/schema+json") shouldBe SchemaFormat.JSON_SCHEMA
            SchemaFormat.fromMimeType("application/xml") shouldBe SchemaFormat.XSD
            SchemaFormat.fromMimeType("application/graphql") shouldBe SchemaFormat.GRAPHQL_SCHEMA
            SchemaFormat.fromMimeType("application/protobuf") shouldBe SchemaFormat.PROTOBUF
        }

        it("returns UNKNOWN for unsupported MIME types") {
            SchemaFormat.fromMimeType("text/plain") shouldBe SchemaFormat.UNKNOWN
            SchemaFormat.fromMimeType("application/custom") shouldBe SchemaFormat.UNKNOWN
        }
    }

    describe("Inferrer format property") {
        it("JsonSchemaInferrer has correct format") {
            JsonSchemaInferrer().format shouldBe SchemaFormat.JSON_SCHEMA
        }

        it("TypeScriptSchemaInferrer has correct format") {
            TypeScriptSchemaInferrer().format shouldBe SchemaFormat.TYPESCRIPT_DEFS
        }

        it("KotlinSchemaInferrer has correct format") {
            KotlinSchemaInferrer().format shouldBe SchemaFormat.KOTLIN_SERIAL
        }

        it("ProtobufSchemaInferrer has correct format") {
            ProtobufSchemaInferrer().format shouldBe SchemaFormat.PROTOBUF
        }

        it("GraphQLSchemaInferrer has correct format") {
            GraphQLSchemaInferrer().format shouldBe SchemaFormat.GRAPHQL_SCHEMA
        }
    }

    describe("Edge cases and error handling") {
        describe("JsonSchemaInferrer edge cases") {
            val inferrer = JsonSchemaInferrer()

            it("handles deeply nested objects") {
                val json = """{"a": {"b": {"c": {"d": "deep"}}}}"""
                val result = inferrer.infer(json)

                result.schema shouldContain "\"a\""
                result.schema shouldContain "\"d\""
            }

            it("handles mixed array types (uses first element)") {
                val json = """[1, "string", true]"""
                val result = inferrer.infer(json)

                result.schema shouldContain "\"array\""
                result.schema shouldContain "\"integer\""
            }

            it("handles objects with special characters in keys") {
                val json = """{"special-key": "value", "another.key": 123}"""
                val result = inferrer.infer(json)

                result.schema shouldContain "special-key"
                result.schema shouldContain "another.key"
            }

            it("handles very large integers") {
                val json = """{"bigNumber": 9223372036854775807}"""
                val result = inferrer.infer(json)

                result.types.first().properties.first().type shouldBe "Long"
            }
        }

        describe("KotlinSchemaInferrer edge cases") {
            val inferrer = KotlinSchemaInferrer()

            it("handles data class with no properties") {
                val kotlin = "data class Empty()"
                val result = inferrer.infer(kotlin)

                // Should handle gracefully even if unusual
                result.types.shouldNotBeEmpty()
            }

            it("handles generic type parameters") {
                val kotlin = """
                    data class Wrapper<T>(
                        val value: T,
                        val items: List<T>
                    )
                """.trimIndent()

                val result = inferrer.infer(kotlin)

                // Should extract the structure even with generics
                result.types.shouldNotBeEmpty()
            }

            it("handles multiple data classes") {
                val kotlin = """
                    data class First(val a: String)
                    data class Second(val b: Int)
                    data class Third(val c: Boolean)
                """.trimIndent()

                val result = inferrer.infer(kotlin)

                result.types.filter { it.kind == TypeKind.CLASS }.shouldHaveSize(3)
            }
        }

        describe("GraphQLSchemaInferrer edge cases") {
            val inferrer = GraphQLSchemaInferrer()

            it("handles types with arguments") {
                val gql = """
                    type Query {
                        user(id: ID!): User
                        users(limit: Int, offset: Int): [User!]!
                    }
                """.trimIndent()

                val result = inferrer.infer(gql)

                result.types.shouldNotBeEmpty()
                val queryType = result.types.find { it.name == "Query" }
                queryType.shouldNotBeNull()
            }

            it("handles types implementing multiple interfaces") {
                val gql = """
                    type User implements Node & Actor {
                        id: ID!
                        name: String!
                    }
                """.trimIndent()

                val result = inferrer.infer(gql)

                result.types.shouldNotBeEmpty()
            }
        }
    }
})
