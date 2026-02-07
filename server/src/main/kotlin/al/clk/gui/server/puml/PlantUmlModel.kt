/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.server.puml

import kotlinx.serialization.Serializable

/**
 * PlantUML model classes for generating UML diagrams
 * Supports class diagrams, sequence diagrams, and component diagrams
 */

// =========================================================================
// Base Types
// =========================================================================

/**
 * Visibility modifiers
 */
enum class Visibility(val symbol: String) {
    PUBLIC("+"),
    PRIVATE("-"),
    PROTECTED("#"),
    PACKAGE("~");

    companion object {
        fun fromJavaModifier(modifier: String): Visibility = when (modifier.lowercase()) {
            "public" -> PUBLIC
            "private" -> PRIVATE
            "protected" -> PROTECTED
            else -> PACKAGE
        }
    }
}

/**
 * Type of classifier (class, interface, enum, etc.)
 */
enum class ClassifierType(val keyword: String) {
    CLASS("class"),
    INTERFACE("interface"),
    ABSTRACT_CLASS("abstract class"),
    ENUM("enum"),
    ANNOTATION("annotation"),
    ENTITY("entity"),
    EXCEPTION("exception");

    companion object {
        fun fromJavaKeywords(isInterface: Boolean, isAbstract: Boolean, isEnum: Boolean, isAnnotation: Boolean): ClassifierType {
            return when {
                isInterface -> INTERFACE
                isEnum -> ENUM
                isAnnotation -> ANNOTATION
                isAbstract -> ABSTRACT_CLASS
                else -> CLASS
            }
        }
    }
}

/**
 * Relationship types between classifiers
 */
enum class RelationType(val arrow: String, val reverseArrow: String) {
    EXTENSION("<|--", "--|>"),           // Inheritance
    IMPLEMENTATION("<|..", "..|>"),      // Interface implementation
    COMPOSITION("*--", "--*"),           // Composition (filled diamond)
    AGGREGATION("o--", "--o"),           // Aggregation (empty diamond)
    ASSOCIATION("--", "--"),             // Simple association
    DIRECTED_ASSOCIATION("-->", "<--"),  // Directed association
    DEPENDENCY("..>", "<.."),            // Dependency (dashed)
    REALIZATION("..|>", "<|.."),         // Realization
    INNER("--+", "+--");                 // Inner class

    fun getArrow(reversed: Boolean = false): String = if (reversed) reverseArrow else arrow
}

// =========================================================================
// Class Diagram Elements
// =========================================================================

/**
 * Represents a field/attribute in a class
 */
@Serializable
data class PumlField(
    val name: String,
    val type: String,
    val visibility: Visibility = Visibility.PRIVATE,
    val isStatic: Boolean = false,
    val isFinal: Boolean = false,
    val defaultValue: String? = null,
    val annotations: List<String> = emptyList()
) {
    fun toPlantUml(): String {
        val modifiers = buildString {
            if (isStatic) append("{static} ")
            if (isFinal) append("{final} ")
        }
        val value = defaultValue?.let { " = $it" } ?: ""
        return "${visibility.symbol}$modifiers$name : $type$value"
    }
}

/**
 * Represents a method parameter
 */
@Serializable
data class PumlParameter(
    val name: String,
    val type: String,
    val defaultValue: String? = null,
    val annotations: List<String> = emptyList()
) {
    fun toPlantUml(): String {
        val value = defaultValue?.let { " = $it" } ?: ""
        return "$name : $type$value"
    }
}

/**
 * Represents a method/operation in a class
 */
@Serializable
data class PumlMethod(
    val name: String,
    val returnType: String?,
    val parameters: List<PumlParameter> = emptyList(),
    val visibility: Visibility = Visibility.PUBLIC,
    val isStatic: Boolean = false,
    val isAbstract: Boolean = false,
    val isFinal: Boolean = false,
    val annotations: List<String> = emptyList(),
    val exceptions: List<String> = emptyList()
) {
    fun toPlantUml(): String {
        val modifiers = buildString {
            if (isStatic) append("{static} ")
            if (isAbstract) append("{abstract} ")
        }
        val params = parameters.joinToString(", ") { it.toPlantUml() }
        val ret = returnType?.let { " : $it" } ?: ""
        return "${visibility.symbol}$modifiers$name($params)$ret"
    }
}

