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