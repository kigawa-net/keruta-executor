package net.kigawa.keruta.executor.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

class CoderWorkspaceServiceTest {

    private lateinit var restTemplate: RestTemplate
    private lateinit var properties: KerutaExecutorProperties
    private lateinit var coderTemplateService: CoderTemplateService
    private lateinit var coderWorkspaceService: CoderWorkspaceService

    @BeforeEach
    fun setUp() {
        restTemplate = mockk(relaxed = true)
        properties = mockk(relaxed = true)
        coderTemplateService = mockk(relaxed = true)

        every { properties.coder.baseUrl } returns "https://coder.example.com"
        every { properties.coder.token } returns "test-token"

        coderWorkspaceService = CoderWorkspaceService(restTemplate, properties, coderTemplateService)
    }

    @Test
    fun `getAllWorkspaces should return list of workspaces when successful`() {
        // Given
        val mockResponse = CoderPaginatedWorkspacesResponse(
            workspaces = listOf(
                CoderWorkspaceApiResponse(
                    id = "ws1",
                    name = "workspace1",
                    owner_id = "user1",
                    owner_name = "User One",
                    template_id = "template1",
                    template_name = "Template 1",
                    template_display_name = "Template One",
                    template_icon = "/icon/test.svg",
                    latest_build = LatestBuildApiResponse(status = "running", resources = listOf(ResourceApiResponse(health = "healthy"))),
                    autostart_schedule = null,
                    ttl_ms = null,
                    last_used_at = "2023-01-01T10:00:00Z",
                    created_at = "2023-01-01T09:00:00Z",
                    updated_at = "2023-01-01T10:00:00Z",
                )
            ),
            count = 1,
            after_id = null
        )

        every {
            restTemplate.exchange(any(), any(), any(), any())
        } throws RuntimeException("API error")

        // When
        val result = coderWorkspaceService.getAllWorkspaces()

        // Then
        assertNotNull(result)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getWorkspacesBySessionId should filter workspaces by session ID`() {
        // Given
        val mockResponse = CoderPaginatedWorkspacesResponse(
            workspaces = listOf(
                CoderWorkspaceApiResponse(
                    id = "ws1",
                    name = "session-123-workspace",
                    owner_id = "user1",
                    owner_name = "User One",
                    template_id = "template1",
                    template_name = "Template 1",
                    template_display_name = "Template One",
                    template_icon = "/icon/test.svg",
                    latest_build = null,
                    autostart_schedule = null,
                    ttl_ms = null,
                    last_used_at = null,
                    created_at = null,
                    updated_at = null,
                ),
                CoderWorkspaceApiResponse(
                    id = "ws2",
                    name = "other-workspace",
                    owner_id = "user1",
                    owner_name = "User One",
                    template_id = "template1",
                    template_name = "Template 1",
                    template_display_name = "Template One",
                    template_icon = "/icon/test.svg",
                    latest_build = null,
                    autostart_schedule = null,
                    ttl_ms = null,
                    last_used_at = null,
                    created_at = null,
                    updated_at = null,
                )
            ),
            count = 2,
            after_id = null
        )

        every {
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                any(),
                any()
            )
        } returns ResponseEntity.ok(mockResponse)

        // When
        val result = coderWorkspaceService.getWorkspacesBySessionId("123")

        // Then
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("ws1", result[0].id)
        assertEquals("session-123-workspace", result[0].name)
    }

    @Test
    fun `getWorkspace should return workspace when found`() {
        // Given
        val workspaceId = "ws1"
        val mockResponse = CoderWorkspaceApiResponse(
            id = workspaceId,
            name = "workspace1",
            owner_id = "user1",
            owner_name = "User One",
            template_id = "template1",
            template_name = "Template 1",
            template_display_name = "Template One",
            template_icon = "/icon/test.svg",
            latest_build = LatestBuildApiResponse(status = "running", resources = emptyList()),
            autostart_schedule = null,
            ttl_ms = null,
            last_used_at = null,
            created_at = null,
            updated_at = null,
        )

        every {
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                any(),
                any()
            )
        } returns ResponseEntity.ok(mockResponse)

        // When
        val result = coderWorkspaceService.getWorkspace(workspaceId)

        // Then
        assertNotNull(result)
        assertEquals(workspaceId, result?.id)
        assertEquals("workspace1", result?.name)
    }

    @Test
    fun `getWorkspace should return null when workspace not found`() {
        // Given
        val workspaceId = "nonexistent"
        every {
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                any(),
                any()
            )
        } throws HttpClientErrorException(HttpStatus.NOT_FOUND)

        // When
        val result = coderWorkspaceService.getWorkspace(workspaceId)

        // Then
        assertNull(result)
    }

    @Test
    fun `createWorkspace should create and return workspace when successful`() {
        // Given
        val request = CreateCoderWorkspaceRequest(
            name = "new-workspace",
            templateId = "template1",
            richParameterValues = null
        )

        val mockResponse = CoderWorkspaceApiResponse(
            id = "ws1",
            name = "new-workspace",
            owner_id = "user1",
            owner_name = "User One",
            template_id = "template1",
            template_name = "Template 1",
            template_display_name = "Template One",
            template_icon = "/icon/test.svg",
            latest_build = null,
            autostart_schedule = null,
            ttl_ms = null,
            last_used_at = null,
            created_at = null,
            updated_at = null,
        )

        every {
            restTemplate.exchange(any(), any(), any(), any())
        } returns ResponseEntity.ok(mockResponse)

        // When
        val result = coderWorkspaceService.createWorkspace(request)

        // Then
        assertNotNull(result)
        assertEquals("ws1", result.id)
        assertEquals("new-workspace", result.name)
    }

    @Test
    fun `startWorkspace should return updated workspace when successful`() {
        // Given
        val workspaceId = "ws1"

        every {
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                any<HttpEntity<Map<String, String>>>(),
                any()
            )
        } returns ResponseEntity.ok(null)

        // Mock getWorkspace call
        val mockWorkspace = CoderWorkspaceApiResponse(
            id = workspaceId,
            name = "workspace1",
            owner_id = "user1",
            owner_name = "User One",
            template_id = "template1",
            template_name = "Template 1",
            template_display_name = "Template One",
            template_icon = "/icon/test.svg",
            latest_build = LatestBuildApiResponse(status = "running", resources = emptyList()),
            autostart_schedule = null,
            ttl_ms = null,
            last_used_at = null,
            created_at = null,
            updated_at = null,
        )

        every {
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                any(),
                any()
            )
        } returns ResponseEntity.ok(mockWorkspace)

        // When
        val result = coderWorkspaceService.startWorkspace(workspaceId)

        // Then
        assertNotNull(result)
        assertEquals(workspaceId, result?.id)
        assertEquals("running", result?.status)
    }

    @Test
    fun `stopWorkspace should return updated workspace when successful`() {
        // Given
        val workspaceId = "ws1"

        every {
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                any<HttpEntity<Map<String, String>>>(),
                any()
            )
        } returns ResponseEntity.ok(null)

        // Mock getWorkspace call
        val mockWorkspace = CoderWorkspaceApiResponse(
            id = workspaceId,
            name = "workspace1",
            owner_id = "user1",
            owner_name = "User One",
            template_id = "template1",
            template_name = "Template 1",
            template_display_name = "Template One",
            template_icon = "/icon/test.svg",
            latest_build = LatestBuildApiResponse(status = "stopped", resources = emptyList()),
            autostart_schedule = null,
            ttl_ms = null,
            last_used_at = null,
            created_at = null,
            updated_at = null,
        )

        every {
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                any(),
                any()
            )
        } returns ResponseEntity.ok(mockWorkspace)

        // When
        val result = coderWorkspaceService.stopWorkspace(workspaceId)

        // Then
        assertNotNull(result)
        assertEquals(workspaceId, result?.id)
        assertEquals("stopped", result?.status)
    }

    @Test
    fun `deleteWorkspace should return true when deletion is successful`() {
        // Given
        val workspaceId = "ws1"

        every {
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                any(),
                any()
            )
        } returns ResponseEntity.noContent().build()

        // When
        val result = coderWorkspaceService.deleteWorkspace(workspaceId)

        // Then
        assertTrue(result)
    }

    @Test
    fun `getWorkspaceTemplates should return templates from coderTemplateService`() {
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

        every { coderTemplateService.getCoderTemplates() } returns mockTemplates

        // When
        val result = coderWorkspaceService.getWorkspaceTemplates()

        // Then
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals("template1", result[0].id)
        verify { coderTemplateService.getCoderTemplates() }
    }
}