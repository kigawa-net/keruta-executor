package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import net.kigawa.keruta.executor.domain.model.Task
import net.kigawa.keruta.executor.domain.model.TaskScript
import net.kigawa.keruta.executor.domain.model.TaskStatus
import net.kigawa.keruta.executor.domain.model.TaskStatusUpdate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

/**
 * Service for interacting with the keruta-api task endpoints.
 */
@Service
open class TaskApiService(
    private val properties: KerutaExecutorProperties,
    private val webClientBuilder: WebClient.Builder
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
        logger.debug("Updating task {} status to {}", taskId, status)
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
                .uri("/api/v1/tasks/$taskId/logs/stream")
                .bodyValue(logs)
                .retrieve()
                .bodyToMono<Void>()
                .then(Mono.just(true))
                .retryWhen(
                    Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter { it is WebClientResponseException && it.statusCode.is5xxServerError }
                        .doBeforeRetry {
                            logger.warn(
                                "Retrying log append for task $taskId after server error: ${it.failure().message}"
                            )
                        }
                )
                .block() ?: false
        } catch (e: Exception) {
            logger.error("Error appending logs to task $taskId", e)
            // Log continues execution even if log appending fails
            false
        }
    }

    /**
     * Gets the script for a task from the keruta-api.
     * @param taskId the ID of the task to get the script for
     * @return the task script, or null if the script could not be retrieved
     */
    fun getTaskScript(taskId: String): TaskScript? {
        logger.debug("Getting script for task $taskId")
        return try {
            // The API returns a different structure than our TaskScript model
            // We need to map the response to our model
            val response = webClient.get()
                .uri("/api/v1/tasks/$taskId/script")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block()

            if (response != null) {
                logger.info("Got script for task $taskId")

                // Extract script content from the response
                @Suppress("UNCHECKED_CAST")
                val scriptContent = response["script"] as? Map<String, String>

                if (scriptContent != null) {
                    // Create a TaskScript from the response
                    TaskScript(
                        taskId = taskId,
                        installScript = scriptContent["installScript"] ?: "",
                        executeScript = scriptContent["executeScript"] ?: "",
                        cleanupScript = scriptContent["cleanupScript"] ?: "",
                        environment = parseEnvironmentMap(response["environment"]) ?: emptyMap()
                    )
                } else {
                    logger.error("Invalid script content format for task $taskId")
                    null
                }
            } else {
                logger.error("No response received for task script $taskId")
                null
            }
        } catch (e: Exception) {
            logger.error("Error getting script for task $taskId", e)
            null
        }
    }

    /**
     * Safely parses an environment map from Any? to Map<String, String>.
     * @param obj the object to parse
     * @return a Map<String, String> if the object can be safely converted, null otherwise
     */
    private fun parseEnvironmentMap(obj: Any?): Map<String, String>? {
        if (obj !is Map<*, *>) {
            return null
        }

        val result = mutableMapOf<String, String>()
        for ((key, value) in obj) {
            if (key is String && value is String) {
                result[key] = value
            } else {
                // If any key-value pair doesn't match the expected types, return null
                logger.warn("Environment map contains non-string key or value: $key -> $value")
                return null
            }
        }

        return result
    }
}
