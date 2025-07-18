package net.kigawa.keruta.executor.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

/**
 * Configuration for RestTemplate.
 */
@Configuration
class RestTemplateConfig {

    /**
     * Creates a RestTemplate bean.
     */
    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}