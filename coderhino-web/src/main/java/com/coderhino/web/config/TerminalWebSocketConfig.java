package com.coderhino.web.config;

import com.coderhino.web.terminal.TerminalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocket
public class TerminalWebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler terminalWebSocketHandler;

    public TerminalWebSocketConfig(TerminalWebSocketHandler terminalWebSocketHandler) {
        this.terminalWebSocketHandler = terminalWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalWebSocketHandler, "/ws/terminals/*")
            .addInterceptors(sessionIdInterceptor())
            .setAllowedOrigins("*");
    }

    private HandshakeInterceptor sessionIdInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(org.springframework.http.server.ServerHttpRequest request,
                                           org.springframework.http.server.ServerHttpResponse response,
                                           org.springframework.web.socket.WebSocketHandler wsHandler,
                                           Map<String, Object> attributes) {
                var query = request.getURI().getQuery();
                if (query == null || query.isBlank()) {
                    return false;
                }
                for (var pair : query.split("&")) {
                    var parts = pair.split("=", 2);
                    if (parts.length == 2 && "sessionId".equals(parts[0]) && !parts[1].isBlank()) {
                        attributes.put("sessionId", java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8));
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void afterHandshake(org.springframework.http.server.ServerHttpRequest request,
                                       org.springframework.http.server.ServerHttpResponse response,
                                       org.springframework.web.socket.WebSocketHandler wsHandler,
                                       Exception exception) {
            }
        };
    }
}
