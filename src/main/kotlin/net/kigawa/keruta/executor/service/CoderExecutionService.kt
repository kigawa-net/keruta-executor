package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import net.kigawa.keruta.executor.domain.model.Task
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Service for executing tasks using coder.
 */
@Service
class CoderExecutionService(
    private val properties: KerutaExecutorProperties,
    private val taskApiService: TaskApiService,
) {
    private val logger = LoggerFactory.getLogger(CoderExecutionService::class.java)

    /**
     * Executes a task using coder.
     * @param task the task to execute
     * @return true if the task was executed successfully, false otherwise
     */
    fun executeTask(task: Task): Boolean {
        logger.info("Executing task ${task.id} with coder")

        // Create working directory
        val workDir = createWorkingDirectory(task)
        if (workDir == null) {
            taskApiService.updateTaskStatus(
                task.id,
                net.kigawa.keruta.executor.domain.model.TaskStatus.FAILED,
                "Failed to create working directory"
            )
            return false
        }

        // Prepare environment variables
        val env = prepareEnvironment(task)

        // Build command
        val command = buildCommand(task)

        return try {
            // Execute command
            val processBuilder = ProcessBuilder(command)
                .directory(workDir)
                .redirectErrorStream(true)
            processBuilder.environment().putAll(env)
            val process = processBuilder.start()

            // Update task status to IN_PROGRESS
            taskApiService.updateTaskStatus(
                task.id,
                net.kigawa.keruta.executor.domain.model.TaskStatus.IN_PROGRESS,
                "Task execution started"
            )

            // Read output
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val logBuilder = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                logger.debug("Coder output: $line")
                logBuilder.appendLine(line)

                // Periodically append logs to the task
                if (logBuilder.length > 1000) {
                    taskApiService.appendTaskLogs(task.id, logBuilder.toString())
                    logBuilder.clear()
                }
            }

            // Append any remaining logs
            if (logBuilder.isNotEmpty()) {
                taskApiService.appendTaskLogs(task.id, logBuilder.toString())
            }

            // Wait for process to complete with timeout
            val completed = process.waitFor(
                properties.coder.timeout.toMillis(),
                TimeUnit.MILLISECONDS
            )

            if (!completed) {
                logger.warn("Coder execution timed out for task ${task.id}")
                process.destroyForcibly()
                taskApiService.updateTaskStatus(
                    task.id,
                    net.kigawa.keruta.executor.domain.model.TaskStatus.FAILED,
                    "Task execution timed out after ${properties.coder.timeout.toMinutes()} minutes"
                )
                return false
            }

            // Check exit code
            val exitCode = process.exitValue()
            if (exitCode == 0) {
                logger.info("Coder execution completed successfully for task ${task.id}")
                taskApiService.updateTaskStatus(
                    task.id,
                    net.kigawa.keruta.executor.domain.model.TaskStatus.COMPLETED,
                    "Task execution completed successfully"
                )
                return true
            } else {
                logger.warn("Coder execution failed for task ${task.id} with exit code $exitCode")
                taskApiService.updateTaskStatus(
                    task.id,
                    net.kigawa.keruta.executor.domain.model.TaskStatus.FAILED,
                    "Task execution failed with exit code $exitCode"
                )
                return false
            }
        } catch (e: Exception) {
            logger.error("Error executing task ${task.id} with coder", e)
            taskApiService.updateTaskStatus(
                task.id,
                net.kigawa.keruta.executor.domain.model.TaskStatus.FAILED,
                "Task execution failed: ${e.message}"
            )
            return false
        } finally {
            // Clean up working directory
            cleanupWorkingDirectory(workDir)
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
     * Prepares environment variables for the task.
     * @param task the task to prepare environment variables for
     * @return the environment variables
     */
    private fun prepareEnvironment(task: Task): Map<String, String> {
        val env = mutableMapOf<String, String>()

        // Add task information as environment variables
        env["TASK_ID"] = task.id
        env["TASK_TITLE"] = task.title
        task.description?.let { env["TASK_DESCRIPTION"] = it }

        // Add additional environment variables from task
        env.putAll(task.additionalEnv)

        // Add additional environment variables from configuration
        env.putAll(properties.coder.additionalEnv)

        return env
    }

    /**
     * Builds the command to execute coder.
     * @param task the task to build the command for
     * @return the command to execute
     */
    private fun buildCommand(task: Task): List<String> {
        // Split the command into a list of arguments
        val command = properties.coder.command.split("\\s+".toRegex())

        // Add task-specific arguments if needed
        // This is just a placeholder - modify as needed based on how coder should be invoked

        return command
    }

    /**
     * Cleans up the working directory for the task.
     * @param workDir the working directory to clean up
     */
    private fun cleanupWorkingDirectory(workDir: File?) {
        if (workDir != null && workDir.exists()) {
            try {
                workDir.deleteRecursively()
            } catch (e: Exception) {
                logger.warn("Error cleaning up working directory ${workDir.absolutePath}", e)
            }
        }
    }
}
