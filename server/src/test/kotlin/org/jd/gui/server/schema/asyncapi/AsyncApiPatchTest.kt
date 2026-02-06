package org.jd.gui.server.schema.asyncapi

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files

/**
 * Comprehensive tests for AsyncApiPatch and ClasspathUriResolver.
 *
 * Tests cover:
 * - All patch operations (ADD, REMOVE, REPLACE, MOVE, COPY, TEST, MERGE)
 * - URI resolution for different schemes (classpath:, file:)
 * - Caching behavior and TTL expiration
 * - AsyncApiRegistry functionality
 */
class AsyncApiPatchTest : DescribeSpec({

    // Helper to create a temporary JSON file for testing
    fun createTempJsonFile(content: String): File {
        val file = Files.createTempFile("asyncapi-test-", ".json").toFile()
        file.writeText(content)
        file.deleteOnExit()
        return file
    }

    // Base AsyncAPI spec for testing
    val baseSpec = """
        {
            "asyncapi": "2.6.0",
            "info": {
                "title": "Test API",
                "version": "1.0.0",
                "description": "Test description"
            },
            "channels": {
                "user/created": {
                    "description": "User creation events",
                    "subscribe": {
                        "operationId": "onUserCreated",
                        "message": {
                            "name": "UserCreated"
                        }
                    }
                }
            },
            "components": {
                "schemas": {
                    "User": {
                        "type": "object",
                        "properties": {
                            "id": { "type": "string" },
                            "name": { "type": "string" }
                        }
                    }
                }
            }
        }
    """.trimIndent()

    describe("AsyncApiPatch") {

        describe("ADD operation") {

            it("should add a string value at a path") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .add("/info/contact", "support@example.com")
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldNotBeNull()
                result.document.shouldContain("support@example.com")
                result.errors.shouldBeEmpty()
            }

            it("should add an integer value at a path") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .add("/info/x-rateLimit", 1000)
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldContain("1000")
            }

            it("should add a boolean value at a path") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .add("/info/x-deprecated", false)
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldContain("false")
            }

            it("should add a JSON object at a path") {
                val baseFile = createTempJsonFile(baseSpec)
                val newSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("email") {
                            put("type", "string")
                            put("format", "email")
                        }
                    }
                }
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .add("/components/schemas/Email", newSchema)
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldContain("Email")
                result.document.shouldContain("email")
            }

            it("should create intermediate paths when adding nested values") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .add("/x-extensions/custom/nested/value", "deep")
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldContain("x-extensions")
                result.document.shouldContain("deep")
            }

            it("should handle JSON Pointer escape sequences") {
                val baseFile = createTempJsonFile(baseSpec)
                // ~1 is the escape for / in JSON Pointer
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .add("/channels/order~1events", buildJsonObject {
                        put("description", "Order events channel")
                    })
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                // The channel name should be "order/events" after unescaping
                result.document.shouldContain("order/events")
            }
        }

        describe("REMOVE operation") {

            it("should remove a value at a path") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .remove("/info/description")
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldNotBeNull()
                // The description should be removed
                val doc = Json.parseToJsonElement(result.document!!)
                doc.jsonObject["info"]?.jsonObject?.get("description").shouldBeNull()
            }

            it("should remove a nested object") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .remove("/components/schemas/User")
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldNotBeNull()
                val doc = Json.parseToJsonElement(result.document!!)
                doc.jsonObject["components"]?.jsonObject?.get("schemas")?.jsonObject?.get("User").shouldBeNull()
            }

            it("should remove an entire section") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .remove("/channels/user~1created")
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                val doc = Json.parseToJsonElement(result.document!!)
                doc.jsonObject["channels"]?.jsonObject?.get("user/created").shouldBeNull()
            }

            it("should handle removing non-existent path gracefully") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .remove("/nonexistent/path")
                    .build()

                val result = patch.apply()

                // Remove on non-existent path should succeed (no-op)
                result.success.shouldBeTrue()
            }
        }

        describe("REPLACE operation") {

            it("should replace a string value at a path") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .replace("/info/version", "2.0.0")
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldContain("2.0.0")
            }

            it("should replace an object at a path") {
                val baseFile = createTempJsonFile(baseSpec)
                val newSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("userId") {
                            put("type", "integer")
                        }
                    }
                }
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .replace("/components/schemas/User", newSchema)
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldContain("userId")
                result.document.shouldContain("integer")
            }

            it("should fail when path does not exist") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .replace("/nonexistent/path", "value")
                    .build()

                val result = patch.apply()

                result.success.shouldBeFalse()
                result.errors.shouldHaveSize(1)
                result.errors[0].message.shouldContain("does not exist")
            }
        }

        describe("MOVE operation") {

            it("should move a value from one path to another") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .move("/info/description", "/info/summary")
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                val doc = Json.parseToJsonElement(result.document!!)
                doc.jsonObject["info"]?.jsonObject?.get("summary")?.jsonPrimitive?.content shouldBe "Test description"
                doc.jsonObject["info"]?.jsonObject?.get("description").shouldBeNull()
            }

            it("should move a nested object") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .move("/components/schemas/User", "/components/schemas/Person")
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                val doc = Json.parseToJsonElement(result.document!!)
                doc.jsonObject["components"]?.jsonObject?.get("schemas")?.jsonObject?.get("Person").shouldNotBeNull()
                doc.jsonObject["components"]?.jsonObject?.get("schemas")?.jsonObject?.get("User").shouldBeNull()
            }

            it("should fail when source path does not exist") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .move("/nonexistent", "/info/moved")
                    .build()

                val result = patch.apply()

                result.success.shouldBeFalse()
                result.errors.shouldHaveSize(1)
                result.errors[0].message.shouldContain("does not exist")
            }
        }

        describe("COPY operation") {

            it("should copy a value from one path to another") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .copy("/info/title", "/info/x-original-title")
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                val doc = Json.parseToJsonElement(result.document!!)
                doc.jsonObject["info"]?.jsonObject?.get("title")?.jsonPrimitive?.content shouldBe "Test API"
                doc.jsonObject["info"]?.jsonObject?.get("x-original-title")?.jsonPrimitive?.content shouldBe "Test API"
            }

            it("should copy a complex object") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .copy("/components/schemas/User", "/components/schemas/UserCopy")
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                val doc = Json.parseToJsonElement(result.document!!)
                val schemas = doc.jsonObject["components"]?.jsonObject?.get("schemas")?.jsonObject
                schemas?.get("User").shouldNotBeNull()
                schemas?.get("UserCopy").shouldNotBeNull()
            }

            it("should fail when source path does not exist") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .copy("/nonexistent", "/info/copied")
                    .build()

                val result = patch.apply()

                result.success.shouldBeFalse()
                result.errors.shouldHaveSize(1)
            }
        }

        describe("TEST operation") {

            it("should pass when value matches expected") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .test("/info/version", JsonPrimitive("1.0.0"))
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.errors.shouldBeEmpty()
            }

            it("should record error when value does not match") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .test("/info/version", JsonPrimitive("wrong-version"))
                    .build()

                val result = patch.apply()

                result.success.shouldBeFalse()
                result.errors.shouldHaveSize(1)
                result.errors[0].operation shouldBe "TEST"
                result.errors[0].message.shouldContain("Test failed")
            }

            it("should test object equality") {
                val baseFile = createTempJsonFile(baseSpec)
                val expectedSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("id") { put("type", "string") }
                        putJsonObject("name") { put("type", "string") }
                    }
                }
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .test("/components/schemas/User", expectedSchema)
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
            }
        }

        describe("MERGE operation") {

            it("should deep merge overlay into document") {
                val baseFile = createTempJsonFile(baseSpec)
                val overlay = buildJsonObject {
                    putJsonObject("info") {
                        put("contact", "support@example.com")
                        put("license", "MIT")
                    }
                }
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .merge(overlay)
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                val doc = Json.parseToJsonElement(result.document!!)
                val info = doc.jsonObject["info"]?.jsonObject
                // Original fields preserved
                info?.get("title")?.jsonPrimitive?.content shouldBe "Test API"
                info?.get("version")?.jsonPrimitive?.content shouldBe "1.0.0"
                // New fields added
                info?.get("contact")?.jsonPrimitive?.content shouldBe "support@example.com"
                info?.get("license")?.jsonPrimitive?.content shouldBe "MIT"
            }

            it("should merge arrays by concatenation") {
                val specWithArray = """
                    {
                        "asyncapi": "2.6.0",
                        "info": { "title": "Test", "version": "1.0.0" },
                        "tags": [
                            { "name": "tag1" }
                        ]
                    }
                """.trimIndent()
                val baseFile = createTempJsonFile(specWithArray)
                val overlay = buildJsonObject {
                    putJsonArray("tags") {
                        addJsonObject { put("name", "tag2") }
                    }
                }
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .merge(overlay)
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                val doc = Json.parseToJsonElement(result.document!!)
                val tags = doc.jsonObject["tags"]?.jsonArray
                tags.shouldNotBeNull()
                tags.shouldHaveSize(2)
            }

            it("should merge from URI") {
                val overlayContent = """
                    {
                        "info": {
                            "x-overlay-applied": true
                        }
                    }
                """.trimIndent()
                val baseFile = createTempJsonFile(baseSpec)
                val overlayFile = createTempJsonFile(overlayContent)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .merge("file:${overlayFile.absolutePath}")
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldContain("x-overlay-applied")
            }
        }

        describe("Multiple operations") {

            it("should apply operations in sequence") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .add("/info/contact", "test@example.com")
                    .replace("/info/version", "2.0.0")
                    .remove("/info/description")
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.appliedOps shouldBe 3
                val doc = Json.parseToJsonElement(result.document!!)
                val info = doc.jsonObject["info"]?.jsonObject
                info?.get("contact")?.jsonPrimitive?.content shouldBe "test@example.com"
                info?.get("version")?.jsonPrimitive?.content shouldBe "2.0.0"
                info?.get("description").shouldBeNull()
            }

            it("should stop and record errors on failure") {
                val baseFile = createTempJsonFile(baseSpec)
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .add("/info/contact", "test@example.com")
                    .replace("/nonexistent/path", "will fail")  // This should fail
                    .add("/info/license", "MIT")  // This should still be attempted
                    .build()

                val result = patch.apply()

                result.success.shouldBeFalse()
                result.errors.shouldHaveSize(1)
            }
        }

        describe("Builder validation") {

            it("should require base URI") {
                val builder = AsyncApiPatch.builder()
                    .add("/info/version", "1.0.0")

                try {
                    builder.build()
                    throw AssertionError("Expected IllegalArgumentException")
                } catch (e: IllegalArgumentException) {
                    e.message.shouldContain("Base URI")
                }
            }
        }

        describe("Error handling") {

            it("should return error when base URI cannot be resolved") {
                val patch = AsyncApiPatch.builder()
                    .base("file:/nonexistent/path/to/spec.json")
                    .add("/info/version", "1.0.0")
                    .build()

                val result = patch.apply()

                result.success.shouldBeFalse()
                result.document.shouldBeNull()
                result.errors.shouldHaveSize(1)
                result.errors[0].operation shouldBe "BASE"
            }

            it("should return error when base content is invalid JSON") {
                val invalidFile = createTempJsonFile("{ invalid json }")
                val patch = AsyncApiPatch.builder()
                    .base("file:${invalidFile.absolutePath}")
                    .add("/info/version", "1.0.0")
                    .build()

                val result = patch.apply()

                result.success.shouldBeFalse()
                result.errors.shouldHaveSize(1)
            }
        }

        describe("addChannel helper") {

            it("should add a channel with subscribe operation") {
                val baseFile = createTempJsonFile(baseSpec)
                val channel = AsyncApiChannel(
                    description = "Order events",
                    subscribe = AsyncApiOperation(
                        operationId = "onOrderCreated",
                        summary = "Receive order events",
                        message = AsyncApiMessage(
                            name = "OrderCreated",
                            contentType = "application/json",
                            schemaRef = "#/components/schemas/Order"
                        )
                    )
                )
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .addChannel("order/events", channel)
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldContain("order/events")
                result.document.shouldContain("onOrderCreated")
                result.document.shouldContain("OrderCreated")
            }

            it("should add a channel with publish operation") {
                val baseFile = createTempJsonFile(baseSpec)
                val channel = AsyncApiChannel(
                    description = "Command channel",
                    publish = AsyncApiOperation(
                        operationId = "sendCommand",
                        summary = "Send commands"
                    )
                )
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .addChannel("commands", channel)
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldContain("commands")
                result.document.shouldContain("sendCommand")
            }
        }

        describe("addSchema helper") {

            it("should add a schema to components") {
                val baseFile = createTempJsonFile(baseSpec)
                val schema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("orderId") { put("type", "string") }
                        putJsonObject("amount") { put("type", "number") }
                    }
                    putJsonArray("required") {
                        add("orderId")
                    }
                }
                val patch = AsyncApiPatch.builder()
                    .base("file:${baseFile.absolutePath}")
                    .addSchema("Order", schema)
                    .build()

                val result = patch.apply()

                result.success.shouldBeTrue()
                val doc = Json.parseToJsonElement(result.document!!)
                doc.jsonObject["components"]?.jsonObject?.get("schemas")?.jsonObject?.get("Order").shouldNotBeNull()
            }
        }
    }

    describe("ClasspathUriResolver") {

        beforeEach {
            ClasspathUriResolver.clearCache()
        }

        describe("resolve with file: URI") {

            it("should resolve file URI to content") {
                val content = """{"test": "value"}"""
                val file = createTempJsonFile(content)

                val result = ClasspathUriResolver.resolve("file:${file.absolutePath}")

                result.shouldNotBeNull()
                result.shouldContain("test")
                result.shouldContain("value")
            }

            it("should return null for non-existent file") {
                val result = ClasspathUriResolver.resolve("file:/nonexistent/path/file.json")

                result.shouldBeNull()
            }
        }

        describe("resolve with classpath: URI") {

            it("should return null for non-existent classpath resource") {
                val result = ClasspathUriResolver.resolve("classpath:/nonexistent/resource.json")

                result.shouldBeNull()
            }
        }

        describe("caching behavior") {

            it("should cache resolved content") {
                val content = """{"cached": true}"""
                val file = createTempJsonFile(content)
                val uri = "file:${file.absolutePath}"

                // First resolve
                val result1 = ClasspathUriResolver.resolve(uri)
                result1.shouldNotBeNull()

                // Modify the file
                file.writeText("""{"cached": false}""")

                // Second resolve should return cached content
                val result2 = ClasspathUriResolver.resolve(uri)
                result2 shouldBe result1
                result2.shouldContain("true")
            }

            it("should clear cache when requested") {
                val content = """{"cached": true}"""
                val file = createTempJsonFile(content)
                val uri = "file:${file.absolutePath}"

                // First resolve
                ClasspathUriResolver.resolve(uri)

                // Modify the file
                file.writeText("""{"cached": false, "new": "content"}""")

                // Clear cache
                ClasspathUriResolver.clearCache()

                // Resolve again should get fresh content
                val result = ClasspathUriResolver.resolve(uri)
                result.shouldContain("false")
                result.shouldContain("new")
            }
        }

        describe("exists check") {

            it("should return true for existing file") {
                val file = createTempJsonFile("{}")
                val exists = ClasspathUriResolver.exists("file:${file.absolutePath}")

                exists.shouldBeTrue()
            }

            it("should return false for non-existent file") {
                val exists = ClasspathUriResolver.exists("file:/nonexistent/path.json")

                exists.shouldBeFalse()
            }
        }

        describe("content type detection") {

            it("should detect JSON content type") {
                ClasspathUriResolver.detectContentType("spec.json") shouldBe ContentType.JSON
                ClasspathUriResolver.detectContentType("/path/to/file.json") shouldBe ContentType.JSON
            }

            it("should detect YAML content type") {
                ClasspathUriResolver.detectContentType("spec.yaml") shouldBe ContentType.YAML
                ClasspathUriResolver.detectContentType("spec.yml") shouldBe ContentType.YAML
            }

            it("should detect XML content type") {
                ClasspathUriResolver.detectContentType("schema.xml") shouldBe ContentType.XML
                ClasspathUriResolver.detectContentType("schema.xsd") shouldBe ContentType.XML
            }

            it("should detect Protobuf content type") {
                ClasspathUriResolver.detectContentType("service.proto") shouldBe ContentType.PROTOBUF
            }

            it("should detect GraphQL content type") {
                ClasspathUriResolver.detectContentType("schema.graphql") shouldBe ContentType.GRAPHQL
                ClasspathUriResolver.detectContentType("schema.gql") shouldBe ContentType.GRAPHQL
            }

            it("should detect Avro content type") {
                ClasspathUriResolver.detectContentType("schema.avsc") shouldBe ContentType.AVRO
            }

            it("should detect Kotlin content type") {
                ClasspathUriResolver.detectContentType("Model.kt") shouldBe ContentType.KOTLIN
            }

            it("should detect Java content type") {
                ClasspathUriResolver.detectContentType("Model.java") shouldBe ContentType.JAVA
            }

            it("should detect TypeScript content type") {
                ClasspathUriResolver.detectContentType("model.ts") shouldBe ContentType.TYPESCRIPT
                ClasspathUriResolver.detectContentType("model.d.ts") shouldBe ContentType.TYPESCRIPT
            }

            it("should return UNKNOWN for unrecognized extensions") {
                ClasspathUriResolver.detectContentType("file.xyz") shouldBe ContentType.UNKNOWN
                ClasspathUriResolver.detectContentType("noextension") shouldBe ContentType.UNKNOWN
            }

            it("should be case insensitive") {
                ClasspathUriResolver.detectContentType("SPEC.JSON") shouldBe ContentType.JSON
                ClasspathUriResolver.detectContentType("Schema.YAML") shouldBe ContentType.YAML
            }
        }

        describe("resolveToStream") {

            it("should return stream for existing file") {
                val content = "stream content"
                val file = createTempJsonFile(content)

                val stream = ClasspathUriResolver.resolveToStream("file:${file.absolutePath}")

                stream.shouldNotBeNull()
                stream.use { it.bufferedReader().readText() } shouldBe content
            }

            it("should return null for non-existent file") {
                val stream = ClasspathUriResolver.resolveToStream("file:/nonexistent/file.json")

                stream.shouldBeNull()
            }
        }

        describe("toUrl") {

            it("should convert file URI to URL") {
                val file = createTempJsonFile("{}")
                val url = ClasspathUriResolver.toUrl("file:${file.absolutePath}")

                url.shouldNotBeNull()
                url.protocol shouldBe "file"
            }

            it("should return null for invalid URI") {
                val url = ClasspathUriResolver.toUrl("not-a-valid-uri-::::")

                url.shouldBeNull()
            }
        }

        describe("ContentType enum") {

            it("should have correct MIME types") {
                ContentType.JSON.mimeType shouldBe "application/json"
                ContentType.YAML.mimeType shouldBe "application/yaml"
                ContentType.XML.mimeType shouldBe "application/xml"
                ContentType.PROTOBUF.mimeType shouldBe "application/protobuf"
                ContentType.GRAPHQL.mimeType shouldBe "application/graphql"
                ContentType.AVRO.mimeType shouldBe "application/avro"
                ContentType.KOTLIN.mimeType shouldBe "text/x-kotlin"
                ContentType.JAVA.mimeType shouldBe "text/x-java"
                ContentType.TYPESCRIPT.mimeType shouldBe "application/typescript"
                ContentType.UNKNOWN.mimeType shouldBe "application/octet-stream"
            }
        }
    }

    describe("AsyncApiRegistry") {

        describe("registration") {

            it("should register a spec") {
                val registry = AsyncApiRegistry()
                registry.register("test-spec", "file:/path/to/spec.json", "Test specification")

                val spec = registry.get("test-spec")

                spec.shouldNotBeNull()
                spec.id shouldBe "test-spec"
                spec.uri shouldBe "file:/path/to/spec.json"
                spec.description shouldBe "Test specification"
            }

            it("should list all registered specs") {
                val registry = AsyncApiRegistry()
                registry.register("spec1", "file:/path/spec1.json")
                registry.register("spec2", "file:/path/spec2.json")
                registry.register("spec3", "file:/path/spec3.json")

                val specs = registry.list()

                specs.shouldHaveSize(3)
            }

            it("should support chained registration") {
                val registry = AsyncApiRegistry()
                    .register("spec1", "file:/path/spec1.json")
                    .register("spec2", "file:/path/spec2.json")

                registry.list().shouldHaveSize(2)
            }
        }

        describe("retrieval") {

            it("should return null for non-existent spec") {
                val registry = AsyncApiRegistry()

                val spec = registry.get("nonexistent")

                spec.shouldBeNull()
            }

            it("should return spec metadata") {
                val registry = AsyncApiRegistry()
                registry.register("test-spec", "file:/path/to/spec.json", "Description")

                val spec = registry.get("test-spec")

                spec.shouldNotBeNull()
                spec.registeredAt.shouldNotBeNull()
            }
        }

        describe("removal") {

            it("should remove spec and its patches") {
                val registry = AsyncApiRegistry()
                registry.register("to-remove", "file:/path/spec.json")

                registry.remove("to-remove")

                registry.get("to-remove").shouldBeNull()
            }
        }

        describe("build with patches") {

            it("should build spec without patches") {
                val content = """{"asyncapi": "2.6.0", "info": {"title": "Test", "version": "1.0.0"}}"""
                val file = createTempJsonFile(content)
                val registry = AsyncApiRegistry()
                registry.register("simple", "file:${file.absolutePath}")

                val result = registry.build("simple")

                result.shouldNotBeNull()
                result.success.shouldBeTrue()
                result.document.shouldContain("Test")
            }

            it("should apply patches to spec") {
                val content = """{"asyncapi": "2.6.0", "info": {"title": "Test", "version": "1.0.0"}}"""
                val file = createTempJsonFile(content)
                val registry = AsyncApiRegistry()
                registry.register("patched", "file:${file.absolutePath}")

                val patch = AsyncApiPatch.builder()
                    .base("file:${file.absolutePath}")
                    .replace("/info/version", "2.0.0")
                    .build()
                registry.addPatch("patched", patch)

                val result = registry.build("patched")

                result.shouldNotBeNull()
                result.success.shouldBeTrue()
                result.document.shouldContain("2.0.0")
            }

            it("should return null for non-existent spec") {
                val registry = AsyncApiRegistry()

                val result = registry.build("nonexistent")

                result.shouldBeNull()
            }

            it("should return error for unloadable spec") {
                val registry = AsyncApiRegistry()
                registry.register("missing", "file:/nonexistent/spec.json")

                val result = registry.build("missing")

                result.shouldNotBeNull()
                result.success.shouldBeFalse()
                result.errors.shouldHaveSize(1)
            }
        }
    }

    describe("DSL functions") {

        describe("asyncApiPatch DSL") {

            it("should create patch using DSL") {
                val content = """{"asyncapi": "2.6.0", "info": {"title": "Test", "version": "1.0.0"}}"""
                val file = createTempJsonFile(content)

                val patch = asyncApiPatch("file:${file.absolutePath}") {
                    replace("/info/version", "2.0.0")
                    add("/info/description", "Added via DSL")
                }

                val result = patch.apply()

                result.success.shouldBeTrue()
                result.document.shouldContain("2.0.0")
                result.document.shouldContain("Added via DSL")
            }
        }

        describe("channel DSL") {

            it("should create channel with description") {
                val ch = channel(description = "Test channel")

                ch.description shouldBe "Test channel"
            }

            it("should create channel with operations") {
                val ch = channel(
                    subscribe = operation(operationId = "onEvent"),
                    publish = operation(operationId = "sendEvent")
                )

                ch.subscribe?.operationId shouldBe "onEvent"
                ch.publish?.operationId shouldBe "sendEvent"
            }
        }

        describe("operation DSL") {

            it("should create operation with message") {
                val op = operation(
                    operationId = "receiveMessage",
                    summary = "Receive a message",
                    message = message(
                        name = "UserMessage",
                        contentType = "application/json",
                        schemaRef = "#/components/schemas/Message"
                    )
                )

                op.operationId shouldBe "receiveMessage"
                op.summary shouldBe "Receive a message"
                op.message?.name shouldBe "UserMessage"
                op.message?.contentType shouldBe "application/json"
                op.message?.schemaRef shouldBe "#/components/schemas/Message"
            }
        }

        describe("message DSL") {

            it("should create message with default content type") {
                val msg = message(name = "TestMessage")

                msg.name shouldBe "TestMessage"
                msg.contentType shouldBe "application/json"
            }

            it("should create message with custom content type") {
                val msg = message(
                    name = "AvroMessage",
                    contentType = "application/avro"
                )

                msg.contentType shouldBe "application/avro"
            }
        }

        describe("asyncApiSpec DSL") {

            it("should build complete spec with DSL") {
                val content = """{"asyncapi": "2.6.0", "info": {"title": "Base", "version": "0.1.0", "description": "old"}}"""
                val file = createTempJsonFile(content)

                val result = asyncApiSpec("my-spec", "file:${file.absolutePath}") {
                    info(version = "1.0.0", title = "My API", description = "New description")
                    channel("events") {
                        description("Event channel")
                        subscribe("onEvent", "Receive events")
                    }
                    remove("/info/x-deprecated")
                }

                result.success.shouldBeTrue()
                result.document.shouldContain("My API")
                result.document.shouldContain("1.0.0")
                result.document.shouldContain("events")
            }
        }
    }

    describe("JSON Pointer parsing") {

        it("should handle empty path") {
            val baseFile = createTempJsonFile(baseSpec)
            val overlay = buildJsonObject {
                put("x-root-level", "added")
            }
            val patch = AsyncApiPatch.builder()
                .base("file:${baseFile.absolutePath}")
                .merge(overlay)
                .build()

            val result = patch.apply()

            result.success.shouldBeTrue()
            result.document.shouldContain("x-root-level")
        }

        it("should handle tilde escaping") {
            // ~0 is escape for ~, ~1 is escape for /
            val specWithTilde = """
                {
                    "asyncapi": "2.6.0",
                    "info": { "title": "Test", "version": "1.0.0" },
                    "x-custom": {
                        "a/b": "slash-value",
                        "a~b": "tilde-value"
                    }
                }
            """.trimIndent()
            val baseFile = createTempJsonFile(specWithTilde)

            // Test accessing path with slash (escaped as ~1)
            val patch = AsyncApiPatch.builder()
                .base("file:${baseFile.absolutePath}")
                .add("/x-custom/a~1b", "modified")
                .build()

            val result = patch.apply()

            result.success.shouldBeTrue()
            result.document.shouldContain("modified")
        }
    }

    describe("Edge cases") {

        it("should handle empty document") {
            val emptyDoc = "{}"
            val file = createTempJsonFile(emptyDoc)

            val patch = AsyncApiPatch.builder()
                .base("file:${file.absolutePath}")
                .add("/asyncapi", "2.6.0")
                .add("/info", buildJsonObject {
                    put("title", "New API")
                    put("version", "1.0.0")
                })
                .build()

            val result = patch.apply()

            result.success.shouldBeTrue()
            result.document.shouldContain("asyncapi")
            result.document.shouldContain("New API")
        }

        it("should handle deeply nested paths") {
            val baseFile = createTempJsonFile(baseSpec)

            val patch = AsyncApiPatch.builder()
                .base("file:${baseFile.absolutePath}")
                .add("/components/schemas/User/properties/address/properties/street", buildJsonObject {
                    put("type", "string")
                })
                .build()

            val result = patch.apply()

            result.success.shouldBeTrue()
            result.document.shouldContain("address")
            result.document.shouldContain("street")
        }

        it("should handle unicode in values") {
            val baseFile = createTempJsonFile(baseSpec)

            val patch = AsyncApiPatch.builder()
                .base("file:${baseFile.absolutePath}")
                .add("/info/x-unicode", JsonPrimitive("Hello \u4e16\u754c \ud83c\udf0d"))
                .build()

            val result = patch.apply()

            result.success.shouldBeTrue()
            result.document.shouldContain("\u4e16\u754c")
        }

        it("should handle special characters in keys") {
            val baseFile = createTempJsonFile(baseSpec)

            val patch = AsyncApiPatch.builder()
                .base("file:${baseFile.absolutePath}")
                .add("/info/x-special-chars_123", "value")
                .build()

            val result = patch.apply()

            result.success.shouldBeTrue()
            result.document.shouldContain("x-special-chars_123")
        }
    }
})
