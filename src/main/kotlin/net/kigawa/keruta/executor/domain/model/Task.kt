package net.kigawa.keruta.executor.domain.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

/**
 * Represents a task in the system.
 * This is a simplified version of the Task model in keruta-api.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Task(
    val id: String,
    val title: String = "a",
    val description: String? = null,
    val priority: Int = 0,
    val status: TaskStatus = TaskStatus.PENDING,
    val image: String? = null,
    val namespace: String = "default",
    val jobName: String? = null,
    val podName: String? = null,
    val additionalEnv: Map<String, String> = emptyMap(),
    val kubernetesManifest: String? = null,
    val logs: String? = null,
    val agentId: String? = null,
    val repositoryId: String? = null,
    val parentId: String? = null,
    val storageClass: String = "",
    val pvcName: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

/**
 * Represents the status of a task.
 */
enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    FAILED,
    WAITING_FOR_INPUT,
}

/**
 * Represents a task update request.
 */
data class TaskStatusUpdate(
    val status: TaskStatus,
    val message: String? = null,
)