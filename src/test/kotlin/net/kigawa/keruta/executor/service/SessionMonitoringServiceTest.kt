package net.kigawa.keruta.executor.service

import io.mockk.*
import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SessionMonitoringServiceTest {

    private lateinit var restTemplate: RestTemplate
    private lateinit var properties: KerutaExecutorProperties
    private lateinit var sessionMonitoringService: SessionMonitoringService

    @BeforeEach
    fun setUp() {
        restTemplate = mockk()
        properties = mockk()
        every { properties.apiBaseUrl } returns "http://localhost:8080"
        
        sessionMonitoringService = SessionMonitoringService(restTemplate, properties)
    }

    @Test
    fun `monitorNewSessions should process pending sessions successfully`() {
        // Given
        val pendingSession = SessionDto(
            id = "session1",
            name = "Test Session",
            status = "PENDING",
            tags = emptyList(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val workspace = WorkspaceDto(
            id = "workspace1",
            name = "Test Workspace",
            sessionId = "session1",
            status = "RUNNING",
            coderWorkspaceId = null,
            workspaceUrl = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        // Mock API calls
        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions?status=PENDING",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<SessionDto>>>()
            )
        } returns ResponseEntity.ok(listOf(pendingSession))

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces?sessionId=session1",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<WorkspaceDto>>>()
            )
        } returns ResponseEntity.ok(listOf(workspace))

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions/session1/status",
                HttpMethod.PUT,
                any<HttpEntity<UpdateSessionStatusRequest>>(),
                SessionDto::class.java
            )
        } returns ResponseEntity.ok(pendingSession.copy(status = "ACTIVE"))

        // When
        sessionMonitoringService.monitorNewSessions()

        // Then
        verify(exactly = 1) {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions?status=PENDING",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<SessionDto>>>()
            )
        }

        verify(exactly = 1) {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces?sessionId=session1",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<WorkspaceDto>>>()
            )
        }

        verify(exactly = 1) {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions/session1/status",
                HttpMethod.PUT,
                any<HttpEntity<UpdateSessionStatusRequest>>(),
                SessionDto::class.java
            )
        }
    }

    @Test
    fun `monitorNewSessions should handle sessions without workspaces`() {
        // Given
        val pendingSession = SessionDto(
            id = "session2",
            name = "Test Session Without Workspace",
            status = "PENDING",
            tags = emptyList(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val template = WorkspaceTemplateDto(
            id = "template1",
            name = "Default Template",
            description = "Default workspace template",
            version = "1.0",
            icon = null,
            isDefault = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val createdWorkspace = WorkspaceDto(
            id = "workspace2",
            name = "Test Session Without Workspace-workspace",
            sessionId = "session2",
            status = "PENDING",
            coderWorkspaceId = null,
            workspaceUrl = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        // Mock API calls
        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions?status=PENDING",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<SessionDto>>>()
            )
        } returns ResponseEntity.ok(listOf(pendingSession))

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces?sessionId=session2",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<WorkspaceDto>>>()
            )
        } returns ResponseEntity.ok(emptyList())

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces/templates",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<WorkspaceTemplateDto>>>()
            )
        } returns ResponseEntity.ok(listOf(template))

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces",
                HttpMethod.POST,
                any<HttpEntity<CreateWorkspaceRequest>>(),
                WorkspaceDto::class.java
            )
        } returns ResponseEntity.ok(createdWorkspace)

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions/session2/status",
                HttpMethod.PUT,
                any<HttpEntity<UpdateSessionStatusRequest>>(),
                SessionDto::class.java
            )
        } returns ResponseEntity.ok(pendingSession.copy(status = "ACTIVE"))

        // When
        sessionMonitoringService.monitorNewSessions()

        // Then
        verify(exactly = 1) {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces",
                HttpMethod.POST,
                any<HttpEntity<CreateWorkspaceRequest>>(),
                WorkspaceDto::class.java
            )
        }

        verify(exactly = 1) {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions/session2/status",
                HttpMethod.PUT,
                any<HttpEntity<UpdateSessionStatusRequest>>(),
                SessionDto::class.java
            )
        }
    }

    @Test
    fun `monitorActiveSessions should start stopped workspaces`() {
        // Given
        val activeSession = SessionDto(
            id = "session3",
            name = "Active Session",
            status = "ACTIVE",
            tags = emptyList(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val stoppedWorkspace = WorkspaceDto(
            id = "workspace3",
            name = "Stopped Workspace",
            sessionId = "session3",
            status = "STOPPED",
            coderWorkspaceId = null,
            workspaceUrl = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        // Mock API calls
        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions?status=ACTIVE",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<SessionDto>>>()
            )
        } returns ResponseEntity.ok(listOf(activeSession))

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces?sessionId=session3",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<WorkspaceDto>>>()
            )
        } returns ResponseEntity.ok(listOf(stoppedWorkspace))

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces/workspace3/start",
                HttpMethod.POST,
                any<HttpEntity<Any>>(),
                WorkspaceDto::class.java
            )
        } returns ResponseEntity.ok(stoppedWorkspace.copy(status = "STARTING"))

        // When
        sessionMonitoringService.monitorActiveSessions()

        // Then
        verify(exactly = 1) {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces/workspace3/start",
                HttpMethod.POST,
                any<HttpEntity<Any>>(),
                WorkspaceDto::class.java
            )
        }
    }

    @Test
    fun `monitorActiveSessions should not start already running workspaces`() {
        // Given
        val activeSession = SessionDto(
            id = "session4",
            name = "Active Session",
            status = "ACTIVE",
            tags = emptyList(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val runningWorkspace = WorkspaceDto(
            id = "workspace4",
            name = "Running Workspace",
            sessionId = "session4",
            status = "RUNNING",
            coderWorkspaceId = null,
            workspaceUrl = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        // Mock API calls
        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions?status=ACTIVE",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<SessionDto>>>()
            )
        } returns ResponseEntity.ok(listOf(activeSession))

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces?sessionId=session4",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<WorkspaceDto>>>()
            )
        } returns ResponseEntity.ok(listOf(runningWorkspace))

        // When
        sessionMonitoringService.monitorActiveSessions()

        // Then
        verify(exactly = 0) {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces/workspace4/start",
                HttpMethod.POST,
                any<HttpEntity<Any>>(),
                WorkspaceDto::class.java
            )
        }
    }

    @Test
    fun `should handle HTTP client errors gracefully`() {
        // Given
        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions?status=PENDING",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<SessionDto>>>()
            )
        } throws HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request")

        // When & Then
        // Should not throw exception, just log the error
        sessionMonitoringService.monitorNewSessions()
    }

    @Test
    fun `should handle HTTP server errors gracefully`() {
        // Given
        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions?status=PENDING",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<SessionDto>>>()
            )
        } throws HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error")

        // When & Then
        // Should not throw exception, just log the error
        sessionMonitoringService.monitorNewSessions()
    }

    @Test
    fun `should handle network errors gracefully`() {
        // Given
        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions?status=PENDING",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<SessionDto>>>()
            )
        } throws ResourceAccessException("Connection refused")

        // When & Then
        // Should not throw exception, just log the error
        sessionMonitoringService.monitorNewSessions()
    }

    @Test
    fun `should create workspace request with correct parameters`() {
        // Given
        val session = SessionDto(
            id = "session5",
            name = "Test Session",
            status = "PENDING",
            tags = emptyList(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val template = WorkspaceTemplateDto(
            id = "template1",
            name = "Default Template",
            description = "Default workspace template",
            version = "1.0",
            icon = null,
            isDefault = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val createdWorkspace = WorkspaceDto(
            id = "workspace5",
            name = "${session.name}-workspace",
            sessionId = session.id,
            status = "PENDING",
            coderWorkspaceId = null,
            workspaceUrl = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions?status=PENDING",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<SessionDto>>>()
            )
        } returns ResponseEntity.ok(listOf(session))

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces?sessionId=session5",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<WorkspaceDto>>>()
            )
        } returns ResponseEntity.ok(emptyList())

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces/templates",
                HttpMethod.GET,
                null,
                any<ParameterizedTypeReference<List<WorkspaceTemplateDto>>>()
            )
        } returns ResponseEntity.ok(listOf(template))

        val capturedRequest = slot<HttpEntity<CreateWorkspaceRequest>>()
        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/workspaces",
                HttpMethod.POST,
                capture(capturedRequest),
                WorkspaceDto::class.java
            )
        } returns ResponseEntity.ok(createdWorkspace)

        every {
            restTemplate.exchange(
                "http://localhost:8080/api/v1/sessions/session5/status",
                HttpMethod.PUT,
                any<HttpEntity<UpdateSessionStatusRequest>>(),
                SessionDto::class.java
            )
        } returns ResponseEntity.ok(session.copy(status = "ACTIVE"))

        // When
        sessionMonitoringService.monitorNewSessions()

        // Then
        val request = capturedRequest.captured.body
        assertNotNull(request)
        assertEquals("Test Session-workspace", request.name)
        assertEquals("session5", request.sessionId)
        assertEquals("template1", request.templateId)
        assertEquals(true, request.automaticUpdates)
        assertEquals(3600000L, request.ttlMs)
    }
}