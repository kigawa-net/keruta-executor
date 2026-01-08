package net.kigawa.keruta.executor.controller

import io.mockk.every
import io.mockk.mockk
import net.kigawa.keruta.executor.service.CoderWorkspaceDto
import net.kigawa.keruta.executor.service.CoderWorkspaceService
import net.kigawa.keruta.executor.service.CreateCoderWorkspaceRequest
import net.kigawa.keruta.executor.service.CoderTemplateDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class WorkspaceControllerTest {

    private lateinit var coderWorkspaceService: CoderWorkspaceService
    private lateinit var workspaceController: WorkspaceController

    @BeforeEach
    fun setUp() {
        coderWorkspaceService = mockk()
        workspaceController = WorkspaceController(coderWorkspaceService)
    }

    @Test
    fun `getWorkspaces should return all workspaces when no sessionId provided`() {
        // Given
        val mockWorkspaces = listOf(
            CoderWorkspaceDto(
                id = "ws1",
                name = "workspace1",
                ownerId = "user1",
                ownerName = "User One",
                templateId = "template1",
                templateName = "Template 1",
                templateDisplayName = "Template One",
                templateIcon = "/icon/test.svg",
                status = "running",
                health = "healthy",
                accessUrl = "https://coder.example.com/workspaces/workspace1",
                autoStart = false,
                autoStop = false,
                lastUsedAt = LocalDateTime.now(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )
        )

        every { coderWorkspaceService.getAllWorkspaces() } returns mockWorkspaces

        // When
        val result = workspaceController.getWorkspaces(null)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.OK, result.statusCode)
        val body = result.body
        assertNotNull(body)
        assertEquals(1, body?.size)
        assertEquals("ws1", body?.get(0)?.id)
    }

    @Test
    fun `getWorkspaces should return filtered workspaces when sessionId provided`() {
        // Given
        val sessionId = "session1"
        val mockWorkspaces = listOf(
            CoderWorkspaceDto(
                id = "ws1",
                name = "workspace1",
                ownerId = "user1",
                ownerName = "User One",
                templateId = "template1",
                templateName = "Template 1",
                templateDisplayName = "Template One",
                templateIcon = "/icon/test.svg",
                status = "running",
                health = "healthy",
                accessUrl = "https://coder.example.com/workspaces/workspace1",
                autoStart = false,
                autoStop = false,
                lastUsedAt = LocalDateTime.now(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )
        )

        every { coderWorkspaceService.getWorkspacesBySessionId(sessionId) } returns mockWorkspaces

        // When
        val result = workspaceController.getWorkspaces(sessionId)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.OK, result.statusCode)
        val body = result.body
        assertNotNull(body)
        assertEquals(1, body?.size)
        assertEquals("ws1", body?.get(0)?.id)
    }

    @Test
    fun `getWorkspace should return workspace when found`() {
        // Given
        val workspaceId = "ws1"
        val mockWorkspace = CoderWorkspaceDto(
            id = workspaceId,
            name = "workspace1",
            ownerId = "user1",
            ownerName = "User One",
            templateId = "template1",
            templateName = "Template 1",
            templateDisplayName = "Template One",
            templateIcon = "/icon/test.svg",
            status = "running",
            health = "healthy",
            accessUrl = "https://coder.example.com/workspaces/workspace1",
            autoStart = false,
            autoStop = false,
            lastUsedAt = LocalDateTime.now(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        every { coderWorkspaceService.getWorkspace(workspaceId) } returns mockWorkspace

        // When
        val result = workspaceController.getWorkspace(workspaceId)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.OK, result.statusCode)
        val body = result.body
        assertNotNull(body)
        assertEquals(workspaceId, body?.id)
    }

    @Test
    fun `getWorkspace should return 404 when workspace not found`() {
        // Given
        val workspaceId = "nonexistent"
        every { coderWorkspaceService.getWorkspace(workspaceId) } returns null

        // When
        val result = workspaceController.getWorkspace(workspaceId)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.NOT_FOUND, result.statusCode)
    }

    @Test
    fun `createWorkspace should create and return workspace when successful`() {
        // Given
        val request = CreateCoderWorkspaceRequest(
            name = "new-workspace",
            templateId = "template1",
            richParameterValues = null
        )

        val mockWorkspace = CoderWorkspaceDto(
            id = "ws1",
            name = "new-workspace",
            ownerId = "user1",
            ownerName = "User One",
            templateId = "template1",
            templateName = "Template 1",
            templateDisplayName = "Template One",
            templateIcon = "/icon/test.svg",
            status = "pending",
            health = "unknown",
            accessUrl = "https://coder.example.com/workspaces/new-workspace",
            autoStart = false,
            autoStop = false,
            lastUsedAt = LocalDateTime.now(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        every { coderWorkspaceService.createWorkspace(request) } returns mockWorkspace

        // When
        val result = workspaceController.createWorkspace(request)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.OK, result.statusCode)
        val body = result.body
        assertNotNull(body)
        assertEquals("ws1", body?.id)
        assertEquals("new-workspace", body?.name)
    }

    @Test
    fun `createWorkspace should return 500 when creation fails`() {
        // Given
        val request = CreateCoderWorkspaceRequest(
            name = "new-workspace",
            templateId = "template1",
            richParameterValues = null
        )

        every { coderWorkspaceService.createWorkspace(request) } throws RuntimeException("Creation failed")

        // When
        val result = workspaceController.createWorkspace(request)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
    }

    @Test
    fun `startWorkspace should return started workspace when successful`() {
        // Given
        val workspaceId = "ws1"
        val mockWorkspace = CoderWorkspaceDto(
            id = workspaceId,
            name = "workspace1",
            ownerId = "user1",
            ownerName = "User One",
            templateId = "template1",
            templateName = "Template 1",
            templateDisplayName = "Template One",
            templateIcon = "/icon/test.svg",
            status = "running",
            health = "healthy",
            accessUrl = "https://coder.example.com/workspaces/workspace1",
            autoStart = false,
            autoStop = false,
            lastUsedAt = LocalDateTime.now(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        every { coderWorkspaceService.startWorkspace(workspaceId) } returns mockWorkspace

        // When
        val result = workspaceController.startWorkspace(workspaceId)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.OK, result.statusCode)
        val body = result.body
        assertNotNull(body)
        assertEquals(workspaceId, body?.id)
        assertEquals("running", body?.status)
    }

    @Test
    fun `startWorkspace should return 404 when workspace not found for start`() {
        // Given
        val workspaceId = "nonexistent"
        every { coderWorkspaceService.startWorkspace(workspaceId) } returns null

        // When
        val result = workspaceController.startWorkspace(workspaceId)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.NOT_FOUND, result.statusCode)
    }

    @Test
    fun `stopWorkspace should return stopped workspace when successful`() {
        // Given
        val workspaceId = "ws1"
        val mockWorkspace = CoderWorkspaceDto(
            id = workspaceId,
            name = "workspace1",
            ownerId = "user1",
            ownerName = "User One",
            templateId = "template1",
            templateName = "Template 1",
            templateDisplayName = "Template One",
            templateIcon = "/icon/test.svg",
            status = "stopped",
            health = "unknown",
            accessUrl = "https://coder.example.com/workspaces/workspace1",
            autoStart = false,
            autoStop = false,
            lastUsedAt = LocalDateTime.now(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        every { coderWorkspaceService.stopWorkspace(workspaceId) } returns mockWorkspace

        // When
        val result = workspaceController.stopWorkspace(workspaceId)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.OK, result.statusCode)
        val body = result.body
        assertNotNull(body)
        assertEquals(workspaceId, body?.id)
        assertEquals("stopped", body?.status)
    }

    @Test
    fun `deleteWorkspace should return 204 when deletion is successful`() {
        // Given
        val workspaceId = "ws1"
        every { coderWorkspaceService.deleteWorkspace(workspaceId) } returns true

        // When
        val result = workspaceController.deleteWorkspace(workspaceId)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.NO_CONTENT, result.statusCode)
    }

    @Test
    fun `deleteWorkspace should return 404 when workspace not found for deletion`() {
        // Given
        val workspaceId = "nonexistent"
        every { coderWorkspaceService.deleteWorkspace(workspaceId) } returns false

        // When
        val result = workspaceController.deleteWorkspace(workspaceId)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.NOT_FOUND, result.statusCode)
    }

    @Test
    fun `getWorkspaceTemplates should return list of templates when successful`() {
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
                deprecated = false,
            )
        )

        every { coderWorkspaceService.getWorkspaceTemplates() } returns mockTemplates

        // When
        val result = workspaceController.getWorkspaceTemplates()

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.OK, result.statusCode)
        val body = result.body
        assertNotNull(body)
        assertEquals(1, body?.size)
        assertEquals("template1", body?.get(0)?.id)
    }

    @Test
    fun `getWorkspaceTemplates should return 500 when fetching templates fails`() {
        // Given
        every { coderWorkspaceService.getWorkspaceTemplates() } throws RuntimeException("Template fetch failed")

        // When
        val result = workspaceController.getWorkspaceTemplates()

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
    }
}