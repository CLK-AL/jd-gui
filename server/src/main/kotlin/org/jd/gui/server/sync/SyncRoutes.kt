/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package org.jd.gui.server.sync

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.jd.gui.server.auth.phase2Principal
import org.jd.gui.server.config.SyncConfig
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Yjs document state for synchronization
 */
@Serializable
data class DocumentState(
    val documentId: String,
    val content: String,
    val version: Long,
    val lastModified: Long,
    val modifiedBy: String?
)

/**
 * Sync message types
 */
@Serializable
sealed class SyncMessage {
    @Serializable
    data class Subscribe(val documentId: String) : SyncMessage()

    @Serializable
    data class Unsubscribe(val documentId: String) : SyncMessage()

    @Serializable
    data class Update(val documentId: String, val update: String, val version: Long) : SyncMessage()

    @Serializable
    data class Awareness(val documentId: String, val clientId: String, val state: String) : SyncMessage()

    @Serializable
    data class Sync(val documentId: String, val stateVector: String) : SyncMessage()
}

/**
 * Connected client session
 */
data class ClientSession(
    val userId: String,
    val organizationId: String?,
    val session: WebSocketServerSession,
    val subscribedDocuments: MutableSet<String> = mutableSetOf()
)

/**
 * Yjs sync manager for document collaboration
 */
class YjsSyncManager(private val config: SyncConfig) {
    // Active client sessions
    private val clients = ConcurrentHashMap<String, ClientSession>()

    // Document subscriptions (documentId -> set of clientIds)
    private val documentSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()

    // Document states
    private val documentStates = ConcurrentHashMap<String, DocumentState>()

    fun addClient(clientId: String, session: ClientSession) {
        clients[clientId] = session
        logger.debug { "Client connected: $clientId (user: ${session.userId})" }
    }

    fun removeClient(clientId: String) {
        val session = clients.remove(clientId) ?: return

        // Unsubscribe from all documents
        session.subscribedDocuments.forEach { docId ->
            documentSubscriptions[docId]?.remove(clientId)
        }

        logger.debug { "Client disconnected: $clientId" }
    }

    fun subscribe(clientId: String, documentId: String) {
        val session = clients[clientId] ?: return

        session.subscribedDocuments.add(documentId)
        documentSubscriptions.getOrPut(documentId) { mutableSetOf() }.add(clientId)

        logger.debug { "Client $clientId subscribed to document $documentId" }
    }

    fun unsubscribe(clientId: String, documentId: String) {
        val session = clients[clientId] ?: return

        session.subscribedDocuments.remove(documentId)
        documentSubscriptions[documentId]?.remove(clientId)
    }

    suspend fun broadcastUpdate(documentId: String, update: String, version: Long, excludeClientId: String? = null) {
        if (!config.broadcastChanges) return

        val subscribers = documentSubscriptions[documentId] ?: return
        val message = Json.encodeToString(SyncMessage.Update(documentId, update, version))

        subscribers.forEach { clientId ->
            if (clientId != excludeClientId) {
                try {
                    clients[clientId]?.session?.send(message)
                } catch (e: Exception) {
                    logger.warn { "Failed to send update to client $clientId: ${e.message}" }
                }
            }
        }
    }

    suspend fun broadcastAwareness(documentId: String, clientId: String, state: String) {
        val subscribers = documentSubscriptions[documentId] ?: return
        val message = Json.encodeToString(SyncMessage.Awareness(documentId, clientId, state))

        subscribers.forEach { subscriberId ->
            if (subscriberId != clientId) {
                try {
                    clients[subscriberId]?.session?.send(message)
                } catch (e: Exception) {
                    logger.warn { "Failed to send awareness to client $subscriberId: ${e.message}" }
                }
            }
        }
    }

    fun getDocumentState(documentId: String): DocumentState? = documentStates[documentId]

    fun updateDocumentState(documentId: String, content: String, modifiedBy: String?) {
        val existing = documentStates[documentId]
        val newVersion = (existing?.version ?: 0) + 1

        documentStates[documentId] = DocumentState(
            documentId = documentId,
            content = content,
            version = newVersion,
            lastModified = System.currentTimeMillis(),
            modifiedBy = modifiedBy
        )
    }

    fun getSubscriberCount(documentId: String): Int =
        documentSubscriptions[documentId]?.size ?: 0

    fun getConnectedClients(): Int = clients.size
}

/**
 * Configure Yjs sync routes
 */
fun Route.configureSyncRoutes(config: SyncConfig) {
    if (!config.enabled) {
        logger.info { "Yjs sync is disabled" }
        return
    }

    val syncManager = YjsSyncManager(config)

    // WebSocket endpoint for Yjs sync
    authenticate("keycloak", optional = true) {
        webSocket("/sync/{documentId}") {
            val documentId = call.parameters["documentId"]
                ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Document ID required"))

            val principal = call.phase2Principal()
            val clientId = java.util.UUID.randomUUID().toString()
            val userId = principal?.userId ?: "anonymous-$clientId"
            val orgId = principal?.activeOrganization?.id

            // Create client session
            val session = ClientSession(
                userId = userId,
                organizationId = orgId,
                session = this
            )

            syncManager.addClient(clientId, session)
            syncManager.subscribe(clientId, documentId)

            // Send initial state
            val state = syncManager.getDocumentState(documentId)
            if (state != null) {
                send(Json.encodeToString(mapOf(
                    "type" to "state",
                    "documentId" to documentId,
                    "content" to state.content,
                    "version" to state.version.toString()
                )))
            }

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        handleSyncMessage(syncManager, clientId, userId, text)
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                logger.debug { "Client $clientId channel closed" }
            } catch (e: Exception) {
                logger.error(e) { "Error in sync WebSocket for client $clientId" }
            } finally {
                syncManager.removeClient(clientId)
            }
        }
    }

    // REST API for sync status
    route("/api/sync") {
        get("/status") {
            call.respond(mapOf(
                "enabled" to config.enabled,
                "connectedClients" to syncManager.getConnectedClients()
            ))
        }

        get("/document/{documentId}") {
            val documentId = call.parameters["documentId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Document ID required")

            val state = syncManager.getDocumentState(documentId)
            if (state != null) {
                call.respond(state)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

private suspend fun handleSyncMessage(
    syncManager: YjsSyncManager,
    clientId: String,
    userId: String,
    messageText: String
) {
    try {
        val json = Json { ignoreUnknownKeys = true }
        val messageMap = json.decodeFromString<Map<String, String>>(messageText)

        when (messageMap["type"]) {
            "subscribe" -> {
                val docId = messageMap["documentId"] ?: return
                syncManager.subscribe(clientId, docId)
            }

            "unsubscribe" -> {
                val docId = messageMap["documentId"] ?: return
                syncManager.unsubscribe(clientId, docId)
            }

            "update" -> {
                val docId = messageMap["documentId"] ?: return
                val update = messageMap["update"] ?: return
                val version = messageMap["version"]?.toLongOrNull() ?: 0

                syncManager.updateDocumentState(docId, update, userId)
                syncManager.broadcastUpdate(docId, update, version, excludeClientId = clientId)
            }

            "awareness" -> {
                val docId = messageMap["documentId"] ?: return
                val state = messageMap["state"] ?: return

                syncManager.broadcastAwareness(docId, clientId, state)
            }
        }
    } catch (e: Exception) {
        logger.warn { "Failed to parse sync message: ${e.message}" }
    }
}
