package net.kigawa.keruta.executor.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Service for WebSocket-based log streaming.
 */
@Service
open class LogWebSocketService(
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(LogWebSocketService::class.java)

    // Store active WebSocket sessions and their associated streams
    private val activeSessions = ConcurrentHashMap<String, WebSocketSession>()
    private val activeStreams = ConcurrentHashMap<String, LogStreamContext>()

    // Executor for background log streaming tasks
    private val executorService: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable).apply {
            isDaemon = true
            name = "websocket-log-stream-${System.currentTimeMillis()}"
        }
    }

    /**
     * Start streaming logs from a workspace.
     */
    fun startWorkspaceLogStream(sessionId: String, workspaceId: String, session: WebSocketSession) {
        logger.info("Starting workspace log stream: sessionId={} workspaceId={}", sessionId, workspaceId)

        val context = LogStreamContext(
            sessionId = sessionId,
            session = session,
            streamType = StreamType.WORKSPACE,
            resourceId = workspaceId,
            isActive = AtomicBoolean(true),
            isPaused = AtomicBoolean(false),
        )

        activeSessions[sessionId] = session
        activeStreams[sessionId] = context

        startWorkspaceLogStreamInternal(context)
    }

    /**
     * Start streaming logs from a task.
     */
    fun startTaskLogStream(sessionId: String, taskId: String, session: WebSocketSession) {
        logger.info("Starting task log stream: sessionId={} taskId={}", sessionId, taskId)

        val context = LogStreamContext(
            sessionId = sessionId,
            session = session,
            streamType = StreamType.TASK,
            resourceId = taskId,
            isActive = AtomicBoolean(true),
            isPaused = AtomicBoolean(false),
        )

        activeSessions[sessionId] = session
        activeStreams[sessionId] = context

        startTaskLogStreamInternal(context)
    }

    /**
     * Start streaming output from a command.
     */
    fun startCommandLogStream(
        sessionId: String,
        command: String,
        workingDirectory: String?,
        session: WebSocketSession,
    ) {
        logger.info("Starting command log stream: sessionId={} command={}", sessionId, command)

        val context = LogStreamContext(
            sessionId = sessionId,
            session = session,
            streamType = StreamType.COMMAND,
            resourceId = command,
            workingDirectory = workingDirectory,
            isActive = AtomicBoolean(true),
            isPaused = AtomicBoolean(false),
        )

        activeSessions[sessionId] = session
        activeStreams[sessionId] = context

        startCommandLogStreamInternal(context)
    }

    /**
     * Stop a log stream.
     */
    fun stopLogStream(sessionId: String) {
        logger.info("Stopping log stream: sessionId={}", sessionId)

        activeStreams[sessionId]?.let { context ->
            context.isActive.set(false)
            context.process?.destroy()
        }

        activeStreams.remove(sessionId)
        activeSessions.remove(sessionId)
    }

    /**
     * Pause a log stream.
     */
    fun pauseLogStream(sessionId: String) {
        logger.info("Pausing log stream: sessionId={}", sessionId)
        activeStreams[sessionId]?.isPaused?.set(true)
    }

    /**
     * Resume a log stream.
     */
    fun resumeLogStream(sessionId: String) {
        logger.info("Resuming log stream: sessionId={}", sessionId)
        activeStreams[sessionId]?.isPaused?.set(false)
    }

    /**
     * Get list of active streams.
     */
    fun getActiveStreams(): List<String> {
        return activeStreams.keys.toList()
    }

    /**
     * Internal method to start workspace log streaming.
     */
    private fun startWorkspaceLogStreamInternal(context: LogStreamContext) {
        CompletableFuture.runAsync({
            try {
                sendLogEvent(
                    context,
                    LogWebSocketEvent(
                        timestamp = LocalDateTime.now(),
                        level = "INFO",
                        source = "workspace",
                        resourceId = context.resourceId,
                        message = "Connected to workspace log stream",
                    ),
                )

                // Simulate workspace log streaming (replace with actual Coder API integration)
                simulateWorkspaceLogStream(context)
            } catch (e: Exception) {
                logger.error("Error in workspace log stream: sessionId={}", context.sessionId, e)
                sendErrorEvent(context, "Workspace log stream error: ${e.message}")
            }
        }, executorService)
    }

    /**
     * Internal method to start task log streaming.
     */
    private fun startTaskLogStreamInternal(context: LogStreamContext) {
        CompletableFuture.runAsync({
            try {
                sendLogEvent(
                    context,
                    LogWebSocketEvent(
                        timestamp = LocalDateTime.now(),
                        level = "INFO",
                        source = "task",
                        resourceId = context.resourceId,
                        message = "Connected to task log stream",
                    ),
                )

                // Simulate task log streaming
                simulateTaskLogStream(context)
            } catch (e: Exception) {
                logger.error("Error in task log stream: sessionId={}", context.sessionId, e)
                sendErrorEvent(context, "Task log stream error: ${e.message}")
            }
        }, executorService)
    }

    /**
     * Internal method to start command log streaming.
     */
    private fun startCommandLogStreamInternal(context: LogStreamContext) {
        CompletableFuture.runAsync({
            try {
                sendLogEvent(
                    context,
                    LogWebSocketEvent(
                        timestamp = LocalDateTime.now(),
                        level = "INFO",
                        source = "command",
                        resourceId = context.resourceId,
                        message = "Starting command: ${context.resourceId}",
                    ),
                )

                executeCommandWithLogStream(context)
            } catch (e: Exception) {
                logger.error("Error in command log stream: sessionId={}", context.sessionId, e)
                sendErrorEvent(context, "Command execution error: ${e.message}")
            }
        }, executorService)
    }

    /**
     * Execute real command and stream its output via WebSocket.
     */
    private fun executeCommandWithLogStream(context: LogStreamContext) {
        try {
            val command = context.resourceId
            val processBuilder = ProcessBuilder(*command.split(" ").toTypedArray())
            context.workingDirectory?.let { processBuilder.directory(java.io.File(it)) }
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            context.process = process

            // Stream stdout/stderr
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null && context.isActive.get()) {
                    if (!context.isPaused.get()) {
                        sendLogEvent(
                            context,
                            LogWebSocketEvent(
                                timestamp = LocalDateTime.now(),
                                level = "INFO",
                                source = "command",
                                resourceId = command,
                                message = line!!,
                            ),
                        )
                    }
                }
            }

            val exitCode = process.waitFor()

            // Send completion event
            sendLogEvent(
                context,
                LogWebSocketEvent(
                    timestamp = LocalDateTime.now(),
                    level = if (exitCode == 0) "INFO" else "ERROR",
                    source = "command",
                    resourceId = command,
                    message = "Command completed with exit code: $exitCode",
                ),
            )

            sendCompletionEvent(context)
        } catch (e: Exception) {
            logger.error("Error executing command: sessionId={}", context.sessionId, e)
            sendErrorEvent(context, "Command execution failed: ${e.message}")
        }
    }

    /**
     * Simulate workspace log streaming.
     */
    private fun simulateWorkspaceLogStream(context: LogStreamContext) {
        var counter = 0
        val maxEvents = 50

        while (context.isActive.get() && counter < maxEvents) {
            try {
                if (!context.isPaused.get()) {
                    sendLogEvent(
                        context,
                        LogWebSocketEvent(
                            timestamp = LocalDateTime.now(),
                            level = if (counter % 10 == 0) "ERROR" else if (counter % 5 == 0) "WARN" else "INFO",
                            source = "workspace",
                            resourceId = context.resourceId,
                            message = "Workspace log entry #${counter + 1}: Simulated workspace activity",
                        ),
                    )
                }

                Thread.sleep(2000)
                counter++
            } catch (e: Exception) {
                logger.error("Error in workspace log simulation: sessionId={}", context.sessionId, e)
                break
            }
        }

        sendCompletionEvent(context)
    }

    /**
     * Simulate task log streaming.
     */
    private fun simulateTaskLogStream(context: LogStreamContext) {
        var counter = 0
        val maxEvents = 30

        while (context.isActive.get() && counter < maxEvents) {
            try {
                if (!context.isPaused.get()) {
                    sendLogEvent(
                        context,
                        LogWebSocketEvent(
                            timestamp = LocalDateTime.now(),
                            level = if (counter % 8 == 0) "ERROR" else if (counter % 4 == 0) "WARN" else "INFO",
                            source = "task",
                            resourceId = context.resourceId,
                            message = "Task log entry #${counter + 1}: Executing task step ${counter + 1}",
                        ),
                    )
                }

                Thread.sleep(1500)
                counter++
            } catch (e: Exception) {
                logger.error("Error in task log simulation: sessionId={}", context.sessionId, e)
                break
            }
        }

        sendCompletionEvent(context)
    }

    /**
     * Send log event to WebSocket client.
     */
    private fun sendLogEvent(context: LogStreamContext, event: LogWebSocketEvent) {
        try {
            val message = LogWebSocketMessage("log", event)
            val json = objectMapper.writeValueAsString(message)
            context.session.sendMessage(TextMessage(json))
        } catch (e: Exception) {
            logger.error("Failed to send log event: sessionId={}", context.sessionId, e)
        }
    }

    /**
     * Send error event to WebSocket client.
     */
    private fun sendErrorEvent(context: LogStreamContext, errorMessage: String) {
        try {
            val message = LogWebSocketMessage(
                "error",
                mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "message" to errorMessage,
                ),
            )
            val json = objectMapper.writeValueAsString(message)
            context.session.sendMessage(TextMessage(json))
        } catch (e: Exception) {
            logger.error("Failed to send error event: sessionId={}", context.sessionId, e)
        }
    }

    /**
     * Send completion event to WebSocket client.
     */
    private fun sendCompletionEvent(context: LogStreamContext) {
        try {
            val message = LogWebSocketMessage(
                "complete",
                mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "sessionId" to context.sessionId,
                ),
            )
            val json = objectMapper.writeValueAsString(message)
            context.session.sendMessage(TextMessage(json))
        } catch (e: Exception) {
            logger.error("Failed to send completion event: sessionId={}", context.sessionId, e)
        }
    }

    /**
     * Cleanup resources.
     */
    fun shutdown() {
        logger.info("Shutting down WebSocket log streaming service")
        activeStreams.values.forEach { context ->
            context.isActive.set(false)
            context.process?.destroy()
        }
        activeStreams.clear()
        activeSessions.clear()
        executorService.shutdown()

        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            executorService.shutdownNow()
        }
    }
}

/**
 * Context for log streaming.
 */
data class LogStreamContext(
    val sessionId: String,
    val session: WebSocketSession,
    val streamType: StreamType,
    val resourceId: String,
    val workingDirectory: String? = null,
    val isActive: AtomicBoolean,
    val isPaused: AtomicBoolean,
    var process: Process? = null,
)

/**
 * Stream type enumeration.
 */
enum class StreamType {
    WORKSPACE,
    TASK,
    COMMAND,
}

/**
 * WebSocket message wrapper.
 */
data class LogWebSocketMessage(
    // log, error, complete
    val type: String,
    val data: Any,
)

/**
 * WebSocket log event.
 */
data class LogWebSocketEvent(
    val timestamp: LocalDateTime,
    val level: String,
    val source: String,
    val resourceId: String,
    val message: String,
)
