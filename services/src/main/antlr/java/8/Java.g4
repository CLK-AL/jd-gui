/*
 [The "BSD licence"]
 Copyright (c) 2013 Terence Parr, Sam Harwell
 Copyright (c) 2017 Ivan Kochurkin (upgrade to Java 8)
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
*/

/** A Java 8 grammar for ANTLR v4.
 *  Supports lambdas and method references.
 */
grammar Java;

// Starting point for parsing a java file
compilationUnit
    :   packageDeclaration? importDeclaration* typeDeclaration* EOF
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
    ;

variableModifier
    :   FINAL
    |   annotation
    ;

classDeclaration
    :   CLASS Identifier typeParameters?
        (EXTENDS typeType)?
        (IMPLEMENTS typeList)?
        classBody
    ;

typeParameters
    :   LT typeParameter (COMMA typeParameter)* GT
    ;

typeParameter
    :   annotation* Identifier (EXTENDS typeBound)?
    ;

typeBound
    :   typeType (BITAND typeType)*
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
    :   INTERFACE Identifier typeParameters? (EXTENDS typeList)? interfaceBody
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
    |   interfaceDeclaration
    |   annotationTypeDeclaration
    |   classDeclaration
    |   enumDeclaration
    ;

methodDeclaration
    :   typeTypeOrVoid Identifier formalParameters (LBRACK RBRACK)*
        (THROWS qualifiedNameList)?
        methodBody
    ;

methodBody
    :   block
    |   SEMI
    ;

typeTypeOrVoid
    :   typeType
    |   VOID
    ;

genericMethodDeclaration
    :   typeParameters methodDeclaration
    ;

genericConstructorDeclaration
    :   typeParameters constructorDeclaration
    ;

constructorDeclaration
    :   Identifier formalParameters (THROWS qualifiedNameList)? block
    ;

fieldDeclaration
    :   typeType variableDeclarators SEMI
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
    ;

constDeclaration
    :   typeType constantDeclarator (COMMA constantDeclarator)* SEMI
    ;

constantDeclarator
    :   Identifier (LBRACK RBRACK)* ASSIGN variableInitializer
    ;

interfaceMethodDeclaration
    :   interfaceMethodModifier* (typeTypeOrVoid | typeParameters annotation* typeTypeOrVoid)
        Identifier formalParameters (LBRACK RBRACK)* (THROWS qualifiedNameList)? methodBody
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
    :   typeParameters interfaceMethodDeclaration
    ;

variableDeclarators
    :   variableDeclarator (COMMA variableDeclarator)*
    ;

variableDeclarator
    :   variableDeclaratorId (ASSIGN variableInitializer)?
    ;

variableDeclaratorId
    :   Identifier (LBRACK RBRACK)*
    ;

variableInitializer
    :   arrayInitializer
    |   expression
    ;

arrayInitializer
    :   LBRACE (variableInitializer (COMMA variableInitializer)* COMMA?)? RBRACE
    ;

classOrInterfaceType
    :   Identifier typeArguments? (DOT Identifier typeArguments?)*
    ;

typeArgument
    :   typeType
    |   QUESTION ((EXTENDS | SUPER) typeType)?
    ;

qualifiedNameList
    :   qualifiedName (COMMA qualifiedName)*
    ;

formalParameters
    :   LPAREN formalParameterList? RPAREN
    ;

formalParameterList
    :   formalParameter (COMMA formalParameter)* (COMMA lastFormalParameter)?
    |   lastFormalParameter
    ;

formalParameter
    :   variableModifier* typeType variableDeclaratorId
    ;

lastFormalParameter
    :   variableModifier* typeType annotation* ELLIPSIS variableDeclaratorId
    ;

qualifiedName
    :   Identifier (DOT Identifier)*
    ;

literal
    :   IntegerLiteral
    |   FloatingPointLiteral
    |   CharacterLiteral
    |   StringLiteral
    |   BooleanLiteral
    |   NullLiteral
    ;

// ANNOTATIONS

annotation
    :   AT qualifiedName (LPAREN ( elementValuePairs | elementValue )? RPAREN)?
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
    :   typeType annotationMethodOrConstantRest SEMI
    |   classDeclaration SEMI?
    |   interfaceDeclaration SEMI?
    |   enumDeclaration SEMI?
    |   annotationTypeDeclaration SEMI?
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

// STATEMENTS / BLOCKS

