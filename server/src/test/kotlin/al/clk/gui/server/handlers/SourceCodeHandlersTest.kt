package al.clk.gui.server.handlers

import io.mockk.*
import kotlinx.coroutines.runBlocking
import al.clk.gui.server.di.MimeCategory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class SourceCodeHandlersTest {

    @Nested
    inner class JavaHandlerTest {
        private lateinit var handler: JavaHandler

        @BeforeEach
        fun setup() {
            handler = JavaHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("java", handler.handlerId)
            assertEquals("Java", handler.displayName)
            assertTrue(handler.supportedExtensions.contains("java"))
            assertEquals(MimeCategory.PROGRAMMING, handler.category)
        }

        @Test
        fun `should extract class declarations`() = runBlocking {
            val code = """
                package com.example;

                public class MyClass {
                    private String field;

                    public void method() {}
                }
            """.trimIndent()

            val content = FileContent(
                name = "MyClass.java",
                extension = "java",
                content = code.toByteArray()
            )

            val declarations = handler.extractDeclarations(content)

            assertTrue(declarations.any { it.name == "MyClass" && it.kind == DeclarationKind.CLASS })
            assertTrue(declarations.any { it.name == "method" && it.kind == DeclarationKind.METHOD })
        }

        @Test
        fun `should extract interface declarations`() = runBlocking {
            val code = """
                public interface MyInterface {
                    void doSomething();
                }
            """.trimIndent()

            val content = FileContent(
                name = "MyInterface.java",
                extension = "java",
                content = code.toByteArray()
            )

            val declarations = handler.extractDeclarations(content)

            assertTrue(declarations.any { it.kind == DeclarationKind.INTERFACE })
        }

        @Test
        fun `should extract import references`() = runBlocking {
            val code = """
                import java.util.List;
                import java.util.Map;

                public class Test {}
            """.trimIndent()

            val content = FileContent(
                name = "Test.java",
                extension = "java",
                content = code.toByteArray()
            )

            val references = handler.extractReferences(content)

            assertTrue(references.any { it.target.contains("java.util.List") })
            assertTrue(references.any { it.target.contains("java.util.Map") })
        }

        @Test
        fun `should process file successfully`() = runBlocking {
            val code = "public class Test {}"
            val content = FileContent(
                name = "Test.java",
                extension = "java",
                content = code.toByteArray()
            )

            val result = handler.process(content)

            assertTrue(result.success)
        }
    }

    @Nested
    inner class KotlinHandlerTest {
        private lateinit var handler: KotlinHandler

        @BeforeEach
        fun setup() {
            handler = KotlinHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("kotlin", handler.handlerId)
            assertEquals("Kotlin", handler.displayName)
            assertTrue(handler.supportedExtensions.contains("kt"))
            assertTrue(handler.supportedExtensions.contains("kts"))
        }

        @Test
        fun `should extract class declarations`() = runBlocking {
            val code = """
                class MyClass {
                    fun doSomething() {}
                }

                data class DataClass(val name: String)
            """.trimIndent()

            val content = FileContent(
                name = "MyClass.kt",
                extension = "kt",
                content = code.toByteArray()
            )

            val declarations = handler.extractDeclarations(content)

            assertTrue(declarations.any { it.name == "MyClass" })
            assertTrue(declarations.any { it.name == "doSomething" })
        }

        @Test
        fun `should extract top-level function`() = runBlocking {
            val code = """
                fun topLevelFunction() {
                    println("Hello")
                }
            """.trimIndent()

            val content = FileContent(
                name = "Functions.kt",
                extension = "kt",
                content = code.toByteArray()
            )

            val declarations = handler.extractDeclarations(content)

            assertTrue(declarations.any { it.name == "topLevelFunction" })
        }

        @Test
        fun `should extract import references`() = runBlocking {
            val code = """
                import kotlin.collections.List
                import kotlinx.coroutines.*

                fun main() {}
            """.trimIndent()

            val content = FileContent(
                name = "Main.kt",
                extension = "kt",
                content = code.toByteArray()
            )

            val references = handler.extractReferences(content)

            assertTrue(references.any { it.target.contains("kotlin.collections.List") })
        }
    }

    @Nested
    inner class TypeScriptHandlerTest {
        private lateinit var handler: TypeScriptHandler

        @BeforeEach
        fun setup() {
            handler = TypeScriptHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("typescript", handler.handlerId)
            assertTrue(handler.supportedExtensions.contains("ts"))
            assertTrue(handler.supportedExtensions.contains("tsx"))
        }

        @Test
        fun `should extract interface declarations`() = runBlocking {
            val code = """
                interface User {
                    name: string;
                    age: number;
                }

                function greet(user: User): void {}
            """.trimIndent()

            val content = FileContent(
                name = "types.ts",
                extension = "ts",
                content = code.toByteArray()
            )

            val declarations = handler.extractDeclarations(content)

            assertTrue(declarations.any { it.name == "User" && it.kind == DeclarationKind.INTERFACE })
            assertTrue(declarations.any { it.name == "greet" && it.kind == DeclarationKind.FUNCTION })
        }

        @Test
        fun `should extract type alias`() = runBlocking {
            val code = """
                type StringOrNumber = string | number;
            """.trimIndent()

            val content = FileContent(
                name = "types.ts",
                extension = "ts",
                content = code.toByteArray()
            )

            val declarations = handler.extractDeclarations(content)

            assertTrue(declarations.any { it.name == "StringOrNumber" && it.kind == DeclarationKind.TYPE_ALIAS })
        }
    }

    @Nested
    inner class PythonHandlerTest {
        private lateinit var handler: PythonHandler

        @BeforeEach
        fun setup() {
            handler = PythonHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("python", handler.handlerId)
            assertTrue(handler.supportedExtensions.contains("py"))
            assertTrue(handler.supportedExtensions.contains("pyw"))
        }

        @Test
        fun `should extract class and function declarations`() = runBlocking {
            val code = """
                class MyClass:
                    def method(self):
                        pass

                def standalone_function():
                    return 42
            """.trimIndent()

            val content = FileContent(
                name = "module.py",
                extension = "py",
                content = code.toByteArray()
            )

            val declarations = handler.extractDeclarations(content)

            assertTrue(declarations.any { it.name == "MyClass" && it.kind == DeclarationKind.CLASS })
            assertTrue(declarations.any { it.name == "method" && it.kind == DeclarationKind.METHOD })
            assertTrue(declarations.any { it.name == "standalone_function" && it.kind == DeclarationKind.FUNCTION })
        }

        @Test
        fun `should extract import references`() = runBlocking {
            val code = """
                import os
                from typing import List, Optional
                from collections import defaultdict

                def main():
                    pass
            """.trimIndent()

            val content = FileContent(
                name = "main.py",
                extension = "py",
                content = code.toByteArray()
            )

            val references = handler.extractReferences(content)

            assertTrue(references.any { it.target == "os" })
            assertTrue(references.any { it.target.contains("typing") })
        }
    }

    @Nested
    inner class GoHandlerTest {
        private lateinit var handler: GoHandler

        @BeforeEach
        fun setup() {
            handler = GoHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("go", handler.handlerId)
            assertTrue(handler.supportedExtensions.contains("go"))
        }

        @Test
        fun `should extract struct and function declarations`() = runBlocking {
            val code = """
                package main

                type User struct {
                    Name string
                    Age  int
                }

                func (u *User) Greet() string {
                    return "Hello"
                }

                func main() {}
            """.trimIndent()

            val content = FileContent(
                name = "main.go",
                extension = "go",
                content = code.toByteArray()
            )

            val declarations = handler.extractDeclarations(content)

            assertTrue(declarations.any { it.name == "User" && it.kind == DeclarationKind.CLASS })
            assertTrue(declarations.any { it.name == "Greet" && it.kind == DeclarationKind.METHOD })
            assertTrue(declarations.any { it.name == "main" && it.kind == DeclarationKind.FUNCTION })
        }

        @Test
        fun `should extract interface declarations`() = runBlocking {
            val code = """
                type Reader interface {
                    Read(p []byte) (n int, err error)
                }
            """.trimIndent()

            val content = FileContent(
                name = "io.go",
                extension = "go",
                content = code.toByteArray()
            )

            val declarations = handler.extractDeclarations(content)

            assertTrue(declarations.any { it.kind == DeclarationKind.INTERFACE })
        }
    }

    @Nested
    inner class RustHandlerTest {
        private lateinit var handler: RustHandler

        @BeforeEach
        fun setup() {
            handler = RustHandler()
        }

        @Test
        fun `should have correct handler properties`() {
            assertEquals("rust", handler.handlerId)
            assertTrue(handler.supportedExtensions.contains("rs"))
        }

        @Test
        fun `should extract struct and impl declarations`() = runBlocking {
            val code = """
                struct User {
                    name: String,
                    age: u32,
                }

                impl User {
                    fn new(name: String) -> User {
                        User { name, age: 0 }
                    }
                }

                fn main() {}
            """.trimIndent()

            val content = FileContent(
                name = "main.rs",
                extension = "rs",
                content = code.toByteArray()
            )

            val declarations = handler.extractDeclarations(content)

            assertTrue(declarations.any { it.name == "User" && it.kind == DeclarationKind.CLASS })
            assertTrue(declarations.any { it.name == "new" && it.kind == DeclarationKind.METHOD })
            assertTrue(declarations.any { it.name == "main" && it.kind == DeclarationKind.FUNCTION })
        }

        @Test
        fun `should extract trait declarations`() = runBlocking {
            val code = """
                trait Display {
                    fn display(&self) -> String;
                }
            """.trimIndent()

            val content = FileContent(
                name = "traits.rs",
                extension = "rs",
                content = code.toByteArray()
            )

            val declarations = handler.extractDeclarations(content)

            assertTrue(declarations.any { it.kind == DeclarationKind.INTERFACE })
        }
    }

    @Nested
    inner class DeclarationKindTest {

        @Test
        fun `should have all declaration kinds`() {
            val kinds = DeclarationKind.values()

            assertTrue(kinds.contains(DeclarationKind.CLASS))
            assertTrue(kinds.contains(DeclarationKind.INTERFACE))
            assertTrue(kinds.contains(DeclarationKind.ENUM))
            assertTrue(kinds.contains(DeclarationKind.FUNCTION))
            assertTrue(kinds.contains(DeclarationKind.METHOD))
            assertTrue(kinds.contains(DeclarationKind.PROPERTY))
            assertTrue(kinds.contains(DeclarationKind.VARIABLE))
            assertTrue(kinds.contains(DeclarationKind.MODULE))
            assertTrue(kinds.contains(DeclarationKind.TYPE_ALIAS))
        }
    }

    @Nested
    inner class ReferenceKindTest {

        @Test
        fun `should have all reference kinds`() {
            val kinds = ReferenceKind.values()

            assertTrue(kinds.contains(ReferenceKind.IMPORT))
            assertTrue(kinds.contains(ReferenceKind.TYPE_REFERENCE))
            assertTrue(kinds.contains(ReferenceKind.METHOD_CALL))
            assertTrue(kinds.contains(ReferenceKind.FIELD_ACCESS))
        }
    }

    @Nested
    inner class FileContentTest {

        @Test
        fun `should create FileContent with required fields`() {
            val content = FileContent(
                name = "test.java",
                extension = "java",
                content = "public class Test {}".toByteArray()
            )

            assertEquals("test.java", content.name)
            assertEquals("java", content.extension)
        }

        @Test
        fun `should convert content to string`() {
            val text = "Hello World"
            val content = FileContent(
                name = "test.txt",
                extension = "txt",
                content = text.toByteArray()
            )

            assertEquals(text, content.asString())
        }

        @Test
        fun `should include optional metadata`() {
            val content = FileContent(
                name = "test.java",
                extension = "java",
                content = "code".toByteArray(),
                metadata = mapOf("author" to "John", "version" to "1.0")
            )

            assertEquals("John", content.metadata["author"])
            assertEquals("1.0", content.metadata["version"])
        }
    }

    @Nested
    inner class ProcessingResultTest {

        @Test
        fun `should create successful result`() {
            val result = ProcessingResult(
                success = true,
                contentType = "text/plain",
                processedContent = "processed"
            )

            assertTrue(result.success)
            assertEquals("text/plain", result.contentType)
            assertEquals("processed", result.processedContent)
            assertNull(result.error)
        }

        @Test
        fun `should create error result`() {
            val result = ProcessingResult(
                success = false,
                contentType = "application/octet-stream",
                error = "Failed to process"
            )

            assertFalse(result.success)
            assertEquals("Failed to process", result.error)
            assertNull(result.processedContent)
        }

        @Test
        fun `should include metadata`() {
            val result = ProcessingResult(
                success = true,
                contentType = "text/x-java",
                metadata = mapOf("lineCount" to "100", "parser" to "antlr")
            )

            assertEquals("100", result.metadata["lineCount"])
            assertEquals("antlr", result.metadata["parser"])
        }
    }
}
