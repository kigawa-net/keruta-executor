package net.kigawa.keruta.executor.config

import net.kigawa.keruta.executor.handler.LogWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * WebSocket configuration for log streaming.
 */
@Configuration
@EnableWebSocket
open class WebSocketConfig(
    private val logWebSocketHandler: LogWebSocketHandler,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(logWebSocketHandler, "/api/v1/logs/ws")
            .setAllowedOrigins("*") // 本番環境では適切なオリジンを設定してください
    }
}
