/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.server.puml

import mu.KotlinLogging
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.tree.*
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * ANTLR to PlantUML Converter Interface
 * Converts parsed source code to PlantUML class diagrams using ANTLR parsers
 *
 * Integrates with existing LanguageSyntaxProviderKt infrastructure and
 * ANTLR grammars in the project (Java, Kotlin, TypeScript, etc.)
 */
interface AntlrToPumlConverter {
    /**
     * Language this converter supports
     */
    val languageId: String

    /**
     * Convert source code to PlantUML class diagram
     */
    fun convert(source: String): PumlClassDiagram

    /**
     * Convert source code to PlantUML string
     */
    fun convertToString(source: String): String = convert(source).toPlantUml()

    /**
     * Extract class information from parse tree
     */
    fun extractClassifiers(parseTree: ParseTree): List<PumlClassifier>

    /**
     * Extract relationships from parse tree
     */
    fun extractRelations(parseTree: ParseTree, classifiers: List<PumlClassifier>): List<PumlRelation>
}

/**
 * Base listener for extracting PlantUML elements from ANTLR parse trees
 */
abstract class PumlExtractionListener : ParseTreeListener {
    protected val classifiers = mutableListOf<PumlClassifier>()
    protected val relations = mutableListOf<PumlRelation>()
    protected val packages = mutableMapOf<String, MutableList<PumlClassifier>>()

    // Current context
    protected var currentPackage: String? = null
    protected var currentClass: ClassifierBuilder? = null
    protected val classStack = ArrayDeque<ClassifierBuilder>()

    fun getClassifiers(): List<PumlClassifier> = classifiers.toList()
    fun getRelations(): List<PumlRelation> = relations.toList()
    fun getPackages(): Map<String, List<PumlClassifier>> = packages.mapValues { it.value.toList() }

    protected fun pushClass(builder: ClassifierBuilder) {
        currentClass?.let { classStack.push(it) }
        currentClass = builder
    }

    protected fun popClass(): ClassifierBuilder? {
        val popped = currentClass
        currentClass = if (classStack.isNotEmpty()) classStack.pop() else null
        return popped
    }

    protected fun addClassifier(classifier: PumlClassifier) {
        classifiers.add(classifier)
        currentPackage?.let { pkg ->
            packages.getOrPut(pkg) { mutableListOf() }.add(classifier)
        }
    }

    protected fun addRelation(relation: PumlRelation) {
        relations.add(relation)
    }

    override fun visitTerminal(node: TerminalNode) {}
    override fun visitErrorNode(node: ErrorNode) {}
    override fun enterEveryRule(ctx: ParserRuleContext) {}
    override fun exitEveryRule(ctx: ParserRuleContext) {}
}

/**
 * Builder for constructing PumlClassifier from parsed elements
 */
class ClassifierBuilder(
    var name: String,
    var type: ClassifierType = ClassifierType.CLASS,
    var packageName: String? = null
) {
    private val fields = mutableListOf<PumlField>()
    private val methods = mutableListOf<PumlMethod>()
    private val stereotypes = mutableListOf<PumlStereotype>()
    private val genericTypes = mutableListOf<String>()
    private val annotations = mutableListOf<String>()
    private val innerClasses = mutableListOf<PumlClassifier>()

    var isAbstract = false
    var isStatic = false
    var isFinal = false
    var isDeprecated = false
    var note: String? = null

    // Track relationships
    val extendsTypes = mutableListOf<String>()
    val implementsTypes = mutableListOf<String>()

    fun addField(field: PumlField) = fields.add(field)
    fun addMethod(method: PumlMethod) = methods.add(method)
    fun addStereotype(stereotype: PumlStereotype) = stereotypes.add(stereotype)
    fun addGenericType(type: String) = genericTypes.add(type)
    fun addAnnotation(annotation: String) = annotations.add(annotation)
    fun addInnerClass(inner: PumlClassifier) = innerClasses.add(inner)

    fun build(): PumlClassifier {
        val actualType = when {
            type == ClassifierType.INTERFACE -> ClassifierType.INTERFACE
            isAbstract -> ClassifierType.ABSTRACT_CLASS
            else -> type
        }

        return PumlClassifier(
            name = name,
            type = actualType,
            packageName = packageName,
            fields = fields.toList(),
            methods = methods.toList(),
            stereotypes = stereotypes.toList(),
            genericTypes = genericTypes.toList(),
            annotations = annotations.toList(),
            note = note,
            isDeprecated = isDeprecated
        )
    }

    fun buildRelations(qualifiedName: String): List<PumlRelation> {
        val relations = mutableListOf<PumlRelation>()

        // Extension relationships
        extendsTypes.forEach { parent ->
            relations.add(PumlRelation(
                source = parent,
                target = qualifiedName,
                type = RelationType.EXTENSION
            ))
        }

        // Implementation relationships
        implementsTypes.forEach { iface ->
            relations.add(PumlRelation(
                source = iface,
                target = qualifiedName,
                type = RelationType.IMPLEMENTATION
            ))
        }

        return relations
    }
}

