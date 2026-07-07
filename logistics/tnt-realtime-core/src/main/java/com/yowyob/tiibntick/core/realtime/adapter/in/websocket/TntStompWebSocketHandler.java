package com.yowyob.tiibntick.core.realtime.adapter.in.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.realtime.adapter.in.websocket.dto.ConnectRequest;
import com.yowyob.tiibntick.core.realtime.adapter.in.websocket.dto.GpsPingMessage;
import com.yowyob.tiibntick.core.realtime.adapter.in.websocket.stomp.StompFrame;
import com.yowyob.tiibntick.core.realtime.adapter.in.websocket.stomp.StompFrameParser;
import com.yowyob.tiibntick.core.realtime.adapter.in.websocket.stomp.StompFrameSerializer;
import com.yowyob.tiibntick.core.realtime.adapter.out.websocket.WebSocketSessionRegistry;
import com.yowyob.tiibntick.core.realtime.application.port.in.IProcessGpsPingUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.in.IRegisterSessionUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.in.ISubscribeToTopicUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.in.ITerminateSessionUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.in.IUnsubscribeFromTopicUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.DeviceInfo;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.SessionId;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.DeviceType;
import com.yowyob.tiibntick.core.realtime.domain.service.WebSocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reactive WebSocket handler implementing STOMP 1.2 protocol over WebFlux WebSocket.
 *
 * <p>This handler manages the full STOMP lifecycle for a single WebSocket connection:</p>
 * <ol>
 *   <li>Parse incoming STOMP frames from the WebSocket text channel.</li>
 *   <li>Handle CONNECT → register session, send CONNECTED response.</li>
 *   <li>Handle SUBSCRIBE/UNSUBSCRIBE → update topic registry.</li>
 *   <li>Handle SEND to {@code /app/gps-ping} → process GPS ping pipeline.</li>
 *   <li>Handle SEND to {@code /app/heartbeat} → refresh session TTL.</li>
 *   <li>Handle DISCONNECT → terminate session cleanly.</li>
 *   <li>Push outbound messages from the per-session {@link Sinks.Many} sink.</li>
 * </ol>
 *
 * <p>Multi-tenant isolation: the tenant is extracted from the JWT token
 * validated during the HTTP upgrade handshake. The handler reads it from
 * the WebSocket session attributes set by the security filter.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class TntStompWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TntStompWebSocketHandler.class);

    private static final String STOMP_VERSION = "1.2";
    private static final String HEARTBEAT_NEGOTIATION = "0,10000"; // server sends every 10s
    private static final String APP_GPS_PING = "/app/gps-ping";
    private static final String APP_HEARTBEAT = "/app/heartbeat";

    /** Maps Spring WebSocketSession.getId() → Sinks.Many for outbound messages */
    private final Map<String, Sinks.Many<String>> outboundSinks = new ConcurrentHashMap<>();

    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketSessionManager sessionManager;
    private final IRegisterSessionUseCase registerSession;
    private final ITerminateSessionUseCase terminateSession;
    private final ISubscribeToTopicUseCase subscribeToTopic;
    private final IUnsubscribeFromTopicUseCase unsubscribeFromTopic;
    private final IProcessGpsPingUseCase processGpsPing;
    private final ObjectMapper objectMapper;

    public TntStompWebSocketHandler(WebSocketSessionRegistry sessionRegistry,
                                    WebSocketSessionManager sessionManager,
                                    IRegisterSessionUseCase registerSession,
                                    ITerminateSessionUseCase terminateSession,
                                    ISubscribeToTopicUseCase subscribeToTopic,
                                    IUnsubscribeFromTopicUseCase unsubscribeFromTopic,
                                    IProcessGpsPingUseCase processGpsPing,
                                    ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.sessionManager = sessionManager;
        this.registerSession = registerSession;
        this.terminateSession = terminateSession;
        this.subscribeToTopic = subscribeToTopic;
        this.unsubscribeFromTopic = unsubscribeFromTopic;
        this.processGpsPing = processGpsPing;
        this.objectMapper = objectMapper;
    }

    @Override
    @Nonnull
    public Mono<Void> handle(@Nonnull WebSocketSession wsSession) {
        SessionId sessionId = SessionId.of(wsSession.getId());

        // Create a multicast sink for server→client messages
        Sinks.Many<String> outboundSink = Sinks.many().multicast().onBackpressureBuffer(256, false);
        outboundSinks.put(wsSession.getId(), outboundSink);

        // Register the session in the adapter-level registry (holds the Spring WS session + sink)
        sessionRegistry.register(wsSession.getId(), wsSession, outboundSink);

        // Inbound: process STOMP frames from the client
        Mono<Void> inbound = wsSession.receive()
                .map(msg -> StompFrameParser.parse(msg.getPayloadAsText()))
                .flatMap(frame -> handleFrame(wsSession, sessionId, frame))
                .doOnError(ex -> log.error("Error in inbound stream for session {}: {}", wsSession.getId(), ex.getMessage()))
                .onErrorResume(ex -> Mono.empty())
                .then();

        // Outbound: push messages from the sink to the client
        Mono<Void> outbound = wsSession.send(
                outboundSink.asFlux().map(wsSession::textMessage)
        );

        return Mono.zip(inbound, outbound)
                .doFinally(signalType -> cleanup(sessionId, wsSession.getId()))
                .then();
    }

    // ─── Frame routing ────────────────────────────────────────────────────────

    private Mono<Void> handleFrame(WebSocketSession wsSession, SessionId sessionId, StompFrame frame) {
        return switch (frame.getCommand()) {
            case CONNECT    -> handleConnect(wsSession, sessionId, frame);
            case SUBSCRIBE  -> handleSubscribe(sessionId, frame);
            case UNSUBSCRIBE -> handleUnsubscribe(sessionId, frame);
            case SEND       -> handleSend(wsSession, sessionId, frame);
            case DISCONNECT -> handleDisconnect(sessionId, frame);
            case HEARTBEAT  -> handleHeartbeat(sessionId);
            default -> {
                log.debug("Ignoring STOMP frame: {}", frame.getCommand());
                yield Mono.empty();
            }
        };
    }

    private Mono<Void> handleConnect(WebSocketSession wsSession, SessionId sessionId, StompFrame frame) {
        // Extract identity from session attributes (set by JWT security filter during HTTP upgrade)
        String userId = extractAttribute(wsSession, "userId", "anonymous");
        String tenantId = extractAttribute(wsSession, "tenantId", "default");
        String remoteAddress = wsSession.getHandshakeInfo().getRemoteAddress() != null
                ? wsSession.getHandshakeInfo().getRemoteAddress().toString() : "unknown";

        // Parse optional device info from the connect body
        DeviceInfo deviceInfo = parseDeviceInfo(frame.getBody());
        DeviceType deviceType = resolveDeviceType(frame.getHeaderOrDefault("device-type", "PWA_BROWSER"));

        log.info("STOMP CONNECT from user {} (tenant {}) via session {}", userId, tenantId, sessionId);

        String connectedFrame = StompFrameSerializer.connected(
                STOMP_VERSION, sessionId.value(), HEARTBEAT_NEGOTIATION);

        return registerSession.registerSession(sessionId, userId, tenantId, deviceType, deviceInfo, remoteAddress)
                .then(Mono.fromRunnable(() -> emit(wsSession.getId(), connectedFrame)));
    }

    private Mono<Void> handleSubscribe(SessionId sessionId, StompFrame frame) {
        String destination = frame.getDestination().orElse("");
        if (destination.isBlank()) {
            log.warn("SUBSCRIBE frame without destination — session {}", sessionId);
            return Mono.empty();
        }

        String subscriptionId = frame.getSubscriptionId().orElse(UUID.randomUUID().toString());
        log.debug("Session {} subscribing to {} (id={})", sessionId, destination, subscriptionId);

        return subscribeToTopic.subscribeToTopic(sessionId, destination)
                .then(frame.getReceiptId()
                        .map(receiptId -> Mono.<Void>fromRunnable(() ->
                                emit(sessionId.value(), StompFrameSerializer.receipt(receiptId))))
                        .orElse(Mono.empty()));
    }

    private Mono<Void> handleUnsubscribe(SessionId sessionId, StompFrame frame) {
        String destination = frame.getDestination().orElse("");
        if (!destination.isBlank()) {
            return unsubscribeFromTopic.unsubscribeFromTopic(sessionId, destination);
        }
        return Mono.empty();
    }

    private Mono<Void> handleSend(WebSocketSession wsSession, SessionId sessionId, StompFrame frame) {
        String destination = frame.getDestination().orElse("");

        if (APP_GPS_PING.equals(destination)) {
            return processGpsPingFrame(wsSession, frame);
        } else if (APP_HEARTBEAT.equals(destination)) {
            return handleHeartbeat(sessionId);
        }

        log.debug("Unknown SEND destination: {} — session {}", destination, sessionId);
        return Mono.empty();
    }

    private Mono<Void> handleDisconnect(SessionId sessionId, StompFrame frame) {
        log.debug("STOMP DISCONNECT from session {}", sessionId);
        return terminateSession.terminateSession(sessionId, "CLIENT_DISCONNECT");
    }

    private Mono<Void> handleHeartbeat(SessionId sessionId) {
        sessionManager.touch(sessionId);
        return Mono.empty();
    }

    private Mono<Void> processGpsPingFrame(WebSocketSession wsSession, StompFrame frame) {
        if (!frame.hasBody()) {
            log.warn("GPS ping frame without body");
            return Mono.empty();
        }

        try {
            GpsPingMessage msg = objectMapper.readValue(frame.getBody(), GpsPingMessage.class);
            String tenantId = extractAttribute(wsSession, "tenantId", "default");

            GPSStreamEntry entry = new GPSStreamEntry(
                    msg.delivererId(),
                    msg.missionId(),
                    tenantId,
                    GeoCoordinates.of(msg.latitude(), msg.longitude(), msg.altitude(), null),
                    msg.speedKmh(),
                    msg.bearing(),
                    msg.accuracy(),
                    msg.batteryLevel(),
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(msg.timestamp()), ZoneId.systemDefault()),
                    msg.freelancerOrgId() // : FreelancerOrg fleet tracking
            );

            return processGpsPing.processGpsPing(entry);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse GPS ping payload: {}", e.getMessage());
            return Mono.empty();
        }
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────────

    private void cleanup(SessionId sessionId, String rawSessionId) {
        outboundSinks.remove(rawSessionId);
        sessionRegistry.unregister(rawSessionId);
        terminateSession.terminateSession(sessionId, "SESSION_CLOSED")
                .subscribe(null, ex -> log.warn("Error during session cleanup {}: {}", sessionId, ex.getMessage()));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Emits a raw STOMP frame text to the session's outbound sink.
     *
     * @param rawSessionId the Spring WebSocket session ID
     * @param frame        the serialized STOMP frame to send
     */
    public void emit(String rawSessionId, String frame) {
        Sinks.Many<String> sink = outboundSinks.get(rawSessionId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(frame);
            if (result.isFailure()) {
                log.warn("Failed to emit STOMP frame to session {}: {}", rawSessionId, result);
            }
        }
    }

    /**
     * Returns all known outbound sinks for cross-handler access (used by broadcaster).
     *
     * @return map of rawSessionId → Sinks.Many
     */
    public Map<String, Sinks.Many<String>> getOutboundSinks() {
        return outboundSinks;
    }

    private String extractAttribute(WebSocketSession wsSession, String name, String defaultValue) {
        Object value = wsSession.getAttributes().get(name);
        return value != null ? value.toString() : defaultValue;
    }

    private DeviceInfo parseDeviceInfo(String body) {
        if (body == null || body.isBlank()) {
            return DeviceInfo.of(DeviceType.PWA_BROWSER, "unknown", "unknown");
        }
        try {
            ConnectRequest req = objectMapper.readValue(body, ConnectRequest.class);
            return new DeviceInfo(
                    resolveDeviceType(req.deviceType()),
                    req.appVersion() != null ? req.appVersion() : "unknown",
                    req.osVersion() != null ? req.osVersion() : "unknown",
                    req.pushToken()
            );
        } catch (Exception e) {
            return DeviceInfo.of(DeviceType.PWA_BROWSER, "unknown", "unknown");
        }
    }

    private DeviceType resolveDeviceType(String raw) {
        if (raw == null) return DeviceType.PWA_BROWSER;
        try {
            return DeviceType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DeviceType.PWA_BROWSER;
        }
    }

    private String wsSession(SessionId sessionId) {
        return sessionId.value();
    }
}