/**
 * Represents a stereotype (e.g., <<interface>>, <<entity>>)
 */
@Serializable
data class PumlStereotype(
    val name: String,
    val spotChar: Char? = null,
    val spotColor: String? = null
) {
    fun toPlantUml(): String {
        val spot = if (spotChar != null && spotColor != null) {
            "($spotChar,$spotColor)"
        } else ""
        return "<<$name>>$spot"
    }
}

/**
 * Represents a class, interface, or other classifier
 */
@Serializable
data class PumlClassifier(
    val name: String,
    val type: ClassifierType = ClassifierType.CLASS,
    val packageName: String? = null,
    val fields: List<PumlField> = emptyList(),
    val methods: List<PumlMethod> = emptyList(),
    val stereotypes: List<PumlStereotype> = emptyList(),
    val genericTypes: List<String> = emptyList(),
    val annotations: List<String> = emptyList(),
    val backgroundColor: String? = null,
    val borderColor: String? = null,
    val note: String? = null,
    val isDeprecated: Boolean = false
) {
    val qualifiedName: String
        get() = packageName?.let { "$it.$name" } ?: name

    fun toPlantUml(includeMembers: Boolean = true): String = buildString {
        // Stereotypes
        if (stereotypes.isNotEmpty()) {
            append(stereotypes.joinToString(" ") { it.toPlantUml() })
            append(" ")
        }

        // Type and name
        append("${type.keyword} ")
        append(qualifiedName)

        // Generics
        if (genericTypes.isNotEmpty()) {
            append("<")
            append(genericTypes.joinToString(", "))
            append(">")
        }

        // Colors
        if (backgroundColor != null || borderColor != null) {
            append(" ")
            backgroundColor?.let { append("#$it") }
            borderColor?.let { append("##$it") }
        }

        if (includeMembers && (fields.isNotEmpty() || methods.isNotEmpty())) {
            appendLine(" {")

            // Fields
            fields.forEach { field ->
                appendLine("  ${field.toPlantUml()}")
            }

            // Separator between fields and methods
            if (fields.isNotEmpty() && methods.isNotEmpty()) {
                appendLine("  --")
            }

            // Methods
            methods.forEach { method ->
                appendLine("  ${method.toPlantUml()}")
            }

            append("}")
        }

        // Note
        note?.let {
            appendLine()
            append("note right of $qualifiedName : $it")
        }
    }
}

/**
 * Represents a relationship between classifiers
 */
@Serializable
data class PumlRelation(
    val source: String,
    val target: String,
    val type: RelationType,
    val sourceLabel: String? = null,
    val targetLabel: String? = null,
    val label: String? = null,
    val sourceCardinality: String? = null,
    val targetCardinality: String? = null,
    val style: String? = null,  // e.g., "bold", "dashed", "dotted"
    val color: String? = null,
    val reversed: Boolean = false
) {
    fun toPlantUml(): String = buildString {
        append(source)
        append(" ")

        // Source cardinality
        sourceCardinality?.let { append("\"$it\" ") }

        // Arrow with optional styling
        val arrow = type.getArrow(reversed)
        if (color != null || style != null) {
            val styleStr = listOfNotNull(
                color?.let { "#$it" },
                style
            ).joinToString(",")
            append(arrow.replace("--", "-[$styleStr]-").replace("..", ".[$styleStr]."))
        } else {
            append(arrow)
        }

        // Target cardinality
        targetCardinality?.let { append(" \"$it\"") }

        append(" ")
        append(target)

        // Relationship label
        label?.let { append(" : $it") }
    }
}

