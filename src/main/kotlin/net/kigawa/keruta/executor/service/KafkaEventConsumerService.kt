package net.kigawa.keruta.executor.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

interface EventConsumerService {
    fun handleSessionEvent(eventJson: JsonNode)
    fun handleWorkspaceEvent(eventJson: JsonNode)
    fun handleTaskEvent(eventJson: JsonNode)
}

@Service
class KafkaEventConsumerService(
    private val objectMapper: ObjectMapper,
    private val sessionMonitoringService: SessionMonitoringService
) : EventConsumerService {

    @KafkaListener(
        topics = ["keruta.sessions"],
        groupId = "keruta-executor-session-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consumeSessionEvents(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        try {
            logger.debug { "Received session event from topic '$topic' [partition: $partition, offset: $offset]: $message" }

            val eventJson = objectMapper.readTree(message)
            handleSessionEvent(eventJson)

            acknowledgment.acknowledge()
            logger.debug { "Successfully processed session event: eventId=${eventJson.get("eventId")?.asText()}" }
        } catch (e: Exception) {
            logger.error(e) { "Error processing session event from topic '$topic': $message" }
        }
    }

    @KafkaListener(
        topics = ["keruta.workspaces"],
        groupId = "keruta-executor-workspace-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consumeWorkspaceEvents(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        try {
            logger.debug { "Received workspace event from topic '$topic' [partition: $partition, offset: $offset]: $message" }

            val eventJson = objectMapper.readTree(message)
            handleWorkspaceEvent(eventJson)

            acknowledgment.acknowledge()
            logger.debug { "Successfully processed workspace event: eventId=${eventJson.get("eventId")?.asText()}" }
        } catch (e: Exception) {
            logger.error(e) { "Error processing workspace event from topic '$topic': $message" }
        }
    }

    @KafkaListener(
        topics = ["keruta.tasks"],
        groupId = "keruta-executor-task-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consumeTaskEvents(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        try {
            logger.debug { "Received task event from topic '$topic' [partition: $partition, offset: $offset]: $message" }

            val eventJson = objectMapper.readTree(message)
            handleTaskEvent(eventJson)

            acknowledgment.acknowledge()
            logger.debug { "Successfully processed task event: eventId=${eventJson.get("eventId")?.asText()}" }
        } catch (e: Exception) {
            logger.error(e) { "Error processing task event from topic '$topic': $message" }
        }
    }

    override fun handleSessionEvent(eventJson: JsonNode) {
        val eventType = eventJson.get("eventType")?.asText()
        val sessionId = eventJson.get("sessionId")?.asText()

        when (eventType) {
            "sessionStatusChanged" -> {
                val newStatus = eventJson.get("newStatus")?.asText()
                val previousStatus = eventJson.get("previousStatus")?.asText()
                logger.info { "Session status changed: sessionId=$sessionId, status=$previousStatus -> $newStatus" }

                if (newStatus == "ACTIVE") {
                    sessionId?.let { sessionMonitoringService.triggerSessionWorkspaceCheck(it) }
                }
            }
            "sessionCreated" -> {
                val sessionName = eventJson.get("sessionName")?.asText()
                logger.info { "Session created: sessionId=$sessionId, name=$sessionName" }

                sessionId?.let { sessionMonitoringService.triggerSessionWorkspaceCheck(it) }
            }
            "sessionDeleted" -> {
                logger.info { "Session deleted: sessionId=$sessionId" }
            }
            else -> {
                logger.info { "Received session event: type=$eventType, sessionId=$sessionId" }
            }
        }
    }

    override fun handleWorkspaceEvent(eventJson: JsonNode) {
        val eventType = eventJson.get("eventType")?.asText()
        val workspaceId = eventJson.get("workspaceId")?.asText()
        val sessionId = eventJson.get("sessionId")?.asText()

        when (eventType) {
            "workspaceCreated" -> {
                val workspaceName = eventJson.get("workspaceName")?.asText()
                logger.info { "Workspace created: workspaceId=$workspaceId, sessionId=$sessionId, name=$workspaceName" }
            }
            "workspaceStarted" -> {
                logger.info { "Workspace started: workspaceId=$workspaceId, sessionId=$sessionId" }
            }
            "workspaceStopped" -> {
                val reason = eventJson.get("reason")?.asText()
                logger.info { "Workspace stopped: workspaceId=$workspaceId, sessionId=$sessionId, reason=$reason" }
            }
            "workspaceDeleted" -> {
                logger.info { "Workspace deleted: workspaceId=$workspaceId, sessionId=$sessionId" }
            }
            else -> {
                logger.info { "Received workspace event: type=$eventType, workspaceId=$workspaceId" }
            }
        }
    }

    override fun handleTaskEvent(eventJson: JsonNode) {
        val eventType = eventJson.get("eventType")?.asText()
        val taskId = eventJson.get("taskId")?.asText()
        val sessionId = eventJson.get("sessionId")?.asText()

        when (eventType) {
            "taskCreated" -> {
                val taskName = eventJson.get("taskName")?.asText()
                logger.info { "Task created: taskId=$taskId, sessionId=$sessionId, name=$taskName" }
            }
            "taskStatusChanged" -> {
                val newStatus = eventJson.get("newStatus")?.asText()
                val previousStatus = eventJson.get("previousStatus")?.asText()
                logger.info { "Task status changed: taskId=$taskId, status=$previousStatus -> $newStatus" }
            }
            "taskCompleted" -> {
                val exitCode = eventJson.get("exitCode")?.asInt()
                val duration = eventJson.get("duration")?.asLong()
                logger.info { "Task completed: taskId=$taskId, sessionId=$sessionId, exitCode=$exitCode, duration=${duration}ms" }
            }
            "taskFailed" -> {
                val errorMessage = eventJson.get("errorMessage")?.asText()
                val exitCode = eventJson.get("exitCode")?.asInt()
                logger.info { "Task failed: taskId=$taskId, sessionId=$sessionId, exitCode=$exitCode, error=$errorMessage" }
            }
            else -> {
                logger.info { "Received task event: type=$eventType, taskId=$taskId" }
            }
        }
    }
}