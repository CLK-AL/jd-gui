package org.jd.gui.server.handlers

import mu.KotlinLogging
import org.jd.gui.server.di.MimeCategory

private val logger = KotlinLogging.logger {}

// ============================================
// Scripting Languages with ANTLR Grammars
// ============================================

/**
 * PHP source file handler
 */
class PhpHandler : AbstractSourceCodeHandler() {
    override val handlerId = "php"
    override val displayName = "PHP"
    override val supportedExtensions = setOf("php", "phtml", "php3", "php4", "php5", "phps")
    override val mimeTypes = setOf("application/x-php", "text/x-php")
    override val languageId = "php"
    override val syntaxStyle = "text/php"
    override val antlrGrammar = "PhpParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val classMatch = Regex("""(abstract\s+|final\s+)?(class|interface|trait)\s+(\w+)""").find(line)
            if (classMatch != null) {
                val kind = when (classMatch.groupValues[2]) {
                    "interface" -> DeclarationKind.INTERFACE
                    "trait" -> DeclarationKind.INTERFACE
                    else -> DeclarationKind.CLASS
                }
                declarations.add(Declaration(classMatch.groupValues[3], kind, index + 1, index + 1))
            }

            val funcMatch = Regex("""function\s+(\w+)\s*\(""").find(line)
            if (funcMatch != null) {
                declarations.add(Declaration(funcMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val useMatch = Regex("""use\s+([\w\\]+)""").find(line)
            if (useMatch != null) {
                references.add(Reference(useMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
            val requireMatch = Regex("""(require|include)(_once)?\s*[('"](.+?)['")]""").find(line)
            if (requireMatch != null) {
                references.add(Reference(requireMatch.groupValues[3], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Ruby source file handler
 */
class RubyHandler : AbstractSourceCodeHandler() {
    override val handlerId = "ruby"
    override val displayName = "Ruby"
    override val supportedExtensions = setOf("rb", "rake", "gemspec", "ru", "erb")
    override val mimeTypes = setOf("text/x-ruby", "application/x-ruby")
    override val languageId = "ruby"
    override val syntaxStyle = "text/ruby"
    override val antlrGrammar = null // No ANTLR grammar, but RSyntaxTextArea support
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val classMatch = Regex("""^class\s+(\w+)""").find(line.trim())
            if (classMatch != null) {
                declarations.add(Declaration(classMatch.groupValues[1], DeclarationKind.CLASS, index + 1, index + 1))
            }

            val moduleMatch = Regex("""^module\s+(\w+)""").find(line.trim())
            if (moduleMatch != null) {
                declarations.add(Declaration(moduleMatch.groupValues[1], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val defMatch = Regex("""^def\s+(self\.)?(\w+[?!=]?)""").find(line.trim())
            if (defMatch != null) {
                declarations.add(Declaration(defMatch.groupValues[2], DeclarationKind.METHOD, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val requireMatch = Regex("""require\s+['"](.+?)['"]""").find(line)
            if (requireMatch != null) {
                references.add(Reference(requireMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Perl source file handler
 */
class PerlHandler : AbstractSourceCodeHandler() {
    override val handlerId = "perl"
    override val displayName = "Perl"
    override val supportedExtensions = setOf("pl", "pm", "t", "pod")
    override val mimeTypes = setOf("text/x-perl", "application/x-perl")
    override val languageId = "perl"
    override val syntaxStyle = "text/perl"
    override val antlrGrammar = null
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val packageMatch = Regex("""^package\s+([\w:]+)""").find(line.trim())
            if (packageMatch != null) {
                declarations.add(Declaration(packageMatch.groupValues[1], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val subMatch = Regex("""^sub\s+(\w+)""").find(line.trim())
            if (subMatch != null) {
                declarations.add(Declaration(subMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val useMatch = Regex("""^use\s+([\w:]+)""").find(line.trim())
            if (useMatch != null) {
                references.add(Reference(useMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Lua source file handler
 */
class LuaHandler : AbstractSourceCodeHandler() {
    override val handlerId = "lua"
    override val displayName = "Lua"
    override val supportedExtensions = setOf("lua")
    override val mimeTypes = setOf("text/x-lua", "application/x-lua")
    override val languageId = "lua"
    override val syntaxStyle = "text/lua"
    override val antlrGrammar = "LuaParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val funcMatch = Regex("""(local\s+)?function\s+(\w+)""").find(line)
            if (funcMatch != null) {
                declarations.add(Declaration(funcMatch.groupValues[2], DeclarationKind.FUNCTION, index + 1, index + 1))
            }

            val localFuncMatch = Regex("""local\s+(\w+)\s*=\s*function""").find(line)
            if (localFuncMatch != null) {
                declarations.add(Declaration(localFuncMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val requireMatch = Regex("""require\s*\(?['"](.+?)['"]\)?""").find(line)
            if (requireMatch != null) {
                references.add(Reference(requireMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Scala source file handler
 */
class ScalaHandler : AbstractSourceCodeHandler() {
    override val handlerId = "scala"
    override val displayName = "Scala"
    override val supportedExtensions = setOf("scala", "sc")
    override val mimeTypes = setOf("text/x-scala")
    override val languageId = "scala"
    override val syntaxStyle = "text/scala"
    override val antlrGrammar = "Scala.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val classMatch = Regex("""(case\s+)?(class|object|trait)\s+(\w+)""").find(line)
            if (classMatch != null) {
                val kind = when (classMatch.groupValues[2]) {
                    "trait" -> DeclarationKind.INTERFACE
                    "object" -> DeclarationKind.CLASS
                    else -> DeclarationKind.CLASS
                }
                declarations.add(Declaration(classMatch.groupValues[3], kind, index + 1, index + 1))
            }

            val defMatch = Regex("""def\s+(\w+)""").find(line)
            if (defMatch != null) {
                declarations.add(Declaration(defMatch.groupValues[1], DeclarationKind.METHOD, index + 1, index + 1))
            }

            val valMatch = Regex("""(val|var)\s+(\w+)""").find(line)
            if (valMatch != null && !line.trim().startsWith("//")) {
                declarations.add(Declaration(valMatch.groupValues[2], DeclarationKind.PROPERTY, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""import\s+([\w._{}]+)""").find(line)
            if (importMatch != null) {
                references.add(Reference(importMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Groovy source file handler
 */
class GroovyHandler : AbstractSourceCodeHandler() {
    override val handlerId = "groovy"
    override val displayName = "Groovy"
    override val supportedExtensions = setOf("groovy", "gvy", "gy", "gsh")
    override val mimeTypes = setOf("text/x-groovy")
    override val languageId = "groovy"
    override val syntaxStyle = "text/groovy"
    override val antlrGrammar = null // Groovy shares Java-like syntax
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val classMatch = Regex("""(class|interface|enum|trait)\s+(\w+)""").find(line)
            if (classMatch != null) {
                val kind = when (classMatch.groupValues[1]) {
                    "interface" -> DeclarationKind.INTERFACE
                    "trait" -> DeclarationKind.INTERFACE
                    "enum" -> DeclarationKind.ENUM
                    else -> DeclarationKind.CLASS
                }
                declarations.add(Declaration(classMatch.groupValues[2], kind, index + 1, index + 1))
            }

            val defMatch = Regex("""def\s+(\w+)\s*\(""").find(line)
            if (defMatch != null) {
                declarations.add(Declaration(defMatch.groupValues[1], DeclarationKind.METHOD, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""import\s+([\w.]+)""").find(line)
            if (importMatch != null) {
                references.add(Reference(importMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

// ============================================
// Modern Languages with ANTLR Grammars
// ============================================

/**
 * Dart source file handler
 */
class DartHandler : AbstractSourceCodeHandler() {
    override val handlerId = "dart"
    override val displayName = "Dart"
    override val supportedExtensions = setOf("dart")
    override val mimeTypes = setOf("application/dart", "text/x-dart")
    override val languageId = "dart"
    override val syntaxStyle = "text/dart"
    override val antlrGrammar = "Dart2Parser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val classMatch = Regex("""(abstract\s+)?(class|mixin|extension)\s+(\w+)""").find(line)
            if (classMatch != null) {
                declarations.add(Declaration(classMatch.groupValues[3], DeclarationKind.CLASS, index + 1, index + 1))
            }

            val enumMatch = Regex("""enum\s+(\w+)""").find(line)
            if (enumMatch != null) {
                declarations.add(Declaration(enumMatch.groupValues[1], DeclarationKind.ENUM, index + 1, index + 1))
            }

            val funcMatch = Regex("""^\s*([\w<>]+)\s+(\w+)\s*\(""").find(line)
            if (funcMatch != null && !line.contains("class ") && !line.contains("if ") && !line.contains("while ")) {
                declarations.add(Declaration(funcMatch.groupValues[2], DeclarationKind.FUNCTION, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""import\s+['"](.+?)['"]""").find(line)
            if (importMatch != null) {
                references.add(Reference(importMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Elixir source file handler
 */
class ElixirHandler : AbstractSourceCodeHandler() {
    override val handlerId = "elixir"
    override val displayName = "Elixir"
    override val supportedExtensions = setOf("ex", "exs")
    override val mimeTypes = setOf("text/x-elixir")
    override val languageId = "elixir"
    override val syntaxStyle = "text/elixir"
    override val antlrGrammar = "ElixirParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val moduleMatch = Regex("""defmodule\s+([\w.]+)""").find(line)
            if (moduleMatch != null) {
                declarations.add(Declaration(moduleMatch.groupValues[1], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val defMatch = Regex("""(def|defp|defmacro|defmacrop)\s+(\w+)""").find(line)
            if (defMatch != null) {
                declarations.add(Declaration(defMatch.groupValues[2], DeclarationKind.FUNCTION, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""(import|alias|use|require)\s+([\w.]+)""").find(line)
            if (importMatch != null) {
                references.add(Reference(importMatch.groupValues[2], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Haskell source file handler
 */
class HaskellHandler : AbstractSourceCodeHandler() {
    override val handlerId = "haskell"
    override val displayName = "Haskell"
    override val supportedExtensions = setOf("hs", "lhs")
    override val mimeTypes = setOf("text/x-haskell")
    override val languageId = "haskell"
    override val syntaxStyle = "text/haskell"
    override val antlrGrammar = "HaskellParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val moduleMatch = Regex("""^module\s+([\w.]+)""").find(line)
            if (moduleMatch != null) {
                declarations.add(Declaration(moduleMatch.groupValues[1], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val dataMatch = Regex("""^data\s+(\w+)""").find(line)
            if (dataMatch != null) {
                declarations.add(Declaration(dataMatch.groupValues[1], DeclarationKind.CLASS, index + 1, index + 1))
            }

            val typeMatch = Regex("""^type\s+(\w+)""").find(line)
            if (typeMatch != null) {
                declarations.add(Declaration(typeMatch.groupValues[1], DeclarationKind.TYPE_ALIAS, index + 1, index + 1))
            }

            val classMatch = Regex("""^class\s+.*\s+(\w+)\s+where""").find(line)
            if (classMatch != null) {
                declarations.add(Declaration(classMatch.groupValues[1], DeclarationKind.INTERFACE, index + 1, index + 1))
            }

            // Function definitions (simplified)
            val funcMatch = Regex("""^(\w+)\s+::""").find(line)
            if (funcMatch != null) {
                declarations.add(Declaration(funcMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""^import\s+(qualified\s+)?([\w.]+)""").find(line)
            if (importMatch != null) {
                references.add(Reference(importMatch.groupValues[2], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Objective-C source file handler
 */
class ObjectiveCHandler : AbstractSourceCodeHandler() {
    override val handlerId = "objectivec"
    override val displayName = "Objective-C"
    override val supportedExtensions = setOf("m", "mm", "h")
    override val mimeTypes = setOf("text/x-objectivec")
    override val languageId = "objectivec"
    override val syntaxStyle = "text/objectivec"
    override val antlrGrammar = "ObjectiveCParser.g4"
    override val priority = 9 // Lower than C/C++ for .h files

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val interfaceMatch = Regex("""@interface\s+(\w+)""").find(line)
            if (interfaceMatch != null) {
                declarations.add(Declaration(interfaceMatch.groupValues[1], DeclarationKind.CLASS, index + 1, index + 1))
            }

            val protocolMatch = Regex("""@protocol\s+(\w+)""").find(line)
            if (protocolMatch != null) {
                declarations.add(Declaration(protocolMatch.groupValues[1], DeclarationKind.INTERFACE, index + 1, index + 1))
            }

            val methodMatch = Regex("""^[-+]\s*\([^)]+\)\s*(\w+)""").find(line.trim())
            if (methodMatch != null) {
                declarations.add(Declaration(methodMatch.groupValues[1], DeclarationKind.METHOD, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""#import\s+[<"](.+?)[>"]""").find(line)
            if (importMatch != null) {
                references.add(Reference(importMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

// ============================================
// Specialized Languages with ANTLR Grammars
// ============================================

/**
 * Solidity (Ethereum) source file handler
 */
class SolidityHandler : AbstractSourceCodeHandler() {
    override val handlerId = "solidity"
    override val displayName = "Solidity"
    override val supportedExtensions = setOf("sol")
    override val mimeTypes = setOf("text/x-solidity")
    override val languageId = "solidity"
    override val syntaxStyle = "text/solidity"
    override val antlrGrammar = "SolidityParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val contractMatch = Regex("""(contract|interface|library|abstract\s+contract)\s+(\w+)""").find(line)
            if (contractMatch != null) {
                val kind = if (contractMatch.groupValues[1].contains("interface")) DeclarationKind.INTERFACE else DeclarationKind.CLASS
                declarations.add(Declaration(contractMatch.groupValues[2], kind, index + 1, index + 1))
            }

            val structMatch = Regex("""struct\s+(\w+)""").find(line)
            if (structMatch != null) {
                declarations.add(Declaration(structMatch.groupValues[1], DeclarationKind.CLASS, index + 1, index + 1))
            }

            val enumMatch = Regex("""enum\s+(\w+)""").find(line)
            if (enumMatch != null) {
                declarations.add(Declaration(enumMatch.groupValues[1], DeclarationKind.ENUM, index + 1, index + 1))
            }

            val funcMatch = Regex("""function\s+(\w+)""").find(line)
            if (funcMatch != null) {
                declarations.add(Declaration(funcMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""import\s+["'](.+?)["']""").find(line)
            if (importMatch != null) {
                references.add(Reference(importMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * GraphQL schema/query handler
 */
class GraphQLHandler : AbstractSourceCodeHandler() {
    override val handlerId = "graphql"
    override val displayName = "GraphQL"
    override val supportedExtensions = setOf("graphql", "gql")
    override val mimeTypes = setOf("application/graphql")
    override val languageId = "graphql"
    override val syntaxStyle = "text/graphql"
    override val antlrGrammar = "GraphQL.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val typeMatch = Regex("""(type|interface|enum|input|union|scalar)\s+(\w+)""").find(line)
            if (typeMatch != null) {
                val kind = when (typeMatch.groupValues[1]) {
                    "interface" -> DeclarationKind.INTERFACE
                    "enum" -> DeclarationKind.ENUM
                    else -> DeclarationKind.CLASS
                }
                declarations.add(Declaration(typeMatch.groupValues[2], kind, index + 1, index + 1))
            }

            val queryMatch = Regex("""(query|mutation|subscription)\s+(\w+)""").find(line)
            if (queryMatch != null) {
                declarations.add(Declaration(queryMatch.groupValues[2], DeclarationKind.FUNCTION, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> = emptyList()
}

/**
 * Protocol Buffers handler
 */
class ProtobufHandler : AbstractSourceCodeHandler() {
    override val handlerId = "protobuf"
    override val displayName = "Protocol Buffers"
    override val supportedExtensions = setOf("proto")
    override val mimeTypes = setOf("text/x-protobuf")
    override val languageId = "protobuf"
    override val syntaxStyle = "text/protobuf"
    override val antlrGrammar = "Protobuf3.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val messageMatch = Regex("""message\s+(\w+)""").find(line)
            if (messageMatch != null) {
                declarations.add(Declaration(messageMatch.groupValues[1], DeclarationKind.CLASS, index + 1, index + 1))
            }

            val enumMatch = Regex("""enum\s+(\w+)""").find(line)
            if (enumMatch != null) {
                declarations.add(Declaration(enumMatch.groupValues[1], DeclarationKind.ENUM, index + 1, index + 1))
            }

            val serviceMatch = Regex("""service\s+(\w+)""").find(line)
            if (serviceMatch != null) {
                declarations.add(Declaration(serviceMatch.groupValues[1], DeclarationKind.INTERFACE, index + 1, index + 1))
            }

            val rpcMatch = Regex("""rpc\s+(\w+)""").find(line)
            if (rpcMatch != null) {
                declarations.add(Declaration(rpcMatch.groupValues[1], DeclarationKind.METHOD, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""import\s+["'](.+?)["']""").find(line)
            if (importMatch != null) {
                references.add(Reference(importMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Terraform/HCL handler
 */
class TerraformHandler : AbstractSourceCodeHandler() {
    override val handlerId = "terraform"
    override val displayName = "Terraform"
    override val supportedExtensions = setOf("tf", "tfvars")
    override val mimeTypes = setOf("text/x-terraform", "application/x-hcl")
    override val languageId = "terraform"
    override val syntaxStyle = "text/terraform"
    override val antlrGrammar = "terraform.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val resourceMatch = Regex("""(resource|data)\s+"(\w+)"\s+"(\w+)"""").find(line)
            if (resourceMatch != null) {
                declarations.add(Declaration(
                    "${resourceMatch.groupValues[2]}.${resourceMatch.groupValues[3]}",
                    DeclarationKind.CLASS,
                    index + 1, index + 1
                ))
            }

            val moduleMatch = Regex("""module\s+"(\w+)"""").find(line)
            if (moduleMatch != null) {
                declarations.add(Declaration(moduleMatch.groupValues[1], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val variableMatch = Regex("""variable\s+"(\w+)"""").find(line)
            if (variableMatch != null) {
                declarations.add(Declaration(variableMatch.groupValues[1], DeclarationKind.PROPERTY, index + 1, index + 1))
            }

            val outputMatch = Regex("""output\s+"(\w+)"""").find(line)
            if (outputMatch != null) {
                declarations.add(Declaration(outputMatch.groupValues[1], DeclarationKind.PROPERTY, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> = emptyList()
}

// ============================================
// Legacy Languages with ANTLR Grammars
// ============================================

/**
 * COBOL source file handler
 */
class CobolHandler : AbstractSourceCodeHandler() {
    override val handlerId = "cobol"
    override val displayName = "COBOL"
    override val supportedExtensions = setOf("cbl", "cob", "cpy")
    override val mimeTypes = setOf("text/x-cobol")
    override val languageId = "cobol"
    override val syntaxStyle = "text/cobol"
    override val antlrGrammar = "Cobol85.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val divisionMatch = Regex("""(\w+)\s+DIVISION""", RegexOption.IGNORE_CASE).find(line)
            if (divisionMatch != null) {
                declarations.add(Declaration(divisionMatch.groupValues[1], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val sectionMatch = Regex("""(\w+)\s+SECTION""", RegexOption.IGNORE_CASE).find(line)
            if (sectionMatch != null) {
                declarations.add(Declaration(sectionMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }

            val paragraphMatch = Regex("""^\s{7}(\w+[-\w]*)\.?\s*$""").find(line)
            if (paragraphMatch != null && !line.contains("DIVISION") && !line.contains("SECTION")) {
                declarations.add(Declaration(paragraphMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val copyMatch = Regex("""COPY\s+(\w+)""", RegexOption.IGNORE_CASE).find(line)
            if (copyMatch != null) {
                references.add(Reference(copyMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Fortran source file handler
 */
class FortranHandler : AbstractSourceCodeHandler() {
    override val handlerId = "fortran"
    override val displayName = "Fortran"
    override val supportedExtensions = setOf("f", "for", "f90", "f95", "f03", "f08")
    override val mimeTypes = setOf("text/x-fortran")
    override val languageId = "fortran"
    override val syntaxStyle = "text/fortran"
    override val antlrGrammar = "Fortran90Parser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val programMatch = Regex("""PROGRAM\s+(\w+)""", RegexOption.IGNORE_CASE).find(line)
            if (programMatch != null) {
                declarations.add(Declaration(programMatch.groupValues[1], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val moduleMatch = Regex("""MODULE\s+(\w+)""", RegexOption.IGNORE_CASE).find(line)
            if (moduleMatch != null && !line.uppercase().contains("END MODULE")) {
                declarations.add(Declaration(moduleMatch.groupValues[1], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val subroutineMatch = Regex("""SUBROUTINE\s+(\w+)""", RegexOption.IGNORE_CASE).find(line)
            if (subroutineMatch != null) {
                declarations.add(Declaration(subroutineMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }

            val functionMatch = Regex("""FUNCTION\s+(\w+)""", RegexOption.IGNORE_CASE).find(line)
            if (functionMatch != null && !line.uppercase().contains("END FUNCTION")) {
                declarations.add(Declaration(functionMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val useMatch = Regex("""USE\s+(\w+)""", RegexOption.IGNORE_CASE).find(line)
            if (useMatch != null) {
                references.add(Reference(useMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Ada source file handler
 */
class AdaHandler : AbstractSourceCodeHandler() {
    override val handlerId = "ada"
    override val displayName = "Ada"
    override val supportedExtensions = setOf("ada", "adb", "ads")
    override val mimeTypes = setOf("text/x-ada")
    override val languageId = "ada"
    override val syntaxStyle = "text/ada"
    override val antlrGrammar = "AdaParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val packageMatch = Regex("""package\s+(body\s+)?(\w+)""", RegexOption.IGNORE_CASE).find(line)
            if (packageMatch != null) {
                declarations.add(Declaration(packageMatch.groupValues[2], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val procedureMatch = Regex("""procedure\s+(\w+)""", RegexOption.IGNORE_CASE).find(line)
            if (procedureMatch != null) {
                declarations.add(Declaration(procedureMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }

            val functionMatch = Regex("""function\s+(\w+)""", RegexOption.IGNORE_CASE).find(line)
            if (functionMatch != null) {
                declarations.add(Declaration(functionMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }

            val typeMatch = Regex("""type\s+(\w+)\s+is""", RegexOption.IGNORE_CASE).find(line)
            if (typeMatch != null) {
                declarations.add(Declaration(typeMatch.groupValues[1], DeclarationKind.CLASS, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val withMatch = Regex("""with\s+([\w.]+)""", RegexOption.IGNORE_CASE).find(line)
            if (withMatch != null) {
                references.add(Reference(withMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Zig source file handler
 */
class ZigHandler : AbstractSourceCodeHandler() {
    override val handlerId = "zig"
    override val displayName = "Zig"
    override val supportedExtensions = setOf("zig")
    override val mimeTypes = setOf("text/x-zig")
    override val languageId = "zig"
    override val syntaxStyle = "text/zig"
    override val antlrGrammar = "ZigParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val structMatch = Regex("""(pub\s+)?const\s+(\w+)\s*=\s*struct""").find(line)
            if (structMatch != null) {
                declarations.add(Declaration(structMatch.groupValues[2], DeclarationKind.CLASS, index + 1, index + 1))
            }

            val enumMatch = Regex("""(pub\s+)?const\s+(\w+)\s*=\s*enum""").find(line)
            if (enumMatch != null) {
                declarations.add(Declaration(enumMatch.groupValues[2], DeclarationKind.ENUM, index + 1, index + 1))
            }

            val fnMatch = Regex("""(pub\s+)?fn\s+(\w+)""").find(line)
            if (fnMatch != null) {
                declarations.add(Declaration(fnMatch.groupValues[2], DeclarationKind.FUNCTION, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""@import\s*\(\s*"(.+?)"\s*\)""").find(line)
            if (importMatch != null) {
                references.add(Reference(importMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

// ============================================
// Hardware Description Languages
// ============================================

/**
 * Verilog HDL handler
 */
class VerilogHandler : AbstractSourceCodeHandler() {
    override val handlerId = "verilog"
    override val displayName = "Verilog"
    override val supportedExtensions = setOf("v", "vh", "sv", "svh")
    override val mimeTypes = setOf("text/x-verilog", "text/x-systemverilog")
    override val languageId = "verilog"
    override val syntaxStyle = "text/verilog"
    override val antlrGrammar = "VerilogParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val moduleMatch = Regex("""module\s+(\w+)""").find(line)
            if (moduleMatch != null) {
                declarations.add(Declaration(moduleMatch.groupValues[1], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val interfaceMatch = Regex("""interface\s+(\w+)""").find(line)
            if (interfaceMatch != null) {
                declarations.add(Declaration(interfaceMatch.groupValues[1], DeclarationKind.INTERFACE, index + 1, index + 1))
            }

            val classMatch = Regex("""class\s+(\w+)""").find(line)
            if (classMatch != null) {
                declarations.add(Declaration(classMatch.groupValues[1], DeclarationKind.CLASS, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val includeMatch = Regex("""`include\s+"(.+?)"""").find(line)
            if (includeMatch != null) {
                references.add(Reference(includeMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * VHDL handler
 */
class VhdlHandler : AbstractSourceCodeHandler() {
    override val handlerId = "vhdl"
    override val displayName = "VHDL"
    override val supportedExtensions = setOf("vhd", "vhdl")
    override val mimeTypes = setOf("text/x-vhdl")
    override val languageId = "vhdl"
    override val syntaxStyle = "text/vhdl"
    override val antlrGrammar = "vhdl.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val entityMatch = Regex("""entity\s+(\w+)\s+is""", RegexOption.IGNORE_CASE).find(line)
            if (entityMatch != null) {
                declarations.add(Declaration(entityMatch.groupValues[1], DeclarationKind.CLASS, index + 1, index + 1))
            }

            val architectureMatch = Regex("""architecture\s+(\w+)\s+of""", RegexOption.IGNORE_CASE).find(line)
            if (architectureMatch != null) {
                declarations.add(Declaration(architectureMatch.groupValues[1], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val packageMatch = Regex("""package\s+(body\s+)?(\w+)\s+is""", RegexOption.IGNORE_CASE).find(line)
            if (packageMatch != null) {
                declarations.add(Declaration(packageMatch.groupValues[2], DeclarationKind.MODULE, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val useMatch = Regex("""use\s+([\w.]+)""", RegexOption.IGNORE_CASE).find(line)
            if (useMatch != null) {
                references.add(Reference(useMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

// ============================================
// Functional Languages
// ============================================

/**
 * Clojure source file handler
 */
class ClojureHandler : AbstractSourceCodeHandler() {
    override val handlerId = "clojure"
    override val displayName = "Clojure"
    override val supportedExtensions = setOf("clj", "cljs", "cljc", "edn")
    override val mimeTypes = setOf("text/x-clojure")
    override val languageId = "clojure"
    override val syntaxStyle = "text/clojure"
    override val antlrGrammar = "Clojure.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val nsMatch = Regex("""\(ns\s+([\w.-]+)""").find(line)
            if (nsMatch != null) {
                declarations.add(Declaration(nsMatch.groupValues[1], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val defnMatch = Regex("""\(defn-?\s+(\w+)""").find(line)
            if (defnMatch != null) {
                declarations.add(Declaration(defnMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }

            val defMatch = Regex("""\(def\s+(\w+)""").find(line)
            if (defMatch != null) {
                declarations.add(Declaration(defMatch.groupValues[1], DeclarationKind.PROPERTY, index + 1, index + 1))
            }

            val defrecordMatch = Regex("""\(defrecord\s+(\w+)""").find(line)
            if (defrecordMatch != null) {
                declarations.add(Declaration(defrecordMatch.groupValues[1], DeclarationKind.CLASS, index + 1, index + 1))
            }

            val defprotocolMatch = Regex("""\(defprotocol\s+(\w+)""").find(line)
            if (defprotocolMatch != null) {
                declarations.add(Declaration(defprotocolMatch.groupValues[1], DeclarationKind.INTERFACE, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val requireMatch = Regex("""\(:require\s+\[?([\w.-]+)""").find(line)
            if (requireMatch != null) {
                references.add(Reference(requireMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}

/**
 * Erlang source file handler
 */
class ErlangHandler : AbstractSourceCodeHandler() {
    override val handlerId = "erlang"
    override val displayName = "Erlang"
    override val supportedExtensions = setOf("erl", "hrl")
    override val mimeTypes = setOf("text/x-erlang")
    override val languageId = "erlang"
    override val syntaxStyle = "text/erlang"
    override val antlrGrammar = "Erlang.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val moduleMatch = Regex("""-module\s*\(\s*(\w+)\s*\)""").find(line)
            if (moduleMatch != null) {
                declarations.add(Declaration(moduleMatch.groupValues[1], DeclarationKind.MODULE, index + 1, index + 1))
            }

            val funcMatch = Regex("""^(\w+)\s*\(""").find(line.trim())
            if (funcMatch != null && !line.startsWith("-") && !line.startsWith("%")) {
                declarations.add(Declaration(funcMatch.groupValues[1], DeclarationKind.FUNCTION, index + 1, index + 1))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""-import\s*\(\s*(\w+)""").find(line)
            if (importMatch != null) {
                references.add(Reference(importMatch.groupValues[1], ReferenceKind.IMPORT, index + 1, 0))
            }
        }
        return references
    }
}