/**
 * Java-specific ANTLR to PlantUML converter
 * Uses existing Java grammars in api/src/main/antlr4/java/
 */
class JavaToPumlConverter : AntlrToPumlConverter {
    override val languageId = "java"

    override fun convert(source: String): PumlClassDiagram {
        val input = CharStreams.fromString(source)
        val listener = JavaPumlListener()

        try {
            // Use reflection to load generated parser classes
            val lexerClass = Class.forName("al.clk.gui.antlr.java.JavaLexer")
            val parserClass = Class.forName("al.clk.gui.antlr.java.JavaParser")

            val lexer = lexerClass.getConstructor(CharStream::class.java)
                .newInstance(input) as Lexer
            val tokens = CommonTokenStream(lexer)
            val parser = parserClass.getConstructor(TokenStream::class.java)
                .newInstance(tokens) as Parser

            // Get the compilationUnit rule
            val compilationUnitMethod = parserClass.getMethod("compilationUnit")
            val tree = compilationUnitMethod.invoke(parser) as ParseTree

            ParseTreeWalker.DEFAULT.walk(listener, tree)
        } catch (e: ClassNotFoundException) {
            logger.warn { "Java parser not found, using fallback regex parsing" }
            return fallbackParse(source)
        } catch (e: Exception) {
            logger.error(e) { "Error parsing Java source" }
            return fallbackParse(source)
        }

        return buildDiagram(listener)
    }

    override fun extractClassifiers(parseTree: ParseTree): List<PumlClassifier> {
        val listener = JavaPumlListener()
        ParseTreeWalker.DEFAULT.walk(listener, parseTree)
        return listener.getClassifiers()
    }

    override fun extractRelations(parseTree: ParseTree, classifiers: List<PumlClassifier>): List<PumlRelation> {
        val listener = JavaPumlListener()
        ParseTreeWalker.DEFAULT.walk(listener, parseTree)
        return listener.getRelations()
    }

    private fun buildDiagram(listener: JavaPumlListener): PumlClassDiagram {
        val builder = PumlClassDiagramBuilder()
            .title("Class Diagram")
            .direction(DiagramDirection.TOP_TO_BOTTOM)

        // Build packages
        listener.getPackages().forEach { (pkgName, classifiersInPkg) ->
            builder.addPackage(PumlPackage(
                name = pkgName,
                classifiers = classifiersInPkg
            ))
        }

        // Add top-level classifiers (no package)
        listener.getClassifiers()
            .filter { it.packageName == null }
            .forEach { builder.addClassifier(it) }

        // Add relations
        listener.getRelations().forEach { builder.addRelation(it) }

        return builder.build()
    }

    /**
     * Fallback regex-based parsing when ANTLR parser is not available
     */
    private fun fallbackParse(source: String): PumlClassDiagram {
        val classifiers = mutableListOf<PumlClassifier>()
        val relations = mutableListOf<PumlRelation>()
        var currentPackage: String? = null

        // Package detection
        val packageRegex = Regex("""package\s+([\w.]+)\s*;""")
        packageRegex.find(source)?.let {
            currentPackage = it.groupValues[1]
        }

        // Class/Interface detection
        val classRegex = Regex(
            """(public\s+|private\s+|protected\s+)?(abstract\s+)?(final\s+)?(class|interface|enum|@interface)\s+(\w+)(?:<([^>]+)>)?(?:\s+extends\s+([\w.]+))?(?:\s+implements\s+([\w.,\s]+))?"""
        )

        classRegex.findAll(source).forEach { match ->
            val visibility = match.groupValues[1].trim()
            val isAbstract = match.groupValues[2].isNotBlank()
            val isFinal = match.groupValues[3].isNotBlank()
            val typeKeyword = match.groupValues[4]
            val className = match.groupValues[5]
            val generics = match.groupValues[6]
            val extendsType = match.groupValues[7]
            val implementsTypes = match.groupValues[8]

            val classType = when (typeKeyword) {
                "interface" -> ClassifierType.INTERFACE
                "enum" -> ClassifierType.ENUM
                "@interface" -> ClassifierType.ANNOTATION
                else -> if (isAbstract) ClassifierType.ABSTRACT_CLASS else ClassifierType.CLASS
            }

            val genericList = if (generics.isNotBlank()) {
                generics.split(",").map { it.trim() }
            } else emptyList()

            // Extract fields and methods within the class body
            val fields = extractFieldsFallback(source, className)
            val methods = extractMethodsFallback(source, className)

            val classifier = PumlClassifier(
                name = className,
                type = classType,
                packageName = currentPackage,
                fields = fields,
                methods = methods,
                genericTypes = genericList
            )
            classifiers.add(classifier)

            // Build qualified name
            val qualifiedName = currentPackage?.let { "$it.$className" } ?: className

            // Extension relationship
            if (extendsType.isNotBlank()) {
                relations.add(PumlRelation(
                    source = extendsType,
                    target = qualifiedName,
                    type = RelationType.EXTENSION
                ))
            }

            // Implementation relationships
            if (implementsTypes.isNotBlank()) {
                implementsTypes.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { iface ->
                    relations.add(PumlRelation(
                        source = iface,
                        target = qualifiedName,
                        type = RelationType.IMPLEMENTATION
                    ))
                }
            }
        }

        val builder = PumlClassDiagramBuilder()
            .title("Class Diagram")
            .direction(DiagramDirection.TOP_TO_BOTTOM)

        // Group by package
        if (currentPackage != null) {
            builder.addPackage(PumlPackage(
                name = currentPackage!!,
                classifiers = classifiers
            ))
        } else {
            classifiers.forEach { builder.addClassifier(it) }
        }

        relations.forEach { builder.addRelation(it) }

        return builder.build()
    }

