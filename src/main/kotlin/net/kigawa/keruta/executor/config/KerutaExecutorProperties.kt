package net.kigawa.keruta.executor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

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
     * Coder server configuration.
     */
    val coder: CoderProperties = CoderProperties("http://localhost:7080"),
)

/**
 * Configuration properties for Coder server integration.
 */
data class CoderProperties(
    /**
     * The base URL of the Coder server.
     */
    val baseUrl: String,

    /**
     * API token for authenticating with Coder server.
     */
    val token: String? = null,
)
