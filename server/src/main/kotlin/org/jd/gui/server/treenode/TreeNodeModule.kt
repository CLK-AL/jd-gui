package org.jd.gui.server.treenode

import mu.KotlinLogging
import org.jd.gui.server.handlers.HandlerRegistry
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private val logger = KotlinLogging.logger {}

/**
 * Koin module for TreeNodeFactory components
 *
 * Provides:
 * - TreeNodeFactoryRegistry (singleton)
 * - All built-in TreeNodeFactory implementations
 * - Integration with existing FileExtensionHandler system
 */
val treeNodeModule = module {

    // Core registry
    single {
        TreeNodeFactoryRegistry().apply {
            // Register built-in factories (ordered by priority)

            // Class file factories
            register(ModuleInfoTreeNodeFactory())  // Priority 20
            register(ClassFileTreeNodeFactory())   // Priority 10

            // Source file factories
            register(JavaFileTreeNodeFactory())
            register(KotlinFileTreeNodeFactory())
            register(JavaScriptFileTreeNodeFactory())
            register(TypeScriptFileTreeNodeFactory())

            // Data file factories
            register(JsonFileTreeNodeFactory())
            register(XmlFileTreeNodeFactory())
            register(YamlFileTreeNodeFactory())
            register(PropertiesFileTreeNodeFactory())

            // Metadata file factories (high priority for exact paths)
            register(ManifestFileTreeNodeFactory())        // Priority 100
            register(MetaInfServiceFileTreeNodeFactory())  // Priority 50
            register(WebXmlFileTreeNodeFactory())          // Priority 100
            register(ApplicationXmlFileTreeNodeFactory())  // Priority 100

            // Directory factories
            register(MetaInfDirectoryTreeNodeFactory())    // Priority 50
            register(WebInfDirectoryTreeNodeFactory())     // Priority 50
            register(PackageTreeNodeFactory())             // Priority 10
            register(DirectoryTreeNodeFactory())           // Priority -100

            // Archive factories
            register(JarFileTreeNodeFactory())
            register(WarFileTreeNodeFactory())
            register(ZipFileTreeNodeFactory())

            // Media file factories
            register(ImageFileTreeNodeFactory())
            register(TextFileTreeNodeFactory())            // Priority -50

            // Default fallback
            register(DefaultFileTreeNodeFactory())         // Priority MIN

            logger.info { "TreeNodeFactoryRegistry initialized with ${size} factories" }
        }
    }

    // Factory instances (for direct injection if needed)
    singleOf(::ClassFileTreeNodeFactory)
    singleOf(::ModuleInfoTreeNodeFactory)
    singleOf(::JavaFileTreeNodeFactory)
    singleOf(::ManifestFileTreeNodeFactory)
    singleOf(::DirectoryTreeNodeFactory)
    singleOf(::PackageTreeNodeFactory)
}

/**
 * Extension to register handlers from HandlerRegistry as TreeNodeFactories
 */
fun TreeNodeFactoryRegistry.registerHandlers(handlerRegistry: HandlerRegistry) {
    handlerRegistry.getAllHandlers().forEach { handler ->
        registerHandler(handler)
    }
    logger.info { "Registered ${handlerRegistry.getAllHandlers().size} handlers as TreeNodeFactories" }
}

/**
 * Combined module that integrates TreeNode with Handlers
 */
val treeNodeWithHandlersModule = module {
    includes(treeNodeModule)

    // After both registries are available, integrate them
    single {
        val treeNodeRegistry: TreeNodeFactoryRegistry = get()
        val handlerRegistry: HandlerRegistry = get()

        treeNodeRegistry.registerHandlers(handlerRegistry)
        treeNodeRegistry
    }
}
