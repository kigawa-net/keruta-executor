package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.dto.*
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Service for monitoring session state and triggering workspace creation.
 */
@Service
open class SessionMonitoringService(
    private val sessionApiClient: SessionApiClient,
    private val circuitBreakerService: CircuitBreakerService,
    private val workspaceCreationHandler: WorkspaceCreationHandler,
    private val coderWorkspaceService: CoderWorkspaceService,
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
            val pendingSessions = sessionApiClient.getPendingSessions()
            if (pendingSessions.isNotEmpty()) {
                logger.info("Found {} pending sessions to process", pendingSessions.size)
            }

            for (session in pendingSessions) {
                logger.info("Processing session: sessionId={} name={}", session.id, session.name)

                try {
                    // Check circuit breaker before processing
                    if (circuitBreakerService.isCircuitOpen("session_${session.id}")) {
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
                            workspaceCreationHandler.createCoderWorkspaceForSession(session)
                            logger.info("Successfully created Coder workspace for session: sessionId={}", session.id)
                        } catch (e: Exception) {
                            logger.error("Failed to create Coder workspace for session: sessionId={}", session.id, e)
                            // Continue processing - don't fail the entire session monitoring
                        }
                        // Update session status to ACTIVE after workspace creation attempt
                        sessionApiClient.updateSessionStatusWithRetry(session.id, "ACTIVE")
                    } else {
                        logger.info(
                            "Coder workspace exists for session: sessionId={} workspaceCount={}",
                            session.id,
                            existingWorkspaces.size,
                        )
                        // Update session status to ACTIVE if workspace exists
                        sessionApiClient.updateSessionStatusWithRetry(session.id, "ACTIVE")
                    }

                    // Record success
                    circuitBreakerService.recordSuccess("session_${session.id}")
                } catch (e: Exception) {
                    circuitBreakerService.recordFailure("session_${session.id}")
                    logger.error("Failed to process session: sessionId={} error={}", session.id, e.message, e)
                    // Continue with next session
                }
            }
        } catch (e: Exception) {
            logger.error("Error monitoring new sessions", e)
        }
    }

    /**
     * Monitors inactive sessions and reactivates them if workspaces become available.
     * Runs every 90 seconds.
     */
    @Scheduled(fixedDelay = 90000)
    fun monitorInactiveSessions() {
        logger.debug("Monitoring inactive sessions")

        try {
            // Get all INACTIVE sessions
            val inactiveSessions = sessionApiClient.getInactiveSessions()

            for (session in inactiveSessions) {
                logger.debug("Checking inactive session: sessionId={}", session.id)

                try {
                    // Check circuit breaker
                    if (circuitBreakerService.isCircuitOpen("session_${session.id}")) {
                        logger.warn("Circuit breaker is open for inactive session: sessionId={}", session.id)
                        continue
                    }

                    // Get Coder workspaces for this session
                    val coderWorkspaces = coderWorkspaceService.getWorkspacesBySessionId(session.id)

                    if (coderWorkspaces.isNotEmpty()) {
                        // Check if any workspace is running or starting
                        val hasRunningWorkspace = coderWorkspaces.any { workspace ->
                            workspace.status.lowercase() in listOf("running", "starting")
                        }

                        if (hasRunningWorkspace) {
                            logger.info(
                                "Found running workspace for inactive session, reactivating: sessionId={} workspaceCount={}",
                                session.id,
                                coderWorkspaces.size,
                            )
                            try {
                                sessionApiClient.updateSessionStatusWithRetry(session.id, "ACTIVE")
                                logger.info("Successfully reactivated session: sessionId={}", session.id)
                            } catch (e: Exception) {
                                logger.error("Failed to reactivate session: sessionId={}", session.id, e)
                            }
                        }
                    }

                    // Record success
                    circuitBreakerService.recordSuccess("session_${session.id}")
                } catch (e: Exception) {
                    circuitBreakerService.recordFailure("session_${session.id}")
                    logger.error("Failed to process inactive session: sessionId={} error={}", session.id, e.message, e)
                }
            }
        } catch (e: Exception) {
            logger.error("Error monitoring inactive sessions", e)
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
            val activeSessions = sessionApiClient.getActiveSessions()

            for (session in activeSessions) {
                logger.debug("Checking active session: sessionId={}", session.id)

                try {
                    // Check circuit breaker
                    if (circuitBreakerService.isCircuitOpen("session_${session.id}")) {
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
                            workspaceCreationHandler.createCoderWorkspaceForSession(session)
                        } catch (e: Exception) {
                            logger.error("Failed to create workspace for active session: sessionId={}", session.id, e)
                        }
                    } else {
                        // Check if all workspaces are stopped and set session to inactive
                        val allWorkspacesStopped = coderWorkspaces.all { workspace ->
                            workspace.status.lowercase() in listOf("stopped", "pending", "failed")
                        }

                        if (allWorkspacesStopped) {
                            logger.info(
                                "All workspaces are stopped for active session, setting session to inactive: sessionId={} workspaceCount={}",
                                session.id,
                                coderWorkspaces.size,
                            )
                            try {
                                sessionApiClient.updateSessionStatusWithRetry(session.id, "INACTIVE")
                                logger.info("Successfully set session to inactive: sessionId={}", session.id)
                                // Skip workspace starting since session is now inactive
                                continue
                            } catch (e: Exception) {
                                logger.error("Failed to set session to inactive: sessionId={}", session.id, e)
                                // Continue with workspace management even if status update fails
                            }
                        }

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
                    circuitBreakerService.recordSuccess("session_${session.id}")
                } catch (e: Exception) {
                    circuitBreakerService.recordFailure("session_${session.id}")
                    logger.error("Failed to process active session: sessionId={} error={}", session.id, e.message, e)
                }
            }
        } catch (e: Exception) {
            logger.error("Error monitoring active sessions", e)
        }
    }
}
