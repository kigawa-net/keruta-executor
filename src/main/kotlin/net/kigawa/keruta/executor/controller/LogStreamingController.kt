package net.kigawa.keruta.executor.controller

import net.kigawa.keruta.executor.service.LogStreamingService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

/**
 * Controller for handling log streaming endpoints.
 */
@RestController
@RequestMapping("/api/v1/logs")
@CrossOrigin(origins = ["*"])
open class LogStreamingController(
    private val logStreamingService: LogStreamingService,
) {
    private val logger = LoggerFactory.getLogger(LogStreamingController::class.java)

    /**
     * Start streaming logs from a workspace.
     */
    @GetMapping("/workspace/{workspaceId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamWorkspaceLogs(
        @PathVariable workspaceId: String,
        @RequestParam(defaultValue = "false") follow: Boolean,
    ): SseEmitter {
        val streamId = "workspace-$workspaceId-${UUID.randomUUID()}"
        logger.info(
            "Starting workspace log stream: workspaceId={} streamId={} follow={}",
            workspaceId,
            streamId,
            follow,
        )

        return logStreamingService.startLogStream(streamId, workspaceId, null)
    }

    /**
     * Start streaming logs from a task.
     */
    @GetMapping("/task/{taskId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamTaskLogs(
        @PathVariable taskId: String,
        @RequestParam(defaultValue = "false") follow: Boolean,
    ): SseEmitter {
        val streamId = "task-$taskId-${UUID.randomUUID()}"
        logger.info(
            "Starting task log stream: taskId={} streamId={} follow={}",
            taskId,
            streamId,
            follow,
        )

        return logStreamingService.startLogStream(streamId, null, taskId)
    }

    /**
     * Execute a command and stream its output.
     */
    @PostMapping("/command/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamCommandOutput(@RequestBody request: CommandStreamRequest): SseEmitter {
        val streamId = "command-${UUID.randomUUID()}"
        logger.info(
            "Starting command stream: command={} streamId={}",
            request.command,
            streamId,
        )

        val emitter = SseEmitter()
        logStreamingService.executeCommandWithLogStream(
            streamId,
            request.command,
            request.workingDirectory,
            emitter,
        )

        return emitter
    }

    /**
     * Stop a log stream.
     */
    @DeleteMapping("/stream/{streamId}")
    fun stopLogStream(@PathVariable streamId: String): Map<String, String> {
        logger.info("Stopping log stream: streamId={}", streamId)
        logStreamingService.stopLogStream(streamId)
        return mapOf("status" to "stopped", "streamId" to streamId)
    }

    /**
     * Get list of active streams.
     */
    @GetMapping("/streams")
    fun getActiveStreams(): Map<String, List<String>> {
        val activeStreams = logStreamingService.getActiveStreams()
        logger.debug("Active streams count: {}", activeStreams.size)
        return mapOf("activeStreams" to activeStreams)
    }

    /**
     * Health check endpoint for log streaming.
     */
    @GetMapping("/health")
    fun healthCheck(): Map<String, Any> {
        val activeStreams = logStreamingService.getActiveStreams()
        return mapOf(
            "status" to "healthy",
            "activeStreamsCount" to activeStreams.size,
            "timestamp" to System.currentTimeMillis(),
        )
    }
}

/**
 * Request for command streaming.
 */
data class CommandStreamRequest(
    val command: String,
    val workingDirectory: String? = null,
)
