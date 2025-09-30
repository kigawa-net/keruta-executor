package net.kigawa.keruta.executor.controller

import com.fasterxml.jackson.databind.ObjectMapper
import net.kigawa.keruta.executor.service.CoderWorkspaceService
import net.kigawa.keruta.executor.service.CreateCoderWorkspaceRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class ExecutorWebSocketHandler(
    private val coderWorkspaceService: CoderWorkspaceService,
    private val objectMapper: ObjectMapper,
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(ExecutorWebSocketHandler::class.java)
    private val sessions = mutableSetOf<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
        logger.info("Executor WebSocket connection established: {}", session.id)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
        logger.info("Executor WebSocket connection closed: {}", session.id)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val wsMessage = objectMapper.readValue(message.payload, WebSocketRequest::class.java)
            logger.info("Received WebSocket message: {} {}", wsMessage.method, wsMessage.path)

            val response = handleRequest(wsMessage)
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(response)))

        } catch (e: Exception) {
            logger.error("Error handling WebSocket message", e)
            val errorResponse = WebSocketError("unknown", 500, "Internal server error", e.message)
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(errorResponse)))
        }
    }

    private fun handleRequest(request: WebSocketRequest): Any {
        return try {
            when (request.method to request.path) {
                "GET" to "/workspaces" -> {
                    val sessionId = request.params["sessionId"]
                    val workspaces = if (sessionId != null) {
                        coderWorkspaceService.getWorkspacesBySessionId(sessionId)
                    } else {
                        coderWorkspaceService.getAllWorkspaces()
                    }
                    WebSocketResponse(request.id, 200, workspaces)
                }

                "GET" to "/workspaces/by-id" -> {
                    val workspaceId = request.params["id"] ?: throw IllegalArgumentException("Missing workspace ID")
                    val workspace = coderWorkspaceService.getWorkspace(workspaceId)
                    if (workspace != null) {
                        WebSocketResponse(request.id, 200, workspace)
                    } else {
                        WebSocketError(request.id, 404, "Workspace not found")
                    }
                }

                "POST" to "/workspaces" -> {
                    val createRequest = objectMapper.convertValue(request.data, CreateCoderWorkspaceRequest::class.java)
                    val workspace = coderWorkspaceService.createWorkspace(createRequest)
                    WebSocketResponse(request.id, 201, workspace)
                }

                "POST" to "/workspaces/start" -> {
                    val workspaceId = request.params["id"] ?: throw IllegalArgumentException("Missing workspace ID")
                    val workspace = coderWorkspaceService.startWorkspace(workspaceId)
                    if (workspace != null) {
                        WebSocketResponse(request.id, 200, workspace)
                    } else {
                        WebSocketError(request.id, 404, "Workspace not found")
                    }
                }

                "POST" to "/workspaces/stop" -> {
                    val workspaceId = request.params["id"] ?: throw IllegalArgumentException("Missing workspace ID")
                    val workspace = coderWorkspaceService.stopWorkspace(workspaceId)
                    if (workspace != null) {
                        WebSocketResponse(request.id, 200, workspace)
                    } else {
                        WebSocketError(request.id, 404, "Workspace not found")
                    }
                }

                "DELETE" to "/workspaces" -> {
                    val workspaceId = request.params["id"] ?: throw IllegalArgumentException("Missing workspace ID")
                    val success = coderWorkspaceService.deleteWorkspace(workspaceId)
                    if (success) {
                        WebSocketResponse(request.id, 204, null)
                    } else {
                        WebSocketError(request.id, 404, "Workspace not found")
                    }
                }

                "GET" to "/workspaces/templates" -> {
                    val templates = coderWorkspaceService.getWorkspaceTemplates()
                    WebSocketResponse(request.id, 200, templates)
                }

                else -> WebSocketError(request.id, 404, "Not found", "Unknown path: ${request.method} ${request.path}")
            }

        } catch (e: IllegalArgumentException) {
            WebSocketError(request.id, 400, "Bad request", e.message)
        } catch (e: Exception) {
            logger.error("Error processing request: ${request.method} ${request.path}", e)
            WebSocketError(request.id, 500, "Internal server error", e.message)
        }
    }
}

data class WebSocketRequest(
    val id: String,
    val action: String,
    val path: String,
    val method: String,
    val data: Any? = null,
    val params: Map<String, String> = emptyMap()
)

data class WebSocketResponse(
    val id: String,
    val status: Int,
    val data: Any? = null
)

data class WebSocketError(
    val id: String,
    val status: Int,
    val message: String,
    val details: String? = null
)
