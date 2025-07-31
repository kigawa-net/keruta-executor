package net.kigawa.keruta.executor.controller

import io.mockk.every
import io.mockk.mockk
import net.kigawa.keruta.executor.service.CoderTemplateDto
import net.kigawa.keruta.executor.service.CoderTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CoderTemplateControllerTest {

    private lateinit var coderTemplateService: CoderTemplateService
    private lateinit var coderTemplateController: CoderTemplateController

    @BeforeEach
    fun setUp() {
        coderTemplateService = mockk()
        coderTemplateController = CoderTemplateController(coderTemplateService)
    }

    @Test
    fun `getCoderTemplates should return list of templates`() {
        // Given
        val mockTemplates = listOf(
            CoderTemplateDto(
                id = "template1",
                name = "template1",
                displayName = "Template 1",
                description = "Test template",
                icon = "/icon/test.svg",
                defaultTtlMs = 3600000L,
                maxTtlMs = 28800000L,
                minAutostartIntervalMs = 3600000L,
                createdByName = "admin",
                updatedAt = LocalDateTime.now(),
                organizationId = "org1",
                provisioner = "terraform",
                activeVersionId = "v1.0.0",
                workspaceCount = 5,
                deprecated = false
            )
        )
        every { coderTemplateService.getCoderTemplates() } returns mockTemplates

        // When
        val result = coderTemplateController.getCoderTemplates()

        // Then
        assertNotNull(result)
        assertEquals(200, result.statusCode.value())
        val body = result.body
        assertNotNull(body)
        assertEquals(1, body!!.size)
        assertEquals("template1", body[0].id)
        assertEquals("Template 1", body[0].displayName)
    }

    @Test
    fun `getCoderTemplate should return specific template when found`() {
        // Given
        val templateId = "template1"
        val mockTemplate = CoderTemplateDto(
            id = templateId,
            name = templateId,
            displayName = "Template 1",
            description = "Test template",
            icon = "/icon/test.svg",
            defaultTtlMs = 3600000L,
            maxTtlMs = 28800000L,
            minAutostartIntervalMs = 3600000L,
            createdByName = "admin",
            updatedAt = LocalDateTime.now(),
            organizationId = "org1",
            provisioner = "terraform",
            activeVersionId = "v1.0.0",
            workspaceCount = 5,
            deprecated = false
        )
        every { coderTemplateService.getCoderTemplate(templateId) } returns mockTemplate

        // When
        val result = coderTemplateController.getCoderTemplate(templateId)

        // Then
        assertNotNull(result)
        assertEquals(200, result.statusCode.value())
        val body = result.body
        assertNotNull(body)
        assertEquals(templateId, body!!.id)
        assertEquals("Template 1", body.displayName)
    }

    @Test
    fun `getCoderTemplate should return 404 when template not found`() {
        // Given
        val templateId = "nonexistent"
        every { coderTemplateService.getCoderTemplate(templateId) } returns null

        // When
        val result = coderTemplateController.getCoderTemplate(templateId)

        // Then
        assertNotNull(result)
        assertEquals(404, result.statusCode.value())
    }
}
