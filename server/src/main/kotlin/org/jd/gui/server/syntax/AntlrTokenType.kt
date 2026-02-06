package org.jd.gui.server.syntax

import kotlinx.serialization.Serializable

/**
 * Generic ANTLR token type categories for syntax highlighting.
 *
 * This enum provides a language-agnostic mapping from ANTLR token names
 * to syntax highlighting styles. Token names from any ANTLR grammar are
 * automatically categorized based on naming conventions.
 *
 * The mapping follows common ANTLR naming patterns:
 * - Keywords end with keyword text (ABSTRACT, CLASS, IF, etc.)
 * - Literals contain LITERAL, STRING, NUMBER, CHAR, etc.
 * - Comments contain COMMENT, LINE_COMMENT, BLOCK_COMMENT
 * - Operators are single chars or contain OP, OPERATOR
 */
@Serializable
enum class SyntaxTokenType(val cssClass: String, val priority: Int) {
    // Keywords
    KEYWORD("keyword", 100),
    KEYWORD_DECLARATION("keyword-declaration", 110),   // class, interface, enum
    KEYWORD_CONTROL("keyword-control", 105),           // if, else, for, while
    KEYWORD_MODIFIER("keyword-modifier", 95),          // public, private, static
    KEYWORD_TYPE("keyword-type", 90),                  // void, boolean, int

    // Types
    TYPE("type", 80),
    TYPE_BUILTIN("type-builtin", 85),                  // int, String, boolean
    TYPE_USER("type-user", 75),

    // Literals
    LITERAL_STRING("string", 70),
    LITERAL_STRING_ESCAPE("string-escape", 72),
    LITERAL_CHAR("char", 68),
    LITERAL_NUMBER("number", 65),
    LITERAL_NUMBER_FLOAT("number-float", 66),
    LITERAL_NUMBER_HEX("number-hex", 67),
    LITERAL_BOOLEAN("boolean", 60),
    LITERAL_NULL("null", 55),

    // Comments
    COMMENT_LINE("comment-line", 50),
    COMMENT_BLOCK("comment-block", 51),
    COMMENT_DOC("comment-doc", 52),

    // Annotations/Decorators
    ANNOTATION("annotation", 45),
    DECORATOR("decorator", 46),

    // Identifiers
    IDENTIFIER("identifier", 30),
    IDENTIFIER_FUNCTION("function", 35),
    IDENTIFIER_VARIABLE("variable", 32),
    IDENTIFIER_CONSTANT("constant", 38),
    IDENTIFIER_PARAMETER("parameter", 31),
    IDENTIFIER_FIELD("field", 33),
    IDENTIFIER_PACKAGE("package", 28),

    // Operators
    OPERATOR("operator", 20),
    OPERATOR_ARITHMETIC("operator-arithmetic", 21),
    OPERATOR_COMPARISON("operator-comparison", 22),
    OPERATOR_LOGICAL("operator-logical", 23),
    OPERATOR_ASSIGNMENT("operator-assignment", 24),
    OPERATOR_BITWISE("operator-bitwise", 25),

    // Delimiters
    DELIMITER("delimiter", 15),
    BRACKET("bracket", 16),
    BRACE("brace", 17),
    PAREN("paren", 18),

    // Whitespace
    WHITESPACE("whitespace", 5),
    NEWLINE("newline", 4),

    // Special
    ERROR("error", 1),
    UNKNOWN("unknown", 0);

