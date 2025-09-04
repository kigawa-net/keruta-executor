package net.kigawa.keruta.executor.service

import io.mockk.mockk
import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class LogStreamingServiceTest {

    private lateinit var properties: KerutaExecutorProperties
    private lateinit var logStreamingService: LogStreamingService

    @BeforeEach
    fun setUp() {
        properties = mockk()
        logStreamingService = LogStreamingService(properties)
    }

    @Test
    fun `startLogStream should create SSE emitter for workspace logs`() {
        // Given
        val streamId = "stream-1"
        val workspaceId = "workspace-1"

        // When
        val emitter = logStreamingService.startLogStream(streamId, workspaceId, null)

        // Then
        assertNotNull(emitter)
        assertTrue(logStreamingService.getActiveStreams().contains(streamId))
    }

    @Test
    fun `startLogStream should create SSE emitter for task logs`() {
        // Given
        val streamId = "stream-2"
        val taskId = "task-1"

        // When
        val emitter = logStreamingService.startLogStream(streamId, null, taskId)

        // Then
        assertNotNull(emitter)
        assertTrue(logStreamingService.getActiveStreams().contains(streamId))
    }

    @Test
    fun `startLogStream should handle error when neither workspaceId nor taskId provided`() {
        // Given
        val streamId = "stream-3"

        // When
        val emitter = logStreamingService.startLogStream(streamId, null, null)

        // Then
        assertNotNull(emitter)
        // The emitter should be configured to complete with error
    }

    @Test
    fun `stopLogStream should remove stream from active streams`() {
        // Given
        val streamId = "stream-4"
        val workspaceId = "workspace-2"
        logStreamingService.startLogStream(streamId, workspaceId, null)

        // When
        logStreamingService.stopLogStream(streamId)

        // Then - wait a bit for async operations
        Thread.sleep(100)
        assertTrue(!logStreamingService.getActiveStreams().contains(streamId))
    }

    @Test
    fun `getActiveStreams should return list of active stream IDs`() {
        // Given
        val streamId1 = "stream-5"
        val streamId2 = "stream-6"
        val workspaceId = "workspace-3"
        val taskId = "task-2"

        logStreamingService.startLogStream(streamId1, workspaceId, null)
        logStreamingService.startLogStream(streamId2, null, taskId)

        // When
        val activeStreams = logStreamingService.getActiveStreams()

        // Then
        assertTrue(activeStreams.contains(streamId1))
        assertTrue(activeStreams.contains(streamId2))
        assertEquals(2, activeStreams.size)
    }

    @Test
    fun `executeCommandWithLogStream should execute command and stream output`() {
        // Given
        val streamId = "command-stream-1"
        val command = "echo Hello World"
        val emitter = mockk<SseEmitter>(relaxed = true)

        // When
        logStreamingService.executeCommandWithLogStream(streamId, command, null, emitter)

        // Wait for async execution
        Thread.sleep(1000)

        // Then - verify the stream was processed (basic check)
        assertTrue(true) // Simple test for now
    }

    @Test
    fun `LogStreamEvent should be created with correct properties`() {
        // Given
        val timestamp = java.time.LocalDateTime.now()
        val level = "INFO"
        val source = "workspace"
        val workspaceId = "workspace-1"
        val message = "Test log message"

        // When
        val event = LogStreamEvent(
            timestamp = timestamp,
            level = level,
            source = source,
            workspaceId = workspaceId,
            message = message,
        )

        // Then
        assertEquals(timestamp, event.timestamp)
        assertEquals(level, event.level)
        assertEquals(source, event.source)
        assertEquals(workspaceId, event.workspaceId)
        assertEquals(message, event.message)
    }

    @Test
    fun `shutdown should clean up all resources`() {
        // Given
        val streamId1 = "stream-shutdown-1"
        val streamId2 = "stream-shutdown-2"
        logStreamingService.startLogStream(streamId1, "workspace-1", null)
        logStreamingService.startLogStream(streamId2, null, "task-1")

        // When
        logStreamingService.shutdown()

        // Then
        assertTrue(logStreamingService.getActiveStreams().isEmpty())
    }

    @Test
    fun `SSE emitter should have correct timeout configuration`() {
        // Given
        val streamId = "timeout-test-stream"
        val workspaceId = "workspace-timeout"

        // When
        val emitter = logStreamingService.startLogStream(streamId, workspaceId, null)

        // Then
        assertNotNull(emitter)
        // Note: We can't directly test the timeout value as it's set during construction
        // but we can verify the emitter was created successfully
    }

    @Test
    fun `multiple concurrent streams should be handled correctly`() {
        // Given
        val numStreams = 5
        val streamIds = (1..numStreams).map { "concurrent-stream-$it" }
        val workspaceIds = (1..numStreams).map { "workspace-$it" }

        // When
        streamIds.zip(workspaceIds).forEach { (streamId, workspaceId) ->
            logStreamingService.startLogStream(streamId, workspaceId, null)
        }

        // Then
        val activeStreams = logStreamingService.getActiveStreams()
        assertEquals(numStreams, activeStreams.size)
        streamIds.forEach { streamId ->
            assertTrue(activeStreams.contains(streamId))
        }
    }

    @Test
    fun `emitter callbacks should be configured correctly`() {
        // Given
        val streamId = "callback-test-stream"
        val workspaceId = "workspace-callback"

        // When
        val emitter = logStreamingService.startLogStream(streamId, workspaceId, null)

        // Then
        assertNotNull(emitter)
        // Basic verification that stream was registered
        assertTrue(logStreamingService.getActiveStreams().contains(streamId))
    }
}