/**
 * Represents a package/namespace
 */
@Serializable
data class PumlPackage(
    val name: String,
    val classifiers: List<PumlClassifier> = emptyList(),
    val subPackages: List<PumlPackage> = emptyList(),
    val stereotype: String? = null,
    val backgroundColor: String? = null,
    val borderStyle: String? = null  // e.g., "Rectangle", "Folder", "Frame", "Cloud", "Database"
) {
    fun toPlantUml(indent: String = ""): String = buildString {
        append(indent)
        borderStyle?.let { append("$it ") } ?: append("package ")
        append("\"$name\"")
        stereotype?.let { append(" <<$it>>") }
        backgroundColor?.let { append(" #$it") }
        appendLine(" {")

        // Sub-packages
        subPackages.forEach { pkg ->
            appendLine(pkg.toPlantUml("$indent  "))
        }

        // Classifiers
        classifiers.forEach { classifier ->
            appendLine("$indent  ${classifier.toPlantUml()}")
        }

        append("$indent}")
    }
}

/**
 * Represents a note in the diagram
 */
@Serializable
data class PumlNote(
    val content: String,
    val position: NotePosition = NotePosition.RIGHT,
    val attachedTo: String? = null,
    val alias: String? = null,
    val backgroundColor: String? = null
) {
    fun toPlantUml(): String = buildString {
        append("note ")
        append(position.keyword)
        attachedTo?.let { append(" of $it") }
        alias?.let { append(" as $it") }
        backgroundColor?.let { append(" #$it") }
        appendLine()
        appendLine(content)
        append("end note")
    }
}

enum class NotePosition(val keyword: String) {
    LEFT("left"),
    RIGHT("right"),
    TOP("top"),
    BOTTOM("bottom")
}

// =========================================================================
// Complete Diagram
// =========================================================================

/**
 * Represents a complete PlantUML class diagram
 */
@Serializable
data class PumlClassDiagram(
    val title: String? = null,
    val header: String? = null,
    val footer: String? = null,
    val packages: List<PumlPackage> = emptyList(),
    val classifiers: List<PumlClassifier> = emptyList(),  // Top-level classifiers
    val relations: List<PumlRelation> = emptyList(),
    val notes: List<PumlNote> = emptyList(),
    val skinParams: Map<String, String> = emptyMap(),
    val hideOptions: List<String> = emptyList(),  // e.g., "empty members", "circle"
    val showOptions: List<String> = emptyList(),
    val direction: DiagramDirection? = null,
    val scale: Double? = null
) {
    fun toPlantUml(): String = buildString {
        appendLine("@startuml")
        appendLine()

        // Title and header
        title?.let { appendLine("title $it") }
        header?.let { appendLine("header $it") }
        direction?.let { appendLine("${it.keyword}") }
        scale?.let { appendLine("scale $it") }
        appendLine()

        // Skin parameters
        if (skinParams.isNotEmpty()) {
            appendLine("' Skin Parameters")
            skinParams.forEach { (key, value) ->
                appendLine("skinparam $key $value")
            }
            appendLine()
        }

        // Hide/Show options
        hideOptions.forEach { appendLine("hide $it") }
        showOptions.forEach { appendLine("show $it") }
        if (hideOptions.isNotEmpty() || showOptions.isNotEmpty()) {
            appendLine()
        }

        // Packages
        packages.forEach { pkg ->
            appendLine(pkg.toPlantUml())
            appendLine()
        }

        // Top-level classifiers
        classifiers.forEach { classifier ->
            appendLine(classifier.toPlantUml())
            appendLine()
        }

        // Relations
        if (relations.isNotEmpty()) {
            appendLine("' Relationships")
            relations.forEach { relation ->
                appendLine(relation.toPlantUml())
            }
            appendLine()
        }

        // Notes
        notes.forEach { note ->
            appendLine(note.toPlantUml())
            appendLine()
        }

        // Footer
        footer?.let { appendLine("footer $it") }

        appendLine("@enduml")
    }
}

