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
}
