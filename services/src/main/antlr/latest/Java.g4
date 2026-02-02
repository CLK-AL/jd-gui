/*
 [The "BSD licence"]
 Copyright (c) 2013 Terence Parr, Sam Harwell
 Copyright (c) 2017 Ivan Kochurkin (upgrade to Java 8)
 Copyright (c) 2021 Michał Lorek (upgrade to Java 11)
 Copyright (c) 2022 Michał Lorek (upgrade to Java 17)
 Copyright (c) 2025 JD-GUI Contributors (Java 24+ tracking)
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 Grammar source: https://github.com/antlr/grammars-v4/tree/master/java/java
 This grammar tracks the latest Java LTS and follows the Java SE 24+ specification.
*/

/** A Java 24+ grammar for ANTLR v4.
 *  Supports all Java features including:
 *  - Lambdas and method references (Java 8)
 *  - Modules (Java 9)
 *  - Local variable type inference - var (Java 10)
 *  - Switch expressions (Java 14)
 *  - Text blocks (Java 15)
 *  - Records (Java 16)
 *  - Sealed classes (Java 17)
 *  - Pattern matching for instanceof (Java 16)
 *  - Pattern matching for switch (Java 21)
 *  - Record patterns (Java 21)
 *  - Unnamed patterns and variables (Java 22)
 *  - Statements before super() (Java 22)
 */
grammar Java;

// Starting point for parsing a java file
compilationUnit
    :   packageDeclaration? importDeclaration* typeDeclaration* EOF
    |   moduleDeclaration EOF
    ;

packageDeclaration
    :   annotation* PACKAGE qualifiedName SEMI
    ;

importDeclaration
    :   IMPORT STATIC? qualifiedName (DOT MUL)? SEMI
    ;

typeDeclaration
    :   classOrInterfaceModifier* classDeclaration
    |   classOrInterfaceModifier* enumDeclaration
    |   classOrInterfaceModifier* interfaceDeclaration
    |   classOrInterfaceModifier* annotationTypeDeclaration
    |   classOrInterfaceModifier* recordDeclaration
    |   SEMI
    ;

modifier
    :   classOrInterfaceModifier
    |   NATIVE
    |   SYNCHRONIZED
    |   TRANSIENT
    |   VOLATILE
    ;

classOrInterfaceModifier
    :   annotation
    |   PUBLIC
    |   PROTECTED
    |   PRIVATE
    |   STATIC
    |   ABSTRACT
    |   FINAL
    |   STRICTFP
    |   SEALED
    |   NON_SEALED
    ;

variableModifier
    :   FINAL
    |   annotation
    ;

classDeclaration
    :   CLASS Identifier typeParameters?
        (EXTENDS type)?
        (IMPLEMENTS typeList)?
        (PERMITS typeList)?
        classBody
    ;

typeParameters
    :   LT typeParameter (COMMA typeParameter)* GT
    ;

typeParameter
    :   annotation* Identifier (EXTENDS annotation* typeBound)?
    ;

typeBound
    :   type (BITAND type)*
    ;

enumDeclaration
    :   ENUM Identifier (IMPLEMENTS typeList)?
        LBRACE enumConstants? COMMA? enumBodyDeclarations? RBRACE
    ;

enumConstants
    :   enumConstant (COMMA enumConstant)*
    ;

enumConstant
    :   annotation* Identifier arguments? classBody?
    ;

enumBodyDeclarations
    :   SEMI classBodyDeclaration*
    ;

interfaceDeclaration
    :   INTERFACE Identifier typeParameters?
        (EXTENDS typeList)?
        (PERMITS typeList)?
        interfaceBody
    ;

// Records (Java 16+)
recordDeclaration
    :   RECORD Identifier typeParameters? recordHeader
        (IMPLEMENTS typeList)?
        recordBody
    ;

recordHeader
    :   LPAREN recordComponentList? RPAREN
    ;

recordComponentList
    :   recordComponent (COMMA recordComponent)*
    ;

recordComponent
    :   annotation* type Identifier
    |   annotation* type UNDERSCORE
    ;

recordBody
    :   LBRACE classBodyDeclaration* RBRACE
    ;

typeList
    :   type (COMMA type)*
    ;

classBody
    :   LBRACE classBodyDeclaration* RBRACE
    ;

interfaceBody
    :   LBRACE interfaceBodyDeclaration* RBRACE
    ;

classBodyDeclaration
    :   SEMI
    |   STATIC? block
    |   modifier* memberDeclaration
    ;

