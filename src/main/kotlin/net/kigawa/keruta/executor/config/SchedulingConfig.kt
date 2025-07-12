package net.kigawa.keruta.executor.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for scheduling-related beans.
 */
@Configuration
class SchedulingConfig(
    private val properties: KerutaExecutorProperties
) {
    /**
     * Bean that provides the processing delay in milliseconds.
     */
    @Bean
    fun processingDelayMillis(): Long {
        return properties.processingDelay.toMillis()
    }
}
