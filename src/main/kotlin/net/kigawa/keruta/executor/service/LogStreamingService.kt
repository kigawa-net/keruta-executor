package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

/**
 * Service for streaming logs from workspaces and tasks.
 */
@Service
open class LogStreamingService(
    private val properties: KerutaExecutorProperties
) {
    private val logger = LoggerFactory.getLogger(LogStreamingService::class.java)
    
    // Store active SSE connections
    private val activeStreams = ConcurrentHashMap<String, SseEmitter>()
    
    // Executor for background log streaming tasks
    private val executorService: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable).apply {
            isDaemon = true
            name = "log-stream-${System.currentTimeMillis()}"
        }
    }

    /**
     * Start streaming logs for a workspace or task.
     */
    fun startLogStream(streamId: String, workspaceId: String?, taskId: String?): SseEmitter {
        logger.info("Starting log stream: streamId={} workspaceId={} taskId={}", streamId, workspaceId, taskId)
        
        val emitter = SseEmitter(TimeUnit.MINUTES.toMillis(30)) // 30 minute timeout
        
        // Configure emitter callbacks
        emitter.onCompletion {
            logger.debug("Log stream completed: streamId={}", streamId)
            activeStreams.remove(streamId)
        }
        
        emitter.onTimeout {
            logger.debug("Log stream timed out: streamId={}", streamId)
            activeStreams.remove(streamId)
        }
        
        emitter.onError { throwable ->
            logger.warn("Log stream error: streamId={} error={}", streamId, throwable.message)
            activeStreams.remove(streamId)
        }
        
        // Store the emitter
        activeStreams[streamId] = emitter
        
        // Start streaming logs in background
        when {
            workspaceId != null -> startWorkspaceLogStream(streamId, workspaceId, emitter)
            taskId != null -> startTaskLogStream(streamId, taskId, emitter)
            else -> {
                logger.error("Neither workspaceId nor taskId provided for stream: streamId={}", streamId)
                emitter.completeWithError(IllegalArgumentException("Either workspaceId or taskId must be provided"))
            }
        }
        
        return emitter
    }

    /**
     * Stop a log stream.
     */
    fun stopLogStream(streamId: String) {
        logger.info("Stopping log stream: streamId={}", streamId)
        activeStreams.remove(streamId)?.complete()
    }

    /**
     * Get list of active streams.
     */
    fun getActiveStreams(): List<String> {
        return activeStreams.keys.toList()
    }

    /**
     * Start streaming logs from a workspace.
     */
    private fun startWorkspaceLogStream(streamId: String, workspaceId: String, emitter: SseEmitter) {
        CompletableFuture.runAsync({
            try {
                logger.debug("Starting workspace log stream: streamId={} workspaceId={}", streamId, workspaceId)
                
                // Send initial connection event
                val connectEvent = LogStreamEvent(
                    timestamp = LocalDateTime.now(),
                    level = "INFO",
                    source = "workspace",
                    workspaceId = workspaceId,
                    message = "Connected to workspace log stream"
                )
                emitter.send(SseEmitter.event().name("log").data(connectEvent))
                
                // Start log tailing process (this would integrate with Coder API)
                simulateWorkspaceLogStream(streamId, workspaceId, emitter)
                
            } catch (e: Exception) {
                logger.error("Error in workspace log stream: streamId={} workspaceId={}", streamId, workspaceId, e)
                emitter.completeWithError(e)
            }
        }, executorService)
    }

    /**
     * Start streaming logs from a task.
     */
    private fun startTaskLogStream(streamId: String, taskId: String, emitter: SseEmitter) {
        CompletableFuture.runAsync({
            try {
                logger.debug("Starting task log stream: streamId={} taskId={}", streamId, taskId)
                
                // Send initial connection event
                val connectEvent = LogStreamEvent(
                    timestamp = LocalDateTime.now(),
                    level = "INFO",
                    source = "task",
                    taskId = taskId,
                    message = "Connected to task log stream"
                )
                emitter.send(SseEmitter.event().name("log").data(connectEvent))
                
                // Start log tailing process
                simulateTaskLogStream(streamId, taskId, emitter)
                
            } catch (e: Exception) {
                logger.error("Error in task log stream: streamId={} taskId={}", streamId, taskId, e)
                emitter.completeWithError(e)
            }
        }, executorService)
    }

    /**
     * Simulate workspace log streaming (placeholder for Coder API integration).
     */
    private fun simulateWorkspaceLogStream(streamId: String, workspaceId: String, emitter: SseEmitter) {
        // This is a simulation - in real implementation, this would connect to Coder API
        // or read from actual workspace log files
        
        var counter = 0
        val maxEvents = 50 // Limit for simulation
        
        while (activeStreams.containsKey(streamId) && counter < maxEvents) {
            try {
                Thread.sleep(2000) // Simulate log interval
                
                val logEvent = LogStreamEvent(
                    timestamp = LocalDateTime.now(),
                    level = if (counter % 10 == 0) "ERROR" else if (counter % 5 == 0) "WARN" else "INFO",
                    source = "workspace",
                    workspaceId = workspaceId,
                    message = "Workspace log entry #${counter + 1}: Simulated workspace activity"
                )
                
                emitter.send(SseEmitter.event().name("log").data(logEvent))
                counter++
                
            } catch (e: Exception) {
                logger.error("Error sending workspace log event: streamId={} counter={}", streamId, counter, e)
                break
            }
        }
        
        // Send completion event
        try {
            val completeEvent = LogStreamEvent(
                timestamp = LocalDateTime.now(),
                level = "INFO",
                source = "workspace",
                workspaceId = workspaceId,
                message = "Workspace log stream completed"
            )
            emitter.send(SseEmitter.event().name("complete").data(completeEvent))
        } catch (e: Exception) {
            logger.error("Error sending completion event: streamId={}", streamId, e)
        }
        
        emitter.complete()
    }

    /**
     * Simulate task log streaming.
     */
    private fun simulateTaskLogStream(streamId: String, taskId: String, emitter: SseEmitter) {
        // This is a simulation - in real implementation, this would read from task execution logs
        
        var counter = 0
        val maxEvents = 30 // Limit for simulation
        
        while (activeStreams.containsKey(streamId) && counter < maxEvents) {
            try {
                Thread.sleep(1500) // Simulate log interval
                
                val logEvent = LogStreamEvent(
                    timestamp = LocalDateTime.now(),
                    level = if (counter % 8 == 0) "ERROR" else if (counter % 4 == 0) "WARN" else "INFO",
                    source = "task",
                    taskId = taskId,
                    message = "Task log entry #${counter + 1}: Executing task step ${counter + 1}"
                )
                
                emitter.send(SseEmitter.event().name("log").data(logEvent))
                counter++
                
            } catch (e: Exception) {
                logger.error("Error sending task log event: streamId={} counter={}", streamId, counter, e)
                break
            }
        }
        
        // Send completion event
        try {
            val completeEvent = LogStreamEvent(
                timestamp = LocalDateTime.now(),
                level = "INFO",
                source = "task",
                taskId = taskId,
                message = "Task execution completed"
            )
            emitter.send(SseEmitter.event().name("complete").data(completeEvent))
        } catch (e: Exception) {
            logger.error("Error sending completion event: streamId={}", streamId, e)
        }
        
        emitter.complete()
    }

    /**
     * Execute real command and stream its output.
     */
    fun executeCommandWithLogStream(
        streamId: String,
        command: String,
        workingDirectory: String? = null,
        emitter: SseEmitter
    ) {
        CompletableFuture.runAsync({
            try {
                logger.info("Executing command with log stream: streamId={} command={}", streamId, command)
                
                val processBuilder = ProcessBuilder(*command.split(" ").toTypedArray())
                workingDirectory?.let { processBuilder.directory(java.io.File(it)) }
                processBuilder.redirectErrorStream(true)
                
                val process = processBuilder.start()
                
                // Stream stdout/stderr
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null && activeStreams.containsKey(streamId)) {
                        val logEvent = LogStreamEvent(
                            timestamp = LocalDateTime.now(),
                            level = "INFO",
                            source = "command",
                            message = line!!
                        )
                        emitter.send(SseEmitter.event().name("log").data(logEvent))
                    }
                }
                
                val exitCode = process.waitFor()
                
                // Send completion event with exit code
                val completeEvent = LogStreamEvent(
                    timestamp = LocalDateTime.now(),
                    level = if (exitCode == 0) "INFO" else "ERROR",
                    source = "command",
                    message = "Command completed with exit code: $exitCode"
                )
                emitter.send(SseEmitter.event().name("complete").data(completeEvent))
                
            } catch (e: Exception) {
                logger.error("Error executing command with log stream: streamId={}", streamId, e)
                val errorEvent = LogStreamEvent(
                    timestamp = LocalDateTime.now(),
                    level = "ERROR",
                    source = "command",
                    message = "Command execution failed: ${e.message}"
                )
                emitter.send(SseEmitter.event().name("error").data(errorEvent))
            } finally {
                emitter.complete()
                activeStreams.remove(streamId)
            }
        }, executorService)
    }

    /**
     * Cleanup resources.
     */
    fun shutdown() {
        logger.info("Shutting down log streaming service")
        activeStreams.values.forEach { it.complete() }
        activeStreams.clear()
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
 * Log stream event data class.
 */
data class LogStreamEvent(
    val timestamp: LocalDateTime,
    val level: String,
    val source: String,
    val workspaceId: String? = null,
    val taskId: String? = null,
    val message: String
)