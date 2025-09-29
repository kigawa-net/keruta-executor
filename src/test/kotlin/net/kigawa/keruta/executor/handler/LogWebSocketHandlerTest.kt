package net.kigawa.keruta.executor.handler

import com.fasterxml.jackson.databind.ObjectMapper
import net.kigawa.keruta.executor.service.LogWebSocketService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class LogWebSocketHandlerTest {

    private lateinit var logWebSocketService: LogWebSocketService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var logWebSocketHandler: LogWebSocketHandler

    @BeforeEach
    fun setup() {
        logWebSocketService = mock(LogWebSocketService::class.java)
        objectMapper = ObjectMapper()
        logWebSocketHandler = LogWebSocketHandler(logWebSocketService, objectMapper)
    }

    @Test
    fun `should create handler successfully`() {
        assertNotNull(logWebSocketHandler)
    }

    @Test
    fun `should create LogStreamControlRequest data class`() {
        val request = LogStreamControlRequest("start", "workspace-123")
        assertEquals("start", request.action)
        assertEquals("workspace-123", request.workspaceId)
    }

    @Test
    fun `should create LogStreamErrorResponse data class`() {
        val response = LogStreamErrorResponse(123L, "test error")
        assertEquals(123L, response.timestamp)
        assertEquals("test error", response.error)
    }
}
