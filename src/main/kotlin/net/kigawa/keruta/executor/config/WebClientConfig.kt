package net.kigawa.keruta.executor.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

/**
 * Configuration for WebClient.
 * Customizes the WebClient.Builder to increase the buffer size limit.
 */
@Configuration
class WebClientConfig {

    /**
     * Configures a WebClient.Builder with increased buffer size limit.
     * The default limit is 256KB (262144 bytes), which is too small for some responses.
     * This increases it to 10MB to handle larger responses.
     */
    @Bean
    fun webClientBuilder(): WebClient.Builder {
        // Increase the buffer size limit to 10MB (10 * 1024 * 1024 bytes)
        val size = 10 * 1024 * 1024
        val strategies = ExchangeStrategies.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(size)
            }
            .build()

        // Create a WebClient.Builder with the increased buffer size limit
        return WebClient.builder()
            .exchangeStrategies(strategies)
    }
}
