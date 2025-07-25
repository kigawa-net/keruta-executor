package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

/**
 * Service for monitoring session state and triggering workspace creation.
 */
@Service
open class SessionMonitoringService(
    private val restTemplate: RestTemplate,
    private val properties: KerutaExecutorProperties
) {
    private val logger = LoggerFactory.getLogger(SessionMonitoringService::class.java)

    /**
     * Monitors new sessions and ensures they have workspaces.
     * In the 1:1 relationship model, workspaces are auto-created with sessions.
     * This monitor primarily updates session status and handles edge cases.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30000)
    fun monitorNewSessions() {
        logger.debug("Monitoring new sessions for workspace verification")
        try {
            // Get all PENDING sessions
            val pendingSessions = getPendingSessions()
            if (pendingSessions.isNotEmpty()) {
                logger.info("Found {} pending sessions to process", pendingSessions.size)
            }

            for (session in pendingSessions) {
                logger.info("Processing session: sessionId={} name={}", session.id, session.name)

                try {
                    // Check if workspace already exists for this session
                    val workspaces = getWorkspacesBySessionId(session.id)

                    if (workspaces.isEmpty()) {
                        logger.warn(
                            "No workspace found for session: sessionId={}. This should not happen in 1:1 model.",
                            session.id
                        )
                        // For backward compatibility or edge cases, try to create workspace
                        // But handle the case where auto-creation already happened
                        try {
                            createWorkspaceForSession(session)
                        } catch (e: Exception) {
                            logger.debug("Workspace creation failed (expected in 1:1 model): sessionId={}", session.id)
                        }
                        // Always try to update status regardless of workspace creation result
                        updateSessionStatus(session.id, "ACTIVE")
                    } else {
                        logger.info(
                            "Workspace exists for session: sessionId={} workspaceId={}",
                            session.id,
                            workspaces.first().id
                        )
                        // Update session status to ACTIVE if workspace exists
                        updateSessionStatus(session.id, "ACTIVE")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to process session: sessionId={}", session.id, e)
                    // Continue with next session
                }
            }
        } catch (e: Exception) {
            logger.error("Error monitoring new sessions", e)
        }
    }

    /**
     * Monitors active sessions and ensures workspaces are running.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedDelay = 60000)
    fun monitorActiveSessions() {
        logger.debug("Monitoring active sessions")

        try {
            // Get all ACTIVE sessions
            val activeSessions = getActiveSessions()

            for (session in activeSessions) {
                logger.debug("Checking active session: sessionId={}", session.id)

                // Get workspaces for this session
                val workspaces = getWorkspacesBySessionId(session.id)

                for (workspace in workspaces) {
                    // Only start workspace if it's in STOPPED or PENDING state
                    when (workspace.status) {
                        "STOPPED", "PENDING" -> {
                            logger.info(
                                "Starting workspace for active session: sessionId={} workspaceId={} status={}",
                                session.id,
                                workspace.id,
                                workspace.status
                            )
                            startWorkspace(workspace.id)
                        }
                        "STARTING" -> {
                            logger.debug(
                                "Workspace is already starting: sessionId={} workspaceId={}",
                                session.id,
                                workspace.id
                            )
                        }
                        "RUNNING" -> {
                            logger.debug(
                                "Workspace is already running: sessionId={} workspaceId={}",
                                session.id,
                                workspace.id
                            )
                        }
                        else -> {
                            logger.debug(
                                "Workspace in non-startable status: sessionId={} workspaceId={} status={}",
                                session.id,
                                workspace.id,
                                workspace.status
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error monitoring active sessions", e)
        }
    }

    /**
     * Gets all pending sessions from the API.
     */
    private fun getPendingSessions(): List<SessionDto> {
        val url = "${properties.apiBaseUrl}/api/v1/sessions?status=PENDING"
        val typeReference = object : ParameterizedTypeReference<List<SessionDto>>() {}
        return restTemplate.exchange(url, HttpMethod.GET, null, typeReference).body ?: emptyList()
    }

    /**
     * Gets all active sessions from the API.
     */
    private fun getActiveSessions(): List<SessionDto> {
        val url = "${properties.apiBaseUrl}/api/v1/sessions?status=ACTIVE"
        val typeReference = object : ParameterizedTypeReference<List<SessionDto>>() {}
        return restTemplate.exchange(url, HttpMethod.GET, null, typeReference).body ?: emptyList()
    }

    /**
     * Gets workspaces by session ID.
     */
    private fun getWorkspacesBySessionId(sessionId: String): List<WorkspaceDto> {
        val url = "${properties.apiBaseUrl}/api/v1/workspaces?sessionId=$sessionId"
        val typeReference = object : ParameterizedTypeReference<List<WorkspaceDto>>() {}
        return restTemplate.exchange(url, HttpMethod.GET, null, typeReference).body ?: emptyList()
    }

    /**
     * Creates a workspace for a session.
     */
    private fun createWorkspaceForSession(session: SessionDto) {
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
            ttlMs = 3600000 // 1 hour
        )

        val entity = HttpEntity(createRequest, headers)

        try {
            val response = restTemplate.exchange(url, HttpMethod.POST, entity, WorkspaceDto::class.java)
            logger.info(
                "Successfully created workspace: sessionId={} workspaceId={}",
                session.id,
                response.body?.id
            )
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            if (e.statusCode.value() == 400) {
                // This might be due to workspace already existing (1:1 relationship)
                logger.warn(
                    "Workspace creation failed, likely due to existing workspace: sessionId={} error={}",
                    session.id,
                    e.message
                )
                // Don't throw the exception, just log it
            } else {
                logger.error(
                    "Failed to create workspace for session (HTTP {}): sessionId={}",
                    e.statusCode.value(),
                    session.id,
                    e
                )
                throw e
            }
        } catch (e: org.springframework.web.client.HttpServerErrorException) {
            // Log server errors but don't fail the entire process
            logger.error("Server error when creating workspace: sessionId={} error={}", session.id, e.message)
            // Don't throw the exception to allow processing of other sessions
        } catch (e: Exception) {
            logger.error("Failed to create workspace for session: sessionId={}", session.id, e)
            // Don't throw the exception to allow processing of other sessions
        }
    }

    /**
     * Gets the first available template ID.
     */
    private fun getFirstAvailableTemplateId(): String? {
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
     * Updates session status.
     */
    private fun updateSessionStatus(sessionId: String, status: String) {
        logger.info("Updating session status: sessionId={} status={}", sessionId, status)

        val url = "${properties.apiBaseUrl}/api/v1/sessions/$sessionId/status"
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val updateRequest = UpdateSessionStatusRequest(status)
        val entity = HttpEntity(updateRequest, headers)

        try {
            restTemplate.exchange(url, HttpMethod.PUT, entity, SessionDto::class.java)
            logger.info("Successfully updated session status: sessionId={} status={}", sessionId, status)
        } catch (e: Exception) {
            logger.error("Failed to update session status: sessionId={} status={}", sessionId, status, e)
        }
    }

    /**
     * Starts a workspace.
     */
    private fun startWorkspace(workspaceId: String) {
        logger.info("Starting workspace: workspaceId={}", workspaceId)

        val url = "${properties.apiBaseUrl}/api/v1/workspaces/$workspaceId/start"
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val entity = HttpEntity<Any>(headers)

        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, WorkspaceDto::class.java)
            logger.info("Successfully started workspace: workspaceId={}", workspaceId)
        } catch (e: Exception) {
            logger.error("Failed to start workspace: workspaceId={}", workspaceId, e)
        }
    }
}

/**
 * DTO for session data.
 */
data class SessionDto(
    val id: String,
    val name: String,
    val status: String,
    val tags: List<String>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/**
 * DTO for workspace data.
 */
data class WorkspaceDto(
    val id: String,
    val name: String,
    val sessionId: String,
    val status: String,
    val coderWorkspaceId: String?,
    val workspaceUrl: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/**
 * Request for creating a workspace.
 */
data class CreateWorkspaceRequest(
    val name: String,
    val sessionId: String,
    val templateId: String?,
    val automaticUpdates: Boolean,
    val ttlMs: Long
)

/**
 * Request for updating session status.
 */
data class UpdateSessionStatusRequest(
    val status: String
)

/**
 * DTO for workspace template data.
 */
data class WorkspaceTemplateDto(
    val id: String,
    val name: String,
    val description: String?,
    val version: String,
    val icon: String?,
    val isDefault: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
