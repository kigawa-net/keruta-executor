package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Service for communicating with Coder server to manage workspaces.
 */
@Service
open class CoderWorkspaceService(
    private val restTemplate: RestTemplate,
    private val properties: KerutaExecutorProperties,
    private val coderTemplateService: CoderTemplateService
) {
    private val logger = LoggerFactory.getLogger(CoderWorkspaceService::class.java)

    /**
     * Gets all workspaces from the Coder server.
     */
    fun getAllWorkspaces(): List<CoderWorkspaceDto> {
        logger.info("Fetching all workspaces from Coder server: ${properties.coder.baseUrl}")

        return try {
            tryFetchWorkspaces()
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401) {
                logger.warn("Authentication failed while fetching workspaces, attempting with refresh")
                return tryFetchWorkspacesWithRefresh()
            } else {
                logger.error("Failed to fetch workspaces from Coder API, returning empty list", e)
                return emptyList()
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch workspaces from Coder API, returning empty list", e)
            return emptyList()
        }
    }

    /**
     * Gets workspaces filtered by session ID.
     */
    fun getWorkspacesBySessionId(sessionId: String): List<CoderWorkspaceDto> {
        logger.info("Fetching workspaces for session: {}", sessionId)

        // Get all workspaces and filter by session ID in the name or tags
        return getAllWorkspaces().filter { workspace ->
            workspace.name.contains(sessionId) || workspace.templateName.contains("session-$sessionId") ||
                // Check if workspace name follows the pattern: session-{sessionId}-{suffix}
                workspace.name.startsWith("session-$sessionId")
        }
    }

    /**
     * Gets a specific workspace by ID.
     */
    fun getWorkspace(id: String): CoderWorkspaceDto? {
        logger.info("Fetching workspace: {}", id)

        return try {
            val url = "${properties.coder.baseUrl}/api/v2/workspaces/$id"
            val headers = createAuthHeaders()
            val entity = HttpEntity<String>(headers)

            val response = restTemplate.exchange(url, HttpMethod.GET, entity, CoderWorkspaceApiResponse::class.java)
            response.body?.toDto()
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 404) {
                logger.warn("Workspace not found: {}", id)
                null
            } else if (e.statusCode.value() == 401) {
                logger.warn("Authentication failed while fetching workspace, attempting with refresh")
                tryGetWorkspaceWithRefresh(id)
            } else {
                logger.error("Failed to fetch workspace: {}", id, e)
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch workspace: {}", id, e)
            null
        }
    }

    /**
     * Creates a new workspace.
     */
    fun createWorkspace(request: CreateCoderWorkspaceRequest): CoderWorkspaceDto {
        logger.info("Creating workspace: {}", request.name)

        return try {
            // Use the correct Coder API v2 endpoint based on official documentation
            // POST /users/{user}/workspaces (using "me" for current user)
            val url = "${properties.coder.baseUrl}/api/v2/users/me/workspaces"
            val headers = createAuthHeaders()
            headers.set("Content-Type", "application/json")

            val createRequest = mapOf(
                "name" to request.name,
                "template_id" to request.templateId,
                "rich_parameter_values" to (request.richParameterValues ?: emptyList<Any>())
            )

            logger.info("Sending workspace creation request to URL: {} with data: {}", url, createRequest)

            val entity = HttpEntity(createRequest, headers)
            val response = restTemplate.exchange(url, HttpMethod.POST, entity, CoderWorkspaceApiResponse::class.java)

            response.body?.toDto() ?: throw RuntimeException("Failed to create workspace - no response body")
        } catch (e: HttpClientErrorException) {
            logger.error(
                "HTTP error creating workspace: {} - Status: {}, Body: {}",
                request.name,
                e.statusCode,
                e.responseBodyAsString
            )
            if (e.statusCode.value() == 401) {
                logger.warn("Authentication failed while creating workspace, attempting with refresh")
                tryCreateWorkspaceWithRefresh(request)
            } else if (e.statusCode.value() == 409) {
                logger.warn("Workspace '{}' already exists, attempting to fetch existing workspace", request.name)
                logger.debug("409 Conflict response body: {}", e.responseBodyAsString)

                // Try to find and return the existing workspace
                val existingWorkspace = findExistingWorkspaceByName(request.name)
                if (existingWorkspace != null) {
                    logger.info(
                        "Successfully found existing workspace: name={} id={}",
                        existingWorkspace.name,
                        existingWorkspace.id
                    )
                    return existingWorkspace
                } else {
                    logger.error(
                        "Could not find existing workspace '{}' despite 409 conflict - API inconsistency",
                        request.name
                    )

                    // Debug: List all workspaces to understand the issue
                    try {
                        val allWorkspaces = getAllWorkspaces()
                        logger.debug("Total workspaces found: {}", allWorkspaces.size)
                        allWorkspaces.take(5).forEach { ws ->
                            logger.debug("  - Workspace: name='{}' id='{}'", ws.name, ws.id)
                        }

                        // Try case-insensitive search as fallback
                        val caseInsensitiveMatch = allWorkspaces.find {
                            it.name.equals(request.name, ignoreCase = true)
                        }
                        if (caseInsensitiveMatch != null) {
                            logger.warn(
                                "Found workspace with case-insensitive match: '{}' vs '{}'",
                                caseInsensitiveMatch.name,
                                request.name
                            )

                            // Try to delete and recreate if exact match is needed
                            logger.info(
                                "Attempting to delete existing workspace '{}' and recreate with correct name",
                                caseInsensitiveMatch.name
                            )
                            try {
                                if (deleteWorkspace(caseInsensitiveMatch.id)) {
                                    logger.info("Successfully deleted existing workspace, retrying creation")
                                    Thread.sleep(2000) // Wait for deletion to complete
                                    return createWorkspace(request) // Recursive retry
                                } else {
                                    logger.error("Failed to delete existing workspace")
                                }
                            } catch (deleteEx: Exception) {
                                logger.error("Error during workspace deletion and recreation", deleteEx)
                            }
                        }
                    } catch (ex: Exception) {
                        logger.warn("Failed to list workspaces for debugging", ex)
                    }

                    throw RuntimeException("Workspace '${request.name}' already exists but could not be retrieved", e)
                }
            } else if (e.statusCode.value() == 405 || e.statusCode.value() == 404) {
                logger.error("Endpoint not found or method not allowed - trying alternative endpoint")
                // Try alternative endpoint formats
                tryCreateWorkspaceAlternativeEndpoint(request)
            } else {
                throw e
            }
        } catch (e: Exception) {
            logger.error("Failed to create workspace: {}", request.name, e)
            throw e
        }
    }

    /**
     * Starts a workspace.
     */
    fun startWorkspace(id: String): CoderWorkspaceDto? {
        logger.info("Starting workspace: {}", id)

        return try {
            val url = "${properties.coder.baseUrl}/api/v2/workspaces/$id/builds"
            val headers = createAuthHeaders()
            headers.set("Content-Type", "application/json")

            val startRequest = mapOf(
                "transition" to "start"
            )

            val entity = HttpEntity(startRequest, headers)
            restTemplate.exchange(url, HttpMethod.POST, entity, Any::class.java)

            // Return updated workspace status
            getWorkspace(id)
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401) {
                logger.warn("Authentication failed while starting workspace, attempting with refresh")
                tryStartWorkspaceWithRefresh(id)
            } else {
                logger.error("Failed to start workspace: {}", id, e)
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to start workspace: {}", id, e)
            null
        }
    }

    /**
     * Stops a workspace.
     */
    fun stopWorkspace(id: String): CoderWorkspaceDto? {
        logger.info("Stopping workspace: {}", id)

        return try {
            val url = "${properties.coder.baseUrl}/api/v2/workspaces/$id/builds"
            val headers = createAuthHeaders()
            headers.set("Content-Type", "application/json")

            val stopRequest = mapOf(
                "transition" to "stop"
            )

            val entity = HttpEntity(stopRequest, headers)
            restTemplate.exchange(url, HttpMethod.POST, entity, Any::class.java)

            // Return updated workspace status
            getWorkspace(id)
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401) {
                logger.warn("Authentication failed while stopping workspace, attempting with refresh")
                tryStopWorkspaceWithRefresh(id)
            } else {
                logger.error("Failed to stop workspace: {}", id, e)
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to stop workspace: {}", id, e)
            null
        }
    }

    /**
     * Deletes a workspace.
     */
    fun deleteWorkspace(id: String): Boolean {
        logger.info("Deleting workspace: {}", id)

        return try {
            val url = "${properties.coder.baseUrl}/api/v2/workspaces/$id"
            val headers = createAuthHeaders()
            val entity = HttpEntity<String>(headers)

            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void::class.java)
            true
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 404) {
                logger.warn("Workspace not found for deletion: {}", id)
                false
            } else if (e.statusCode.value() == 401) {
                logger.warn("Authentication failed while deleting workspace, attempting with refresh")
                tryDeleteWorkspaceWithRefresh(id)
            } else {
                logger.error("Failed to delete workspace: {}", id, e)
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to delete workspace: {}", id, e)
            false
        }
    }

    /**
     * Gets available workspace templates.
     */
    fun getWorkspaceTemplates(): List<CoderTemplateDto> {
        return coderTemplateService.getCoderTemplates()
    }

    /**
     * Finds an existing workspace by name.
     */
    private fun findExistingWorkspaceByName(workspaceName: String): CoderWorkspaceDto? {
        logger.info("Searching for existing workspace with name: '{}'", workspaceName)

        return try {
            val workspaces = getAllWorkspaces()
            logger.debug("Retrieved {} total workspaces from Coder API", workspaces.size)

            // Debug: Log first few workspace names for comparison
            workspaces.take(3).forEach { ws ->
                logger.debug("  - Found workspace: name='{}' (exact match: {})", ws.name, ws.name == workspaceName)
            }

            val matchingWorkspace = workspaces.find { it.name == workspaceName }

            if (matchingWorkspace != null) {
                logger.info(
                    "Successfully found existing workspace: name='{}' id='{}'",
                    matchingWorkspace.name,
                    matchingWorkspace.id
                )
            } else {
                logger.warn("No existing workspace found with exact name: '{}'. Available workspaces:", workspaceName)
                workspaces.take(5).forEach { ws ->
                    logger.warn("  - Available: name='{}' id='{}'", ws.name, ws.id)
                }
            }

            matchingWorkspace
        } catch (e: Exception) {
            logger.error("Failed to search for existing workspace: '{}'", workspaceName, e)
            null
        }
    }

    // Private helper methods with authentication refresh

    private fun tryFetchWorkspaces(): List<CoderWorkspaceDto> {
        val url = "${properties.coder.baseUrl}/api/v2/workspaces"
        val headers = createAuthHeaders()
        val entity = HttpEntity<String>(headers)

        // Start with paginated format since it's working correctly
        try {
            val paginatedResponse = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                CoderPaginatedWorkspacesResponse::class.java
            )
            val apiWorkspaces = paginatedResponse.body?.workspaces ?: emptyList()
            logger.info("Successfully fetched {} workspaces from paginated response", apiWorkspaces.size)
            return apiWorkspaces.map { it.toDto() }
        } catch (e: Exception) {
            logger.warn("Failed to parse as paginated format, trying wrapped object", e)
            // Try to get as a wrapped object (some Coder versions wrap in { "workspaces": [...] })
            try {
                val wrappedResponse = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    CoderWorkspacesWrapperResponse::class.java
                )
                val apiWorkspaces = wrappedResponse.body?.workspaces ?: emptyList()
                logger.info("Successfully fetched {} workspaces from wrapped response", apiWorkspaces.size)
                return apiWorkspaces.map { it.toDto() }
            } catch (e2: Exception) {
                logger.warn("Failed to parse as wrapped object, trying direct list", e2)
                // Last try: direct array format
                try {
                    val typeReference = object : ParameterizedTypeReference<List<CoderWorkspaceApiResponse>>() {}
                    val response = restTemplate.exchange(url, HttpMethod.GET, entity, typeReference)
                    val apiWorkspaces = response.body ?: emptyList()
                    logger.info("Successfully fetched {} workspaces from direct list", apiWorkspaces.size)
                    return apiWorkspaces.map { it.toDto() }
                } catch (e3: Exception) {
                    logger.error("Failed to parse workspaces response in all formats", e3)
                    // Log the raw response for debugging
                    try {
                        val rawResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String::class.java)
                        logger.error("Raw Coder API response: {}", rawResponse.body)
                    } catch (e4: Exception) {
                        logger.error("Failed to get raw response", e4)
                    }
                    return emptyList()
                }
            }
        }
    }

    private fun tryFetchWorkspacesWithRefresh(): List<CoderWorkspaceDto> {
        return try {
            coderTemplateService.scheduledTokenRefresh() // Reuse token refresh logic
            tryFetchWorkspaces()
        } catch (e: Exception) {
            logger.error("Failed to fetch workspaces after token refresh", e)
            emptyList()
        }
    }

    private fun tryGetWorkspaceWithRefresh(id: String): CoderWorkspaceDto? {
        return try {
            coderTemplateService.scheduledTokenRefresh() // Reuse token refresh logic
            getWorkspace(id)
        } catch (e: Exception) {
            logger.error("Failed to fetch workspace after token refresh: {}", id, e)
            null
        }
    }

    private fun tryCreateWorkspaceWithRefresh(request: CreateCoderWorkspaceRequest): CoderWorkspaceDto {
        coderTemplateService.scheduledTokenRefresh() // Reuse token refresh logic
        return createWorkspace(request)
    }

    private fun tryCreateWorkspaceAlternativeEndpoint(request: CreateCoderWorkspaceRequest): CoderWorkspaceDto {
        logger.info("Trying alternative endpoints for workspace creation: {}", request.name)

        // Try organization-based endpoint first
        try {
            val orgUrl = "${properties.coder.baseUrl}/api/v2/organizations/default/members/me/workspaces"
            val headers = createAuthHeaders()
            headers.set("Content-Type", "application/json")

            val createRequest = mapOf(
                "name" to request.name,
                "template_id" to request.templateId,
                "rich_parameter_values" to (request.richParameterValues ?: emptyList<Any>())
            )

            logger.info("Trying organization-based endpoint: {} with data: {}", orgUrl, createRequest)

            val entity = HttpEntity(createRequest, headers)
            val response = restTemplate.exchange(orgUrl, HttpMethod.POST, entity, CoderWorkspaceApiResponse::class.java)

            return response.body?.toDto() ?: throw RuntimeException(
                "Failed to create workspace via organization endpoint - no response body"
            )
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 409) {
                logger.warn(
                    "Workspace '{}' already exists at organization endpoint, attempting to fetch existing workspace",
                    request.name
                )
                val existingWorkspace = findExistingWorkspaceByName(request.name)
                if (existingWorkspace != null) {
                    logger.info(
                        "Found existing workspace via organization endpoint: name={} id={}",
                        existingWorkspace.name,
                        existingWorkspace.id
                    )
                    return existingWorkspace
                }
            }
            logger.warn(
                "Organization endpoint failed: {} - Status: {}, trying legacy endpoint",
                request.name,
                e.statusCode
            )
        }

        // Try legacy endpoint as last resort
        return try {
            val legacyUrl = "${properties.coder.baseUrl}/api/v2/workspaces"
            val headers = createAuthHeaders()
            headers.set("Content-Type", "application/json")

            val createRequest = mapOf(
                "name" to request.name,
                "template_id" to request.templateId,
                "rich_parameter_values" to (request.richParameterValues ?: emptyList<Any>())
            )

            logger.info("Trying legacy endpoint: {} with data: {}", legacyUrl, createRequest)

            val entity = HttpEntity(createRequest, headers)
            val response = restTemplate.exchange(
                legacyUrl,
                HttpMethod.POST,
                entity,
                CoderWorkspaceApiResponse::class.java
            )

            response.body?.toDto() ?: throw RuntimeException(
                "Failed to create workspace via legacy endpoint - no response body"
            )
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 409) {
                logger.warn(
                    "Workspace '{}' already exists at legacy endpoint, attempting to fetch existing workspace",
                    request.name
                )
                val existingWorkspace = findExistingWorkspaceByName(request.name)
                if (existingWorkspace != null) {
                    logger.info(
                        "Found existing workspace via legacy endpoint: name={} id={}",
                        existingWorkspace.name,
                        existingWorkspace.id
                    )
                    return existingWorkspace
                }
            }
            logger.error(
                "All alternative endpoints failed: {} - Status: {}, Body: {}",
                request.name,
                e.statusCode,
                e.responseBodyAsString
            )
            throw e
        }
    }

    private fun tryStartWorkspaceWithRefresh(id: String): CoderWorkspaceDto? {
        return try {
            coderTemplateService.scheduledTokenRefresh() // Reuse token refresh logic
            startWorkspace(id)
        } catch (e: Exception) {
            logger.error("Failed to start workspace after token refresh: {}", id, e)
            null
        }
    }

    private fun tryStopWorkspaceWithRefresh(id: String): CoderWorkspaceDto? {
        return try {
            coderTemplateService.scheduledTokenRefresh() // Reuse token refresh logic
            stopWorkspace(id)
        } catch (e: Exception) {
            logger.error("Failed to stop workspace after token refresh: {}", id, e)
            null
        }
    }

    private fun tryDeleteWorkspaceWithRefresh(id: String): Boolean {
        return try {
            coderTemplateService.scheduledTokenRefresh() // Reuse token refresh logic
            deleteWorkspace(id)
        } catch (e: Exception) {
            logger.error("Failed to delete workspace after token refresh: {}", id, e)
            false
        }
    }

    private fun createAuthHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        val token = properties.coder.token
        token?.let {
            headers.set("Coder-Session-Token", it)
        }
        headers.set("Accept", "application/json")
        return headers
    }
}

