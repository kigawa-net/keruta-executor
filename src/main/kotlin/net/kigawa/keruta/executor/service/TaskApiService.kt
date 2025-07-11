package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import net.kigawa.keruta.executor.domain.model.Task
import net.kigawa.keruta.executor.domain.model.TaskStatus
import net.kigawa.keruta.executor.domain.model.TaskStatusUpdate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

/**
 * Service for interacting with the keruta-api task endpoints.
 */
@Service
class TaskApiService(
    private val properties: KerutaExecutorProperties,
    private val webClientBuilder: WebClient.Builder,
) {
    private val logger = LoggerFactory.getLogger(TaskApiService::class.java)
    private val webClient by lazy {
        webClientBuilder.baseUrl(properties.apiBaseUrl).build()
    }

    /**
     * Gets the next pending task from the keruta-api.
     * @return the next pending task, or null if there are no pending tasks
     */
    fun getNextPendingTask(): Task? {
        logger.debug("Getting next pending task from keruta-api")
        return try {
            webClient.get()
                .uri("/api/v1/tasks?status=PENDING&limit=1")
                .retrieve()
                .bodyToMono<List<Task>>()
                .block()
                ?.firstOrNull()
                ?.also { logger.info("Got pending task: ${it.id}") }
        } catch (e: Exception) {
            logger.error("Error getting pending task from keruta-api", e)
            null
        }
    }

    /**
     * Updates the status of a task in the keruta-api.
     * @param taskId the ID of the task to update
     * @param status the new status of the task
     * @param message an optional message to include with the status update
     * @return the updated task, or null if the update failed
     */
    fun updateTaskStatus(taskId: String, status: TaskStatus, message: String? = null): Task? {
        logger.debug("Updating task $taskId status to $status")
        return try {
            webClient.put()
                .uri("/api/v1/tasks/$taskId/status")
                .bodyValue(TaskStatusUpdate(status, message))
                .retrieve()
                .bodyToMono<Task>()
                .block()
                ?.also { logger.info("Updated task $taskId status to $status") }
        } catch (e: Exception) {
            logger.error("Error updating task $taskId status to $status", e)
            null
        }
    }

    /**
     * Appends logs to a task in the keruta-api.
     * @param taskId the ID of the task to append logs to
     * @param logs the logs to append
     * @return true if the logs were appended successfully, false otherwise
     */
    fun appendTaskLogs(taskId: String, logs: String): Boolean {
        logger.debug("Appending logs to task $taskId")
        return try {
            webClient.post()
                .uri("/api/v1/tasks/$taskId/logs")
                .bodyValue(logs)
                .retrieve()
                .bodyToMono<Void>()
                .then(Mono.just(true))
                .block() ?: false
        } catch (e: Exception) {
            logger.error("Error appending logs to task $taskId", e)
            false
        }
    }
}