block
    :   LBRACE blockStatement* RBRACE
    ;

blockStatement
    :   localVariableDeclaration SEMI
    |   statement
    |   typeDeclaration
    ;

localVariableDeclaration
    :   variableModifier* typeType variableDeclarators
    ;

statement
    :   block
    |   ASSERT expression (COLON expression)? SEMI
    |   IF LPAREN expression RPAREN statement (ELSE statement)?
    |   FOR LPAREN forControl RPAREN statement
    |   WHILE LPAREN expression RPAREN statement
    |   DO statement WHILE LPAREN expression RPAREN SEMI
    |   TRY block (catchClause+ finallyBlock? | finallyBlock)
    |   TRY resourceSpecification block catchClause* finallyBlock?
    |   SWITCH LPAREN expression RPAREN LBRACE switchBlockStatementGroup* switchLabel* RBRACE
    |   SYNCHRONIZED LPAREN expression RPAREN block
    |   RETURN expression? SEMI
    |   THROW expression SEMI
    |   BREAK Identifier? SEMI
    |   CONTINUE Identifier? SEMI
    |   SEMI
    |   expression SEMI
    |   Identifier COLON statement
    ;

catchClause
    :   CATCH LPAREN variableModifier* catchType Identifier RPAREN block
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
    :   variableModifier* classOrInterfaceType variableDeclaratorId ASSIGN expression
    ;

switchBlockStatementGroup
    :   switchLabel+ blockStatement+
    ;

switchLabel
    :   CASE expression COLON
    |   CASE Identifier COLON
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

forUpdate
    :   expressionList
    ;

enhancedForControl
    :   variableModifier* typeType variableDeclaratorId COLON expression
    ;

// EXPRESSIONS

expressionList
    :   expression (COMMA expression)*
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
    |   LPAREN typeType RPAREN expression
    |   expression (INC | DEC)
    |   (ADD | SUB | INC | DEC) expression
    |   (TILDE | BANG) expression
    |   expression (MUL | DIV | MOD) expression
    |   expression (ADD | SUB) expression
    |   expression (LT LT | GT GT GT | GT GT) expression
    |   expression (LE | GE | GT | LT) expression
    |   expression INSTANCEOF typeType
    |   expression (EQUAL | NOTEQUAL) expression
    |   expression BITAND expression
    |   expression CARET expression
    |   expression BITOR expression
    |   expression AND expression
    |   expression OR expression
    |   expression QUESTION expression COLON expression
    |   expression
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
    |   expression COLONCOLON typeArguments? Identifier
    |   typeType COLONCOLON (typeArguments? Identifier | NEW)
    |   classType COLONCOLON typeArguments? NEW
    ;

// Java 8 Lambda
lambdaExpression
    :   lambdaParameters ARROW lambdaBody
    ;

lambdaParameters
    :   Identifier
    |   LPAREN formalParameterList? RPAREN
    |   LPAREN Identifier (COMMA Identifier)* RPAREN
    ;

lambdaBody
    :   expression
    |   block
    ;

primary
    :   LPAREN expression RPAREN
    |   THIS
    |   SUPER
    |   literal
    |   Identifier
    |   typeTypeOrVoid DOT CLASS
    |   nonWildcardTypeArguments (explicitGenericInvocationSuffix | THIS arguments)
    ;

classType
    :   (classOrInterfaceType DOT)? annotation* Identifier typeArguments?
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

typeArgumentsOrDiamond
    :   LT GT
    |   typeArguments
    ;

nonWildcardTypeArgumentsOrDiamond
    :   LT GT
    |   nonWildcardTypeArguments
    ;

nonWildcardTypeArguments
    :   LT typeList GT
    ;

typeList
    :   typeType (COMMA typeType)*
    ;

typeType
    :   annotation* (classOrInterfaceType | primitiveType) (annotation* LBRACK RBRACK)*
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

superSuffix
    :   arguments
    |   DOT Identifier arguments?
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

// Literals
IntegerLiteral
    :   DecimalIntegerLiteral
    |   HexIntegerLiteral
    |   OctalIntegerLiteral
    |   BinaryIntegerLiteral
    ;

