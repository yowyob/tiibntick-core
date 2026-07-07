package com.yowyob.tiibntick.core.realtime.config;

import com.yowyob.tiibntick.core.realtime.adapter.in.websocket.TntStompWebSocketHandler;
import com.yowyob.tiibntick.core.realtime.config.properties.RealtimeProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

/**
 * Spring WebFlux configuration for reactive WebSocket support.
 *
 * <p>Registers the {@link TntStompWebSocketHandler} at the configured endpoint path
 * (default: {@code /ws/realtime}). The handler processes STOMP frames over the raw
 * WebFlux WebSocket API, without requiring the servlet stack.</p>
 *
 * <p>Security note: WebSocket connections are authenticated during the HTTP upgrade
 * handshake via the JWT token in the {@code Authorization} header. The security
 * filter chain (configured in tnt-bootstrap) validates the token and populates
 * the WebSocket session attributes ({@code userId}, {@code tenantId}).</p>
 *
 * @author MANFOUO Braun
 */
@Configuration
public class WebSocketConfig {

    private final RealtimeProperties realtimeProperties;

    public WebSocketConfig(RealtimeProperties realtimeProperties) {
        this.realtimeProperties = realtimeProperties;
    }

    /**
     * Registers the STOMP WebSocket handler at the configured endpoint.
     *
     * @param handler the reactive STOMP WebSocket handler
     * @return a HandlerMapping that routes {@code /ws/realtime} to the handler
     */
    @Bean
    public HandlerMapping webSocketHandlerMapping(TntStompWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of(realtimeProperties.getWebsocketPath(), handler));

        // Higher priority than the default dispatcher (order = -1 means very high priority)
        mapping.setOrder(-1);
        return mapping;
    }

    /**
     * Provides the WebSocketHandlerAdapter required by Spring WebFlux to invoke
     * {@link WebSocketHandler#handle(org.springframework.web.reactive.socket.WebSocketSession)}.
     *
     * @return the adapter bean
     */
    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
