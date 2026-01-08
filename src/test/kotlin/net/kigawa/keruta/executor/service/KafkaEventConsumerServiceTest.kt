package net.kigawa.keruta.executor.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment

class KafkaEventConsumerServiceTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var sessionMonitoringService: SessionMonitoringService
    private lateinit var kafkaEventConsumerService: KafkaEventConsumerService

    @BeforeEach
    fun setUp() {
        objectMapper = mockk(relaxed = true)
        sessionMonitoringService = mockk(relaxed = true)
        kafkaEventConsumerService = KafkaEventConsumerService(objectMapper, sessionMonitoringService)
    }

    @Test
    fun `consumeSessionEvents should process session status changed event`() {
        // Given
        val message = """{"eventType":"sessionStatusChanged","sessionId":"session1","newStatus":"ACTIVE","previousStatus":"PENDING","eventId":"event1"}"""
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)

        val mockJsonNode = mockk<JsonNode>(relaxed = true)
        every { objectMapper.readTree(message) } returns mockJsonNode
        every { mockJsonNode.get("eventType")?.asText() } returns "sessionStatusChanged"
        every { mockJsonNode.get("sessionId")?.asText() } returns "session1"
        every { mockJsonNode.get("newStatus")?.asText() } returns "ACTIVE"
        every { mockJsonNode.get("previousStatus")?.asText() } returns "PENDING"
        every { mockJsonNode.get("eventId")?.asText() } returns "event1"

        // When
        kafkaEventConsumerService.consumeSessionEvents(
            message = message,
            topic = "keruta.sessions",
            partition = 0,
            offset = 1L,
            acknowledgment = acknowledgment
        )

        // Then
        verify { acknowledgment.acknowledge() }
        verify { objectMapper.readTree(message) }
    }

    @Test
    fun `consumeSessionEvents should process session created event`() {
        // Given
        val message = """{"eventType":"sessionCreated","sessionId":"session1","sessionName":"Test Session","eventId":"event1"}"""
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)

        val mockJsonNode = mockk<JsonNode>(relaxed = true)
        every { objectMapper.readTree(message) } returns mockJsonNode
        every { mockJsonNode.get("eventType")?.asText() } returns "sessionCreated"
        every { mockJsonNode.get("sessionId")?.asText() } returns "session1"
        every { mockJsonNode.get("sessionName")?.asText() } returns "Test Session"
        every { mockJsonNode.get("eventId")?.asText() } returns "event1"

        // When
        kafkaEventConsumerService.consumeSessionEvents(
            message = message,
            topic = "keruta.sessions",
            partition = 0,
            offset = 1L,
            acknowledgment = acknowledgment
        )

        // Then
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `consumeWorkspaceEvents should process workspace created event`() {
        // Given
        val message = """{"eventType":"workspaceCreated","workspaceId":"ws1","sessionId":"session1","workspaceName":"workspace1","eventId":"event1"}"""
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)

        val mockJsonNode = mockk<JsonNode>(relaxed = true)
        every { objectMapper.readTree(message) } returns mockJsonNode
        every { mockJsonNode.get("eventType")?.asText() } returns "workspaceCreated"
        every { mockJsonNode.get("workspaceId")?.asText() } returns "ws1"
        every { mockJsonNode.get("sessionId")?.asText() } returns "session1"
        every { mockJsonNode.get("workspaceName")?.asText() } returns "workspace1"
        every { mockJsonNode.get("eventId")?.asText() } returns "event1"

        // When
        kafkaEventConsumerService.consumeWorkspaceEvents(
            message = message,
            topic = "keruta.workspaces",
            partition = 0,
            offset = 1L,
            acknowledgment = acknowledgment
        )

        // Then
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `consumeTaskEvents should process task completed event`() {
        // Given
        val message = """{"eventType":"taskCompleted","taskId":"task1","sessionId":"session1","exitCode":0,"duration":5000,"eventId":"event1"}"""
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)

        val mockJsonNode = mockk<JsonNode>(relaxed = true)
        every { objectMapper.readTree(message) } returns mockJsonNode
        every { mockJsonNode.get("eventType")?.asText() } returns "taskCompleted"
        every { mockJsonNode.get("taskId")?.asText() } returns "task1"
        every { mockJsonNode.get("sessionId")?.asText() } returns "session1"
        every { mockJsonNode.get("exitCode")?.asInt() } returns 0
        every { mockJsonNode.get("duration")?.asLong() } returns 5000L
        every { mockJsonNode.get("eventId")?.asText() } returns "event1"

        // When
        kafkaEventConsumerService.consumeTaskEvents(
            message = message,
            topic = "keruta.tasks",
            partition = 0,
            offset = 1L,
            acknowledgment = acknowledgment
        )

        // Then
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `handleSessionEvent should handle unknown event type gracefully`() {
        // Given
        val mockJsonNode = mockk<JsonNode>(relaxed = true)
        every { mockJsonNode.get("eventType")?.asText() } returns "unknownEvent"
        every { mockJsonNode.get("sessionId")?.asText() } returns "session1"

        // When
        kafkaEventConsumerService.handleSessionEvent(mockJsonNode)

        // Then - Should not throw exception
        verify { mockJsonNode.get("eventType") }
        verify { mockJsonNode.get("sessionId") }
    }

    @Test
    fun `handleWorkspaceEvent should handle workspace stopped event with reason`() {
        // Given
        val mockJsonNode = mockk<JsonNode>(relaxed = true)
        every { mockJsonNode.get("eventType")?.asText() } returns "workspaceStopped"
        every { mockJsonNode.get("workspaceId")?.asText() } returns "ws1"
        every { mockJsonNode.get("sessionId")?.asText() } returns "session1"
        every { mockJsonNode.get("reason")?.asText() } returns "auto-stop"

        // When
        kafkaEventConsumerService.handleWorkspaceEvent(mockJsonNode)

        // Then - Should not throw exception
        verify { mockJsonNode.get("eventType") }
        verify { mockJsonNode.get("workspaceId") }
        verify { mockJsonNode.get("sessionId") }
        verify { mockJsonNode.get("reason") }
    }

    @Test
    fun `handleTaskEvent should handle task failed event with error message`() {
        // Given
        val mockJsonNode = mockk<JsonNode>(relaxed = true)
        every { mockJsonNode.get("eventType")?.asText() } returns "taskFailed"
        every { mockJsonNode.get("taskId")?.asText() } returns "task1"
        every { mockJsonNode.get("sessionId")?.asText() } returns "session1"
        every { mockJsonNode.get("errorMessage")?.asText() } returns "Command failed"
        every { mockJsonNode.get("exitCode")?.asInt() } returns 1

        // When
        kafkaEventConsumerService.handleTaskEvent(mockJsonNode)

        // Then - Should not throw exception
        verify { mockJsonNode.get("eventType") }
        verify { mockJsonNode.get("taskId") }
        verify { mockJsonNode.get("sessionMessage") }
        verify { mockJsonNode.get("errorMessage") }
        verify { mockJsonNode.get("exitCode") }
    }
}