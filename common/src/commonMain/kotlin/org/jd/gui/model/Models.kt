/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.model

import kotlinx.serialization.Serializable

/**
 * Represents a decompiled class file with its source code.
 */
@Serializable
data class DecompiledClass(
    val className: String,
    val packageName: String,
    val sourceCode: String,
    val bytecodeVersion: Int,
    val modifiers: Int,
    val superClass: String? = null,
    val interfaces: List<String> = emptyList(),
    val methods: List<MethodInfo> = emptyList(),
    val fields: List<FieldInfo> = emptyList(),
    val innerClasses: List<String> = emptyList()
)

/**
 * Method information extracted from bytecode.
 */
@Serializable
data class MethodInfo(
    val name: String,
    val descriptor: String,
    val modifiers: Int,
    val lineStart: Int = -1,
    val lineEnd: Int = -1,
    val parameters: List<ParameterInfo> = emptyList(),
    val returnType: String,
    val exceptions: List<String> = emptyList()
)

/**
 * Field information extracted from bytecode.
 */
@Serializable
data class FieldInfo(
    val name: String,
    val descriptor: String,
    val modifiers: Int,
    val type: String,
    val initialValue: String? = null
)

/**
 * Parameter information.
 */
@Serializable
data class ParameterInfo(
    val name: String?,
    val type: String,
    val modifiers: Int = 0
)

/**
 * Represents a syntax token for highlighting.
 */
@Serializable
data class SyntaxToken(
    val type: TokenType,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val line: Int,
    val column: Int
)

/**
 * Token types for syntax highlighting.
 */
@Serializable
enum class TokenType {
    KEYWORD,
    IDENTIFIER,
    TYPE,
    STRING,
    NUMBER,
    COMMENT,
    ANNOTATION,
    OPERATOR,
    SEPARATOR,
    LITERAL_BOOLEAN,
    LITERAL_NULL,
    WHITESPACE,
    ERROR,
    // Java-specific
    MODIFIER,
    PACKAGE,
    IMPORT,
    CLASS_NAME,
    METHOD_NAME,
    FIELD_NAME,
    PARAMETER,
    LOCAL_VARIABLE,
    // Pattern matching (Java 21)
    RECORD_PATTERN,
    TYPE_PATTERN,
    // String templates (Java 21)
    STRING_TEMPLATE,
    TEMPLATE_EXPRESSION
}

/**
 * Navigation target for hyperlink navigation.
 */
@Serializable
data class NavigationTarget(
    val uri: String,
    val fragment: String? = null,
    val lineNumber: Int = -1,
    val columnNumber: Int = -1,
    val targetType: TargetType
)

/**
 * Type of navigation target.
 */
@Serializable
enum class TargetType {
    CLASS,
    METHOD,
    FIELD,
    PACKAGE,
    MODULE,
    FILE
}

/**
 * Declaration information for indexing.
 */
@Serializable
data class Declaration(
    val name: String,
    val descriptor: String,
    val type: DeclarationType,
    val startPosition: Int,
    val endPosition: Int,
    val lineNumber: Int,
    val flags: Int = 0
)

/**
 * Type of declaration.
 */
@Serializable
enum class DeclarationType {
    TYPE,
    FIELD,
    METHOD,
    CONSTRUCTOR,
    PARAMETER,
    LOCAL_VARIABLE,
    PACKAGE,
    MODULE,
    // Java 21 specific
    RECORD,
    RECORD_COMPONENT,
    SEALED_CLASS,
    PATTERN_VARIABLE
}

/**
 * Reference to a declaration.
 */
@Serializable
data class Reference(
    val name: String,
    val descriptor: String? = null,
    val owner: String? = null,
    val startPosition: Int,
    val endPosition: Int,
    val lineNumber: Int,
    val type: ReferenceType
)

/**
 * Type of reference.
 */
@Serializable
enum class ReferenceType {
    TYPE_REFERENCE,
    FIELD_ACCESS,
    METHOD_INVOCATION,
    CONSTRUCTOR_CALL,
    SUPER_TYPE,
    IMPLEMENTS,
    IMPORT,
    ANNOTATION,
    CAST,
    INSTANCEOF,
    // Java 21 specific
    PATTERN_TYPE,
    SEALED_PERMITS
}

/**
 * Search result item.
 */
@Serializable
data class SearchResult(
    val uri: String,
    val name: String,
    val type: String,
    val snippet: String,
    val lineNumber: Int,
    val matchScore: Float = 1.0f
)

/**
 * File entry in a JAR/archive.
 */
@Serializable
data class ArchiveEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long = 0,
    val lastModified: Long = 0,
    val entryType: EntryType
)

/**
 * Type of archive entry.
 */
@Serializable
enum class EntryType {
    CLASS_FILE,
    JAVA_SOURCE,
    RESOURCE,
    MANIFEST,
    DIRECTORY,
    MODULE_INFO,
    PACKAGE_INFO,
    UNKNOWN
}

/**
 * Application preferences.
 */
@Serializable
data class Preferences(
    val showLineNumbers: Boolean = true,
    val showMethodParameters: Boolean = true,
    val showInnerClasses: Boolean = true,
    val fontSize: Int = 12,
    val fontFamily: String = "monospace",
    val theme: String = "default",
    val tabSize: Int = 4,
    val showWhitespace: Boolean = false,
    val wordWrap: Boolean = false,
    val maxDecompilationDepth: Int = 100,
    val useVineflower: Boolean = true
)
