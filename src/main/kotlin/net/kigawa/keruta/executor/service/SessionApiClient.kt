package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import net.kigawa.keruta.executor.dto.*
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate

/**
 * Client for session API communication.
 */
@Service
open class SessionApiClient(
    private val restTemplate: RestTemplate,
    private val properties: KerutaExecutorProperties,
    private val circuitBreakerService: CircuitBreakerService,
) {
    private val logger = LoggerFactory.getLogger(SessionApiClient::class.java)

    /**
     * Gets all pending sessions from the API.
     */
    fun getPendingSessions(): List<SessionDto> {
        val url = "${properties.apiBaseUrl}/api/v1/sessions/status/PENDING"
        val typeReference = object : ParameterizedTypeReference<List<SessionDto>>() {}
        return restTemplate.exchange(url, HttpMethod.GET, null, typeReference).body ?: emptyList()
    }

    /**
     * Gets all active sessions from the API.
     */
    fun getActiveSessions(): List<SessionDto> {
        val url = "${properties.apiBaseUrl}/api/v1/sessions/status/ACTIVE"
        val typeReference = object : ParameterizedTypeReference<List<SessionDto>>() {}
        return restTemplate.exchange(url, HttpMethod.GET, null, typeReference).body ?: emptyList()
    }

    /**
     * Gets all inactive sessions from the API.
     */
    fun getInactiveSessions(): List<SessionDto> {
        val url = "${properties.apiBaseUrl}/api/v1/sessions/status/INACTIVE"
        val typeReference = object : ParameterizedTypeReference<List<SessionDto>>() {}
        return restTemplate.exchange(url, HttpMethod.GET, null, typeReference).body ?: emptyList()
    }

    /**
     * Gets workspaces by session ID.
     */
    fun getWorkspacesBySessionId(sessionId: String): List<WorkspaceDto> {
        val url = "${properties.apiBaseUrl}/api/v1/workspaces?sessionId=$sessionId"
        val typeReference = object : ParameterizedTypeReference<List<WorkspaceDto>>() {}
        return restTemplate.exchange(url, HttpMethod.GET, null, typeReference).body ?: emptyList()
    }

    /**
     * Updates session status.
     */
    fun updateSessionStatus(sessionId: String, status: String) {
        logger.info("Updating session status via system API: sessionId={} status={}", sessionId, status)

        val url = "${properties.apiBaseUrl}/api/v1/sessions/$sessionId/system-status"
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val updateRequest = mapOf("status" to status)
        val entity = HttpEntity(updateRequest, headers)

        try {
            val response = restTemplate.exchange(url, HttpMethod.PUT, entity, SessionDto::class.java)
            logger.info(
                "Successfully updated session status: sessionId={} status={} newStatus={}",
                sessionId,
                status,
                response.body?.status,
            )
        } catch (e: HttpClientErrorException) {
            logger.error(
                "Client error updating session status: sessionId={} status={} httpStatus={} error={}",
                sessionId,
                status,
                e.statusCode.value(),
                e.message,
            )
            throw e
        } catch (e: HttpServerErrorException) {
            logger.error(
                "Server error updating session status: sessionId={} status={} httpStatus={} error={}",
                sessionId,
                status,
                e.statusCode.value(),
                e.message,
            )
            throw e
        } catch (e: ResourceAccessException) {
            logger.error(
                "Network error updating session status: sessionId={} status={} error={}",
                sessionId,
                status,
                e.message,
            )
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error updating session status: sessionId={} status={}", sessionId, status, e)
            throw e
        }
    }

    /**
     * Updates session status with retry logic.
     */
    fun updateSessionStatusWithRetry(sessionId: String, status: String) {
        val operationKey = "updateSessionStatus_${sessionId}"
        try {
            circuitBreakerService.executeWithRetry(operationKey, 3) {
                updateSessionStatus(sessionId, status)
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to update session status after retries: sessionId={} status={} error={}",
                sessionId,
                status,
                e.message
            )
            throw e
        }
    }

    /**
     * Gets workspaces by session ID with retry logic.
     */
    fun getWorkspacesBySessionIdWithRetry(sessionId: String): List<WorkspaceDto> {
        return circuitBreakerService.executeWithRetry("getWorkspacesBySessionId", 3) {
            getWorkspacesBySessionId(sessionId)
        }
    }

    /**
     * Starts a workspace.
     */
    fun startWorkspace(workspaceId: String) {
        logger.info("Starting workspace: workspaceId={}", workspaceId)

        val url = "${properties.apiBaseUrl}/api/v1/workspaces/$workspaceId/start"
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val entity = HttpEntity<Any>(headers)

        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, WorkspaceDto::class.java)
            logger.info("Successfully started workspace: workspaceId={}", workspaceId)
        } catch (e: HttpClientErrorException) {
            logger.error(
                "Client error starting workspace: workspaceId={} httpStatus={} error={}",
                workspaceId,
                e.statusCode.value(),
                e.message,
            )
            throw e
        } catch (e: HttpServerErrorException) {
            logger.error(
                "Server error starting workspace: workspaceId={} httpStatus={} error={}",
                workspaceId,
                e.statusCode.value(),
                e.message,
            )
            throw e
        } catch (e: ResourceAccessException) {
            logger.error(
                "Network error starting workspace: workspaceId={} error={}",
                workspaceId,
                e.message,
            )
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error starting workspace: workspaceId={}", workspaceId, e)
            throw e
        }
    }

    /**
     * Starts a workspace with retry logic.
     */
    fun startWorkspaceWithRetry(workspaceId: String) {
        circuitBreakerService.executeWithRetry("startWorkspace", 3) {
            startWorkspace(workspaceId)
        }
    }

    /**
     * Gets the first available template ID.
     */
    fun getFirstAvailableTemplateId(): String? {
        val url = "${properties.apiBaseUrl}/api/v1/workspaces/templates"
        val typeReference = object : ParameterizedTypeReference<List<WorkspaceTemplateDto>>() {}
        val templates = restTemplate.exchange(url, HttpMethod.GET, null, typeReference).body ?: emptyList()

        // First try to find default template
        val defaultTemplate = templates.find { it.isDefault }
        if (defaultTemplate != null) {
            logger.debug("Using default template: {}", defaultTemplate.id)
            return defaultTemplate.id
        }

        // If no default template, use the first available template
        val firstTemplate = templates.firstOrNull()
        if (firstTemplate != null) {
            logger.debug("Using first available template: {}", firstTemplate.id)
            return firstTemplate.id
        }

        logger.warn("No templates available")
        return null
    }

    /**
     * Creates a workspace for a session.
     */
    fun createWorkspaceForSession(session: SessionDto) {
        logger.info("Creating workspace for session: sessionId={}", session.id)

        // Try to get available templates
        val templateId = try {
            getFirstAvailableTemplateId()
        } catch (e: Exception) {
            logger.warn("Failed to get template, proceeding with null templateId", e)
            null
        }

        val url = "${properties.apiBaseUrl}/api/v1/workspaces"
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val createRequest = CreateWorkspaceRequest(
            name = "${session.name}-workspace",
            sessionId = session.id,
            templateId = templateId,
            automaticUpdates = true,
            // 1 hour
            ttlMs = 3600000,
        )

        val entity = HttpEntity(createRequest, headers)

        try {
            val response = restTemplate.exchange(url, HttpMethod.POST, entity, WorkspaceDto::class.java)
            logger.info(
                "Successfully created workspace: sessionId={} workspaceId={}",
                session.id,
                response.body?.id,
            )
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 400) {
                // This might be due to workspace already existing (1:1 relationship)
                logger.warn(
                    "Workspace creation failed, likely due to existing workspace: sessionId={} error={}",
                    session.id,
                    e.message,
                )
                // Don't throw the exception, just log it
            } else {
                logger.error(
                    "Failed to create workspace for session (HTTP {}): sessionId={}",
                    e.statusCode.value(),
                    session.id,
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            // Log server errors and throw to trigger retry
            logger.error(
                "Server error when creating workspace: sessionId={} status={} error={}",
                session.id,
                e.statusCode.value(),
                e.message,
            )
            throw e
        } catch (e: ResourceAccessException) {
            // Network/connection issues - throw to trigger retry
            logger.error("Network error when creating workspace: sessionId={} error={}", session.id, e.message)
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error creating workspace for session: sessionId={}", session.id, e)
            throw e
        }
    }

    /**
     * Creates a workspace for a session with retry logic.
     */
    fun createWorkspaceForSessionWithRetry(session: SessionDto) {
        circuitBreakerService.executeWithRetry("createWorkspaceForSession", 2) {
            createWorkspaceForSession(session)
        }
    }

    /**
     * Synchronizes session status with workspace state.
     * This is a more intelligent sync that checks workspace state and updates session accordingly.
     */
    fun syncSessionWithWorkspaces(sessionId: String): Map<String, Any>? {
        logger.info("Syncing session with workspaces: sessionId={}", sessionId)

        val url = "${properties.apiBaseUrl}/api/v1/sessions/$sessionId/sync-status"
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val entity = HttpEntity<Any>(headers)

        return try {
            val response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                object : ParameterizedTypeReference<Map<String, Any>>() {}
            )

            val syncResult = response.body
            logger.info(
                "Successfully synced session with workspaces: sessionId={} result={}",
                sessionId,
                syncResult?.get("message") ?: "synced"
            )
            syncResult
        } catch (e: HttpClientErrorException) {
            logger.error(
                "Client error syncing session: sessionId={} httpStatus={} error={}",
                sessionId,
                e.statusCode.value(),
                e.message,
            )
            throw e
        } catch (e: HttpServerErrorException) {
            logger.error(
                "Server error syncing session: sessionId={} httpStatus={} error={}",
                sessionId,
                e.statusCode.value(),
                e.message,
            )
            throw e
        } catch (e: ResourceAccessException) {
            logger.error(
                "Network error syncing session: sessionId={} error={}",
                sessionId,
                e.message,
            )
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error syncing session: sessionId={}", sessionId, e)
            throw e
        }
    }

    /**
     * Synchronizes session status with workspace state using retry logic.
     */
    fun syncSessionWithWorkspacesWithRetry(sessionId: String): Map<String, Any>? {
        val operationKey = "syncSessionWithWorkspaces_${sessionId}"
        return try {
            circuitBreakerService.executeWithRetry(operationKey, 2) {
                syncSessionWithWorkspaces(sessionId)
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to sync session with workspaces after retries: sessionId={} error={}",
                sessionId,
                e.message
            )
            throw e
        }
    }
}
