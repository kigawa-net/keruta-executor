package net.kigawa.keruta.executor.handler

import com.fasterxml.jackson.databind.ObjectMapper
import net.kigawa.keruta.executor.service.LogWebSocketService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.net.URI

/**
 * WebSocket handler for log streaming.
 */
@Component
open class LogWebSocketHandler(
    private val logWebSocketService: LogWebSocketService,
    private val objectMapper: ObjectMapper,
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(LogWebSocketHandler::class.java)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info("WebSocket connection established: sessionId={}", session.id)

        // Parse query parameters from WebSocket URI
        val uri = session.uri ?: return
        val params = parseQueryParams(uri)

        val workspaceId = params["workspaceId"]
        val taskId = params["taskId"]
        val command = params["command"]

        when {
            workspaceId != null -> {
                logger.info("Starting workspace log stream: sessionId={} workspaceId={}", session.id, workspaceId)
                logWebSocketService.startWorkspaceLogStream(session.id, workspaceId, session)
            }
            taskId != null -> {
                logger.info("Starting task log stream: sessionId={} taskId={}", session.id, taskId)
                logWebSocketService.startTaskLogStream(session.id, taskId, session)
            }
            command != null -> {
                val workingDirectory = params["workingDirectory"]
                logger.info("Starting command stream: sessionId={} command={}", session.id, command)
                logWebSocketService.startCommandLogStream(session.id, command, workingDirectory, session)
            }
            else -> {
                logger.warn("No valid parameters provided for WebSocket connection: sessionId={}", session.id)
                sendError(session, "Either workspaceId, taskId, or command parameter must be provided")
                session.close()
            }
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        logger.debug("Received WebSocket message: sessionId={} message={}", session.id, message.payload)

        try {
            val request = objectMapper.readValue(message.payload, LogStreamControlRequest::class.java)

            when (request.action) {
                "start" -> {
                    // Already handled in afterConnectionEstablished
                    logger.debug("Start action received for already active stream: sessionId={}", session.id)
                }
                "stop" -> {
                    logger.info("Stop request received: sessionId={}", session.id)
                    logWebSocketService.stopLogStream(session.id)
                    session.close()
                }
                "pause" -> {
                    logger.info("Pause request received: sessionId={}", session.id)
                    logWebSocketService.pauseLogStream(session.id)
                }
                "resume" -> {
                    logger.info("Resume request received: sessionId={}", session.id)
                    logWebSocketService.resumeLogStream(session.id)
                }
                else -> {
                    sendError(session, "Unknown action: ${request.action}")
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling WebSocket message: sessionId={}", session.id, e)
            sendError(session, "Invalid message format: ${e.message}")
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info("WebSocket connection closed: sessionId={} status={}", session.id, status)
        logWebSocketService.stopLogStream(session.id)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error("WebSocket transport error: sessionId={}", session.id, exception)
        logWebSocketService.stopLogStream(session.id)
    }

    /**
     * Parse query parameters from URI.
     */
    private fun parseQueryParams(uri: URI): Map<String, String> {
        val query = uri.query ?: return emptyMap()
        return query.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    java.net.URLDecoder.decode(parts[0], "UTF-8") to java.net.URLDecoder.decode(parts[1], "UTF-8")
                } else {
                    null
                }
            }
            .toMap()
    }

    /**
     * Send error message to WebSocket client.
     */
    private fun sendError(session: WebSocketSession, message: String) {
        try {
            val errorResponse = LogStreamErrorResponse(
                timestamp = System.currentTimeMillis(),
                error = message,
            )
            val json = objectMapper.writeValueAsString(errorResponse)
            session.sendMessage(TextMessage(json))
        } catch (e: Exception) {
            logger.error("Failed to send error message: sessionId={}", session.id, e)
        }
    }
}

/**
 * Control request for log streaming.
 */
data class LogStreamControlRequest(
    // start, stop, pause, resume
    val action: String,
    val workspaceId: String? = null,
    val taskId: String? = null,
    val command: String? = null,
    val workingDirectory: String? = null,
)

/**
 * Error response for log streaming.
 */
data class LogStreamErrorResponse(
    val timestamp: Long,
    val error: String,
)