memberDeclaration
    :   methodDeclaration
    |   genericMethodDeclaration
    |   fieldDeclaration
    |   constructorDeclaration
    |   genericConstructorDeclaration
    |   compactConstructorDeclaration
    |   interfaceDeclaration
    |   annotationTypeDeclaration
    |   classDeclaration
    |   enumDeclaration
    |   recordDeclaration
    ;

methodDeclaration
    :   typeTypeOrVoid Identifier formalParameters (LBRACK RBRACK)*
        (THROWS qualifiedNameList)?
        methodBody
    ;

typeTypeOrVoid
    :   type
    |   VOID
    ;

methodBody
    :   block
    |   SEMI
    ;

genericMethodDeclaration
    :   typeParameters methodDeclaration
    ;

constructorDeclaration
    :   Identifier formalParameters (THROWS qualifiedNameList)?
        constructorBody
    ;

// Java 22+: Statements before super()
constructorBody
    :   block
    ;

genericConstructorDeclaration
    :   typeParameters constructorDeclaration
    ;

// Compact constructor for records (Java 16+)
compactConstructorDeclaration
    :   modifier* Identifier constructorBody
    ;

fieldDeclaration
    :   type variableDeclarators SEMI
    ;

interfaceBodyDeclaration
    :   modifier* interfaceMemberDeclaration
    |   SEMI
    ;

interfaceMemberDeclaration
    :   constDeclaration
    |   interfaceMethodDeclaration
    |   genericInterfaceMethodDeclaration
    |   interfaceDeclaration
    |   annotationTypeDeclaration
    |   classDeclaration
    |   enumDeclaration
    |   recordDeclaration
    ;

constDeclaration
    :   type constantDeclarator (COMMA constantDeclarator)* SEMI
    ;

constantDeclarator
    :   Identifier (LBRACK RBRACK)* ASSIGN variableInitializer
    ;

interfaceMethodDeclaration
    :   interfaceMethodModifier* typeTypeOrVoid Identifier formalParameters (LBRACK RBRACK)*
        (THROWS qualifiedNameList)?
        methodBody
    ;

interfaceMethodModifier
    :   annotation
    |   PUBLIC
    |   ABSTRACT
    |   DEFAULT
    |   STATIC
    |   STRICTFP
    ;

genericInterfaceMethodDeclaration
    :   interfaceMethodModifier* typeParameters interfaceMethodDeclaration
    ;

variableDeclarators
    :   variableDeclarator (COMMA variableDeclarator)*
    ;

variableDeclarator
    :   variableDeclaratorId (ASSIGN variableInitializer)?
    ;

variableDeclaratorId
    :   Identifier (LBRACK RBRACK)*
    |   UNDERSCORE
    ;

variableInitializer
    :   arrayInitializer
    |   expression
    ;

arrayInitializer
    :   LBRACE (variableInitializer (COMMA variableInitializer)* COMMA?)? RBRACE
    ;

type
    :   annotation* classOrInterfaceType (LBRACK RBRACK)*
    |   annotation* primitiveType (LBRACK RBRACK)*
    ;

classOrInterfaceType
    :   Identifier typeArguments? (DOT Identifier typeArguments?)*
    ;

primitiveType
    :   BOOLEAN
    |   CHAR
    |   BYTE
    |   SHORT
    |   INT
    |   LONG
    |   FLOAT
    |   DOUBLE
    ;

typeArguments
    :   LT typeArgument (COMMA typeArgument)* GT
    ;

typeArgument
    :   type
    |   annotation* QUESTION ((EXTENDS | SUPER) type)?
    ;

qualifiedNameList
    :   qualifiedName (COMMA qualifiedName)*
    ;

formalParameters
    :   LPAREN (receiverParameter COMMA)? formalParameterList? RPAREN
    |   LPAREN receiverParameter RPAREN
    ;

receiverParameter
    :   type (Identifier DOT)* THIS
    ;

formalParameterList
    :   formalParameter (COMMA formalParameter)* (COMMA lastFormalParameter)?
    |   lastFormalParameter
    ;

formalParameter
    :   variableModifier* type variableDeclaratorId
    ;

lastFormalParameter
    :   variableModifier* type annotation* ELLIPSIS variableDeclaratorId
    ;

// Lambda type inference (Java 10+)
lambdaLVTIList
    :   lambdaLVTIParameter (COMMA lambdaLVTIParameter)*
    ;

