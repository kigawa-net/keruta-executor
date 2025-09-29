package net.kigawa.keruta.executor.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.web.socket.WebSocketSession

class LogWebSocketServiceTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var webSocketSession: WebSocketSession
    private lateinit var logWebSocketService: LogWebSocketService

    @BeforeEach
    fun setup() {
        objectMapper = ObjectMapper()
        webSocketSession = mock(WebSocketSession::class.java)
        logWebSocketService = LogWebSocketService(objectMapper)
    }

    @Test
    fun `should create service successfully`() {
        assertNotNull(logWebSocketService)
    }

    @Test
    fun `should return empty list when no active streams`() {
        val activeStreams = logWebSocketService.getActiveStreams()
        assertEquals(0, activeStreams.size)
    }

    @Test
    fun `should handle stopping non-existent stream gracefully`() {
        // Should not throw exception
        assertDoesNotThrow {
            logWebSocketService.stopLogStream("non-existent-session")
        }
    }

    @Test
    fun `should handle pause and resume of non-existent stream gracefully`() {
        // Should not throw exception
        assertDoesNotThrow {
            logWebSocketService.pauseLogStream("non-existent-session")
            logWebSocketService.resumeLogStream("non-existent-session")
        }
    }
}
