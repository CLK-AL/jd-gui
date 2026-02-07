/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.server.sync

import com.google.common.collect.MapDifference
import com.google.common.collect.Maps
import ezvcard.VCard
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import al.clk.gui.server.auth.Organization
import al.clk.gui.server.auth.User
import al.clk.gui.server.auth.VCardOrgManager
import al.clk.gui.server.bedework.BedeworkService
import al.clk.gui.server.matrix.MatrixSocialService
import al.clk.gui.server.xmpp.OpenfireOrgManager
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

private val logger = KotlinLogging.logger {}

/**
 * VCard change tracker using Guava MapDifference
 */
private val differencer = VCardDifferencer()

/**
 * Unified vCard sync manager that synchronizes changes across all services
 * with local backup when dependent services are offline
 */
class VCardSyncManager(
    private val vcardManager: VCardOrgManager,
    private val openfireManager: OpenfireOrgManager,
    private val matrixService: MatrixSocialService,
    private val bedeworkService: BedeworkService,
    private val basePath: String
) {
    // Pending sync queue for offline operations
    private val pendingSyncQueue = ConcurrentLinkedQueue<PendingSyncOperation>()

    // Service status tracking
    private val serviceStatus = ConcurrentHashMap<SyncService, ServiceStatus>()

    // Sync state tracking
    private val lastSyncState = ConcurrentHashMap<String, SyncState>()

    // Coroutine scope for background operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Retry job
    private var retryJob: Job? = null

    // JSON serializer
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // Backup directory
    private val backupDir = File("$basePath/sync-backup").apply { mkdirs() }
    private val pendingOpsFile = File(backupDir, "pending-operations.json")

    init {
        // Initialize service status
        SyncService.values().forEach { service ->
            serviceStatus[service] = ServiceStatus(service, false, null)
        }

        // Load pending operations from disk
        loadPendingOperations()

        // Start retry job
        startRetryJob()
    }

    /**
     * Sync organization vCard to all services
     */
    suspend fun syncOrganization(org: Organization): SyncResult {
        logger.info { "Syncing organization ${org.name} to all services..." }

        val results = mutableMapOf<SyncService, Boolean>()

        // Always save locally first (primary source of truth)
        val localSuccess = try {
            vcardManager.getOrganizationVCard(org.id) != null
        } catch (e: Exception) {
            logger.error(e) { "Local vCard access failed for org ${org.id}" }
            false
        }
        results[SyncService.LOCAL] = localSuccess

        if (!localSuccess) {
            return SyncResult(
                entityId = org.id,
                entityType = EntityType.ORGANIZATION,
                results = results,
                pendingServices = emptyList(),
                error = "Local vCard not found"
            )
        }

        // Sync to Openfire
        results[SyncService.OPENFIRE] = syncToOpenfire(org)

        // Sync to Bedework CardDAV
        results[SyncService.BEDEWORK] = syncToBedework(org)

        // Update Matrix org room if needed
        results[SyncService.MATRIX] = syncOrgToMatrix(org)

        // Track pending services
        val pendingServices = results
            .filter { !it.value && it.key != SyncService.LOCAL }
            .map { it.key }

        // Queue failed syncs for retry
        pendingServices.forEach { service ->
            queuePendingSync(PendingSyncOperation(
                id = UUID.randomUUID().toString(),
                entityId = org.id,
                entityType = EntityType.ORGANIZATION,
                targetService = service,
                operationType = OperationType.UPSERT,
                timestamp = System.currentTimeMillis(),
                retryCount = 0
            ))
        }

        // Update sync state
        updateSyncState(org.id, EntityType.ORGANIZATION, results)

        return SyncResult(
            entityId = org.id,
            entityType = EntityType.ORGANIZATION,
            results = results,
            pendingServices = pendingServices
        )
    }

    /**
     * Sync user vCard to all services
     */
    suspend fun syncUser(orgId: String, user: User): SyncResult {
        logger.info { "Syncing user ${user.username} to all services..." }

        val results = mutableMapOf<SyncService, Boolean>()

        // Always save locally first
        val localSuccess = try {
            vcardManager.getUserVCard(orgId, user.id) != null
        } catch (e: Exception) {
            logger.error(e) { "Local vCard access failed for user ${user.id}" }
            false
        }
        results[SyncService.LOCAL] = localSuccess

        if (!localSuccess) {
            return SyncResult(
                entityId = user.id,
                entityType = EntityType.USER,
                results = results,
                pendingServices = emptyList(),
                error = "Local vCard not found"
            )
        }

        // Sync to Openfire
        results[SyncService.OPENFIRE] = syncUserToOpenfire(orgId, user)

        // Sync to Bedework CardDAV
        results[SyncService.BEDEWORK] = syncUserToBedework(orgId, user)

        // Update Matrix profile
        results[SyncService.MATRIX] = syncUserToMatrix(user)

        // Track pending services
        val pendingServices = results
            .filter { !it.value && it.key != SyncService.LOCAL }
            .map { it.key }

        // Queue failed syncs for retry
        pendingServices.forEach { service ->
            queuePendingSync(PendingSyncOperation(
                id = UUID.randomUUID().toString(),
                entityId = user.id,
                entityType = EntityType.USER,
                targetService = service,
                operationType = OperationType.UPSERT,
                timestamp = System.currentTimeMillis(),
                retryCount = 0,
                metadata = mapOf("orgId" to orgId)
            ))
        }

        // Update sync state
        updateSyncState(user.id, EntityType.USER, results)

        return SyncResult(
            entityId = user.id,
            entityType = EntityType.USER,
            results = results,
            pendingServices = pendingServices
        )
    }

    /**
     * Delete entity from all services
     */
    suspend fun deleteEntity(entityId: String, entityType: EntityType, orgId: String? = null): SyncResult {
        val results = mutableMapOf<SyncService, Boolean>()

        when (entityType) {
            EntityType.ORGANIZATION -> {
                // Delete from all services
                results[SyncService.LOCAL] = true // Already deleted by caller
                results[SyncService.OPENFIRE] = deleteFromOpenfire(entityId, entityType)
                results[SyncService.BEDEWORK] = deleteFromBedework(entityId, entityType)
                results[SyncService.MATRIX] = deleteFromMatrix(entityId, entityType)
            }
            EntityType.USER -> {
                results[SyncService.LOCAL] = true
                results[SyncService.OPENFIRE] = deleteFromOpenfire(entityId, entityType, orgId)
                results[SyncService.BEDEWORK] = deleteFromBedework(entityId, entityType)
                results[SyncService.MATRIX] = deleteFromMatrix(entityId, entityType)
            }
        }

        val pendingServices = results
            .filter { !it.value && it.key != SyncService.LOCAL }
            .map { it.key }

        pendingServices.forEach { service ->
            queuePendingSync(PendingSyncOperation(
                id = UUID.randomUUID().toString(),
                entityId = entityId,
                entityType = entityType,
                targetService = service,
                operationType = OperationType.DELETE,
                timestamp = System.currentTimeMillis(),
                retryCount = 0,
                metadata = orgId?.let { mapOf("orgId" to it) } ?: emptyMap()
            ))
        }

        return SyncResult(
            entityId = entityId,
            entityType = entityType,
            results = results,
            pendingServices = pendingServices
        )
    }

    /**
     * Sync all entities to all services
     */
    suspend fun syncAll(): FullSyncResult {
        logger.info { "Starting full sync to all services..." }

        var orgsSuccess = 0
        var orgsFailed = 0
        var usersSuccess = 0
        var usersFailed = 0

        val organizations = vcardManager.listOrganizations()
        for (org in organizations) {
            val result = syncOrganization(org)
            if (result.isFullySuccessful()) {
                orgsSuccess++
            } else {
                orgsFailed++
            }

            val users = vcardManager.listUsers(org.id)
            for (user in users) {
                val userResult = syncUser(org.id, user)
                if (userResult.isFullySuccessful()) {
                    usersSuccess++
                } else {
                    usersFailed++
                }
            }
        }

        return FullSyncResult(
            organizationsSynced = orgsSuccess,
            organizationsFailed = orgsFailed,
            usersSynced = usersSuccess,
            usersFailed = usersFailed,
            pendingOperations = pendingSyncQueue.size
        )
    }

    /**
     * Retry pending sync operations
     */
    suspend fun retryPendingOperations(): Int {
        var retried = 0

        val iterator = pendingSyncQueue.iterator()
        while (iterator.hasNext()) {
            val operation = iterator.next()

            if (operation.retryCount >= MAX_RETRIES) {
                logger.warn { "Max retries reached for ${operation.entityType} ${operation.entityId} to ${operation.targetService}" }
                // Keep in queue but mark as failed
                continue
            }

            val success = when (operation.operationType) {
                OperationType.UPSERT -> retryUpsert(operation)
                OperationType.DELETE -> retryDelete(operation)
            }

            if (success) {
                iterator.remove()
                retried++
                logger.info { "Successfully retried sync for ${operation.entityId} to ${operation.targetService}" }
            } else {
                // Increment retry count
                operation.retryCount++
                operation.lastRetryTimestamp = System.currentTimeMillis()
            }
        }

        // Persist pending operations
        savePendingOperations()

        return retried
    }

    /**
     * Get pending operations count
     */
    fun getPendingCount(): Int = pendingSyncQueue.size

    /**
     * Get sync state for entity
     */
    fun getSyncState(entityId: String): SyncState? = lastSyncState[entityId]

    /**
     * Get service status
     */
    fun getServiceStatus(): Map<SyncService, ServiceStatus> = serviceStatus.toMap()

    /**
     * Update service status
     */
    fun updateServiceStatus(service: SyncService, isOnline: Boolean, error: String? = null) {
        serviceStatus[service] = ServiceStatus(service, isOnline, error)
    }

    /**
     * Shutdown sync manager
     */
    fun shutdown() {
        retryJob?.cancel()
        scope.cancel()
        savePendingOperations()
    }

    /**
     * Sync with change detection using Guava MapDifference
     * Compares local vCard with remote and syncs only if changes detected
     */
    suspend fun syncWithChangeDetection(
        orgId: String,
        userId: String,
        remoteVCard: VCard
    ): VCardSyncResult {
        val localVCard = vcardManager.getUserVCard(orgId, userId)
            ?: return VCardSyncResult(
                entityId = userId,
                changeDetected = true,
                changeSummary = null,
                mergeResult = null,
                syncResult = null,
                error = "Local vCard not found"
            )

        // Compute difference using Guava MapDifference
        val diff = differencer.computeDifference(localVCard, remoteVCard)

        if (diff.areEqual) {
            logger.debug { "No changes detected for user $userId" }
            return VCardSyncResult(
                entityId = userId,
                changeDetected = false,
                changeSummary = differencer.createChangeSummary(localVCard, remoteVCard),
                mergeResult = null,
                syncResult = null
            )
        }

        // Create change summary
        val changeSummary = differencer.createChangeSummary(localVCard, remoteVCard)
        logger.info { "Changes detected for user $userId: ${changeSummary.totalChanges} changes" }

        // Merge changes (remote wins for conflicts by default)
        val mergeResult = differencer.mergeRemoteIntoLocal(localVCard, remoteVCard)

        // Save merged vCard locally
        if (mergeResult.hasChanges) {
            val user = vcardManager.listUsers(orgId).find { it.id == userId }
            if (user != null) {
                // Update the local vCard with merged changes
                vcardManager.updateUserFromVCard(orgId, user, mergeResult.merged)
            }
        }

        // Sync to all services
        val user = vcardManager.listUsers(orgId).find { it.id == userId }
        val syncResult = user?.let { syncUser(orgId, it) }

        return VCardSyncResult(
            entityId = userId,
            changeDetected = true,
            changeSummary = changeSummary,
            mergeResult = mergeResult,
            syncResult = syncResult
        )
    }

    /**
     * Bidirectional sync - fetch from remote, merge, and sync back
     */
    suspend fun bidirectionalSync(orgId: String, userId: String): BidirectionalSyncResult {
        logger.info { "Starting bidirectional sync for user $userId" }

        val results = mutableMapOf<SyncService, ServiceSyncDetail>()

        // Get local vCard
        val localVCard = vcardManager.getUserVCard(orgId, userId)
            ?: return BidirectionalSyncResult(
                entityId = userId,
                success = false,
                serviceResults = results,
                error = "Local vCard not found"
            )

        // Fetch and merge from each service
        // 1. Bedework CardDAV
        try {
            val bedeworkVCard = bedeworkService.fetchVCardFromCardDav(userId)
            if (bedeworkVCard != null) {
                val diff = differencer.computeDifference(localVCard, bedeworkVCard)
                results[SyncService.BEDEWORK] = ServiceSyncDetail(
                    fetched = true,
                    hasChanges = !diff.areEqual,
                    changeSummary = differencer.createChangeSummary(localVCard, bedeworkVCard)
                )

                if (!diff.areEqual) {
                    val mergeResult = differencer.mergeRemoteIntoLocal(localVCard, bedeworkVCard)
                    results[SyncService.BEDEWORK] = results[SyncService.BEDEWORK]!!.copy(
                        merged = true,
                        conflicts = mergeResult.conflicts
                    )
                }
            } else {
                results[SyncService.BEDEWORK] = ServiceSyncDetail(fetched = false)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch from Bedework" }
            results[SyncService.BEDEWORK] = ServiceSyncDetail(fetched = false, error = e.message)
        }

        // 2. Openfire XMPP vCard
        try {
            val openfireVCard = openfireManager.loadVCardFromOpenfire(userId)
            if (openfireVCard != null) {
                // Convert Smack vCard to ez-vcard for comparison
                val openfireVCardString = openfireManager.smackVCardToString(openfireVCard)
                val openfireEzVCard = differencer.parseVCard(openfireVCardString)

                if (openfireEzVCard != null) {
                    val diff = differencer.computeDifference(localVCard, openfireEzVCard)
                    results[SyncService.OPENFIRE] = ServiceSyncDetail(
                        fetched = true,
                        hasChanges = !diff.areEqual,
                        changeSummary = differencer.createChangeSummary(localVCard, openfireEzVCard)
                    )
                }
            } else {
                results[SyncService.OPENFIRE] = ServiceSyncDetail(fetched = false)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch from Openfire" }
            results[SyncService.OPENFIRE] = ServiceSyncDetail(fetched = false, error = e.message)
        }

        // Push local to all services
        val user = vcardManager.listUsers(orgId).find { it.id == userId }
        val syncResult = user?.let { syncUser(orgId, it) }

        return BidirectionalSyncResult(
            entityId = userId,
            success = syncResult?.isPartiallySuccessful() ?: false,
            serviceResults = results,
            finalSyncResult = syncResult
        )
    }

    /**
     * Get change summary between local and a specific service
     */
    suspend fun getChangeSummary(
        orgId: String,
        userId: String,
        targetService: SyncService
    ): ChangeSummary? {
        val localVCard = vcardManager.getUserVCard(orgId, userId) ?: return null

        return when (targetService) {
            SyncService.BEDEWORK -> {
                val remoteVCard = bedeworkService.fetchVCardFromCardDav(userId)
                remoteVCard?.let { differencer.createChangeSummary(localVCard, it) }
            }
            SyncService.OPENFIRE -> {
                val openfireVCard = openfireManager.loadVCardFromOpenfire(userId)
                if (openfireVCard != null) {
                    val openfireVCardString = openfireManager.smackVCardToString(openfireVCard)
                    val openfireEzVCard = differencer.parseVCard(openfireVCardString)
                    openfireEzVCard?.let { differencer.createChangeSummary(localVCard, it) }
                } else null
            }
            else -> null
        }
    }

    // Private helper methods

    private suspend fun syncToOpenfire(org: Organization): Boolean {
        return try {
            if (!openfireManager.isConnected()) {
                updateServiceStatus(SyncService.OPENFIRE, false, "Not connected")
                false
            } else {
                val result = openfireManager.syncOrganization(org)
                updateServiceStatus(SyncService.OPENFIRE, true)
                result
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync org to Openfire" }
            updateServiceStatus(SyncService.OPENFIRE, false, e.message)
            false
        }
    }

    private suspend fun syncUserToOpenfire(orgId: String, user: User): Boolean {
        return try {
            if (!openfireManager.isConnected()) {
                updateServiceStatus(SyncService.OPENFIRE, false, "Not connected")
                false
            } else {
                val result = openfireManager.syncUser(orgId, user)
                updateServiceStatus(SyncService.OPENFIRE, true)
                result
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync user to Openfire" }
            updateServiceStatus(SyncService.OPENFIRE, false, e.message)
            false
        }
    }

    private suspend fun syncToBedework(org: Organization): Boolean {
        return try {
            val result = bedeworkService.syncOrgVCardToCardDav(org)
            updateServiceStatus(SyncService.BEDEWORK, result)
            result
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync org to Bedework" }
            updateServiceStatus(SyncService.BEDEWORK, false, e.message)
            false
        }
    }

    private suspend fun syncUserToBedework(orgId: String, user: User): Boolean {
        return try {
            val result = bedeworkService.syncVCardToCardDav(orgId, user.id)
            updateServiceStatus(SyncService.BEDEWORK, result)
            result
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync user to Bedework" }
            updateServiceStatus(SyncService.BEDEWORK, false, e.message)
            false
        }
    }

    private suspend fun syncOrgToMatrix(org: Organization): Boolean {
        // Matrix doesn't store org vCards directly, but we can update room info
        return try {
            val roomId = matrixService.getOrgRoomId(org.id)
            if (roomId == null) {
                // Create org room if it doesn't exist
                matrixService.createOrgRoom(org.id, org.name, "") != null
            } else {
                true // Room exists
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync org to Matrix" }
            false
        }
    }

    private suspend fun syncUserToMatrix(user: User): Boolean {
        // Matrix profile sync would go here
        return true // Matrix user profiles are updated separately
    }

    private suspend fun deleteFromOpenfire(entityId: String, entityType: EntityType, orgId: String? = null): Boolean {
        return try {
            if (!openfireManager.isConnected()) return false
            // Openfire deletion logic
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete from Openfire" }
            false
        }
    }

    private suspend fun deleteFromBedework(entityId: String, entityType: EntityType): Boolean {
        return try {
            bedeworkService.deleteVCard(entityId)
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete from Bedework" }
            false
        }
    }

    private suspend fun deleteFromMatrix(entityId: String, entityType: EntityType): Boolean {
        // Matrix doesn't delete users, just remove from rooms
        return true
    }

    private suspend fun retryUpsert(operation: PendingSyncOperation): Boolean {
        return when (operation.targetService) {
            SyncService.OPENFIRE -> {
                when (operation.entityType) {
                    EntityType.ORGANIZATION -> {
                        val org = vcardManager.listOrganizations().find { it.id == operation.entityId }
                        org?.let { syncToOpenfire(it) } ?: false
                    }
                    EntityType.USER -> {
                        val orgId = operation.metadata["orgId"] ?: return false
                        val user = vcardManager.listUsers(orgId).find { it.id == operation.entityId }
                        user?.let { syncUserToOpenfire(orgId, it) } ?: false
                    }
                }
            }
            SyncService.BEDEWORK -> {
                when (operation.entityType) {
                    EntityType.ORGANIZATION -> {
                        val org = vcardManager.listOrganizations().find { it.id == operation.entityId }
                        org?.let { syncToBedework(it) } ?: false
                    }
                    EntityType.USER -> {
                        val orgId = operation.metadata["orgId"] ?: return false
                        val user = vcardManager.listUsers(orgId).find { it.id == operation.entityId }
                        user?.let { syncUserToBedework(orgId, it) } ?: false
                    }
                }
            }
            SyncService.MATRIX -> {
                when (operation.entityType) {
                    EntityType.ORGANIZATION -> {
                        val org = vcardManager.listOrganizations().find { it.id == operation.entityId }
                        org?.let { syncOrgToMatrix(it) } ?: false
                    }
                    EntityType.USER -> {
                        val user = vcardManager.listUsers(operation.metadata["orgId"] ?: "")
                            .find { it.id == operation.entityId }
                        user?.let { syncUserToMatrix(it) } ?: false
                    }
                }
            }
            SyncService.LOCAL -> true // Local is always primary
        }
    }

    private suspend fun retryDelete(operation: PendingSyncOperation): Boolean {
        return when (operation.targetService) {
            SyncService.OPENFIRE -> deleteFromOpenfire(operation.entityId, operation.entityType, operation.metadata["orgId"])
            SyncService.BEDEWORK -> deleteFromBedework(operation.entityId, operation.entityType)
            SyncService.MATRIX -> deleteFromMatrix(operation.entityId, operation.entityType)
            SyncService.LOCAL -> true
        }
    }

    private fun queuePendingSync(operation: PendingSyncOperation) {
        pendingSyncQueue.add(operation)
        savePendingOperations()
        logger.debug { "Queued pending sync: ${operation.entityType} ${operation.entityId} to ${operation.targetService}" }
    }

    private fun updateSyncState(entityId: String, entityType: EntityType, results: Map<SyncService, Boolean>) {
        lastSyncState[entityId] = SyncState(
            entityId = entityId,
            entityType = entityType,
            lastSyncTimestamp = System.currentTimeMillis(),
            serviceStates = results.mapValues { (service, success) ->
                ServiceSyncState(service, success, System.currentTimeMillis())
            }
        )
    }

    private fun startRetryJob() {
        retryJob = scope.launch {
            while (isActive) {
                delay(RETRY_INTERVAL_MS)
                if (pendingSyncQueue.isNotEmpty()) {
                    logger.info { "Retrying ${pendingSyncQueue.size} pending sync operations..." }
                    val retried = retryPendingOperations()
                    logger.info { "Retried $retried operations successfully" }
                }
            }
        }
    }

    private fun savePendingOperations() {
        try {
            val operations = pendingSyncQueue.map { it.toSerializable() }
            pendingOpsFile.writeText(json.encodeToString(operations))
        } catch (e: Exception) {
            logger.error(e) { "Failed to save pending operations" }
        }
    }

    private fun loadPendingOperations() {
        try {
            if (pendingOpsFile.exists()) {
                val operations = json.decodeFromString<List<SerializablePendingSyncOperation>>(
                    pendingOpsFile.readText()
                )
                operations.forEach { op ->
                    pendingSyncQueue.add(op.toPendingSyncOperation())
                }
                logger.info { "Loaded ${operations.size} pending sync operations from backup" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load pending operations" }
        }
    }

    companion object {
        private const val MAX_RETRIES = 10
        private const val RETRY_INTERVAL_MS = 60_000L // 1 minute
    }
}

// Enums and data classes

enum class SyncService {
    LOCAL,
    OPENFIRE,
    MATRIX,
    BEDEWORK
}

enum class EntityType {
    ORGANIZATION,
    USER
}

enum class OperationType {
    UPSERT,
    DELETE
}

data class PendingSyncOperation(
    val id: String,
    val entityId: String,
    val entityType: EntityType,
    val targetService: SyncService,
    val operationType: OperationType,
    val timestamp: Long,
    var retryCount: Int = 0,
    var lastRetryTimestamp: Long? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toSerializable() = SerializablePendingSyncOperation(
        id, entityId, entityType.name, targetService.name,
        operationType.name, timestamp, retryCount, lastRetryTimestamp, metadata
    )
}

@Serializable
data class SerializablePendingSyncOperation(
    val id: String,
    val entityId: String,
    val entityType: String,
    val targetService: String,
    val operationType: String,
    val timestamp: Long,
    val retryCount: Int,
    val lastRetryTimestamp: Long?,
    val metadata: Map<String, String>
) {
    fun toPendingSyncOperation() = PendingSyncOperation(
        id, entityId, EntityType.valueOf(entityType),
        SyncService.valueOf(targetService), OperationType.valueOf(operationType),
        timestamp, retryCount, lastRetryTimestamp, metadata
    )
}

data class ServiceStatus(
    val service: SyncService,
    val isOnline: Boolean,
    val lastError: String?
)

data class SyncState(
    val entityId: String,
    val entityType: EntityType,
    val lastSyncTimestamp: Long,
    val serviceStates: Map<SyncService, ServiceSyncState>
)

data class ServiceSyncState(
    val service: SyncService,
    val success: Boolean,
    val timestamp: Long
)

data class SyncResult(
    val entityId: String,
    val entityType: EntityType,
    val results: Map<SyncService, Boolean>,
    val pendingServices: List<SyncService>,
    val error: String? = null
) {
    fun isFullySuccessful() = results.all { it.value }
    fun isPartiallySuccessful() = results.any { it.value }
}

data class FullSyncResult(
    val organizationsSynced: Int,
    val organizationsFailed: Int,
    val usersSynced: Int,
    val usersFailed: Int,
    val pendingOperations: Int
)

/**
 * VCard sync result with change detection
 */
data class VCardSyncResult(
    val entityId: String,
    val changeDetected: Boolean,
    val changeSummary: ChangeSummary?,
    val mergeResult: MergeResult?,
    val syncResult: SyncResult?,
    val error: String? = null
)

/**
 * Bidirectional sync result
 */
data class BidirectionalSyncResult(
    val entityId: String,
    val success: Boolean,
    val serviceResults: Map<SyncService, ServiceSyncDetail>,
    val finalSyncResult: SyncResult? = null,
    val error: String? = null
)

/**
 * Detail of sync with a specific service
 */
data class ServiceSyncDetail(
    val fetched: Boolean,
    val hasChanges: Boolean = false,
    val changeSummary: ChangeSummary? = null,
    val merged: Boolean = false,
    val conflicts: List<String> = emptyList(),
    val error: String? = null
)
