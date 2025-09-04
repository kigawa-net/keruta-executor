package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Handles workspace creation logic for sessions.
 */
@Service
open class WorkspaceCreationHandler(
    private val coderWorkspaceService: CoderWorkspaceService,
    private val coderTemplateService: CoderTemplateService,
) {
    private val logger = LoggerFactory.getLogger(WorkspaceCreationHandler::class.java)

    /**
     * Creates a Coder workspace directly for a session.
     */
    fun createCoderWorkspaceForSession(session: SessionDto) {
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
     * Follows Coder workspace naming rules:
     * - Must contain only lowercase alphanumeric characters, hyphens, and underscores
     * - Must start with a lowercase letter
     * - Must be between 1-32 characters
     */
    private fun generateWorkspaceName(session: SessionDto): String {
        // Start with a letter as required by Coder
        val prefix = "ws"

        // Use shortened session ID (8 chars) for uniqueness
        val sessionIdShort = session.id.take(8).lowercase()

        // Sanitize session name: only lowercase alphanumeric, max 10 chars
        val sanitizedName = session.name
            .lowercase()
            .replace("[^a-z0-9]".toRegex(), "")
            .take(10)
            .ifEmpty { "session" }

        // Add short timestamp for uniqueness (4 digits)
        val timestamp = (System.currentTimeMillis() % 10000).toString().padStart(4, '0')

        // Combine: ws-{sessionId8}-{name10}-{time4} = max 29 chars (within 32 limit)
        return "$prefix-$sessionIdShort-$sanitizedName-$timestamp"
    }
}