lambdaLVTIParameter
    :   variableModifier* VAR Identifier
    |   UNDERSCORE
    ;

qualifiedName
    :   Identifier (DOT Identifier)*
    ;

literal
    :   integerLiteral
    |   floatLiteral
    |   CHAR_LITERAL
    |   STRING_LITERAL
    |   TEXT_BLOCK
    |   BOOL_LITERAL
    |   NULL_LITERAL
    ;

integerLiteral
    :   DECIMAL_LITERAL
    |   HEX_LITERAL
    |   OCT_LITERAL
    |   BINARY_LITERAL
    ;

floatLiteral
    :   FLOAT_LITERAL
    |   HEX_FLOAT_LITERAL
    ;

// ANNOTATIONS

annotation
    :   AT qualifiedName (LPAREN (elementValuePairs | elementValue)? RPAREN)?
    ;

elementValuePairs
    :   elementValuePair (COMMA elementValuePair)*
    ;

elementValuePair
    :   Identifier ASSIGN elementValue
    ;

elementValue
    :   expression
    |   annotation
    |   elementValueArrayInitializer
    ;

elementValueArrayInitializer
    :   LBRACE (elementValue (COMMA elementValue)*)? COMMA? RBRACE
    ;

annotationTypeDeclaration
    :   AT INTERFACE Identifier annotationTypeBody
    ;

annotationTypeBody
    :   LBRACE annotationTypeElementDeclaration* RBRACE
    ;

annotationTypeElementDeclaration
    :   modifier* annotationTypeElementRest
    |   SEMI
    ;

annotationTypeElementRest
    :   type annotationMethodOrConstantRest SEMI
    |   classDeclaration SEMI?
    |   interfaceDeclaration SEMI?
    |   enumDeclaration SEMI?
    |   annotationTypeDeclaration SEMI?
    |   recordDeclaration SEMI?
    ;

annotationMethodOrConstantRest
    :   annotationMethodRest
    |   annotationConstantRest
    ;

annotationMethodRest
    :   Identifier LPAREN RPAREN defaultValue?
    ;

annotationConstantRest
    :   variableDeclarators
    ;

defaultValue
    :   DEFAULT elementValue
    ;

// MODULE DECLARATIONS (Java 9+)

moduleDeclaration
    :   OPEN? MODULE qualifiedName moduleBody
    ;

moduleBody
    :   LBRACE moduleDirective* RBRACE
    ;

moduleDirective
    :   REQUIRES requiresModifier* qualifiedName SEMI
    |   EXPORTS qualifiedName (TO qualifiedName (COMMA qualifiedName)*)? SEMI
    |   OPENS qualifiedName (TO qualifiedName (COMMA qualifiedName)*)? SEMI
    |   USES qualifiedName SEMI
    |   PROVIDES qualifiedName WITH qualifiedName (COMMA qualifiedName)* SEMI
    ;

requiresModifier
    :   TRANSITIVE
    |   STATIC
    ;

// STATEMENTS / BLOCKS

block
    :   LBRACE blockStatement* RBRACE
    ;

blockStatement
    :   localVariableDeclaration SEMI
    |   statement
    |   localTypeDeclaration
    ;

localVariableDeclaration
    :   variableModifier* (type variableDeclarators | VAR Identifier ASSIGN expression)
    ;

localTypeDeclaration
    :   classOrInterfaceModifier* (classDeclaration | interfaceDeclaration | recordDeclaration)
    |   SEMI
    ;

statement
    :   block
    |   ASSERT expression (COLON expression)? SEMI
    |   IF parExpression statement (ELSE statement)?
    |   FOR LPAREN forControl RPAREN statement
    |   WHILE parExpression statement
    |   DO statement WHILE parExpression SEMI
    |   TRY block (catchClause+ finallyBlock? | finallyBlock)
    |   TRY resourceSpecification block catchClause* finallyBlock?
    |   SWITCH parExpression LBRACE switchBlockStatementGroup* switchLabel* RBRACE
    |   SYNCHRONIZED parExpression block
    |   RETURN expression? SEMI
    |   THROW expression SEMI
    |   BREAK Identifier? SEMI
    |   CONTINUE Identifier? SEMI
    |   YIELD expression SEMI
    |   SEMI
    |   statementExpression SEMI
    |   switchExpression SEMI?
    |   Identifier COLON statement
    ;

catchClause
    :   CATCH LPAREN variableModifier* catchType Identifier RPAREN block
    |   CATCH LPAREN variableModifier* catchType UNDERSCORE RPAREN block
    ;

