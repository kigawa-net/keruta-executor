package net.kigawa.keruta.executor.service

import io.mockk.every
import io.mockk.mockk
import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestTemplate

class SessionMonitoringServiceTest {

    private lateinit var restTemplate: RestTemplate
    private lateinit var properties: KerutaExecutorProperties
    private lateinit var sessionMonitoringService: SessionMonitoringService

    @BeforeEach
    fun setUp() {
        restTemplate = mockk(relaxed = true)
        properties = mockk(relaxed = true)
        every { properties.apiBaseUrl } returns "http://localhost:8080"

        sessionMonitoringService = SessionMonitoringService(restTemplate, properties)
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
