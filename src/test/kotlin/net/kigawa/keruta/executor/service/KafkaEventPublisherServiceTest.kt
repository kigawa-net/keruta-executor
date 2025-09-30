package net.kigawa.keruta.executor.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture

class KafkaEventPublisherServiceTest {

    private val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
    private val objectMapper = mockk<ObjectMapper>()
    private val kafkaEventPublisherService = KafkaEventPublisherService(kafkaTemplate, objectMapper)

    @Test
    fun `publishWorkspaceEvent should send event to workspace topic`() {
        // Given
        val event = WorkspaceEvent(
            eventType = "workspaceCreated",
            workspaceId = "ws-123",
            sessionId = "session-123",
            workspaceName = "test-workspace"
        )
        val eventJson = """{"eventType":"workspaceCreated","workspaceId":"ws-123"}"""
        val future = CompletableFuture<SendResult<String, String>>()

        every { objectMapper.writeValueAsString(event) } returns eventJson
        every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns future

        // When
        kafkaEventPublisherService.publishWorkspaceEvent(event)

        // Then
        verify { kafkaTemplate.send("keruta.workspaces", "ws-123", eventJson) }
    }

    @Test
    fun `createWorkspaceCreatedEvent should create correct event`() {
        // When
        val event = kafkaEventPublisherService.createWorkspaceCreatedEvent(
            workspaceId = "ws-123",
            workspaceName = "test-workspace",
            sessionId = "session-123",
            templateId = "template-123"
        )

        // Then
        assert(event.eventType == "workspaceCreated")
        assert(event.workspaceId == "ws-123")
        assert(event.workspaceName == "test-workspace")
        assert(event.sessionId == "session-123")
        assert(event.templateId == "template-123")
        assert(event.source == "keruta-executor")
    }

    @Test
    fun `createWorkspaceStartedEvent should create correct event`() {
        // When
        val event = kafkaEventPublisherService.createWorkspaceStartedEvent(
            workspaceId = "ws-123",
            sessionId = "session-123"
        )

        // Then
        assert(event.eventType == "workspaceStarted")
        assert(event.workspaceId == "ws-123")
        assert(event.sessionId == "session-123")
        assert(event.source == "keruta-executor")
    }

    @Test
    fun `createWorkspaceStoppedEvent should create correct event`() {
        // When
        val event = kafkaEventPublisherService.createWorkspaceStoppedEvent(
            workspaceId = "ws-123",
            sessionId = "session-123",
            reason = "user requested"
        )

        // Then
        assert(event.eventType == "workspaceStopped")
        assert(event.workspaceId == "ws-123")
        assert(event.sessionId == "session-123")
        assert(event.reason == "user requested")
        assert(event.source == "keruta-executor")
    }

    @Test
    fun `createWorkspaceDeletedEvent should create correct event`() {
        // When
        val event = kafkaEventPublisherService.createWorkspaceDeletedEvent(
            workspaceId = "ws-123",
            sessionId = "session-123"
        )

        // Then
        assert(event.eventType == "workspaceDeleted")
        assert(event.workspaceId == "ws-123")
        assert(event.sessionId == "session-123")
        assert(event.source == "keruta-executor")
    }
}