catchType
    :   qualifiedName (BITOR qualifiedName)*
    ;

finallyBlock
    :   FINALLY block
    ;

resourceSpecification
    :   LPAREN resources SEMI? RPAREN
    ;

resources
    :   resource (SEMI resource)*
    ;

resource
    :   variableModifier* (classOrInterfaceType | VAR) variableDeclaratorId ASSIGN expression
    |   Identifier
    ;

switchBlockStatementGroup
    :   switchLabel+ blockStatement+
    ;

switchLabel
    :   CASE expressionList COLON
    |   CASE NULL_LITERAL (COMMA DEFAULT)? COLON
    |   CASE pattern (WHEN expression)? COLON
    |   DEFAULT COLON
    ;

forControl
    :   enhancedForControl
    |   forInit? SEMI expression? SEMI forUpdate?
    ;

forInit
    :   localVariableDeclaration
    |   expressionList
    ;

enhancedForControl
    :   variableModifier* (type | VAR) variableDeclaratorId COLON expression
    ;

forUpdate
    :   expressionList
    ;

// EXPRESSIONS

parExpression
    :   LPAREN expression RPAREN
    ;

expressionList
    :   expression (COMMA expression)*
    ;

statementExpression
    :   expression
    ;

expression
    :   primary
    |   expression DOT Identifier
    |   expression DOT THIS
    |   expression DOT NEW nonWildcardTypeArguments? innerCreator
    |   expression DOT SUPER superSuffix
    |   expression DOT explicitGenericInvocation
    |   expression LBRACK expression RBRACK
    |   expression LPAREN expressionList? RPAREN
    |   NEW creator
    |   LPAREN annotation* type (BITAND type)* RPAREN expression
    |   expression (INC | DEC)
    |   (ADD | SUB | INC | DEC) expression
    |   (TILDE | BANG) expression
    |   expression (MUL | DIV | MOD) expression
    |   expression (ADD | SUB) expression
    |   expression (LT LT | GT GT GT | GT GT) expression
    |   expression (LE | GE | GT | LT) expression
    |   expression INSTANCEOF (type | pattern)
    |   expression (EQUAL | NOTEQUAL) expression
    |   expression BITAND expression
    |   expression CARET expression
    |   expression BITOR expression
    |   expression AND expression
    |   expression OR expression
    |   <assoc=right> expression QUESTION expression COLON expression
    |   <assoc=right> expression
        (   ASSIGN
        |   ADD_ASSIGN
        |   SUB_ASSIGN
        |   MUL_ASSIGN
        |   DIV_ASSIGN
        |   AND_ASSIGN
        |   OR_ASSIGN
        |   XOR_ASSIGN
        |   RSHIFT_ASSIGN
        |   URSHIFT_ASSIGN
        |   LSHIFT_ASSIGN
        |   MOD_ASSIGN
        )
        expression
    |   lambdaExpression
    |   switchExpression
    |   expression COLONCOLON typeArguments? Identifier
    |   type COLONCOLON (typeArguments? Identifier | NEW)
    |   classOrInterfaceType COLONCOLON typeArguments? NEW
    ;

// Pattern matching (Java 16+ for instanceof, Java 21 for switch, Java 22 finalized)
pattern
    :   typePattern
    |   recordPattern
    ;

typePattern
    :   variableModifier* type annotation* Identifier
    |   variableModifier* type annotation* UNDERSCORE
    ;

recordPattern
    :   type LPAREN recordPatternComponentList? RPAREN
    ;

recordPatternComponentList
    :   recordPatternComponent (COMMA recordPatternComponent)*
    ;

recordPatternComponent
    :   pattern
    |   UNDERSCORE
    ;

// Lambda expressions (Java 8+)
lambdaExpression
    :   lambdaParameters ARROW lambdaBody
    ;

lambdaParameters
    :   Identifier
    |   UNDERSCORE
    |   LPAREN formalParameterList? RPAREN
    |   LPAREN Identifier (COMMA Identifier)* RPAREN
    |   LPAREN lambdaLVTIList? RPAREN
    ;

lambdaBody
    :   expression
    |   block
    ;

// Switch expressions (Java 14+)
switchExpression
    :   SWITCH parExpression LBRACE switchLabeledRule* RBRACE
    ;