    companion object {
        /**
         * Infer token type from ANTLR token name using naming conventions
         */
        fun fromAntlrTokenName(tokenName: String): SyntaxTokenType {
            val upper = tokenName.uppercase()

            // Comments
            if (upper.contains("DOC_COMMENT") || upper.contains("JAVADOC")) return COMMENT_DOC
            if (upper.contains("LINE_COMMENT") || upper.contains("COMMENT_EOL") || upper == "SL_COMMENT") return COMMENT_LINE
            if (upper.contains("BLOCK_COMMENT") || upper.contains("COMMENT_MULTILINE") || upper == "ML_COMMENT") return COMMENT_BLOCK
            if (upper.contains("COMMENT")) return COMMENT_LINE

            // Literals - Strings
            if (upper.contains("TEXT_BLOCK") || upper.contains("TEMPLATE")) return LITERAL_STRING
            if (upper.contains("STRING") || upper.contains("CHAR_LITERAL")) {
                return if (upper.contains("ESCAPE")) LITERAL_STRING_ESCAPE else LITERAL_STRING
            }
            if (upper.contains("CHAR_LITERAL") || upper == "CHAR") return LITERAL_CHAR

            // Literals - Numbers
            if (upper.contains("FLOAT") || upper.contains("DOUBLE") || upper.contains("DECIMAL")) return LITERAL_NUMBER_FLOAT
            if (upper.contains("HEX")) return LITERAL_NUMBER_HEX
            if (upper.contains("NUMBER") || upper.contains("LITERAL") && (upper.contains("INT") || upper.contains("LONG"))) return LITERAL_NUMBER
            if (upper.contains("BINARY_LITERAL") || upper.contains("OCT_LITERAL")) return LITERAL_NUMBER

            // Literals - Boolean/Null
            if (upper.contains("BOOL") || upper == "TRUE" || upper == "FALSE") return LITERAL_BOOLEAN
            if (upper == "NULL" || upper.contains("NULL_LITERAL")) return LITERAL_NULL

            // Annotations
            if (upper.contains("ANNOTATION") || upper == "AT") return ANNOTATION
            if (upper.contains("DECORATOR")) return DECORATOR

            // Whitespace
            if (upper.contains("WHITESPACE") || upper == "WS" || upper == "SPACE") return WHITESPACE
            if (upper.contains("NEWLINE") || upper == "NL" || upper == "EOL") return NEWLINE

            // Operators (check before keywords since some overlap)
            if (isOperator(upper)) return categorizeOperator(upper)

            // Delimiters
            if (upper in setOf("LPAREN", "RPAREN", "LEFT_PAREN", "RIGHT_PAREN")) return PAREN
            if (upper in setOf("LBRACE", "RBRACE", "LEFT_BRACE", "RIGHT_BRACE", "LCURL", "RCURL")) return BRACE
            if (upper in setOf("LBRACK", "RBRACK", "LEFT_BRACKET", "RIGHT_BRACKET", "LSQUARE", "RSQUARE")) return BRACKET
            if (upper in setOf("COMMA", "SEMICOLON", "COLON", "DOT", "SEMI", "QUESTION")) return DELIMITER

            // Declaration keywords
            if (upper in setOf("CLASS", "INTERFACE", "ENUM", "STRUCT", "TRAIT", "RECORD",
                    "FUN", "FUNC", "FUNCTION", "DEF", "METHOD",
                    "VAL", "VAR", "LET", "CONST", "TYPE", "TYPEALIAS")) return KEYWORD_DECLARATION

            // Control flow keywords
            if (upper in setOf("IF", "ELSE", "ELSEIF", "ELIF", "FOR", "WHILE", "DO", "LOOP",
                    "SWITCH", "CASE", "DEFAULT", "WHEN", "MATCH",
                    "BREAK", "CONTINUE", "RETURN", "YIELD", "GOTO",
                    "TRY", "CATCH", "FINALLY", "THROW", "THROWS", "RAISE",
                    "WITH", "USING", "ASYNC", "AWAIT", "DEFER")) return KEYWORD_CONTROL

            // Modifier keywords
            if (upper in setOf("PUBLIC", "PRIVATE", "PROTECTED", "INTERNAL",
                    "STATIC", "FINAL", "CONST", "READONLY", "MUTABLE",
                    "ABSTRACT", "VIRTUAL", "OVERRIDE", "SEALED", "OPEN",
                    "VOLATILE", "TRANSIENT", "SYNCHRONIZED", "NATIVE",
                    "INLINE", "NOINLINE", "CROSSINLINE", "REIFIED", "SUSPEND",
                    "EXPORT", "EXTERN", "UNSAFE")) return KEYWORD_MODIFIER

            // Type keywords (primitive types)
            if (upper in setOf("VOID", "BOOLEAN", "BOOL", "BYTE", "CHAR",
                    "SHORT", "INT", "INTEGER", "LONG", "FLOAT", "DOUBLE",
                    "STRING", "OBJECT", "ANY", "UNIT", "NOTHING",
                    "UINT", "ULONG", "USHORT", "UBYTE")) return KEYWORD_TYPE

            // Other keywords (check if it's a known keyword pattern)
            if (isKeyword(upper)) return KEYWORD

            // Identifiers
            if (upper == "IDENTIFIER" || upper == "ID" || upper == "NAME" || upper == "IDENT") return IDENTIFIER

            // Error tokens
            if (upper.contains("ERROR") || upper.contains("INVALID")) return ERROR

            return UNKNOWN
        }

        private fun isOperator(name: String): Boolean {
            return name in setOf(
                // Assignment
                "ASSIGN", "EQ", "EQUALS", "COLON_EQ",
                "PLUS_ASSIGN", "MINUS_ASSIGN", "MUL_ASSIGN", "DIV_ASSIGN", "MOD_ASSIGN",
                "AND_ASSIGN", "OR_ASSIGN", "XOR_ASSIGN", "LSHIFT_ASSIGN", "RSHIFT_ASSIGN",

                // Comparison
                "EQEQ", "EQ_EQ", "EQUALS_EQUALS", "DOUBLE_EQ",
                "NE", "NEQ", "NOT_EQ", "BANG_EQ", "EXCL_EQ",
                "LT", "LE", "LTEQ", "LT_EQ", "LESS", "LESS_EQ",
                "GT", "GE", "GTEQ", "GT_EQ", "GREATER", "GREATER_EQ",
                "SPACESHIP", "CMP",

                // Arithmetic
                "PLUS", "MINUS", "MUL", "STAR", "DIV", "SLASH", "MOD", "PERCENT", "POW",
                "INCR", "DECR", "PLUSPLUS", "MINUSMINUS", "INC", "DEC",

                // Logical
                "AND", "ANDAND", "AMP_AMP", "DOUBLE_AMP",
                "OR", "OROR", "PIPE_PIPE", "DOUBLE_PIPE",
                "NOT", "BANG", "EXCL",

                // Bitwise
                "AMP", "AMPERSAND", "PIPE", "BAR", "CARET", "XOR",
                "TILDE", "LSHIFT", "RSHIFT", "URSHIFT",

                // Arrow/Lambda
                "ARROW", "RARROW", "LARROW", "FAT_ARROW", "DOUBLE_ARROW", "LAMBDA",

                // Range
                "RANGE", "DOTDOT", "DOTDOTDOT", "ELLIPSIS",

                // Other
                "DOUBLE_COLON", "COLONCOLON", "SCOPE",
                "SAFE_ACCESS", "QUESTION_DOT", "ELVIS", "QUESTION_COLON",
                "SPREAD", "STAR_STAR", "DOUBLE_STAR"
            )
        }

        private fun categorizeOperator(name: String): SyntaxTokenType {
            return when {
                name.contains("ASSIGN") -> OPERATOR_ASSIGNMENT
                name in setOf("EQ", "NE", "LT", "LE", "GT", "GE", "LTEQ", "GTEQ", "CMP") ||
                        name.contains("EQUALS") || name.contains("LESS") || name.contains("GREATER") -> OPERATOR_COMPARISON
                name.contains("AND") || name.contains("OR") || name.contains("NOT") ||
                        name in setOf("BANG", "EXCL") -> OPERATOR_LOGICAL
                name.contains("SHIFT") || name in setOf("AMP", "PIPE", "CARET", "TILDE", "XOR") -> OPERATOR_BITWISE
                name in setOf("PLUS", "MINUS", "MUL", "STAR", "DIV", "SLASH", "MOD", "PERCENT", "POW",
                    "INCR", "DECR", "INC", "DEC") -> OPERATOR_ARITHMETIC
                else -> OPERATOR
            }
        }

        private fun isKeyword(name: String): Boolean {
            // Common keyword patterns across languages
            return name in setOf(
                // OOP
                "CLASS", "INTERFACE", "ENUM", "STRUCT", "OBJECT", "TRAIT", "RECORD",
                "EXTENDS", "IMPLEMENTS", "INHERITS", "SUPER", "THIS", "SELF", "BASE",
                "NEW", "DELETE", "INSTANCEOF", "TYPEOF", "AS", "IS",

                // Functions
                "FUNCTION", "FUN", "FUNC", "DEF", "LAMBDA", "RETURN", "YIELD",

                // Variables
                "VAR", "VAL", "LET", "CONST", "FINAL", "STATIC",

                // Control
                "IF", "ELSE", "ELSEIF", "ELIF", "THEN",
                "FOR", "WHILE", "DO", "LOOP", "REPEAT", "UNTIL",
                "SWITCH", "CASE", "DEFAULT", "WHEN", "MATCH",
                "BREAK", "CONTINUE", "GOTO", "FALLTHROUGH",
                "TRY", "CATCH", "FINALLY", "THROW", "THROWS", "RAISE", "RESCUE",

                // Modules
                "IMPORT", "EXPORT", "MODULE", "PACKAGE", "FROM", "AS", "USING", "USE",
                "REQUIRE", "INCLUDE", "NAMESPACE",

                // Concurrency
                "ASYNC", "AWAIT", "DEFER", "SPAWN", "GO", "YIELD",
                "SYNCHRONIZED", "VOLATILE", "ATOMIC", "LOCK",

                // Other
                "TRUE", "FALSE", "NULL", "NIL", "NONE", "UNDEFINED",
                "IN", "OUT", "REF", "INOUT", "WHERE", "ASSERT", "DEBUG"
            )
        }
    }
}

/**
 * Represents a highlighted token with position and type
 */
@Serializable
data class HighlightToken(
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val tokenType: SyntaxTokenType,
    val antlrTokenType: Int = -1,
    val antlrTokenName: String = ""
)

/**
 * Result of tokenizing source code
 */
@Serializable
data class HighlightResult(
    val tokens: List<HighlightToken>,
    val language: String,
    val errors: List<String> = emptyList()
)
