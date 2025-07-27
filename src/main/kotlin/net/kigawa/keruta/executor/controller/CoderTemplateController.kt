package net.kigawa.keruta.executor.controller

import net.kigawa.keruta.executor.service.CoderTemplateDto
import net.kigawa.keruta.executor.service.CoderTemplateService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for Coder template management via executor.
 */
@RestController
@RequestMapping("/api/v1/coder")
@CrossOrigin(origins = ["*"])
class CoderTemplateController(
    private val coderTemplateService: CoderTemplateService
) {
    private val logger = LoggerFactory.getLogger(CoderTemplateController::class.java)

    /**
     * Gets available Coder templates from the Coder server.
     */
    @GetMapping("/templates")
    fun getCoderTemplates(): ResponseEntity<List<CoderTemplateDto>> {
        logger.info("Executor: Fetching Coder templates")

        val templates = coderTemplateService.getCoderTemplates()
        logger.info("Executor: Found {} Coder templates", templates.size)

        return ResponseEntity.ok(templates)
    }

    /**
     * Gets a specific Coder template by ID.
     */
    @GetMapping("/templates/{id}")
    fun getCoderTemplate(@PathVariable id: String): ResponseEntity<CoderTemplateDto> {
        logger.info("Executor: Fetching Coder template: $id")

        val template = coderTemplateService.getCoderTemplate(id)
        return if (template != null) {
            ResponseEntity.ok(template)
        } else {
            logger.warn("Executor: Coder template not found: $id")
            ResponseEntity.notFound().build()
        }
    }
}
