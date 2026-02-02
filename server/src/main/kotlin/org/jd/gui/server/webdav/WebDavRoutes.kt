/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.webdav

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.jd.gui.server.auth.phase2Principal
import org.jd.gui.server.config.StorageConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * File entry response
 */
@Serializable
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: String,
    val icon: String,
    val mimeType: String?
)

/**
 * Configure WebDAV routes with Phase Two organization support
 */
fun Route.configureWebDav(config: StorageConfig) {
    val storageManager = StorageManager(config)

    // Initialize storage directories
    storageManager.initialize()

    authenticate("keycloak") {
        route("/api/files") {

            /**
             * List files in user's folder
             * GET /api/files?path=/
             */
            get {
                val principal = call.phase2Principal()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val relativePath = call.parameters["path"] ?: "/"
                val folderPath = principal.getOrgUserFolderPath(config.basePath)

                val entries = storageManager.listFiles(folderPath, relativePath)
                call.respond(entries)
            }

            /**
             * Get file content
             * GET /api/files/content?path=/MyClass.java
             */
            get("/content") {
                val principal = call.phase2Principal()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val relativePath = call.parameters["path"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Path required")

                val folderPath = principal.getOrgUserFolderPath(config.basePath)
                val file = storageManager.getFile(folderPath, relativePath)
                    ?: return@get call.respond(HttpStatusCode.NotFound)

                if (file.isDirectory) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Cannot read directory")
                }

                val mimeType = ContentType.fromFilePath(file.absolutePath).firstOrNull()
                    ?: ContentType.Application.OctetStream

                call.respondFile(file)
            }

            /**
             * Upload file to user's folder
             * POST /api/files?path=/
             */
            post {
                val principal = call.phase2Principal()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val relativePath = call.parameters["path"] ?: "/"
                val folderPath = principal.getOrgUserFolderPath(config.basePath)

                val multipart = call.receiveMultipart()
                val uploadedFiles = mutableListOf<FileEntry>()

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val fileName = part.originalFileName ?: "unnamed"

                        // Validate extension
                        val ext = "." + fileName.substringAfterLast('.', "")
                        if (!config.allowedExtensions.contains(ext.lowercase())) {
                            part.dispose()
                            return@forEachPart
                        }

                        val savedFile = storageManager.saveFile(folderPath, relativePath, fileName, part.streamProvider())
                        if (savedFile != null) {
                            uploadedFiles.add(savedFile)
                        }

                        part.dispose()
                    }
                }

                call.respond(HttpStatusCode.Created, uploadedFiles)
            }

            /**
             * Create directory
             * POST /api/files/mkdir?path=/newfolder
             */
            post("/mkdir") {
                val principal = call.phase2Principal()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val relativePath = call.parameters["path"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Path required")

                val folderPath = principal.getOrgUserFolderPath(config.basePath)
                val created = storageManager.createDirectory(folderPath, relativePath)

                if (created) {
                    call.respond(HttpStatusCode.Created, mapOf("path" to relativePath))
                } else {
                    call.respond(HttpStatusCode.Conflict, "Directory already exists")
                }
            }

            /**
             * Delete file or directory
             * DELETE /api/files?path=/file.java
             */
            delete {
                val principal = call.phase2Principal()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                val relativePath = call.parameters["path"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Path required")

                val folderPath = principal.getOrgUserFolderPath(config.basePath)
                val deleted = storageManager.delete(folderPath, relativePath)

                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            /**
             * Move/rename file
             * PUT /api/files/move?from=/old.java&to=/new.java
             */
            put("/move") {
                val principal = call.phase2Principal()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)

                val fromPath = call.parameters["from"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "from path required")
                val toPath = call.parameters["to"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "to path required")

                val folderPath = principal.getOrgUserFolderPath(config.basePath)
                val moved = storageManager.move(folderPath, fromPath, toPath)

                if (moved) {
                    call.respond(HttpStatusCode.OK, mapOf("path" to toPath))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            /**
             * Copy file from system to user folder
             * POST /api/files/copy-from-system?path=/MyClass.java
             */
            post("/copy-from-system") {
                val principal = call.phase2Principal()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val systemPath = call.parameters["path"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Path required")
                val targetPath = call.parameters["target"] ?: systemPath

                val systemFolder = config.getSystemFolderPath()
                val userFolder = principal.getOrgUserFolderPath(config.basePath)

                val copied = storageManager.copyBetweenFolders(systemFolder, systemPath, userFolder, targetPath)

                if (copied) {
                    call.respond(HttpStatusCode.Created, mapOf("path" to targetPath))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        // Organization shared files
        route("/api/shared") {
            /**
             * List shared files in organization
             */
            get {
                val principal = call.phase2Principal()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val relativePath = call.parameters["path"] ?: "/"
                val sharedPath = principal.getOrgSharedFolderPath(config.basePath)

                val entries = storageManager.listFiles(sharedPath, relativePath)
                call.respond(entries)
            }

            /**
             * Copy from user folder to shared
             */
            post("/share") {
                val principal = call.phase2Principal()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                if (!principal.isOrgAdmin() && !principal.hasOrgRole("editor")) {
                    return@post call.respond(HttpStatusCode.Forbidden, "Editor role required")
                }

                val sourcePath = call.parameters["path"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Path required")

                val userFolder = principal.getOrgUserFolderPath(config.basePath)
                val sharedFolder = principal.getOrgSharedFolderPath(config.basePath)

                val copied = storageManager.copyBetweenFolders(userFolder, sourcePath, sharedFolder, sourcePath)

                if (copied) {
                    call.respond(HttpStatusCode.Created, mapOf("shared" to true))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        // System files (admin only)
        route("/api/system") {
            /**
             * List system files
             */
            get {
                val principal = call.phase2Principal()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                if (!principal.isSystemAdmin()) {
                    return@get call.respond(HttpStatusCode.Forbidden)
                }

                val relativePath = call.parameters["path"] ?: "/"
                val systemPath = config.getSystemFolderPath()

                val entries = storageManager.listFiles(systemPath, relativePath)
                call.respond(entries)
            }

            /**
             * Upload to system folder
             */
            post {
                val principal = call.phase2Principal()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                if (!principal.isSystemAdmin()) {
                    return@post call.respond(HttpStatusCode.Forbidden)
                }

                val relativePath = call.parameters["path"] ?: "/"
                val systemPath = config.getSystemFolderPath()

                val multipart = call.receiveMultipart()
                val uploadedFiles = mutableListOf<FileEntry>()

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val fileName = part.originalFileName ?: "unnamed"
                        val savedFile = storageManager.saveFile(systemPath, relativePath, fileName, part.streamProvider())
                        if (savedFile != null) {
                            uploadedFiles.add(savedFile)
                        }
                        part.dispose()
                    }
                }

                call.respond(HttpStatusCode.Created, uploadedFiles)
            }
        }
    }
}

/**
 * Storage manager for file operations
 */
class StorageManager(private val config: StorageConfig) {

    fun initialize() {
        // Create base directories
        File(config.basePath).mkdirs()
        File(config.getSystemFolderPath()).mkdirs()
        logger.info { "Initialized storage at ${config.basePath}" }
    }

    fun listFiles(basePath: String, relativePath: String): List<FileEntry> {
        val dir = resolvePath(basePath, relativePath)
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }

        return dir.listFiles()?.map { file ->
            FileEntry(
                name = file.name,
                path = relativePath + "/" + file.name,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0,
                lastModified = DateTimeFormatter.ISO_INSTANT.format(
                    Instant.ofEpochMilli(file.lastModified())
                ),
                icon = getFileIcon(file.name, file.isDirectory),
                mimeType = if (file.isFile) getMimeType(file.name) else null
            )
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }

    fun getFile(basePath: String, relativePath: String): File? {
        val file = resolvePath(basePath, relativePath)
        return if (file.exists()) file else null
    }

    fun saveFile(basePath: String, relativePath: String, fileName: String, inputStream: () -> java.io.InputStream): FileEntry? {
        val dir = resolvePath(basePath, relativePath)
        dir.mkdirs()

        val targetFile = File(dir, fileName)
        return try {
            inputStream().use { input ->
                Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            logger.info { "Saved file: ${targetFile.absolutePath}" }

            FileEntry(
                name = fileName,
                path = "$relativePath/$fileName",
                isDirectory = false,
                size = targetFile.length(),
                lastModified = DateTimeFormatter.ISO_INSTANT.format(
                    Instant.ofEpochMilli(targetFile.lastModified())
                ),
                icon = getFileIcon(fileName, false),
                mimeType = getMimeType(fileName)
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to save file: $fileName" }
            null
        }
    }

    fun createDirectory(basePath: String, relativePath: String): Boolean {
        val dir = resolvePath(basePath, relativePath)
        return if (!dir.exists()) {
            dir.mkdirs()
        } else {
            false
        }
    }

    fun delete(basePath: String, relativePath: String): Boolean {
        val file = resolvePath(basePath, relativePath)
        return if (file.exists()) {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } else {
            false
        }
    }

    fun move(basePath: String, fromPath: String, toPath: String): Boolean {
        val source = resolvePath(basePath, fromPath)
        val target = resolvePath(basePath, toPath)

        if (!source.exists()) return false

        target.parentFile?.mkdirs()
        return try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to move $fromPath to $toPath" }
            false
        }
    }

    fun copyBetweenFolders(sourceBase: String, sourcePath: String, targetBase: String, targetPath: String): Boolean {
        val source = resolvePath(sourceBase, sourcePath)
        val target = resolvePath(targetBase, targetPath)

        if (!source.exists()) return false

        target.parentFile?.mkdirs()
        return try {
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = true)
            } else {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to copy from $sourcePath to $targetPath" }
            false
        }
    }

    private fun resolvePath(basePath: String, relativePath: String): File {
        val normalizedRelative = relativePath.removePrefix("/").replace("..", "")
        return File(basePath, normalizedRelative)
    }

    private fun getFileIcon(name: String, isDirectory: Boolean): String {
        if (isDirectory) return "📁"

        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "java" -> "☕"
            "class" -> "⚙️"
            "jar" -> "📦"
            "kt", "kts" -> "🟣"
            "scala" -> "🔴"
            "groovy" -> "💎"
            "xml" -> "📋"
            "json" -> "📋"
            "yaml", "yml" -> "📋"
            "properties" -> "⚙️"
            "md" -> "📝"
            "txt" -> "📄"
            else -> "📄"
        }
    }

    private fun getMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "java" -> "text/x-java-source"
            "kt" -> "text/x-kotlin"
            "xml" -> "application/xml"
            "json" -> "application/json"
            "jar" -> "application/java-archive"
            "class" -> "application/java-vm"
            else -> "application/octet-stream"
        }
    }
}
