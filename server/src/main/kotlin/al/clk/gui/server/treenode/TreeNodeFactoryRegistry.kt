package al.clk.gui.server.treenode

import mu.KotlinLogging
import al.clk.gui.server.handlers.FileExtensionHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.regex.Pattern

private val logger = KotlinLogging.logger {}

/**
 * Registry for TreeNodeFactory instances
 *
 * Uses the same selector matching algorithm as Java legacy TreeNodeFactoryService:
 *
 * Priority order for matching:
 * 1. Exact path match
 * 2. Directory prefix match (*/name)
 * 3. Extension match (*.ext)
 * 4. Wildcard match (*)
 *
 * Container type matching:
 * 1. Try specific container type (jar, war, etc.)
 * 2. Fall back to wildcard (*) container
 */
class TreeNodeFactoryRegistry : KoinComponent {

    // Map of selector -> list of factories (sorted by priority)
    private val factoriesBySelector = mutableMapOf<String, TreeNodeFactories>()

    // All registered factories
    private val allFactories = mutableListOf<TreeNodeFactory>()

    /**
     * Register a factory
     */
    fun register(factory: TreeNodeFactory) {
        allFactories.add(factory)

        factory.getSelectors().forEach { selector ->
            val factories = factoriesBySelector.getOrPut(selector) { TreeNodeFactories() }
            factories.add(factory)
        }

        logger.debug { "Registered factory: ${factory.factoryId} with selectors: ${factory.getSelectors().joinToString()}" }
    }

    /**
     * Register multiple factories
     */
    fun registerAll(vararg factories: TreeNodeFactory) {
        factories.forEach { register(it) }
    }

    /**
     * Register a FileExtensionHandler as a TreeNodeFactory adapter
     */
    fun registerHandler(handler: FileExtensionHandler, containerTypes: List<String> = listOf("*")) {
        register(HandlerAdapterFactory(handler, containerTypes))
    }

    /**
     * Get factory for a container entry
     * Uses Java legacy matching algorithm
     */
    fun get(entry: ContainerEntry): TreeNodeFactory? {
        // First try specific container type
        var factory = get(entry.containerType, entry)

        // Fall back to wildcard container if not found
        if (factory == null && entry.containerType != "*") {
            factory = get("*", entry)
        }

        return factory
    }

    /**
     * Get factory for specific container type and entry
     */
    private fun get(containerType: String, entry: ContainerEntry): TreeNodeFactory? {
        val path = entry.path
        val type = if (entry.isDirectory) "dir" else "file"
        val prefix = "$containerType:$type:"

        // Priority 1: Exact path match
        var factory = matchSelector(prefix + path, path)
        if (factory != null) return factory

        // Priority 2: Directory prefix match (*/name)
        val lastSlashIndex = path.lastIndexOf('/')
        val name = if (lastSlashIndex >= 0) path.substring(lastSlashIndex + 1) else path

        factory = matchSelector("$prefix*/$name", path)
        if (factory != null) return factory

        // Priority 3: Extension match (*.ext)
        val dotIndex = name.lastIndexOf('.')
        if (dotIndex > 0) {
            val extension = name.substring(dotIndex + 1)
            factory = matchSelector("$prefix*.$extension", path)
            if (factory != null) return factory
        }

        // Priority 4: Wildcard match (*)
        factory = matchSelector("$prefix*", path)
        return factory
    }

    /**
     * Match a selector and find the best factory using path patterns
     */
    private fun matchSelector(selector: String, path: String): TreeNodeFactory? {
        val factories = factoriesBySelector[selector] ?: return null
        return factories.match(path)
    }

    /**
     * Get all factories
     */
    fun getAllFactories(): List<TreeNodeFactory> = allFactories.toList()

    /**
     * Get factories by container type
     */
    fun getByContainerType(containerType: String): List<TreeNodeFactory> {
        return allFactories.filter { factory ->
            factory.getSelectors().any { it.startsWith("$containerType:") || it.startsWith("*:") }
        }
    }

    /**
     * Check if registry has a factory for the entry
     */
    fun hasFactory(entry: ContainerEntry): Boolean = get(entry) != null

    /**
     * Get count of registered factories
     */
    val size: Int get() = allFactories.size

    /**
     * Clear all factories
     */
    fun clear() {
        factoriesBySelector.clear()
        allFactories.clear()
    }

    /**
     * Inner class for managing factories with the same selector
     * Handles priority and path pattern matching
     */
    private inner class TreeNodeFactories {
        private val factoriesWithPattern = mutableMapOf<String, TreeNodeFactory>()
        private var defaultFactory: TreeNodeFactory? = null

        fun add(factory: TreeNodeFactory) {
            val pattern = factory.getPathPattern()
            if (pattern != null) {
                factoriesWithPattern[pattern.pattern()] = factory
            } else {
                // Keep highest priority as default
                if (defaultFactory == null || factory.priority > defaultFactory!!.priority) {
                    defaultFactory = factory
                }
            }
        }

        fun match(path: String): TreeNodeFactory? {
            // First check pattern-based factories
            for ((_, factory) in factoriesWithPattern) {
                val pattern = factory.getPathPattern()
                if (pattern != null && pattern.matcher(path).matches()) {
                    return factory
                }
            }
            // Fall back to default
            return defaultFactory
        }
    }

    companion object {
        /**
         * Build a selector string
         */
        fun buildSelector(
            containerType: ContainerType = ContainerType.ANY,
            entryType: EntryType = EntryType.FILE,
            pathPattern: String
        ): String = "${containerType.extension}:${entryType.selector}:$pathPattern"

        /**
         * Build extension selector
         */
        fun extensionSelector(extension: String, containerType: ContainerType = ContainerType.ANY): String =
            buildSelector(containerType, EntryType.FILE, "*.$extension")

        /**
         * Build directory selector
         */
        fun directorySelector(path: String, containerType: ContainerType = ContainerType.ANY): String =
            buildSelector(containerType, EntryType.DIRECTORY, path)
    }
}

/**
 * Factory registration DSL
 */
class TreeNodeFactoryRegistryBuilder {
    private val factories = mutableListOf<TreeNodeFactory>()

    fun factory(factory: TreeNodeFactory) {
        factories.add(factory)
    }

    fun adapter(handler: FileExtensionHandler, vararg containerTypes: String) {
        factories.add(HandlerAdapterFactory(handler, containerTypes.toList().ifEmpty { listOf("*") }))
    }

    fun build(): List<TreeNodeFactory> = factories.toList()
}

fun buildFactories(block: TreeNodeFactoryRegistryBuilder.() -> Unit): List<TreeNodeFactory> {
    return TreeNodeFactoryRegistryBuilder().apply(block).build()
}
