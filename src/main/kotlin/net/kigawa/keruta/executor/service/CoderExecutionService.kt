package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import net.kigawa.keruta.executor.domain.model.Task
import net.kigawa.keruta.executor.domain.model.TaskStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Service for executing tasks locally.
 */
@Service
class CoderExecutionService(
    private val properties: KerutaExecutorProperties,
    private val taskApiService: TaskApiService,
    private val localExecutionService: LocalExecutionService,
) {
    private val logger = LoggerFactory.getLogger(CoderExecutionService::class.java)

    /**
     * Executes the coder CLI with the given arguments.
     * @param args the arguments to pass to the coder CLI
     * @param environment the environment variables to set
     * @return the output of the coder CLI
     */
    fun executeCoder(args: String, environment: Map<String, String> = emptyMap()): String {
        logger.info("Executing coder CLI with arguments: $args")

        val command = "${properties.coder.command} $args"
        val combinedEnv = properties.coder.additionalEnv + environment

        return localExecutionService.executeCommand(command, combinedEnv)
    }

    /**
     * Executes a task locally.
     * @param task the task to execute
     * @return true if the task was executed successfully, false otherwise
     */
    fun executeTask(task: Task): Boolean {
        logger.info("Executing task ${task.id} locally")

        try {
            // Update task status to IN_PROGRESS
            if (taskApiService.updateTaskStatus(
                task.id,
                TaskStatus.IN_PROGRESS,
                "Task execution started"
            ) == null) return false

            // Get the task script
            val script = taskApiService.getTaskScript(task.id)
            if (script == null) {
                logger.error("Failed to get script for task ${task.id}")
                taskApiService.updateTaskStatus(
                    task.id,
                    TaskStatus.FAILED,
                    "Failed to get task script"
                )
                return false
            }

            logger.info("Successfully retrieved script for task ${task.id}")

            // Log that we're executing the task
            val logMessage = "Task is being executed locally"
            taskApiService.appendTaskLogs(task.id, logMessage)

            // Create temporary script files
            val workDir = createWorkingDirectory(task)
            if (workDir == null) {
                taskApiService.updateTaskStatus(
                    task.id,
                    TaskStatus.FAILED,
                    "Failed to create working directory"
                )
                return false
            }

            // Execute install script if not empty
            if (script.installScript.isNotBlank()) {
                logger.info("Executing install script for task ${task.id}")
                val installOutput = executeScript(task.id, "install", script.installScript, script.environment)
                taskApiService.appendTaskLogs(task.id, "Install script output:\n$installOutput")
            }

            // Execute main script
            logger.info("Executing main script for task ${task.id}")
            val executeOutput = executeScript(task.id, "execute", script.executeScript, script.environment)
            taskApiService.appendTaskLogs(task.id, "Execute script output:\n$executeOutput")

            // Execute cleanup script if not empty
            if (script.cleanupScript.isNotBlank()) {
                logger.info("Executing cleanup script for task ${task.id}")
                val cleanupOutput = executeScript(task.id, "cleanup", script.cleanupScript, script.environment)
                taskApiService.appendTaskLogs(task.id, "Cleanup script output:\n$cleanupOutput")
            }

            // Update task status to COMPLETED
            taskApiService.updateTaskStatus(
                task.id,
                TaskStatus.COMPLETED,
                "Task execution completed successfully"
            )

            logger.info("Task ${task.id} executed successfully")
            return true

        } catch (e: Exception) {
            logger.error("Error executing task ${task.id}", e)
            taskApiService.updateTaskStatus(
                task.id,
                TaskStatus.FAILED,
                "Task execution failed: ${e.message}"
            )
            return false
        }
    }

    /**
     * Creates a working directory for the task.
     * @param task the task to create a working directory for
     * @return the working directory, or null if it could not be created
     */
    private fun createWorkingDirectory(task: Task): File? {
        val baseDir = Paths.get(properties.coder.workingDir)
        val taskDir = baseDir.resolve(task.id)

        return try {
            Files.createDirectories(taskDir)
            taskDir.toFile()
        } catch (e: Exception) {
            logger.error("Error creating working directory for task ${task.id}", e)
            null
        }
    }

    /**
     * Executes a script locally.
     * @param taskId the ID of the task
     * @param scriptType the type of script (install, execute, cleanup)
     * @param scriptContent the content of the script
     * @param environment the environment variables to set
     * @return the output of the script
     */
    private fun executeScript(
        taskId: String,
        scriptType: String,
        scriptContent: String,
        environment: Map<String, String>
    ): String {
        logger.debug("Executing $scriptType script for task $taskId")

        try {
            // Execute the script locally
            return localExecutionService.executeCommand(scriptContent, environment)
        } catch (e: Exception) {
            logger.error("Error executing $scriptType script for task $taskId", e)
            throw e
        }
    }
}
