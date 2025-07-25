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
    val apiBaseUrl: String
)
