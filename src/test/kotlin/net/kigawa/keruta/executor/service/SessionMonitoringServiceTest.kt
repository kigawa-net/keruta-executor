package net.kigawa.keruta.executor.service

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionMonitoringServiceTest {

    private lateinit var sessionApiClient: SessionApiClient
    private lateinit var circuitBreakerService: CircuitBreakerService
    private lateinit var workspaceCreationHandler: WorkspaceCreationHandler
    private lateinit var coderWorkspaceService: CoderWorkspaceService
    private lateinit var sessionMonitoringService: SessionMonitoringService

    @BeforeEach
    fun setUp() {
        sessionApiClient = mockk(relaxed = true)
        circuitBreakerService = mockk(relaxed = true)
        workspaceCreationHandler = mockk(relaxed = true)
        coderWorkspaceService = mockk(relaxed = true)

        sessionMonitoringService = SessionMonitoringService(
            sessionApiClient,
            circuitBreakerService,
            workspaceCreationHandler,
            coderWorkspaceService,
        )
    }

    @Test
    fun `SessionMonitoringService should be created successfully`() {
        // Given & When
        // Service is created in setUp()

        // Then
        assertNotNull(sessionMonitoringService)
    }

    @Test
    fun `monitorNewSessions should handle empty sessions gracefully`() {
        // Given & When
        sessionMonitoringService.monitorNewSessions()

        // Then - should not throw exception
        assertNotNull(sessionMonitoringService)
    }

    @Test
    fun `monitorActiveSessions should handle empty sessions gracefully`() {
        // Given & When
        sessionMonitoringService.monitorActiveSessions()

        // Then - should not throw exception
        assertNotNull(sessionMonitoringService)
    }
}
