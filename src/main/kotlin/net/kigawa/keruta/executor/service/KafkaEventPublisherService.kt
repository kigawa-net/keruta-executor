package net.kigawa.keruta.executor.service

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

data class WorkspaceEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val source: String = "keruta-executor",
    val correlationId: String? = null,
    val eventType: String,
    val workspaceId: String,
    val sessionId: String,
    val workspaceName: String? = null,
    val templateId: String? = null,
    val reason: String? = null,
    val userId: String? = null
)

interface EventPublisherService {
    suspend fun publishWorkspaceEvent(event: WorkspaceEvent)
    suspend fun publishEvent(topicName: String, event: Any)
}

@Service
class KafkaEventPublisherService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : EventPublisherService {

    companion object {
        private const val WORKSPACE_TOPIC = "keruta.workspaces"
    }

    override suspend fun publishWorkspaceEvent(event: WorkspaceEvent) {
        publishEvent(WORKSPACE_TOPIC, event)
    }

    override suspend fun publishEvent(topicName: String, event: Any) {
        try {
            val eventJson = objectMapper.writeValueAsString(event)
            val eventKey = generateEventKey(event)

            logger.info { "Publishing event to topic '$topicName': type=${event::class.simpleName}" }

            val future: CompletableFuture<SendResult<String, String>> =
                kafkaTemplate.send(topicName, eventKey, eventJson)

            future.whenComplete { result, exception ->
                if (exception != null) {
                    logger.error(exception) { "Failed to publish event to topic '$topicName'" }
                } else {
                    logger.debug {
                        "Successfully published event to topic '$topicName': " +
                            "partition=${result.recordMetadata.partition()}, " +
                            "offset=${result.recordMetadata.offset()}"
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error publishing event to topic '$topicName'" }
            throw e
        }
    }

    private fun generateEventKey(event: Any): String {
        return when (event) {
            is WorkspaceEvent -> event.workspaceId
            else -> UUID.randomUUID().toString()
        }
    }

    fun createWorkspaceCreatedEvent(
        workspaceId: String,
        workspaceName: String,
        sessionId: String,
        templateId: String? = null,
        userId: String? = null
    ): WorkspaceEvent {
        return WorkspaceEvent(
            eventType = "workspaceCreated",
            workspaceId = workspaceId,
            workspaceName = workspaceName,
            sessionId = sessionId,
            templateId = templateId,
            userId = userId
        )
    }

    fun createWorkspaceStartedEvent(
        workspaceId: String,
        sessionId: String,
        userId: String? = null
    ): WorkspaceEvent {
        return WorkspaceEvent(
            eventType = "workspaceStarted",
            workspaceId = workspaceId,
            sessionId = sessionId,
            userId = userId
        )
    }

    fun createWorkspaceStoppedEvent(
        workspaceId: String,
        sessionId: String,
        reason: String? = null,
        userId: String? = null
    ): WorkspaceEvent {
        return WorkspaceEvent(
            eventType = "workspaceStopped",
            workspaceId = workspaceId,
            sessionId = sessionId,
            reason = reason,
            userId = userId
        )
    }

    fun createWorkspaceDeletedEvent(
        workspaceId: String,
        sessionId: String,
        userId: String? = null
    ): WorkspaceEvent {
        return WorkspaceEvent(
            eventType = "workspaceDeleted",
            workspaceId = workspaceId,
            sessionId = sessionId,
            userId = userId
        )
    }
}