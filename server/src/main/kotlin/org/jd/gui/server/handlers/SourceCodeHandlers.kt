package org.jd.gui.server.handlers

import mu.KotlinLogging
import org.jd.gui.server.di.MimeCategory

private val logger = KotlinLogging.logger {}

/**
 * Base implementation for source code handlers
 */
abstract class AbstractSourceCodeHandler : SourceCodeHandler {
    override val category: MimeCategory = MimeCategory.PROGRAMMING

    override suspend fun process(content: FileContent): ProcessingResult {
        return try {
            val declarations = extractDeclarations(content)
            val metadata = extractMetadata(content)

            ProcessingResult(
                success = true,
                contentType = primaryMimeType,
                data = content.asString(),
                metadata = metadata + mapOf(
                    "declarationCount" to declarations.size.toString(),
                    "language" to languageId
                )
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

    override suspend fun extractMetadata(content: FileContent): Map<String, String> {
        val lines = content.asString().lines()
        return mapOf(
            "lineCount" to lines.size.toString(),
            "size" to content.size.toString(),
            "language" to languageId,
            "syntaxStyle" to syntaxStyle
        )
    }
}

/**
 * Java source file handler
 */
class JavaHandler : AbstractSourceCodeHandler() {
    override val handlerId = "java"
    override val displayName = "Java"
    override val supportedExtensions = setOf("java")
    override val mimeTypes = setOf("text/x-java-source", "text/java")
    override val languageId = "java"
    override val syntaxStyle = "text/java"
    override val antlrGrammar = "Java20Parser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            // Simple regex-based extraction (ANTLR would be more accurate)
            val classMatch = Regex("""(public|private|protected)?\s*(abstract|final)?\s*(class|interface|enum|record)\s+(\w+)""").find(line)
            if (classMatch != null) {
                val kind = when (classMatch.groupValues[3]) {
                    "interface" -> DeclarationKind.INTERFACE
                    "enum" -> DeclarationKind.ENUM
                    else -> DeclarationKind.CLASS
                }
                declarations.add(Declaration(
                    name = classMatch.groupValues[4],
                    kind = kind,
                    startLine = index + 1,
                    endLine = index + 1,
                    modifiers = setOfNotNull(classMatch.groupValues[1].takeIf { it.isNotEmpty() })
                ))
            }

            val methodMatch = Regex("""(public|private|protected)?\s*(static)?\s*[\w<>\[\]]+\s+(\w+)\s*\(""").find(line)
            if (methodMatch != null && !line.contains("class ") && !line.contains("interface ")) {
                declarations.add(Declaration(
                    name = methodMatch.groupValues[3],
                    kind = DeclarationKind.METHOD,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""import\s+(static\s+)?([\w.]+)""").find(line)
            if (importMatch != null) {
                references.add(Reference(
                    name = importMatch.groupValues[2],
                    kind = ReferenceKind.IMPORT,
                    line = index + 1,
                    column = importMatch.range.first
                ))
            }
        }
        return references
    }
}

/**
 * Kotlin source file handler
 */
class KotlinHandler : AbstractSourceCodeHandler() {
    override val handlerId = "kotlin"
    override val displayName = "Kotlin"
    override val supportedExtensions = setOf("kt", "kts")
    override val mimeTypes = setOf("text/x-kotlin")
    override val languageId = "kotlin"
    override val syntaxStyle = "text/kotlin"
    override val antlrGrammar = "KotlinParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val classMatch = Regex("""(data\s+|sealed\s+|open\s+|abstract\s+)?(class|interface|enum\s+class|object)\s+(\w+)""").find(line)
            if (classMatch != null) {
                val kind = when {
                    classMatch.groupValues[2].contains("interface") -> DeclarationKind.INTERFACE
                    classMatch.groupValues[2].contains("enum") -> DeclarationKind.ENUM
                    classMatch.groupValues[2] == "object" -> DeclarationKind.CLASS
                    else -> DeclarationKind.CLASS
                }
                declarations.add(Declaration(
                    name = classMatch.groupValues[3],
                    kind = kind,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val funMatch = Regex("""(suspend\s+)?(fun)\s+(<[\w\s,]+>\s+)?(\w+)""").find(line)
            if (funMatch != null) {
                declarations.add(Declaration(
                    name = funMatch.groupValues[4],
                    kind = DeclarationKind.FUNCTION,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val propMatch = Regex("""(val|var)\s+(\w+)""").find(line)
            if (propMatch != null && !line.trimStart().startsWith("//")) {
                declarations.add(Declaration(
                    name = propMatch.groupValues[2],
                    kind = DeclarationKind.PROPERTY,
                    startLine = index + 1,
                    endLine = index + 1
                ))
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
                references.add(Reference(
                    name = importMatch.groupValues[1],
                    kind = ReferenceKind.IMPORT,
                    line = index + 1,
                    column = importMatch.range.first
                ))
            }
        }
        return references
    }
}

/**
 * TypeScript source file handler
 */
class TypeScriptHandler : AbstractSourceCodeHandler() {
    override val handlerId = "typescript"
    override val displayName = "TypeScript"
    override val supportedExtensions = setOf("ts", "tsx", "mts", "cts")
    override val mimeTypes = setOf("application/typescript", "text/typescript")
    override val languageId = "typescript"
    override val syntaxStyle = "text/typescript"
    override val antlrGrammar = "TypeScriptParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val classMatch = Regex("""(export\s+)?(abstract\s+)?(class|interface|enum|type)\s+(\w+)""").find(line)
            if (classMatch != null) {
                val kind = when (classMatch.groupValues[3]) {
                    "interface" -> DeclarationKind.INTERFACE
                    "enum" -> DeclarationKind.ENUM
                    "type" -> DeclarationKind.TYPE_ALIAS
                    else -> DeclarationKind.CLASS
                }
                declarations.add(Declaration(
                    name = classMatch.groupValues[4],
                    kind = kind,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val functionMatch = Regex("""(export\s+)?(async\s+)?function\s+(\w+)""").find(line)
            if (functionMatch != null) {
                declarations.add(Declaration(
                    name = functionMatch.groupValues[3],
                    kind = DeclarationKind.FUNCTION,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val constMatch = Regex("""(export\s+)?(const|let|var)\s+(\w+)""").find(line)
            if (constMatch != null) {
                declarations.add(Declaration(
                    name = constMatch.groupValues[3],
                    kind = if (constMatch.groupValues[2] == "const") DeclarationKind.CONSTANT else DeclarationKind.PROPERTY,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""import\s+.*\s+from\s+['"](.+)['"]""").find(line)
            if (importMatch != null) {
                references.add(Reference(
                    name = importMatch.groupValues[1],
                    kind = ReferenceKind.IMPORT,
                    line = index + 1,
                    column = importMatch.range.first
                ))
            }
        }
        return references
    }
}

/**
 * JavaScript source file handler
 */
class JavaScriptHandler : AbstractSourceCodeHandler() {
    override val handlerId = "javascript"
    override val displayName = "JavaScript"
    override val supportedExtensions = setOf("js", "jsx", "mjs", "cjs")
    override val mimeTypes = setOf("application/javascript", "text/javascript")
    override val languageId = "javascript"
    override val syntaxStyle = "text/javascript"
    override val antlrGrammar = "ECMAScript.g4"
    override val priority = 9

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val classMatch = Regex("""(export\s+)?(class)\s+(\w+)""").find(line)
            if (classMatch != null) {
                declarations.add(Declaration(
                    name = classMatch.groupValues[3],
                    kind = DeclarationKind.CLASS,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val functionMatch = Regex("""(export\s+)?(async\s+)?function\s+(\w+)""").find(line)
            if (functionMatch != null) {
                declarations.add(Declaration(
                    name = functionMatch.groupValues[3],
                    kind = DeclarationKind.FUNCTION,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val constMatch = Regex("""(export\s+)?(const|let|var)\s+(\w+)""").find(line)
            if (constMatch != null) {
                declarations.add(Declaration(
                    name = constMatch.groupValues[3],
                    kind = if (constMatch.groupValues[2] == "const") DeclarationKind.CONSTANT else DeclarationKind.PROPERTY,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""import\s+.*\s+from\s+['"](.+)['"]""").find(line)
            if (importMatch != null) {
                references.add(Reference(
                    name = importMatch.groupValues[1],
                    kind = ReferenceKind.IMPORT,
                    line = index + 1,
                    column = importMatch.range.first
                ))
            }
            val requireMatch = Regex("""require\s*\(\s*['"](.+)['"]\s*\)""").find(line)
            if (requireMatch != null) {
                references.add(Reference(
                    name = requireMatch.groupValues[1],
                    kind = ReferenceKind.IMPORT,
                    line = index + 1,
                    column = requireMatch.range.first
                ))
            }
        }
        return references
    }
}

/**
 * Python source file handler
 */
class PythonHandler : AbstractSourceCodeHandler() {
    override val handlerId = "python"
    override val displayName = "Python"
    override val supportedExtensions = setOf("py", "pyw", "pyi")
    override val mimeTypes = setOf("text/x-python")
    override val languageId = "python"
    override val syntaxStyle = "text/python"
    override val antlrGrammar = "Python3Parser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val classMatch = Regex("""^class\s+(\w+)""").find(line.trimStart())
            if (classMatch != null) {
                declarations.add(Declaration(
                    name = classMatch.groupValues[1],
                    kind = DeclarationKind.CLASS,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val defMatch = Regex("""^(async\s+)?def\s+(\w+)""").find(line.trimStart())
            if (defMatch != null) {
                declarations.add(Declaration(
                    name = defMatch.groupValues[2],
                    kind = DeclarationKind.FUNCTION,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""^(from\s+([\w.]+)\s+)?import\s+([\w., ]+)""").find(line.trimStart())
            if (importMatch != null) {
                val module = importMatch.groupValues[2].ifEmpty { importMatch.groupValues[3] }
                references.add(Reference(
                    name = module,
                    kind = ReferenceKind.IMPORT,
                    line = index + 1,
                    column = 0
                ))
            }
        }
        return references
    }
}

/**
 * Go source file handler
 */
class GoHandler : AbstractSourceCodeHandler() {
    override val handlerId = "go"
    override val displayName = "Go"
    override val supportedExtensions = setOf("go")
    override val mimeTypes = setOf("text/x-go")
    override val languageId = "go"
    override val syntaxStyle = "text/go"
    override val antlrGrammar = "GoParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val typeMatch = Regex("""^type\s+(\w+)\s+(struct|interface)""").find(line.trimStart())
            if (typeMatch != null) {
                declarations.add(Declaration(
                    name = typeMatch.groupValues[1],
                    kind = if (typeMatch.groupValues[2] == "interface") DeclarationKind.INTERFACE else DeclarationKind.CLASS,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val funcMatch = Regex("""^func\s+(\(\w+\s+\*?\w+\)\s+)?(\w+)""").find(line.trimStart())
            if (funcMatch != null) {
                declarations.add(Declaration(
                    name = funcMatch.groupValues[2],
                    kind = if (funcMatch.groupValues[1].isNotEmpty()) DeclarationKind.METHOD else DeclarationKind.FUNCTION,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex(""""([\w./]+)"""").find(line)
            if (importMatch != null && line.contains("import")) {
                references.add(Reference(
                    name = importMatch.groupValues[1],
                    kind = ReferenceKind.IMPORT,
                    line = index + 1,
                    column = importMatch.range.first
                ))
            }
        }
        return references
    }
}

/**
 * Rust source file handler
 */
class RustHandler : AbstractSourceCodeHandler() {
    override val handlerId = "rust"
    override val displayName = "Rust"
    override val supportedExtensions = setOf("rs")
    override val mimeTypes = setOf("text/x-rust")
    override val languageId = "rust"
    override val syntaxStyle = "text/rust"
    override val antlrGrammar = "RustParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val structMatch = Regex("""^(pub\s+)?struct\s+(\w+)""").find(line.trimStart())
            if (structMatch != null) {
                declarations.add(Declaration(
                    name = structMatch.groupValues[2],
                    kind = DeclarationKind.CLASS,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val traitMatch = Regex("""^(pub\s+)?trait\s+(\w+)""").find(line.trimStart())
            if (traitMatch != null) {
                declarations.add(Declaration(
                    name = traitMatch.groupValues[2],
                    kind = DeclarationKind.INTERFACE,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val enumMatch = Regex("""^(pub\s+)?enum\s+(\w+)""").find(line.trimStart())
            if (enumMatch != null) {
                declarations.add(Declaration(
                    name = enumMatch.groupValues[2],
                    kind = DeclarationKind.ENUM,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val fnMatch = Regex("""^(pub\s+)?(async\s+)?fn\s+(\w+)""").find(line.trimStart())
            if (fnMatch != null) {
                declarations.add(Declaration(
                    name = fnMatch.groupValues[3],
                    kind = DeclarationKind.FUNCTION,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val useMatch = Regex("""^use\s+([\w:]+)""").find(line.trimStart())
            if (useMatch != null) {
                references.add(Reference(
                    name = useMatch.groupValues[1],
                    kind = ReferenceKind.IMPORT,
                    line = index + 1,
                    column = 0
                ))
            }
        }
        return references
    }
}

/**
 * C/C++ source file handler
 */
class CppHandler : AbstractSourceCodeHandler() {
    override val handlerId = "cpp"
    override val displayName = "C/C++"
    override val supportedExtensions = setOf("c", "cpp", "cxx", "cc", "h", "hpp", "hxx")
    override val mimeTypes = setOf("text/x-c++src", "text/x-csrc", "text/x-chdr")
    override val languageId = "cpp"
    override val syntaxStyle = "text/cpp"
    override val antlrGrammar = "CPP14Parser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val classMatch = Regex("""^(class|struct)\s+(\w+)""").find(line.trimStart())
            if (classMatch != null) {
                declarations.add(Declaration(
                    name = classMatch.groupValues[2],
                    kind = DeclarationKind.CLASS,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val enumMatch = Regex("""^enum\s+(class\s+)?(\w+)""").find(line.trimStart())
            if (enumMatch != null) {
                declarations.add(Declaration(
                    name = enumMatch.groupValues[2],
                    kind = DeclarationKind.ENUM,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val includeMatch = Regex("""^#include\s+[<"](.+)[>"]""").find(line.trimStart())
            if (includeMatch != null) {
                references.add(Reference(
                    name = includeMatch.groupValues[1],
                    kind = ReferenceKind.IMPORT,
                    line = index + 1,
                    column = 0
                ))
            }
        }
        return references
    }
}

/**
 * C# source file handler
 */
class CSharpHandler : AbstractSourceCodeHandler() {
    override val handlerId = "csharp"
    override val displayName = "C#"
    override val supportedExtensions = setOf("cs", "csx")
    override val mimeTypes = setOf("text/x-csharp")
    override val languageId = "csharp"
    override val syntaxStyle = "text/csharp"
    override val antlrGrammar = "CSharpParser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val classMatch = Regex("""(public|private|internal|protected)?\s*(abstract|sealed|static)?\s*(class|interface|struct|enum|record)\s+(\w+)""").find(line)
            if (classMatch != null) {
                val kind = when (classMatch.groupValues[3]) {
                    "interface" -> DeclarationKind.INTERFACE
                    "enum" -> DeclarationKind.ENUM
                    else -> DeclarationKind.CLASS
                }
                declarations.add(Declaration(
                    name = classMatch.groupValues[4],
                    kind = kind,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val usingMatch = Regex("""^using\s+([\w.]+)""").find(line.trimStart())
            if (usingMatch != null) {
                references.add(Reference(
                    name = usingMatch.groupValues[1],
                    kind = ReferenceKind.IMPORT,
                    line = index + 1,
                    column = 0
                ))
            }
        }
        return references
    }
}

/**
 * Swift source file handler
 */
class SwiftHandler : AbstractSourceCodeHandler() {
    override val handlerId = "swift"
    override val displayName = "Swift"
    override val supportedExtensions = setOf("swift")
    override val mimeTypes = setOf("text/x-swift")
    override val languageId = "swift"
    override val syntaxStyle = "text/swift"
    override val antlrGrammar = "Swift5Parser.g4"
    override val priority = 10

    override suspend fun extractDeclarations(content: FileContent): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val classMatch = Regex("""(public|private|internal|fileprivate|open)?\s*(final\s+)?(class|struct|enum|protocol)\s+(\w+)""").find(line)
            if (classMatch != null) {
                val kind = when (classMatch.groupValues[3]) {
                    "protocol" -> DeclarationKind.INTERFACE
                    "enum" -> DeclarationKind.ENUM
                    else -> DeclarationKind.CLASS
                }
                declarations.add(Declaration(
                    name = classMatch.groupValues[4],
                    kind = kind,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }

            val funcMatch = Regex("""func\s+(\w+)""").find(line)
            if (funcMatch != null) {
                declarations.add(Declaration(
                    name = funcMatch.groupValues[1],
                    kind = DeclarationKind.FUNCTION,
                    startLine = index + 1,
                    endLine = index + 1
                ))
            }
        }
        return declarations
    }

    override suspend fun extractReferences(content: FileContent): List<Reference> {
        val references = mutableListOf<Reference>()
        val lines = content.asString().lines()

        lines.forEachIndexed { index, line ->
            val importMatch = Regex("""^import\s+(\w+)""").find(line.trimStart())
            if (importMatch != null) {
                references.add(Reference(
                    name = importMatch.groupValues[1],
                    kind = ReferenceKind.IMPORT,
                    line = index + 1,
                    column = 0
                ))
            }
        }
        return references
    }
}
