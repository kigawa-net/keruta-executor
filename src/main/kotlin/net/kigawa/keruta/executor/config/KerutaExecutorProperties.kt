package net.kigawa.keruta.executor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import java.time.Duration

/**
 * Configuration properties for the Keruta Executor application.
 */
@ConfigurationProperties(prefix = "keruta.executor")
data class KerutaExecutorProperties @ConstructorBinding constructor(
    /**
     * The base URL of the keruta-api.
     */
    val apiBaseUrl: String,

    /**
     * The delay between task processing attempts.
     */
    val processingDelay: Duration = Duration.ofSeconds(10),

    /**
     * Configuration for the coder integration.
     */
    val coder: CoderProperties,

    /**
     * Configuration for SSH connection.
     */
    val ssh: SshProperties = SshProperties(),
)

/**
 * Configuration properties for the coder integration.
 */
data class CoderProperties(
    /**
     * The command to execute coder.
     */
    val command: String,

    /**
     * The working directory for coder.
     */
    val workingDir: String = "/tmp/coder",

    /**
     * The timeout for coder execution.
     */
    val timeout: Duration = Duration.ofMinutes(30),

    /**
     * Additional environment variables for coder.
     */
    val additionalEnv: Map<String, String> = emptyMap(),
    )

    /**
     * Configuration properties for SSH connection.
     */
    data class SshProperties(
        /**
         * The hostname or IP address of the SSH server.
         */
        val host: String = "localhost",

        /**
         * The port of the SSH server.
         */
        val port: Int = 22,

        /**
         * The username for SSH authentication.
         */
        val username: String = "root",

        /**
         * The password for SSH authentication.
         * Note: Using private key authentication is recommended over password authentication.
         */
        val password: String? = null,

        /**
         * The path to the private key file for SSH authentication.
         */
        val privateKeyPath: String? = null,

        /**
         * The passphrase for the private key, if it is encrypted.
         */
        val privateKeyPassphrase: String? = null,

        /**
         * The timeout for SSH connection in milliseconds.
         */
        val connectionTimeout: Int = 30000,

        /**
         * Whether to use strict host key checking.
         */
        val strictHostKeyChecking: Boolean = false,
    )
