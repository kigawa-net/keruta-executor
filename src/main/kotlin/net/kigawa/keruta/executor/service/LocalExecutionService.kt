package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Service for executing commands locally.
 */
@Service
class LocalExecutionService(
    private val properties: KerutaExecutorProperties
) {
    private val logger = LoggerFactory.getLogger(LocalExecutionService::class.java)

    /**
     * Executes a command locally.
     * @param command the command to execute
     * @param environment the environment variables to set
     * @return the output of the command
     */
    fun executeCommand(command: String, environment: Map<String, String> = emptyMap()): String {
        logger.info("Executing command locally: $command")

        try {
            // Split the command into parts
            // This is a simple implementation that may not handle all complex command scenarios
            // For more complex commands, a proper command parser might be needed
            val commandParts = command.split("\\s+".toRegex()).toTypedArray()

            // Build the process
            val processBuilder = ProcessBuilder(*commandParts)

            // Set environment variables
            val processEnv = processBuilder.environment()
            processEnv.putAll(environment)

            // Set working directory
            processBuilder.directory(File(properties.coder.workingDir))

            // Redirect error stream to output stream
            processBuilder.redirectErrorStream(true)

            // Start the process
            val process = processBuilder.start()

            // Wait for the process to complete with timeout
            val timeoutSeconds = properties.coder.timeout.seconds
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                throw RuntimeException("Command execution timed out after $timeoutSeconds seconds")
            }

            // Get the output
            val output = process.inputStream.bufferedReader().use { it.readText() }

            // Log output
            logger.debug("Command output: $output")

            // Return output
            return output
        } catch (e: Exception) {
            logger.error("Error executing command locally", e)
            throw e
        }
    }
}