switchLabeledRule
    :   CASE expressionList (WHEN expression)? (ARROW | COLON) switchRuleOutcome
    |   CASE NULL_LITERAL (COMMA DEFAULT)? (WHEN expression)? (ARROW | COLON) switchRuleOutcome
    |   CASE pattern (WHEN expression)? (ARROW | COLON) switchRuleOutcome
    |   DEFAULT (ARROW | COLON) switchRuleOutcome
    ;

switchRuleOutcome
    :   block
    |   blockStatement*
    ;

primary
    :   LPAREN expression RPAREN
    |   THIS
    |   SUPER
    |   literal
    |   Identifier
    |   type DOT CLASS
    |   VOID DOT CLASS
    |   nonWildcardTypeArguments (explicitGenericInvocationSuffix | THIS arguments)
    ;

creator
    :   nonWildcardTypeArguments createdName classCreatorRest
    |   createdName (arrayCreatorRest | classCreatorRest)
    ;

createdName
    :   Identifier typeArgumentsOrDiamond? (DOT Identifier typeArgumentsOrDiamond?)*
    |   primitiveType
    ;

innerCreator
    :   Identifier nonWildcardTypeArgumentsOrDiamond? classCreatorRest
    ;

arrayCreatorRest
    :   LBRACK (RBRACK (LBRACK RBRACK)* arrayInitializer | expression RBRACK (LBRACK expression RBRACK)* (LBRACK RBRACK)*)
    ;

classCreatorRest
    :   arguments classBody?
    ;

explicitGenericInvocation
    :   nonWildcardTypeArguments explicitGenericInvocationSuffix
    ;

nonWildcardTypeArguments
    :   LT typeList GT
    ;

typeArgumentsOrDiamond
    :   LT GT
    |   typeArguments
    ;

nonWildcardTypeArgumentsOrDiamond
    :   LT GT
    |   nonWildcardTypeArguments
    ;

superSuffix
    :   arguments
    |   DOT typeArguments? Identifier arguments?
    ;

explicitGenericInvocationSuffix
    :   SUPER superSuffix
    |   Identifier arguments
    ;

arguments
    :   LPAREN expressionList? RPAREN
    ;

// LEXER

// Keywords
ABSTRACT      : 'abstract';
ASSERT        : 'assert';
BOOLEAN       : 'boolean';
BREAK         : 'break';
BYTE          : 'byte';
CASE          : 'case';
CATCH         : 'catch';
CHAR          : 'char';
CLASS         : 'class';
CONST         : 'const';
CONTINUE      : 'continue';
DEFAULT       : 'default';
DO            : 'do';
DOUBLE        : 'double';
ELSE          : 'else';
ENUM          : 'enum';
EXTENDS       : 'extends';
FINAL         : 'final';
FINALLY       : 'finally';
FLOAT         : 'float';
FOR           : 'for';
IF            : 'if';
GOTO          : 'goto';
IMPLEMENTS    : 'implements';
IMPORT        : 'import';
INSTANCEOF    : 'instanceof';
INT           : 'int';
INTERFACE     : 'interface';
LONG          : 'long';
NATIVE        : 'native';
NEW           : 'new';
PACKAGE       : 'package';
PRIVATE       : 'private';
PROTECTED     : 'protected';
PUBLIC        : 'public';
RETURN        : 'return';
SHORT         : 'short';
STATIC        : 'static';
STRICTFP      : 'strictfp';
SUPER         : 'super';
SWITCH        : 'switch';
SYNCHRONIZED  : 'synchronized';
THIS          : 'this';
THROW         : 'throw';
THROWS        : 'throws';
TRANSIENT     : 'transient';
TRY           : 'try';
VOID          : 'void';
VOLATILE      : 'volatile';
WHILE         : 'while';

// Module keywords (Java 9+)
MODULE        : 'module';
OPEN          : 'open';
REQUIRES      : 'requires';
EXPORTS       : 'exports';
OPENS         : 'opens';
TO            : 'to';
USES          : 'uses';
PROVIDES      : 'provides';
WITH          : 'with';
TRANSITIVE    : 'transitive';

// Java 10+ keywords
VAR           : 'var';

// Java 14+ keywords
YIELD         : 'yield';

// Java 16+ keywords
RECORD        : 'record';

// Java 17+ keywords
SEALED        : 'sealed';
PERMITS       : 'permits';
NON_SEALED    : 'non-sealed';

// Java 21+ keywords
WHEN          : 'when';

// Java 22+ unnamed patterns/variables (finalized)
UNDERSCORE    : '_';

