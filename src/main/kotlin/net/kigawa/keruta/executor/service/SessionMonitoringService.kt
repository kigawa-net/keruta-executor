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
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Service for monitoring session state and triggering workspace creation.
 */
@Service
open class SessionMonitoringService(
    private val restTemplate: RestTemplate,
    private val properties: KerutaExecutorProperties,
    private val coderWorkspaceService: CoderWorkspaceService,
    private val coderTemplateService: CoderTemplateService,
) {
    private val logger = LoggerFactory.getLogger(SessionMonitoringService::class.java)

    // Circuit breaker pattern
    private val failureCounters = ConcurrentHashMap<String, AtomicInteger>()
    private val lastFailureTime = ConcurrentHashMap<String, Long>()
    private val circuitOpenTime = ConcurrentHashMap<String, Long>()

    // Configuration for circuit breaker
    private val maxFailures = 5
    private val circuitOpenDuration = 60000L // 1 minute
    private val resetTimeout = 120000L // 2 minutes

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
                    // Check circuit breaker before processing
                    if (isCircuitOpen("session_${session.id}")) {
                        logger.warn("Circuit breaker is open for session: sessionId={}", session.id)
                        continue
                    }

                    // Check if workspace already exists for this session in Coder
                    val existingWorkspaces = coderWorkspaceService.getWorkspacesBySessionId(session.id)

                    if (existingWorkspaces.isEmpty()) {
                        logger.info(
                            "No Coder workspace found for session: sessionId={}. Creating workspace automatically.",
                            session.id,
                        )
                        try {
                            createCoderWorkspaceForSession(session)
                            logger.info("Successfully created Coder workspace for session: sessionId={}", session.id)
                        } catch (e: Exception) {
                            logger.error("Failed to create Coder workspace for session: sessionId={}", session.id, e)
                            // Continue processing - don't fail the entire session monitoring
                        }
                        // Update session status to ACTIVE after workspace creation attempt
                        updateSessionStatusWithRetry(session.id, "ACTIVE")
                    } else {
                        logger.info(
                            "Coder workspace exists for session: sessionId={} workspaceCount={}",
                            session.id,
                            existingWorkspaces.size,
                        )
                        // Update session status to ACTIVE if workspace exists
                        updateSessionStatusWithRetry(session.id, "ACTIVE")
                    }

                    // Record success
                    recordSuccess("session_${session.id}")
                } catch (e: Exception) {
                    recordFailure("session_${session.id}")
                    logger.error("Failed to process session: sessionId={} error={}", session.id, e.message, e)
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

                try {
                    // Check circuit breaker
                    if (isCircuitOpen("session_${session.id}")) {
                        logger.warn("Circuit breaker is open for active session: sessionId={}", session.id)
                        continue
                    }

                    // Get Coder workspaces for this session
                    val coderWorkspaces = coderWorkspaceService.getWorkspacesBySessionId(session.id)

                    if (coderWorkspaces.isEmpty()) {
                        logger.warn(
                            "No Coder workspace found for active session: sessionId={}. Creating workspace.",
                            session.id,
                        )
                        try {
                            createCoderWorkspaceForSession(session)
                        } catch (e: Exception) {
                            logger.error("Failed to create workspace for active session: sessionId={}", session.id, e)
                        }
                    } else {
                        for (workspace in coderWorkspaces) {
                            // Only start workspace if it's in STOPPED or similar state
                            when (workspace.status.lowercase()) {
                                "stopped", "pending", "failed" -> {
                                    logger.info(
                                        "Starting Coder workspace: sessionId={} workspaceId={} status={}",
                                        session.id,
                                        workspace.id,
                                        workspace.status,
                                    )
                                    try {
                                        coderWorkspaceService.startWorkspace(workspace.id)
                                    } catch (e: Exception) {
                                        logger.error(
                                            "Failed to start Coder workspace: sessionId={} workspaceId={}",
                                            session.id,
                                            workspace.id,
                                            e,
                                        )
                                    }
                                }
                                "starting", "running" -> {
                                    logger.debug(
                                        "Workspace running/starting: sessionId={} workspaceId={} status={}",
                                        session.id,
                                        workspace.id,
                                        workspace.status,
                                    )
                                }
                                else -> {
                                    logger.debug(
                                        "Workspace non-startable: sessionId={} workspaceId={} status={}",
                                        session.id,
                                        workspace.id,
                                        workspace.status,
                                    )
                                }
                            }
                        }
                    }

                    // Record success
                    recordSuccess("session_${session.id}")
                } catch (e: Exception) {
                    recordFailure("session_${session.id}")
                    logger.error("Failed to process active session: sessionId={} error={}", session.id, e.message, e)
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
     * Gets workspaces by session ID with retry logic.
     */
    private fun getWorkspacesBySessionIdWithRetry(sessionId: String): List<WorkspaceDto> {
        return executeWithRetry("getWorkspacesBySessionId", 3) {
            getWorkspacesBySessionId(sessionId)
        }
    }

    /**
     * Creates a workspace for a session with retry logic.
     */
    private fun createWorkspaceForSessionWithRetry(session: SessionDto) {
        executeWithRetry("createWorkspaceForSession", 2) {
            createWorkspaceForSession(session)
        }
    }

    /**
     * Creates a Coder workspace directly for a session.
     */
    private fun createCoderWorkspaceForSession(session: SessionDto) {
        logger.info("Creating Coder workspace for session: sessionId={} name={}", session.id, session.name)

        try {
            // Get available templates from Coder
            val templates = coderTemplateService.getCoderTemplates()
            val selectedTemplate = selectBestTemplate(templates, session)

            if (selectedTemplate == null) {
                logger.error("No suitable template found for session: sessionId={}", session.id)
                return
            }

            logger.info(
                "Using template for workspace creation: sessionId={} templateId={} templateName={}",
                session.id,
                selectedTemplate.id,
                selectedTemplate.name,
            )

            // Create workspace name with session ID for easy identification
            val workspaceName = generateWorkspaceName(session)

            val createRequest = CreateCoderWorkspaceRequest(
                name = workspaceName,
                templateId = selectedTemplate.id,
                richParameterValues = emptyList(),
            )

            val createdWorkspace = coderWorkspaceService.createWorkspace(createRequest)
            logger.info(
                "Successfully created Coder workspace: sessionId={} workspaceId={} workspaceName={}",
                session.id,
                createdWorkspace.id,
                createdWorkspace.name,
            )

            // Start the workspace immediately for active sessions
            try {
                coderWorkspaceService.startWorkspace(createdWorkspace.id)
                logger.info(
                    "Started newly created workspace: sessionId={} workspaceId={}",
                    session.id,
                    createdWorkspace.id,
                )
            } catch (e: Exception) {
                logger.warn(
                    "Failed to start newly created workspace (will retry later): sessionId={} workspaceId={}",
                    session.id,
                    createdWorkspace.id,
                    e,
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to create Coder workspace for session: sessionId={}", session.id, e)
            throw e
        }
    }

    /**
     * Selects the best template for a session based on tags and preferences.
     */
    private fun selectBestTemplate(templates: List<CoderTemplateDto>, session: SessionDto): CoderTemplateDto? {
        if (templates.isEmpty()) {
            logger.warn("No templates available for workspace creation")
            return null
        }

        // First, try to find a template that matches session tags
        for (tag in session.tags) {
            val matchingTemplate = templates.find { template ->
                template.name.contains(tag, ignoreCase = true) ||
                    template.displayName.contains(tag, ignoreCase = true) ||
                    template.description.contains(tag, ignoreCase = true)
            }
            if (matchingTemplate != null) {
                logger.info("Found template matching tag '{}': templateId={}", tag, matchingTemplate.id)
                return matchingTemplate
            }
        }

        // Look for keruta-specific template
        val kerutaTemplate = templates.find { it.name.contains("keruta", ignoreCase = true) }
        if (kerutaTemplate != null) {
            logger.info("Using Keruta-optimized template: templateId={}", kerutaTemplate.id)
            return kerutaTemplate
        }

        // Fallback to first available template
        val defaultTemplate = templates.firstOrNull()
        if (defaultTemplate != null) {
            logger.info("Using first available template as fallback: templateId={}", defaultTemplate.id)
        }
        return defaultTemplate
    }

    /**
     * Generates a workspace name for the session.
     */
    private fun generateWorkspaceName(session: SessionDto): String {
        // Use session name but ensure it's Coder-compatible
        val sanitizedSessionName = session.name
            .replace("[^a-zA-Z0-9-_]".toRegex(), "-")
            .replace("-+".toRegex(), "-")
            .trim('-')
            .take(20) // Limit length to leave room for timestamp

        // Add timestamp to ensure uniqueness
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        return "session-${session.id.take(8)}-$sanitizedSessionName-$timestamp"
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
     * Updates session status with retry logic.
     */
    private fun updateSessionStatusWithRetry(sessionId: String, status: String) {
        executeWithRetry("updateSessionStatus", 3) {
            updateSessionStatus(sessionId, status)
        }
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
     * Starts a workspace with retry logic.
     */
    private fun startWorkspaceWithRetry(workspaceId: String) {
        executeWithRetry("startWorkspace", 3) {
            startWorkspace(workspaceId)
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
     * Circuit breaker and retry utility methods
     */
    private fun isCircuitOpen(key: String): Boolean {
        val openTime = circuitOpenTime[key]
        if (openTime != null) {
            val elapsed = System.currentTimeMillis() - openTime
            if (elapsed > circuitOpenDuration) {
                // Circuit breaker timeout - allow one test request
                logger.info("Circuit breaker timeout for key: {}, allowing test request", key)
                circuitOpenTime.remove(key)
                return false
            }
            return true
        }
        return false
    }

    private fun recordFailure(key: String) {
        val counter = failureCounters.computeIfAbsent(key) { AtomicInteger(0) }
        val failures = counter.incrementAndGet()
        lastFailureTime[key] = System.currentTimeMillis()

        if (failures >= maxFailures) {
            logger.warn("Circuit breaker opened for key: {} after {} failures", key, failures)
            circuitOpenTime[key] = System.currentTimeMillis()
        }
    }

    private fun recordSuccess(key: String) {
        failureCounters.remove(key)
        lastFailureTime.remove(key)
        circuitOpenTime.remove(key)
    }

    private fun <T> executeWithRetry(operation: String, maxRetries: Int, block: () -> T): T {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: HttpClientErrorException) {
                // Don't retry client errors (4xx)
                logger.debug("Client error in {} (attempt {}): {}", operation, attempt + 1, e.message)
                throw e
            } catch (e: Exception) {
                lastException = e
                val isLastAttempt = attempt == maxRetries - 1

                if (isLastAttempt) {
                    logger.error("Final attempt failed for {}: {}", operation, e.message)
                } else {
                    val delay = calculateBackoffDelay(attempt)
                    logger.warn(
                        "Attempt {} failed for {}, retrying in {}ms: {}",
                        attempt + 1,
                        operation,
                        delay,
                        e.message,
                    )

                    try {
                        Thread.sleep(delay)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw RuntimeException("Retry interrupted", ie)
                    }
                }
            }
        }

        throw lastException ?: RuntimeException("Retry failed for operation: $operation")
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        // Exponential backoff with jitter
        val baseDelay = 1000L * (1L shl attempt) // 1s, 2s, 4s, 8s...
        val jitter = Random.nextLong(0, baseDelay / 2)
        return baseDelay + jitter
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
    val updatedAt: LocalDateTime,
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
    val updatedAt: LocalDateTime,
)

/**
 * Request for creating a workspace.
 */
data class CreateWorkspaceRequest(
    val name: String,
    val sessionId: String,
    val templateId: String?,
    val automaticUpdates: Boolean,
    val ttlMs: Long,
)

/**
 * Request for updating session status.
 */
data class UpdateSessionStatusRequest(
    val status: String,
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
    val updatedAt: LocalDateTime,
)
