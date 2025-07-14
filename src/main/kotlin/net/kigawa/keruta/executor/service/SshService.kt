package net.kigawa.keruta.executor.service

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties

/**
 * Service for executing commands via SSH.
 */
@Service
class SshService(
    private val properties: KerutaExecutorProperties,
) {
    private val logger = LoggerFactory.getLogger(SshService::class.java)
    private val jsch = JSch()

    // SSH configuration keys in environment variables
    companion object {
        const val SSH_HOST = "SSH_HOST"
        const val SSH_PORT = "SSH_PORT"
        const val SSH_USERNAME = "SSH_USERNAME"
        const val SSH_PASSWORD = "SSH_PASSWORD"
        const val SSH_PRIVATE_KEY_PATH = "SSH_PRIVATE_KEY_PATH"
        const val SSH_PRIVATE_KEY_PASSPHRASE = "SSH_PRIVATE_KEY_PASSPHRASE"
        const val SSH_STRICT_HOST_KEY_CHECKING = "SSH_STRICT_HOST_KEY_CHECKING"
        const val SSH_CONNECTION_TIMEOUT = "SSH_CONNECTION_TIMEOUT"
    }

    /**
     * Executes a command via SSH.
     * @param command the command to execute
     * @param environment the environment variables to set, which may include SSH configuration
     * @return the output of the command
     */
    fun executeCommand(command: String, environment: Map<String, String> = emptyMap()): String {
        logger.info("Executing command via SSH: $command")
        var session: Session? = null
        var channel: ChannelExec? = null

        try {
            // Create SSH session using environment variables if available
            session = createSession(environment)

            // Create channel
            channel = session.openChannel("exec") as ChannelExec

            // Set environment variables
            val envCommand = buildEnvironmentCommand(environment) + command
            channel.setCommand(envCommand)

            // Get output stream
            val outputStream = ByteArrayOutputStream()
            channel.outputStream = outputStream

            // Get error stream
            val errorStream = ByteArrayOutputStream()
            channel.setErrStream(errorStream)

            // Connect and execute command
            channel.connect()

            // Wait for command to complete
            while (channel.isConnected) {
                Thread.sleep(100)
            }

            // Get output
            val output = outputStream.toString()
            val error = errorStream.toString()

            // Log output
            logger.debug("Command output: $output")
            if (error.isNotEmpty()) {
                logger.warn("Command error: $error")
            }

            // Return output
            return output
        } catch (e: Exception) {
            logger.error("Error executing command via SSH", e)
            throw e
        } finally {
            // Disconnect channel and session
            channel?.disconnect()
            session?.disconnect()
        }
    }

    /**
     * Creates an SSH session using configuration from environment variables if available.
     * @param environment the environment variables that may contain SSH configuration
     * @return the SSH session
     */
    private fun createSession(environment: Map<String, String> = emptyMap()): Session {
        // Extract SSH configuration from environment variables
        val sshConfig = extractSshConfig(environment)

        logger.debug("Creating SSH session to ${sshConfig.host}:${sshConfig.port}")

        // Set up private key authentication if configured
        sshConfig.privateKeyPath?.let { privateKeyPath ->
            val privateKey = File(privateKeyPath)
            if (privateKey.exists()) {
                if (sshConfig.privateKeyPassphrase != null) {
                    jsch.addIdentity(privateKeyPath, sshConfig.privateKeyPassphrase)
                } else {
                    jsch.addIdentity(privateKeyPath)
                }
            } else {
                logger.warn("Private key file not found: $privateKeyPath")
            }
        }

        // Create session
        val session = jsch.getSession(
            sshConfig.username,
            sshConfig.host,
            sshConfig.port
        )

        // Set password if configured
        sshConfig.password?.let { password ->
            session.setPassword(password)
        }

        // Set session properties
        val config = Properties()
        config["StrictHostKeyChecking"] = if (sshConfig.strictHostKeyChecking) "yes" else "no"
        session.setConfig(config)
        session.timeout = sshConfig.connectionTimeout

        // Connect session
        session.connect()
        logger.debug("SSH session connected")

        return session
    }

    /**
     * Extracts SSH configuration from environment variables.
     * @param environment the environment variables that may contain SSH configuration
     * @return the SSH configuration
     */
    private fun extractSshConfig(environment: Map<String, String>): SshConfig {
        return SshConfig(
            host = environment[SSH_HOST] ?: properties.ssh.host,
            port = environment[SSH_PORT]?.toIntOrNull() ?: properties.ssh.port,
            username = environment[SSH_USERNAME] ?: properties.ssh.username,
            password = environment[SSH_PASSWORD] ?: properties.ssh.password,
            privateKeyPath = environment[SSH_PRIVATE_KEY_PATH] ?: properties.ssh.privateKeyPath,
            privateKeyPassphrase = environment[SSH_PRIVATE_KEY_PASSPHRASE] ?: properties.ssh.privateKeyPassphrase,
            strictHostKeyChecking = environment[SSH_STRICT_HOST_KEY_CHECKING]?.toBoolean() ?: properties.ssh.strictHostKeyChecking,
            connectionTimeout = environment[SSH_CONNECTION_TIMEOUT]?.toIntOrNull() ?: properties.ssh.connectionTimeout
        )
    }

    /**
     * Data class for SSH configuration.
     */
    private data class SshConfig(
        val host: String,
        val port: Int,
        val username: String,
        val password: String?,
        val privateKeyPath: String?,
        val privateKeyPassphrase: String?,
        val strictHostKeyChecking: Boolean,
        val connectionTimeout: Int
    )

    /**
     * Builds a command to set environment variables.
     * @param environment the environment variables to set
     * @return the command to set environment variables
     */
    private fun buildEnvironmentCommand(environment: Map<String, String>): String {
        if (environment.isEmpty()) {
            return ""
        }

        val envCommand = StringBuilder()
        for ((key, value) in environment) {
            envCommand.append("export $key=\"$value\" && ")
        }

        return envCommand.toString()
    }
}