enum class DiagramDirection(val keyword: String) {
    LEFT_TO_RIGHT("left to right direction"),
    TOP_TO_BOTTOM("top to bottom direction")
}

// =========================================================================
// Diagram Builder
// =========================================================================

/**
 * Builder for constructing PlantUML class diagrams
 */
class PumlClassDiagramBuilder {
    private var title: String? = null
    private var header: String? = null
    private var footer: String? = null
    private val packages = mutableListOf<PumlPackage>()
    private val classifiers = mutableListOf<PumlClassifier>()
    private val relations = mutableListOf<PumlRelation>()
    private val notes = mutableListOf<PumlNote>()
    private val skinParams = mutableMapOf<String, String>()
    private val hideOptions = mutableListOf<String>()
    private val showOptions = mutableListOf<String>()
    private var direction: DiagramDirection? = null
    private var scale: Double? = null

    fun title(title: String) = apply { this.title = title }
    fun header(header: String) = apply { this.header = header }
    fun footer(footer: String) = apply { this.footer = footer }
    fun direction(direction: DiagramDirection) = apply { this.direction = direction }
    fun scale(scale: Double) = apply { this.scale = scale }

    fun addPackage(pkg: PumlPackage) = apply { packages.add(pkg) }
    fun addClassifier(classifier: PumlClassifier) = apply { classifiers.add(classifier) }
    fun addRelation(relation: PumlRelation) = apply { relations.add(relation) }
    fun addNote(note: PumlNote) = apply { notes.add(note) }

    fun skinParam(key: String, value: String) = apply { skinParams[key] = value }
    fun hide(option: String) = apply { hideOptions.add(option) }
    fun show(option: String) = apply { showOptions.add(option) }

    // Convenience methods for common relationships
    fun extends(child: String, parent: String, label: String? = null) = apply {
        relations.add(PumlRelation(parent, child, RelationType.EXTENSION, label = label))
    }

    fun implements(implementor: String, iface: String) = apply {
        relations.add(PumlRelation(iface, implementor, RelationType.IMPLEMENTATION))
    }

    fun composes(container: String, component: String, label: String? = null, cardinality: String? = null) = apply {
        relations.add(PumlRelation(container, component, RelationType.COMPOSITION,
            label = label, targetCardinality = cardinality))
    }

    fun aggregates(container: String, component: String, label: String? = null, cardinality: String? = null) = apply {
        relations.add(PumlRelation(container, component, RelationType.AGGREGATION,
            label = label, targetCardinality = cardinality))
    }

    fun associates(source: String, target: String, label: String? = null) = apply {
        relations.add(PumlRelation(source, target, RelationType.ASSOCIATION, label = label))
    }

    fun dependsOn(dependent: String, dependency: String, label: String? = null) = apply {
        relations.add(PumlRelation(dependent, dependency, RelationType.DEPENDENCY, label = label))
    }

    // Default skin params for clean diagrams
    fun applyDefaultSkin() = apply {
        skinParams["classAttributeIconSize"] = "0"
        skinParams["monochrome"] = "false"
        skinParams["shadowing"] = "false"
        skinParams["linetype"] = "ortho"
        skinParams["class"] = "{\n  BackgroundColor White\n  BorderColor Black\n}"
    }

    fun build(): PumlClassDiagram = PumlClassDiagram(
        title = title,
        header = header,
        footer = footer,
        packages = packages.toList(),
        classifiers = classifiers.toList(),
        relations = relations.toList(),
        notes = notes.toList(),
        skinParams = skinParams.toMap(),
        hideOptions = hideOptions.toList(),
        showOptions = showOptions.toList(),
        direction = direction,
        scale = scale
    )
}

// Extension function for DSL-style building
fun pumlClassDiagram(block: PumlClassDiagramBuilder.() -> Unit): PumlClassDiagram {
    return PumlClassDiagramBuilder().apply(block).build()
}
