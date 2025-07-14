package net.kigawa.keruta.executor.domain.model

/**
 * Model class for a task script.
 */
data class TaskScript(
    val taskId: String,
    val installScript: String,
    val executeScript: String,
    val cleanupScript: String,
    val environment: Map<String, String> = emptyMap()
)