/**
 * DTO for Coder workspace data.
 */
data class CoderWorkspaceDto(
    val id: String,
    val name: String,
    val ownerId: String,
    val ownerName: String,
    val templateId: String,
    val templateName: String,
    val templateDisplayName: String,
    val templateIcon: String,
    val status: String,
    val health: String,
    val accessUrl: String,
    val autoStart: Boolean,
    val autoStop: Boolean,
    val lastUsedAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/**
 * Request for creating a new Coder workspace.
 */
data class CreateCoderWorkspaceRequest(
    val name: String,
    val templateId: String,
    val richParameterValues: List<Any>? = null
)

/**
 * Response from Coder API for workspace data.
 */
data class CoderWorkspaceApiResponse(
    val id: String,
    val name: String,
    val owner_id: String?,
    val owner_name: String?,
    val template_id: String?,
    val template_name: String?,
    val template_display_name: String?,
    val template_icon: String?,
    val latest_build: LatestBuildApiResponse?,
    val autostart_schedule: String?,
    val ttl_ms: Long?,
    val last_used_at: String?,
    val created_at: String?,
    val updated_at: String?
) {
    fun toDto(): CoderWorkspaceDto {
        return CoderWorkspaceDto(
            id = id,
            name = name,
            ownerId = owner_id ?: "unknown",
            ownerName = owner_name ?: "unknown",
            templateId = template_id ?: "unknown",
            templateName = template_name ?: "unknown",
            templateDisplayName = template_display_name ?: template_name ?: "Unknown Template",
            templateIcon = template_icon ?: "/icon/default.svg",
            status = latest_build?.status ?: "unknown",
            health = latest_build?.resources?.firstOrNull()?.health ?: "unknown",
            accessUrl = "https://coder.example.com/workspaces/$name", // Placeholder
            autoStart = autostart_schedule != null,
            autoStop = ttl_ms != null && ttl_ms > 0,
            lastUsedAt = parseDateTime(last_used_at),
            createdAt = parseDateTime(created_at),
            updatedAt = parseDateTime(updated_at)
        )
    }

    private fun parseDateTime(dateTimeString: String?): LocalDateTime {
        return try {
            if (dateTimeString != null) {
                Instant.parse(dateTimeString).atZone(ZoneId.systemDefault()).toLocalDateTime()
            } else {
                LocalDateTime.now()
            }
        } catch (e: Exception) {
            LocalDateTime.now()
        }
    }
}

data class LatestBuildApiResponse(
    val status: String?,
    val resources: List<ResourceApiResponse>?
)

data class ResourceApiResponse(
    val health: String?
)

/**
 * Wrapper response for Coder API that returns workspaces in a wrapped format.
 */
data class CoderWorkspacesWrapperResponse(
    val workspaces: List<CoderWorkspaceApiResponse>?
)

/**
 * Paginated response for Coder API v2 (most common format).
 */
data class CoderPaginatedWorkspacesResponse(
    val workspaces: List<CoderWorkspaceApiResponse>?,
    val count: Int?,
    val after_id: String?
)