    private fun extractFieldsFallback(source: String, className: String): List<PumlField> {
        val fields = mutableListOf<PumlField>()

        // Find class body
        val classBodyRegex = Regex("""(?:class|interface|enum)\s+$className[^{]*\{([^}]+(?:\{[^}]*\}[^}]*)*)""", RegexOption.DOT_MATCHES_ALL)
        val classBody = classBodyRegex.find(source)?.groupValues?.getOrNull(1) ?: return fields

        // Field pattern: visibility type name (= value)?;
        val fieldRegex = Regex(
            """(public|private|protected)?\s*(static\s+)?(final\s+)?([\w<>,\s\[\]]+)\s+(\w+)\s*(?:=\s*([^;]+))?\s*;"""
        )

        fieldRegex.findAll(classBody).forEach { match ->
            val visibilityStr = match.groupValues[1]
            val isStatic = match.groupValues[2].isNotBlank()
            val isFinal = match.groupValues[3].isNotBlank()
            val type = match.groupValues[4].trim()
            val name = match.groupValues[5]
            val defaultValue = match.groupValues[6].takeIf { it.isNotBlank() }

            // Skip if it looks like a method
            if (type.contains("(") || name.contains("(")) return@forEach

            val visibility = Visibility.fromJavaModifier(visibilityStr)

            fields.add(PumlField(
                name = name,
                type = type,
                visibility = visibility,
                isStatic = isStatic,
                isFinal = isFinal,
                defaultValue = defaultValue
            ))
        }

        return fields
    }

    private fun extractMethodsFallback(source: String, className: String): List<PumlMethod> {
        val methods = mutableListOf<PumlMethod>()

        // Find class body
        val classBodyRegex = Regex("""(?:class|interface|enum)\s+$className[^{]*\{([^}]+(?:\{[^}]*\}[^}]*)*)""", RegexOption.DOT_MATCHES_ALL)
        val classBody = classBodyRegex.find(source)?.groupValues?.getOrNull(1) ?: return methods

        // Method pattern
        val methodRegex = Regex(
            """(public|private|protected)?\s*(static\s+)?(abstract\s+)?(final\s+)?(?:([\w<>,\s\[\]]+)\s+)?(\w+)\s*\(([^)]*)\)(?:\s*throws\s+([\w,\s]+))?"""
        )

        methodRegex.findAll(classBody).forEach { match ->
            val visibilityStr = match.groupValues[1]
            val isStatic = match.groupValues[2].isNotBlank()
            val isAbstract = match.groupValues[3].isNotBlank()
            val isFinal = match.groupValues[4].isNotBlank()
            val returnType = match.groupValues[5].trim().takeIf { it.isNotBlank() }
            val name = match.groupValues[6]
            val paramsStr = match.groupValues[7]
            val throwsStr = match.groupValues[8]

            // Skip constructors for now (they have same name as class)
            // if (name == className && returnType == null) return@forEach

            val visibility = Visibility.fromJavaModifier(visibilityStr)

            val params = if (paramsStr.isNotBlank()) {
                paramsStr.split(",").mapNotNull { paramStr ->
                    val parts = paramStr.trim().split(Regex("""\s+"""))
                    if (parts.size >= 2) {
                        PumlParameter(
                            name = parts.last(),
                            type = parts.dropLast(1).joinToString(" ")
                        )
                    } else null
                }
            } else emptyList()

            val exceptions = if (throwsStr.isNotBlank()) {
                throwsStr.split(",").map { it.trim() }
            } else emptyList()

            methods.add(PumlMethod(
                name = name,
                returnType = returnType,
                parameters = params,
                visibility = visibility,
                isStatic = isStatic,
                isAbstract = isAbstract,
                isFinal = isFinal,
                exceptions = exceptions
            ))
        }

        return methods
    }
}

