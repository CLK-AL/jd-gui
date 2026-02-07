/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.server.matrix

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import al.clk.gui.server.config.MatrixConfig
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Matrix social service for user communication and collaboration
 * Uses Matrix protocol for real-time messaging, presence, and rooms
 */
class MatrixSocialService(private val config: MatrixConfig) {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    // User sessions
    private val userSessions = ConcurrentHashMap<String, MatrixUserSession>()

    // Org room mappings
    private val orgRooms = ConcurrentHashMap<String, String>() // orgId -> roomId

    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Initialize Matrix service
     */
    suspend fun initialize(): Boolean {
        if (!config.enabled) {
            logger.info { "Matrix integration is disabled" }
            return false
        }

        return try {
            // Verify homeserver connection
            val response = httpClient.get("${config.homeserverUrl}/_matrix/client/versions")
            if (response.status.isSuccess()) {
                logger.info { "Connected to Matrix homeserver at ${config.homeserverUrl}" }
                true
            } else {
                logger.error { "Failed to connect to Matrix homeserver" }
                false
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize Matrix service" }
            false
        }
    }

    /**
     * Register or login user on Matrix server
     */
    suspend fun loginUser(username: String, password: String): MatrixUserSession? {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.post("${config.homeserverUrl}/_matrix/client/v3/login") {
                    contentType(ContentType.Application.Json)
                    setBody(MatrixLoginRequest(
                        type = "m.login.password",
                        identifier = MatrixUserIdentifier(
                            type = "m.id.user",
                            user = username
                        ),
                        password = password,
                        deviceId = "gui-${username}"
                    ))
                }

                if (response.status.isSuccess()) {
                    val loginResponse: MatrixLoginResponse = response.body()
                    val session = MatrixUserSession(
                        userId = loginResponse.userId,
                        accessToken = loginResponse.accessToken,
                        deviceId = loginResponse.deviceId
                    )
                    userSessions[username] = session
                    logger.info { "User $username logged into Matrix" }
                    session
                } else {
                    logger.error { "Matrix login failed for $username: ${response.status}" }
                    null
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to login user to Matrix: $username" }
                null
            }
        }
    }

    /**
     * Set user session from external token (e.g., from Keycloak SSO)
     */
    fun setUserSession(username: String, accessToken: String, userId: String) {
        userSessions[username] = MatrixUserSession(
            userId = userId,
            accessToken = accessToken,
            deviceId = "gui-$username"
        )
    }

