package al.clk.gui.server.treenode

import mu.KotlinLogging
import java.util.regex.Pattern

private val logger = KotlinLogging.logger {}

// =============================================================================
// Class File Factories (matching Java legacy ClassFileTreeNodeFactoryProvider)
// =============================================================================

/**
 * Factory for .class files - Java bytecode
 * Matches: *:file:*.class (excluding module-info.class)
 */
class ClassFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "class-file"
    override val displayName = "Java Class File"
    override val priority = 10

    override fun getSelectors() = appendSelectors("*:file:*.class")

    // Exclude module-info.class (handled by ModuleInfoTreeNodeFactory)
    override fun getPathPattern(): Pattern = Pattern.compile("^((?!module-info\\.class).)*\$")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        val classInfo = if (content != null && content.size >= 8) {
            extractClassInfo(content)
        } else {
            emptyMap()
        }

        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name.removeSuffix(".class"),
            iconName = "class_icon",
            tooltip = buildTooltip(entry, classInfo),
            data = classInfo
        )
    }

    private fun extractClassInfo(bytes: ByteArray): Map<String, String> {
        return try {
            // Read magic number and version
            if (bytes[0] == 0xCA.toByte() && bytes[1] == 0xFE.toByte() &&
                bytes[2] == 0xBA.toByte() && bytes[3] == 0xBE.toByte()) {

                val minorVersion = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
                val majorVersion = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)

                val javaVersion = when {
                    majorVersion >= 49 -> (majorVersion - 49 + 5).toString()
                    majorVersion >= 45 -> "1.${majorVersion - 45 + 1}"
                    else -> "Unknown"
                }

                mapOf(
                    "majorVersion" to majorVersion.toString(),
                    "minorVersion" to minorVersion.toString(),
                    "javaVersion" to javaVersion,
                    "classVersion" to "$majorVersion.$minorVersion"
                )
            } else {
                mapOf("error" to "Invalid class file magic number")
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Failed to read class info"))
        }
    }
}

/**
 * Factory for module-info.class files
 * Higher priority than ClassFileTreeNodeFactory
 */
class ModuleInfoTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "module-info"
    override val displayName = "Java Module Info"
    override val priority = 20  // Higher than ClassFileTreeNodeFactory

    override fun getSelectors() = appendSelectors(
        "*:file:module-info.class",
        "*:file:*/module-info.class"
    )

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = "module-info",
            iconName = "module_icon",
            tooltip = buildTooltip(entry, mapOf("Type" to "Java 9+ Module Descriptor")),
            data = mapOf("type" to "module-info")
        )
    }
}

// =============================================================================
// Source File Factories
// =============================================================================

/**
 * Factory for Java source files
 */
class JavaFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "java-source"
    override val displayName = "Java Source"

    override fun getSelectors() = appendSelectors("*:file:*.java")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        val sourceInfo = content?.let { extractSourceInfo(String(it, Charsets.UTF_8)) } ?: emptyMap()

        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "java_icon",
            tooltip = buildTooltip(entry, sourceInfo),
            data = sourceInfo
        )
    }

    private fun extractSourceInfo(source: String): Map<String, String> {
        val info = mutableMapOf<String, String>()

        // Extract package
        Regex("""package\s+([\w.]+);""").find(source)?.let {
            info["package"] = it.groupValues[1]
        }

        // Extract class/interface name
        Regex("""(?:public\s+)?(?:class|interface|enum|record)\s+(\w+)""").find(source)?.let {
            info["type"] = it.groupValues[1]
        }

        return info
    }
}

/**
 * Factory for Kotlin source files
 */
class KotlinFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "kotlin-source"
    override val displayName = "Kotlin Source"

    override fun getSelectors() = appendSelectors("*:file:*.kt", "*:file:*.kts")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "kotlin_icon",
            tooltip = buildTooltip(entry),
            data = mapOf("language" to "Kotlin")
        )
    }
}

/**
 * Factory for JavaScript files
 */
class JavaScriptFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "javascript-source"
    override val displayName = "JavaScript Source"

    override fun getSelectors() = appendSelectors("*:file:*.js", "*:file:*.mjs", "*:file:*.jsx")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "javascript_icon",
            tooltip = buildTooltip(entry),
            data = mapOf("language" to "JavaScript")
        )
    }
}

/**
 * Factory for TypeScript files
 */
class TypeScriptFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "typescript-source"
    override val displayName = "TypeScript Source"

    override fun getSelectors() = appendSelectors("*:file:*.ts", "*:file:*.tsx")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "typescript_icon",
            tooltip = buildTooltip(entry),
            data = mapOf("language" to "TypeScript")
        )
    }
}

// =============================================================================
// Data File Factories
// =============================================================================

/**
 * Factory for JSON files
 */
class JsonFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "json-file"
    override val displayName = "JSON File"

    override fun getSelectors() = appendSelectors("*:file:*.json")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "json_icon",
            tooltip = buildTooltip(entry),
            data = mapOf("format" to "JSON")
        )
    }
}

/**
 * Factory for XML files
 */
class XmlFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "xml-file"
    override val displayName = "XML File"

    override fun getSelectors() = appendSelectors("*:file:*.xml")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "xml_icon",
            tooltip = buildTooltip(entry),
            data = mapOf("format" to "XML")
        )
    }
}

/**
 * Factory for YAML files
 */
class YamlFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "yaml-file"
    override val displayName = "YAML File"

    override fun getSelectors() = appendSelectors("*:file:*.yaml", "*:file:*.yml")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "yaml_icon",
            tooltip = buildTooltip(entry),
            data = mapOf("format" to "YAML")
        )
    }
}

/**
 * Factory for properties files
 */
class PropertiesFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "properties-file"
    override val displayName = "Properties File"

    override fun getSelectors() = appendSelectors("*:file:*.properties")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        val propCount = content?.let {
            String(it, Charsets.UTF_8).lines().count { line ->
                line.isNotBlank() && !line.trimStart().startsWith("#")
            }
        } ?: 0

        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "properties_icon",
            tooltip = buildTooltip(entry, mapOf("Properties" to propCount.toString())),
            data = mapOf("propertyCount" to propCount.toString())
        )
    }
}

// =============================================================================
// Metadata File Factories (JAR/WAR specific)
// =============================================================================

/**
 * Factory for MANIFEST.MF files
 * Specific path match = higher priority than extension match
 */
class ManifestFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "manifest-file"
    override val displayName = "Manifest File"
    override val priority = 100  // High priority for exact path match

    override fun getSelectors() = appendSelectors(
        "*:file:META-INF/MANIFEST.MF",
        "jar:file:META-INF/MANIFEST.MF",
        "war:file:META-INF/MANIFEST.MF"
    )

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        val manifestInfo = content?.let { parseManifest(String(it, Charsets.UTF_8)) } ?: emptyMap()

        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = "MANIFEST.MF",
            iconName = "manifest_icon",
            tooltip = buildTooltip(entry, manifestInfo),
            data = manifestInfo
        )
    }

    private fun parseManifest(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        content.lines().forEach { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                if (key.isNotEmpty()) {
                    result[key] = value
                }
            }
        }
        return result
    }
}

/**
 * Factory for META-INF/services/* files (SPI registrations)
 */
class MetaInfServiceFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "metainf-service"
    override val displayName = "SPI Service File"
    override val priority = 50

    override fun getSelectors() = appendSelectors(
        "jar:file:META-INF/services/*",
        "*:file:META-INF/services/*"
    )

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        val implementations = content?.let {
            String(it, Charsets.UTF_8).lines()
                .filter { line -> line.isNotBlank() && !line.trimStart().startsWith("#") }
        } ?: emptyList()

        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "service_icon",
            tooltip = buildTooltip(entry, mapOf(
                "Interface" to entry.name,
                "Implementations" to implementations.size.toString()
            )),
            data = mapOf(
                "interface" to entry.name,
                "implementations" to implementations.joinToString(",")
            )
        )
    }
}

/**
 * Factory for web.xml in WAR files
 */
class WebXmlFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "web-xml"
    override val displayName = "Web Deployment Descriptor"
    override val priority = 100

    override fun getSelectors() = appendSelectors(
        "war:file:WEB-INF/web.xml"
    )

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = "web.xml",
            iconName = "webapp_icon",
            tooltip = buildTooltip(entry, mapOf("Type" to "Web Deployment Descriptor")),
            data = mapOf("type" to "deployment-descriptor")
        )
    }
}

/**
 * Factory for application.xml in EAR files
 */
class ApplicationXmlFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "application-xml"
    override val displayName = "Application Descriptor"
    override val priority = 100

    override fun getSelectors() = appendSelectors(
        "ear:file:META-INF/application.xml"
    )

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = "application.xml",
            iconName = "ear_icon",
            tooltip = buildTooltip(entry, mapOf("Type" to "EAR Application Descriptor")),
            data = mapOf("type" to "ear-descriptor")
        )
    }
}

// =============================================================================
// Directory Factories
// =============================================================================

/**
 * Default factory for directories
 */
class DirectoryTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "directory"
    override val displayName = "Directory"
    override val priority = -100  // Lowest priority (default)

    override fun getSelectors() = appendSelectors("*:dir:*")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "folder_icon",
            tooltip = buildTooltip(entry),
            data = emptyMap()
        )
    }
}

/**
 * Factory for Java packages (directories in JAR containing classes)
 */
class PackageTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "package"
    override val displayName = "Java Package"
    override val priority = 10

    override fun getSelectors() = appendSelectors(
        "jar:dir:*",
        "war:dir:WEB-INF/classes/*"
    )

    // Exclude META-INF and special directories
    override fun getPathPattern(): Pattern =
        Pattern.compile("^(?!META-INF)(?!WEB-INF(?!/classes)).*$")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        val packageName = entry.path.replace('/', '.')

        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "package_icon",
            tooltip = buildTooltip(entry, mapOf("Package" to packageName)),
            data = mapOf("packageName" to packageName)
        )
    }
}

/**
 * Factory for META-INF directory
 */
class MetaInfDirectoryTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "metainf-dir"
    override val displayName = "META-INF"
    override val priority = 50

    override fun getSelectors() = appendSelectors(
        "jar:dir:META-INF",
        "war:dir:META-INF",
        "ear:dir:META-INF"
    )

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = "META-INF",
            iconName = "metainf_icon",
            tooltip = buildTooltip(entry, mapOf("Type" to "Metadata Directory")),
            data = mapOf("type" to "metadata")
        )
    }
}

/**
 * Factory for WEB-INF directory in WAR files
 */
class WebInfDirectoryTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "webinf-dir"
    override val displayName = "WEB-INF"
    override val priority = 50

    override fun getSelectors() = appendSelectors(
        "war:dir:WEB-INF",
        "war:dir:WEB-INF/*"
    )

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "webinf_icon",
            tooltip = buildTooltip(entry, mapOf("Type" to "Web Application Directory")),
            data = mapOf("type" to "web-inf")
        )
    }
}

// =============================================================================
// Archive Factories (nested archives)
// =============================================================================

/**
 * Factory for JAR files (nested in other archives)
 */
class JarFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "jar-file"
    override val displayName = "JAR Archive"
    override val priority = 50

    override fun getSelectors() = appendSelectors("*:file:*.jar")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "jar_icon",
            tooltip = buildTooltip(entry, mapOf("Type" to "Java Archive")),
            data = mapOf("isContainer" to "true", "containerType" to "jar")
        )
    }
}

/**
 * Factory for WAR files in EAR
 */
class WarFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "war-file"
    override val displayName = "WAR Archive"
    override val priority = 50

    override fun getSelectors() = appendSelectors("ear:file:*.war", "*:file:*.war")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "war_icon",
            tooltip = buildTooltip(entry, mapOf("Type" to "Web Application Archive")),
            data = mapOf("isContainer" to "true", "containerType" to "war")
        )
    }
}

/**
 * Factory for ZIP files
 */
class ZipFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "zip-file"
    override val displayName = "ZIP Archive"
    override val priority = 10

    override fun getSelectors() = appendSelectors("*:file:*.zip")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "zip_icon",
            tooltip = buildTooltip(entry, mapOf("Type" to "ZIP Archive")),
            data = mapOf("isContainer" to "true", "containerType" to "zip")
        )
    }
}

// =============================================================================
// Media File Factories
// =============================================================================

/**
 * Factory for image files
 */
class ImageFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "image-file"
    override val displayName = "Image File"

    override fun getSelectors() = appendSelectors(
        "*:file:*.png", "*:file:*.jpg", "*:file:*.jpeg",
        "*:file:*.gif", "*:file:*.ico", "*:file:*.bmp",
        "*:file:*.webp", "*:file:*.svg"
    )

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "image_icon",
            tooltip = buildTooltip(entry),
            data = mapOf("category" to "image")
        )
    }
}

/**
 * Factory for text files (generic)
 */
class TextFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "text-file"
    override val displayName = "Text File"
    override val priority = -50  // Low priority (fallback)

    override fun getSelectors() = appendSelectors(
        "*:file:*.txt", "*:file:*.md", "*:file:*.log",
        "*:file:*.csv", "*:file:*.ini", "*:file:*.cfg"
    )

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        val lineCount = content?.let {
            String(it, Charsets.UTF_8).lines().size
        } ?: 0

        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "text_icon",
            tooltip = buildTooltip(entry, mapOf("Lines" to lineCount.toString())),
            data = mapOf("lineCount" to lineCount.toString())
        )
    }
}

// =============================================================================
// Default/Fallback Factories
// =============================================================================

/**
 * Default factory for unknown files
 */
class DefaultFileTreeNodeFactory : AbstractTreeNodeFactory() {
    override val factoryId = "default-file"
    override val displayName = "File"
    override val priority = Int.MIN_VALUE  // Absolute fallback

    override fun getSelectors() = appendSelectors("*:file:*")

    override suspend fun make(entry: ContainerEntry, content: ByteArray?): TreeNodeResult {
        return TreeNodeResult(
            entry = entry,
            factoryId = factoryId,
            displayName = entry.name,
            iconName = "file_icon",
            tooltip = buildTooltip(entry),
            data = emptyMap()
        )
    }
}
