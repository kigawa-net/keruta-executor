package net.kigawa.keruta.executor.controller

import io.mockk.every
import io.mockk.mockk
import net.kigawa.keruta.executor.service.LogStreamingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class LogStreamingControllerTest {

    private lateinit var logStreamingService: LogStreamingService
    private lateinit var logStreamingController: LogStreamingController

    @BeforeEach
    fun setUp() {
        logStreamingService = mockk()
        logStreamingController = LogStreamingController(logStreamingService)
    }

    @Test
    fun `streamWorkspaceLogs should return SSE emitter`() {
        // Given
        val workspaceId = "workspace123"
        val mockEmitter = mockk<SseEmitter>()
        every { 
            logStreamingService.startLogStream(any(), workspaceId, null) 
        } returns mockEmitter

        // When
        val result = logStreamingController.streamWorkspaceLogs(workspaceId, false)

        // Then
        assertNotNull(result)
        assertEquals(mockEmitter, result)
    }

    @Test
    fun `streamTaskLogs should return SSE emitter`() {
        // Given
        val taskId = "task123"
        val mockEmitter = mockk<SseEmitter>()
        every { 
            logStreamingService.startLogStream(any(), null, taskId) 
        } returns mockEmitter

        // When
        val result = logStreamingController.streamTaskLogs(taskId, false)

        // Then
        assertNotNull(result)
        assertEquals(mockEmitter, result)
    }

    @Test
    fun `stopLogStream should return success response`() {
        // Given
        val streamId = "stream123"
        every { logStreamingService.stopLogStream(streamId) } returns Unit

        // When
        val result = logStreamingController.stopLogStream(streamId)

        // Then
        assertNotNull(result)
        assertEquals("stopped", result["status"])
        assertEquals(streamId, result["streamId"])
    }

    @Test
    fun `getActiveStreams should return active streams list`() {
        // Given
        val mockStreams = listOf("stream1", "stream2", "stream3")
        every { logStreamingService.getActiveStreams() } returns mockStreams

        // When
        val result = logStreamingController.getActiveStreams()

        // Then
        assertNotNull(result)
        assertEquals(mockStreams, result["activeStreams"])
    }

    @Test
    fun `healthCheck should return health status`() {
        // Given
        val mockStreams = listOf("stream1", "stream2")
        every { logStreamingService.getActiveStreams() } returns mockStreams

        // When
        val result = logStreamingController.healthCheck()

        // Then
        assertNotNull(result)
        assertEquals("healthy", result["status"])
        assertEquals(2, result["activeStreamsCount"])
        assertNotNull(result["timestamp"])
    }
}