    /**
     * Create organization room
     */
    suspend fun createOrgRoom(orgId: String, orgName: String, adminUserId: String): String? {
        if (!config.enabled || !config.enableRooms) return null

        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.post("${config.homeserverUrl}/_matrix/client/v3/createRoom") {
                    header("Authorization", "Bearer ${config.adminAccessToken}")
                    contentType(ContentType.Application.Json)
                    setBody(CreateRoomRequest(
                        name = orgName,
                        roomAliasName = "${config.orgRoomPrefix}-$orgId",
                        topic = "Organization room for $orgName",
                        preset = "private_chat",
                        visibility = "private",
                        invite = listOf(adminUserId),
                        initialState = listOf(
                            MatrixStateEvent(
                                type = "m.room.guest_access",
                                content = mapOf("guest_access" to "forbidden")
                            )
                        )
                    ))
                }

                if (response.status.isSuccess()) {
                    val roomResponse: CreateRoomResponse = response.body()
                    orgRooms[orgId] = roomResponse.roomId
                    logger.info { "Created Matrix room for org $orgName: ${roomResponse.roomId}" }
                    roomResponse.roomId
                } else {
                    logger.error { "Failed to create Matrix room for org $orgId" }
                    null
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to create org room: $orgId" }
                null
            }
        }
    }

    /**
     * Invite user to organization room
     */
    suspend fun inviteUserToOrg(orgId: String, userId: String): Boolean {
        val roomId = orgRooms[orgId] ?: return false

        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.post("${config.homeserverUrl}/_matrix/client/v3/rooms/$roomId/invite") {
                    header("Authorization", "Bearer ${config.adminAccessToken}")
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("user_id" to userId))
                }
                response.status.isSuccess().also {
                    if (it) logger.debug { "Invited $userId to org room $orgId" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to invite user to org room" }
                false
            }
        }
    }

    /**
     * Remove user from organization room
     */
    suspend fun removeUserFromOrg(orgId: String, userId: String): Boolean {
        val roomId = orgRooms[orgId] ?: return false

        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.post("${config.homeserverUrl}/_matrix/client/v3/rooms/$roomId/kick") {
                    header("Authorization", "Bearer ${config.adminAccessToken}")
                    contentType(ContentType.Application.Json)
                    setBody(mapOf(
                        "user_id" to userId,
                        "reason" to "Removed from organization"
                    ))
                }
                response.status.isSuccess()
            } catch (e: Exception) {
                logger.error(e) { "Failed to remove user from org room" }
                false
            }
        }
    }

    /**
     * Send message to organization room
     */
    suspend fun sendOrgMessage(orgId: String, senderToken: String, message: String): Boolean {
        val roomId = orgRooms[orgId] ?: return false

        return withContext(Dispatchers.IO) {
            try {
                val txnId = System.currentTimeMillis().toString()
                val response = httpClient.put(
                    "${config.homeserverUrl}/_matrix/client/v3/rooms/$roomId/send/m.room.message/$txnId"
                ) {
                    header("Authorization", "Bearer $senderToken")
                    contentType(ContentType.Application.Json)
                    setBody(mapOf(
                        "msgtype" to "m.text",
                        "body" to message
                    ))
                }
                response.status.isSuccess()
            } catch (e: Exception) {
                logger.error(e) { "Failed to send message to org room" }
                false
            }
        }
    }

    /**
     * Create direct message room between two users
     */
    suspend fun createDirectMessage(user1Token: String, user2Id: String): String? {
        if (!config.enabled || !config.enableDirectMessages) return null

        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.post("${config.homeserverUrl}/_matrix/client/v3/createRoom") {
                    header("Authorization", "Bearer $user1Token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateRoomRequest(
                        preset = "trusted_private_chat",
                        visibility = "private",
                        invite = listOf(user2Id),
                        isDirect = true
                    ))
                }

                if (response.status.isSuccess()) {
                    val roomResponse: CreateRoomResponse = response.body()
                    logger.debug { "Created DM room with $user2Id" }
                    roomResponse.roomId
                } else null
            } catch (e: Exception) {
                logger.error(e) { "Failed to create DM room" }
                null
            }
        }
    }

    /**
     * Update user presence
     */
    suspend fun setPresence(userToken: String, userId: String, presence: UserPresence): Boolean {
        if (!config.enabled || !config.enablePresence) return false

        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.put(
                    "${config.homeserverUrl}/_matrix/client/v3/presence/$userId/status"
                ) {
                    header("Authorization", "Bearer $userToken")
                    contentType(ContentType.Application.Json)
                    setBody(mapOf(
                        "presence" to presence.value,
                        "status_msg" to presence.statusMessage
                    ))
                }
                response.status.isSuccess()
            } catch (e: Exception) {
                logger.error(e) { "Failed to set presence" }
                false
            }
        }
    }

    /**
     * Get user presence
     */
    suspend fun getPresence(userId: String): UserPresence? {
        if (!config.enabled || !config.enablePresence) return null

        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get(
                    "${config.homeserverUrl}/_matrix/client/v3/presence/$userId/status"
                ) {
                    header("Authorization", "Bearer ${config.adminAccessToken}")
                }

                if (response.status.isSuccess()) {
                    val presenceResponse: PresenceResponse = response.body()
                    UserPresence(
                        value = presenceResponse.presence,
                        statusMessage = presenceResponse.statusMsg,
                        lastActiveAgo = presenceResponse.lastActiveAgo
                    )
                } else null
            } catch (e: Exception) {
                logger.error(e) { "Failed to get presence for $userId" }
                null
            }
        }
    }

    /**
     * Get room members
     */
    suspend fun getRoomMembers(roomId: String): List<RoomMember> {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get(
                    "${config.homeserverUrl}/_matrix/client/v3/rooms/$roomId/members"
                ) {
                    header("Authorization", "Bearer ${config.adminAccessToken}")
                }

                if (response.status.isSuccess()) {
                    val membersResponse: RoomMembersResponse = response.body()
                    membersResponse.chunk.mapNotNull { event ->
                        if (event.content.membership == "join") {
                            RoomMember(
                                userId = event.stateKey,
                                displayName = event.content.displayname,
                                avatarUrl = event.content.avatarUrl
                            )
                        } else null
                    }
                } else emptyList()
            } catch (e: Exception) {
                logger.error(e) { "Failed to get room members" }
                emptyList()
            }
        }
    }

    /**
     * Sync user profile to Matrix
     */
    suspend fun syncUserProfile(
        userToken: String,
        userId: String,
        displayName: String?,
        avatarUrl: String?
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var success = true

            displayName?.let {
                try {
                    httpClient.put(
                        "${config.homeserverUrl}/_matrix/client/v3/profile/$userId/displayname"
                    ) {
                        header("Authorization", "Bearer $userToken")
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("displayname" to it))
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to update display name" }
                    success = false
                }
            }

            avatarUrl?.let {
                try {
                    httpClient.put(
                        "${config.homeserverUrl}/_matrix/client/v3/profile/$userId/avatar_url"
                    ) {
                        header("Authorization", "Bearer $userToken")
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("avatar_url" to it))
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to update avatar" }
                    success = false
                }
            }

            success
        }
    }

    /**
     * Get org room ID
     */
    fun getOrgRoomId(orgId: String): String? = orgRooms[orgId]

    /**
     * Get user session
     */
    fun getUserSession(username: String): MatrixUserSession? = userSessions[username]

    /**
     * Shutdown service
     */
    fun shutdown() {
        scope.cancel()
        httpClient.close()
    }
}