fragment DecimalIntegerLiteral : DecimalNumeral IntegerTypeSuffix?;
fragment HexIntegerLiteral     : HexNumeral IntegerTypeSuffix?;
fragment OctalIntegerLiteral   : OctalNumeral IntegerTypeSuffix?;
fragment BinaryIntegerLiteral  : BinaryNumeral IntegerTypeSuffix?;
fragment IntegerTypeSuffix     : [lL];
fragment DecimalNumeral        : '0' | NonZeroDigit (Digits? | Underscores Digits);
fragment Digits                : Digit (DigitsAndUnderscores? Digit)?;
fragment Digit                 : '0' | NonZeroDigit;
fragment NonZeroDigit          : [1-9];
fragment DigitsAndUnderscores  : DigitOrUnderscore+;
fragment DigitOrUnderscore     : Digit | '_';
fragment Underscores           : '_'+;
fragment HexNumeral            : '0' [xX] HexDigits;
fragment HexDigits             : HexDigit (HexDigitsAndUnderscores? HexDigit)?;
fragment HexDigit              : [0-9a-fA-F];
fragment HexDigitsAndUnderscores : HexDigitOrUnderscore+;
fragment HexDigitOrUnderscore  : HexDigit | '_';
fragment OctalNumeral          : '0' Underscores? OctalDigits;
fragment OctalDigits           : OctalDigit (OctalDigitsAndUnderscores? OctalDigit)?;
fragment OctalDigit            : [0-7];
fragment OctalDigitsAndUnderscores : OctalDigitOrUnderscore+;
fragment OctalDigitOrUnderscore : OctalDigit | '_';
fragment BinaryNumeral         : '0' [bB] BinaryDigits;
fragment BinaryDigits          : BinaryDigit (BinaryDigitsAndUnderscores? BinaryDigit)?;
fragment BinaryDigit           : [01];
fragment BinaryDigitsAndUnderscores : BinaryDigitOrUnderscore+;
fragment BinaryDigitOrUnderscore : BinaryDigit | '_';

FloatingPointLiteral
    :   DecimalFloatingPointLiteral
    |   HexadecimalFloatingPointLiteral
    ;

fragment DecimalFloatingPointLiteral
    :   Digits '.' Digits? ExponentPart? FloatTypeSuffix?
    |   '.' Digits ExponentPart? FloatTypeSuffix?
    |   Digits ExponentPart FloatTypeSuffix?
    |   Digits FloatTypeSuffix
    ;

fragment ExponentPart          : [eE] [+-]? Digits;
fragment FloatTypeSuffix       : [fFdD];

fragment HexadecimalFloatingPointLiteral
    :   HexSignificand BinaryExponent FloatTypeSuffix?
    ;

fragment HexSignificand
    :   HexNumeral '.'?
    |   '0' [xX] HexDigits? '.' HexDigits
    ;

fragment BinaryExponent        : [pP] [+-]? Digits;

BooleanLiteral : 'true' | 'false';

CharacterLiteral : '\'' SingleCharacter '\'' | '\'' EscapeSequence '\'';
fragment SingleCharacter : ~['\\\r\n];

StringLiteral : '"' StringCharacters? '"';
fragment StringCharacters : StringCharacter+;
fragment StringCharacter  : ~["\\\r\n] | EscapeSequence;

fragment EscapeSequence
    :   '\\' [btnfr"'\\]
    |   OctalEscape
    |   UnicodeEscape
    ;

fragment OctalEscape
    :   '\\' OctalDigit
    |   '\\' OctalDigit OctalDigit
    |   '\\' ZeroToThree OctalDigit OctalDigit
    ;

fragment ZeroToThree : [0-3];

fragment UnicodeEscape : '\\' 'u'+ HexDigit HexDigit HexDigit HexDigit;

NullLiteral : 'null';

// Separators
LPAREN   : '(';
RPAREN   : ')';
LBRACE   : '{';
RBRACE   : '}';
LBRACK   : '[';
RBRACK   : ']';
SEMI     : ';';
COMMA    : ',';
DOT      : '.';

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
Identifier      : JavaLetter JavaLetterOrDigit*;

fragment JavaLetter
    :   [a-zA-Z$_]
    |   ~[\u0000-\u007F\uD800-\uDBFF]
    |   [\uD800-\uDBFF] [\uDC00-\uDFFF]
    ;

fragment JavaLetterOrDigit
    :   [a-zA-Z0-9$_]
    |   ~[\u0000-\u007F\uD800-\uDBFF]
    |   [\uD800-\uDBFF] [\uDC00-\uDFFF]
    ;
