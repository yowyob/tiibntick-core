package com.yowyob.tiibntick.core.realtime.application.service;

import com.yowyob.tiibntick.core.realtime.application.port.in.IRegisterSessionUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.in.ISubscribeToTopicUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.in.ITerminateSessionUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.in.IUnsubscribeFromTopicUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.out.IRealtimeEventPublisher;
import com.yowyob.tiibntick.core.realtime.application.port.out.ISessionRepository;
import com.yowyob.tiibntick.core.realtime.domain.event.ActorConnectedEvent;
import com.yowyob.tiibntick.core.realtime.domain.event.ActorDisconnectedEvent;
import com.yowyob.tiibntick.core.realtime.domain.model.DeviceInfo;
import com.yowyob.tiibntick.core.realtime.domain.model.SessionId;
import com.yowyob.tiibntick.core.realtime.domain.model.WebSocketSession;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.DeviceType;
import com.yowyob.tiibntick.core.realtime.domain.service.GeofenceMonitorService;
import com.yowyob.tiibntick.core.realtime.domain.service.GpsPingProcessor;
import com.yowyob.tiibntick.core.realtime.domain.service.PresenceDomainService;
import com.yowyob.tiibntick.core.realtime.domain.service.WebSocketSessionManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Application service managing the full lifecycle of WebSocket sessions:
 * registration, topic subscription management, and termination.
 *
 * @author MANFOUO Braun
 */
@Service
public class SessionApplicationService
        implements IRegisterSessionUseCase, ITerminateSessionUseCase,
                   ISubscribeToTopicUseCase, IUnsubscribeFromTopicUseCase {

    private static final Logger log = LoggerFactory.getLogger(SessionApplicationService.class);

    private final WebSocketSessionManager sessionManager;
    private final PresenceDomainService presenceDomainService;
    private final GeofenceMonitorService geofenceMonitorService;
    private final GpsPingProcessor gpsPingProcessor;
    private final ISessionRepository sessionRepository;
    private final IRealtimeEventPublisher eventPublisher;

    public SessionApplicationService(WebSocketSessionManager sessionManager,
                                     PresenceDomainService presenceDomainService,
                                     GeofenceMonitorService geofenceMonitorService,
                                     GpsPingProcessor gpsPingProcessor,
                                     ISessionRepository sessionRepository,
                                     IRealtimeEventPublisher eventPublisher,
                                     MeterRegistry meterRegistry) {
        this.sessionManager = sessionManager;
        this.presenceDomainService = presenceDomainService;
        this.geofenceMonitorService = geofenceMonitorService;
        this.gpsPingProcessor = gpsPingProcessor;
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;

        Gauge.builder("tnt.realtime.sessions.active", sessionManager, WebSocketSessionManager::getActiveSessionCount)
             .description("Number of active WebSocket sessions")
             .register(meterRegistry);
    }

    @Override
    public Mono<Void> registerSession(SessionId sessionId, String userId, String tenantId,
                                      DeviceType deviceType, DeviceInfo deviceInfo, String remoteAddress) {
        WebSocketSession session = WebSocketSession.builder()
                .id(sessionId)
                .userId(userId)
                .tenantId(tenantId)
                .deviceType(deviceType)
                .deviceInfo(deviceInfo)
                .remoteAddress(remoteAddress)
                .build();

        sessionManager.register(session);

        ActorConnectedEvent event = new ActorConnectedEvent(tenantId, sessionId.value(), userId, deviceType, remoteAddress);

        return presenceDomainService.markOnline(userId, tenantId, deviceInfo)
                .then(sessionRepository.save(session))
                .then(eventPublisher.publish(event))
                .doOnSuccess(v -> log.info("Session {} registered for user {} (tenant {})", sessionId, userId, tenantId))
                .doOnError(ex -> log.error("Failed to register session {}: {}", sessionId, ex.getMessage()));
    }

    @Override
    public Mono<Void> terminateSession(SessionId sessionId, String reason) {
        return Mono.defer(() -> {
            return sessionManager.findSession(sessionId)
                    .map(session -> {
                        sessionManager.unregister(sessionId);
                        geofenceMonitorService.clearDelivererState(session.getUserId());
                        gpsPingProcessor.clearLastPosition(session.getUserId());

                        ActorDisconnectedEvent event = new ActorDisconnectedEvent(
                                session.getTenantId(), sessionId.value(), session.getUserId(), reason);

                        // Check if user still has other active sessions before marking offline
                        boolean hasOtherSessions = !sessionManager.getSessionsByUser(session.getUserId()).isEmpty();

                        return sessionRepository.deleteById(sessionId.value())
                                .then(hasOtherSessions
                                        ? Mono.empty()
                                        : presenceDomainService.markOffline(session.getUserId(), session.getTenantId()))
                                .then(eventPublisher.publish(event))
                                .doOnSuccess(v -> log.info("Session {} terminated for user {} — reason: {}",
                                        sessionId, session.getUserId(), reason));
                    })
                    .orElseGet(() -> {
                        log.warn("Terminate called for unknown session: {}", sessionId);
                        return Mono.empty();
                    });
        });
    }

    @Override
    public Mono<Void> subscribeToTopic(SessionId sessionId, String topic) {
        return Mono.fromRunnable(() -> {
            sessionManager.subscribe(sessionId, topic);
            log.debug("Session {} subscribed to topic {}", sessionId, topic);
        });
    }

    @Override
    public Mono<Void> unsubscribeFromTopic(SessionId sessionId, String topic) {
        return Mono.fromRunnable(() -> {
            sessionManager.unsubscribe(sessionId, topic);
            log.debug("Session {} unsubscribed from topic {}", sessionId, topic);
        });
    }
}