// Data classes for Matrix API

@Serializable
data class MatrixLoginRequest(
    val type: String,
    val identifier: MatrixUserIdentifier,
    val password: String,
    val deviceId: String? = null
)

@Serializable
data class MatrixUserIdentifier(
    val type: String,
    val user: String
)

@Serializable
data class MatrixLoginResponse(
    val userId: String,
    val accessToken: String,
    val deviceId: String
)

@Serializable
data class CreateRoomRequest(
    val name: String? = null,
    val roomAliasName: String? = null,
    val topic: String? = null,
    val preset: String? = null,
    val visibility: String? = null,
    val invite: List<String>? = null,
    val isDirect: Boolean? = null,
    val initialState: List<MatrixStateEvent>? = null
)

@Serializable
data class MatrixStateEvent(
    val type: String,
    val content: Map<String, String>
)

@Serializable
data class CreateRoomResponse(
    val roomId: String
)

@Serializable
data class PresenceResponse(
    val presence: String,
    val statusMsg: String? = null,
    val lastActiveAgo: Long? = null
)

@Serializable
data class RoomMembersResponse(
    val chunk: List<MemberEvent>
)

@Serializable
data class MemberEvent(
    val stateKey: String,
    val content: MemberContent
)

@Serializable
data class MemberContent(
    val membership: String,
    val displayname: String? = null,
    val avatarUrl: String? = null
)

data class MatrixUserSession(
    val userId: String,
    val accessToken: String,
    val deviceId: String
)

data class UserPresence(
    val value: String,
    val statusMessage: String? = null,
    val lastActiveAgo: Long? = null
)

data class RoomMember(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?
)
