package org.jd.gui.server.syntax.languages

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.Vocabulary
import org.jd.gui.server.syntax.GenericTokenMaker
import org.jd.gui.server.syntax.SyntaxTokenType

/**
 * TokenMaker for Java source files.
 *
 * Extends the generic TokenMaker with Java-specific rules:
 * - Java 21+ keywords (sealed, permits, when, record)
 * - Java primitive types and wrapper classes
 * - Common Java annotations
 *
 * File extensions: .java
 */
class JavaTokenMaker : GenericTokenMaker(
    languageId = "java",
    displayName = "Java",
    fileExtensions = setOf("java")
) {

    override val tokenOverrides = mapOf(
        // Ensure proper classification of Java-specific tokens
        "TEXT_BLOCK" to SyntaxTokenType.LITERAL_STRING,
        "STRING_TEMPLATE_BEGIN" to SyntaxTokenType.LITERAL_STRING,
        "STRING_TEMPLATE_MID" to SyntaxTokenType.LITERAL_STRING,
        "STRING_TEMPLATE_END" to SyntaxTokenType.LITERAL_STRING,
        "AT" to SyntaxTokenType.ANNOTATION
    )

    override val additionalKeywords = setOf(
        // Java 9+ module keywords
        "module", "open", "requires", "exports", "opens", "to", "uses", "provides", "with", "transitive",
        // Java 10+
        "var",
        // Java 14+
        "yield",
        // Java 16+
        "record",
        // Java 17+
        "sealed", "permits", "non-sealed",
        // Java 21+
        "when"
    )

    override val additionalTypes = setOf(
        // Primitive wrappers
        "Boolean", "Byte", "Character", "Short", "Integer", "Long", "Float", "Double",
        // Common types
        "String", "Object", "Class", "Enum", "Throwable", "Exception", "Error",
        "Number", "Void", "CharSequence", "Comparable", "Cloneable", "Serializable",
        // Collections
        "List", "Set", "Map", "Collection", "Iterator", "Iterable",
        "ArrayList", "LinkedList", "HashSet", "TreeSet", "HashMap", "TreeMap",
        "Optional", "Stream", "Collector",
        // IO
        "File", "Path", "InputStream", "OutputStream", "Reader", "Writer",
        // Concurrent
        "Thread", "Runnable", "Callable", "Future", "CompletableFuture",
        "Executor", "ExecutorService",
        // Records/Sealed (Java 17+)
        "Record"
    )

    override val additionalConstants = setOf(
        "System", "Math", "Arrays", "Collections", "Objects"
    )

    override fun classifyToken(token: Token, tokenName: String, text: String): SyntaxTokenType? {
        // Annotations (text starts with @)
        if (text.startsWith("@")) {
            return SyntaxTokenType.ANNOTATION
        }

        // Method references (::)
        if (tokenName == "COLONCOLON" || tokenName == "DOUBLE_COLON") {
            return SyntaxTokenType.OPERATOR
        }

        return null
    }

    // TODO: Replace with actual ANTLR lexer when generated
    override fun createLexer(input: CharStream): Lexer {
        throw NotImplementedError("Use actual JavaLexer from antlr4 generated sources")
    }

    override fun getVocabulary(): Vocabulary {
        throw NotImplementedError("Use actual JavaLexer.VOCABULARY from antlr4 generated sources")
    }
}
