package net.kigawa.keruta.executor.config

import net.kigawa.keruta.executor.controller.ExecutorWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
open class WebSocketConfig(
    private val executorWebSocketHandler: ExecutorWebSocketHandler,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(executorWebSocketHandler, "/api/ws/executor")
            .setAllowedOrigins("*")
    }
}