// Literals
DECIMAL_LITERAL:    ('0' | [1-9] (Digits? | '_'+ Digits)) [lL]?;
HEX_LITERAL:        '0' [xX] [0-9a-fA-F] ([0-9a-fA-F_]* [0-9a-fA-F])? [lL]?;
OCT_LITERAL:        '0' '_'* [0-7] ([0-7_]* [0-7])? [lL]?;
BINARY_LITERAL:     '0' [bB] [01] ([01_]* [01])? [lL]?;

FLOAT_LITERAL:      (Digits '.' Digits? | '.' Digits) ExponentPart? [fFdD]?
             |       Digits (ExponentPart [fFdD]? | [fFdD])
             ;

HEX_FLOAT_LITERAL:  '0' [xX] (HexDigits '.'? | HexDigits? '.' HexDigits) [pP] [+-]? Digits [fFdD]?;

BOOL_LITERAL:       'true' | 'false';

CHAR_LITERAL:       '\'' (~['\\\r\n] | EscapeSequence) '\'';

STRING_LITERAL:     '"' (~["\\\r\n] | EscapeSequence)* '"';

TEXT_BLOCK:         '"""' [ \t]* [\r\n] (. | EscapeSequence)*? '"""';

NULL_LITERAL:       'null';

// Separators
LPAREN      : '(';
RPAREN      : ')';
LBRACE      : '{';
RBRACE      : '}';
LBRACK      : '[';
RBRACK      : ']';
SEMI        : ';';
COMMA       : ',';
DOT         : '.';

// Operators
ASSIGN          : '=';
GT              : '>';
LT              : '<';
BANG            : '!';
TILDE           : '~';
QUESTION        : '?';
COLON           : ':';
EQUAL           : '==';
LE              : '<=';
GE              : '>=';
NOTEQUAL        : '!=';
AND             : '&&';
OR              : '||';
INC             : '++';
DEC             : '--';
ADD             : '+';
SUB             : '-';
MUL             : '*';
DIV             : '/';
BITAND          : '&';
BITOR           : '|';
CARET           : '^';
MOD             : '%';

ADD_ASSIGN      : '+=';
SUB_ASSIGN      : '-=';
MUL_ASSIGN      : '*=';
DIV_ASSIGN      : '/=';
AND_ASSIGN      : '&=';
OR_ASSIGN       : '|=';
XOR_ASSIGN      : '^=';
MOD_ASSIGN      : '%=';
LSHIFT_ASSIGN   : '<<=';
RSHIFT_ASSIGN   : '>>=';
URSHIFT_ASSIGN  : '>>>=';

// Java 8 tokens
ARROW           : '->';
COLONCOLON      : '::';

// Additional symbols
AT              : '@';
ELLIPSIS        : '...';

// Whitespace and comments
WS              : [ \t\r\n\u000C]+ -> skip;
COMMENT         : '/*' .*? '*/' -> skip;
LINE_COMMENT    : '//' ~[\r\n]* -> skip;

// Identifiers
Identifier
    :   JavaLetter JavaLetterOrDigit*
    ;

// Fragments
fragment Digits
    :   [0-9] ([0-9_]* [0-9])?
    ;

fragment HexDigits
    :   HexDigit ((HexDigit | '_')* HexDigit)?
    ;

fragment HexDigit
    :   [0-9a-fA-F]
    ;

fragment ExponentPart
    :   [eE] [+-]? Digits
    ;

fragment EscapeSequence
    :   '\\' [btnfr"'\\]
    |   '\\' ([0-3]? [0-7])? [0-7]
    |   '\\' 'u'+ HexDigit HexDigit HexDigit HexDigit
    ;

fragment JavaLetter
    :   [a-zA-Z$_]
    |   ~[\u0000-\u00FF\uD800-\uDBFF]
        {Character.isJavaIdentifierStart(_input.LA(-1))}?
    |   [\uD800-\uDBFF] [\uDC00-\uDFFF]
        {Character.isJavaIdentifierStart(Character.toCodePoint((char)_input.LA(-2), (char)_input.LA(-1)))}?
    ;

fragment JavaLetterOrDigit
    :   [a-zA-Z0-9$_]
    |   ~[\u0000-\u00FF\uD800-\uDBFF]
        {Character.isJavaIdentifierPart(_input.LA(-1))}?
    |   [\uD800-\uDBFF] [\uDC00-\uDFFF]
        {Character.isJavaIdentifierPart(Character.toCodePoint((char)_input.LA(-2), (char)_input.LA(-1)))}?
    ;
