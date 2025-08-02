package net.kigawa.keruta.executor.controller

import net.kigawa.keruta.executor.service.CoderWorkspaceDto
import net.kigawa.keruta.executor.service.CoderWorkspaceService
import net.kigawa.keruta.executor.service.CreateCoderWorkspaceRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for Coder workspace management via executor.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
@CrossOrigin(origins = ["*"])
class WorkspaceController(
    private val coderWorkspaceService: CoderWorkspaceService
) {
    private val logger = LoggerFactory.getLogger(WorkspaceController::class.java)

    /**
     * Gets all workspaces or workspaces filtered by session ID.
     */
    @GetMapping
    fun getWorkspaces(@RequestParam(required = false) sessionId: String?): ResponseEntity<List<CoderWorkspaceDto>> {
        logger.info("Executor: Fetching workspaces (sessionId: {})", sessionId ?: "all")

        val workspaces = if (sessionId != null) {
            coderWorkspaceService.getWorkspacesBySessionId(sessionId)
        } else {
            coderWorkspaceService.getAllWorkspaces()
        }

        logger.info("Executor: Found {} workspaces", workspaces.size)
        return ResponseEntity.ok(workspaces)
    }

    /**
     * Gets a specific workspace by ID.
     */
    @GetMapping("/{id}")
    fun getWorkspace(@PathVariable id: String): ResponseEntity<CoderWorkspaceDto> {
        logger.info("Executor: Fetching workspace: {}", id)

        val workspace = coderWorkspaceService.getWorkspace(id)
        return if (workspace != null) {
            ResponseEntity.ok(workspace)
        } else {
            logger.warn("Executor: Workspace not found: {}", id)
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Creates a new workspace.
     */
    @PostMapping
    fun createWorkspace(@RequestBody request: CreateCoderWorkspaceRequest): ResponseEntity<CoderWorkspaceDto> {
        logger.info("Executor: Creating workspace: {}", request.name)

        return try {
            val workspace = coderWorkspaceService.createWorkspace(request)
            logger.info("Executor: Successfully created workspace: {}", workspace.id)
            ResponseEntity.ok(workspace)
        } catch (e: Exception) {
            logger.error("Executor: Failed to create workspace", e)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Starts a workspace.
     */
    @PostMapping("/{id}/start")
    fun startWorkspace(@PathVariable id: String): ResponseEntity<CoderWorkspaceDto> {
        logger.info("Executor: Starting workspace: {}", id)

        return try {
            val workspace = coderWorkspaceService.startWorkspace(id)
            if (workspace != null) {
                logger.info("Executor: Successfully started workspace: {}", id)
                ResponseEntity.ok(workspace)
            } else {
                logger.warn("Executor: Workspace not found for start: {}", id)
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            logger.error("Executor: Failed to start workspace: {}", id, e)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Stops a workspace.
     */
    @PostMapping("/{id}/stop")
    fun stopWorkspace(@PathVariable id: String): ResponseEntity<CoderWorkspaceDto> {
        logger.info("Executor: Stopping workspace: {}", id)

        return try {
            val workspace = coderWorkspaceService.stopWorkspace(id)
            if (workspace != null) {
                logger.info("Executor: Successfully stopped workspace: {}", id)
                ResponseEntity.ok(workspace)
            } else {
                logger.warn("Executor: Workspace not found for stop: {}", id)
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            logger.error("Executor: Failed to stop workspace: {}", id, e)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Deletes a workspace.
     */
    @DeleteMapping("/{id}")
    fun deleteWorkspace(@PathVariable id: String): ResponseEntity<Void> {
        logger.info("Executor: Deleting workspace: {}", id)

        return try {
            val success = coderWorkspaceService.deleteWorkspace(id)
            if (success) {
                logger.info("Executor: Successfully deleted workspace: {}", id)
                ResponseEntity.noContent().build()
            } else {
                logger.warn("Executor: Workspace not found for deletion: {}", id)
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            logger.error("Executor: Failed to delete workspace: {}", id, e)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * Gets available workspace templates.
     */
    @GetMapping("/templates")
    fun getWorkspaceTemplates(): ResponseEntity<List<net.kigawa.keruta.executor.service.CoderTemplateDto>> {
        logger.info("Executor: Fetching workspace templates")

        return try {
            val templates = coderWorkspaceService.getWorkspaceTemplates()
            logger.info("Executor: Found {} workspace templates", templates.size)
            ResponseEntity.ok(templates)
        } catch (e: Exception) {
            logger.error("Executor: Failed to fetch workspace templates", e)
            ResponseEntity.internalServerError().build()
        }
    }
}
