package com.mysillydreams.delivery.config;

import com.mysillydreams.delivery.websocket.GpsWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket // Enables WebSocket server support
public class WebSocketConfig implements WebSocketConfigurer {

    private final GpsWebSocketHandler gpsWebSocketHandler;

    public WebSocketConfig(GpsWebSocketHandler gpsWebSocketHandler) {
        this.gpsWebSocketHandler = gpsWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register GpsWebSocketHandler to handle messages at "/delivery-updates/gps"
        // setAllowedOrigins("*") allows all origins, for production, restrict this to specific domains.
        registry.addHandler(gpsWebSocketHandler, "/delivery-updates/gps")
                .setAllowedOrigins("*");
                // .withSockJS(); // Optionally enable SockJS fallback for older browsers/proxies

        // Add more handlers for different paths if needed
        // registry.addHandler(new OtherWebSocketHandler(), "/other-updates").setAllowedOrigins("*");
    }
}