/**
 * Java-specific PlantUML extraction listener
 * Works with generated Java parser from api/src/main/antlr4/java/
 */
class JavaPumlListener : PumlExtractionListener() {

    // These methods would be implemented to match the specific ANTLR grammar rules
    // For now, they serve as placeholders that the reflection-based parser would call

    fun enterPackageDeclaration(packageName: String) {
        currentPackage = packageName
    }

    fun enterClassDeclaration(
        name: String,
        isAbstract: Boolean,
        isFinal: Boolean,
        extendsType: String?,
        implementsTypes: List<String>,
        genericTypes: List<String>
    ) {
        val builder = ClassifierBuilder(name, ClassifierType.CLASS, currentPackage)
        builder.isAbstract = isAbstract
        builder.isFinal = isFinal
        extendsType?.let { builder.extendsTypes.add(it) }
        builder.implementsTypes.addAll(implementsTypes)
        genericTypes.forEach { builder.addGenericType(it) }
        pushClass(builder)
    }

    fun enterInterfaceDeclaration(
        name: String,
        extendsTypes: List<String>,
        genericTypes: List<String>
    ) {
        val builder = ClassifierBuilder(name, ClassifierType.INTERFACE, currentPackage)
        builder.extendsTypes.addAll(extendsTypes)
        genericTypes.forEach { builder.addGenericType(it) }
        pushClass(builder)
    }

    fun enterEnumDeclaration(name: String, implementsTypes: List<String>) {
        val builder = ClassifierBuilder(name, ClassifierType.ENUM, currentPackage)
        builder.implementsTypes.addAll(implementsTypes)
        pushClass(builder)
    }

    fun enterAnnotationDeclaration(name: String) {
        val builder = ClassifierBuilder(name, ClassifierType.ANNOTATION, currentPackage)
        pushClass(builder)
    }

    fun enterFieldDeclaration(
        name: String,
        type: String,
        visibility: Visibility,
        isStatic: Boolean,
        isFinal: Boolean,
        defaultValue: String?
    ) {
        currentClass?.addField(PumlField(
            name = name,
            type = type,
            visibility = visibility,
            isStatic = isStatic,
            isFinal = isFinal,
            defaultValue = defaultValue
        ))
    }

    fun enterMethodDeclaration(
        name: String,
        returnType: String?,
        parameters: List<PumlParameter>,
        visibility: Visibility,
        isStatic: Boolean,
        isAbstract: Boolean,
        isFinal: Boolean,
        exceptions: List<String>
    ) {
        currentClass?.addMethod(PumlMethod(
            name = name,
            returnType = returnType,
            parameters = parameters,
            visibility = visibility,
            isStatic = isStatic,
            isAbstract = isAbstract,
            isFinal = isFinal,
            exceptions = exceptions
        ))
    }

    fun exitTypeDeclaration() {
        val builder = popClass()
        if (builder != null) {
            val classifier = builder.build()
            addClassifier(classifier)

            // Add inheritance/implementation relations
            val qualifiedName = classifier.packageName?.let { "${it}.${classifier.name}" } ?: classifier.name
            builder.buildRelations(qualifiedName).forEach { addRelation(it) }
        }
    }
}

/**
 * Converter registry for multiple language support
 */
object AntlrToPumlConverterRegistry {
    private val converters = mutableMapOf<String, AntlrToPumlConverter>()

    init {
        // Register default converters
        register(JavaToPumlConverter())
    }

    fun register(converter: AntlrToPumlConverter) {
        converters[converter.languageId] = converter
    }

    fun getConverter(languageId: String): AntlrToPumlConverter? = converters[languageId]

    fun getSupportedLanguages(): Set<String> = converters.keys.toSet()

    /**
     * Convert source code to PlantUML using language detection
     */
    fun convert(source: String, languageId: String): PumlClassDiagram {
        val converter = converters[languageId]
            ?: throw IllegalArgumentException("No converter for language: $languageId")
        return converter.convert(source)
    }

    /**
     * Convert source code to PlantUML string
     */
    fun convertToString(source: String, languageId: String): String {
        return convert(source, languageId).toPlantUml()
    }
}

// Extension functions for easy conversion
fun String.toPlantUmlDiagram(languageId: String = "java"): PumlClassDiagram =
    AntlrToPumlConverterRegistry.convert(this, languageId)

fun String.toPlantUmlString(languageId: String = "java"): String =
    AntlrToPumlConverterRegistry.convertToString(this, languageId)
