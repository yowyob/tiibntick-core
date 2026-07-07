package com.yowyob.tiibntick.core.realtime.application.service;

import com.yowyob.tiibntick.core.realtime.application.port.out.IRealtimeEventPublisher;
import com.yowyob.tiibntick.core.realtime.application.port.out.ISessionRepository;
import com.yowyob.tiibntick.core.realtime.domain.model.DeviceInfo;
import com.yowyob.tiibntick.core.realtime.domain.model.SessionId;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.DeviceType;
import com.yowyob.tiibntick.core.realtime.domain.service.GeofenceMonitorService;
import com.yowyob.tiibntick.core.realtime.domain.service.GpsPingProcessor;
import com.yowyob.tiibntick.core.realtime.domain.service.PresenceDomainService;
import com.yowyob.tiibntick.core.realtime.domain.service.WebSocketSessionManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SessionApplicationService}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class SessionApplicationServiceTest {

    @Mock private PresenceDomainService presenceDomainService;
    @Mock private GeofenceMonitorService geofenceMonitorService;
    @Mock private GpsPingProcessor gpsPingProcessor;
    @Mock private ISessionRepository sessionRepository;
    @Mock private IRealtimeEventPublisher eventPublisher;

    private WebSocketSessionManager sessionManager;
    private SessionApplicationService service;

    private static final SessionId SESSION_ID = SessionId.of("session-001");
    private static final String USER_ID = "user-abc";
    private static final String TENANT_ID = "tenant-A";
    private static final DeviceInfo DEVICE_INFO = DeviceInfo.of(DeviceType.IOS, "3.0", "iOS 17");

    @BeforeEach
    void setUp() {
        sessionManager = new WebSocketSessionManager();
        service = new SessionApplicationService(
                sessionManager, presenceDomainService, geofenceMonitorService,
                gpsPingProcessor, sessionRepository, eventPublisher, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("registerSession() registers session in domain manager and publishes ActorConnectedEvent")
    void registerSessionRegistersAndPublishesEvent() {
        when(presenceDomainService.markOnline(any(), any(), any())).thenReturn(Mono.empty());
        when(sessionRepository.save(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.registerSession(
                SESSION_ID, USER_ID, TENANT_ID, DeviceType.IOS, DEVICE_INFO, "192.168.1.1"))
                .verifyComplete();

        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(1);
        assertThat(sessionManager.findSession(SESSION_ID)).isPresent();

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(any());
        verify(presenceDomainService).markOnline(USER_ID, TENANT_ID, DEVICE_INFO);
    }

    @Test
    @DisplayName("terminateSession() removes session from manager and publishes ActorDisconnectedEvent")
    void terminateSessionRemovesAndPublishesEvent() {
        // Register first
        when(presenceDomainService.markOnline(any(), any(), any())).thenReturn(Mono.empty());
        when(sessionRepository.save(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        service.registerSession(SESSION_ID, USER_ID, TENANT_ID, DeviceType.IOS, DEVICE_INFO, "127.0.0.1").block();

        when(presenceDomainService.markOffline(any(), any())).thenReturn(Mono.empty());
        when(sessionRepository.deleteById(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.terminateSession(SESSION_ID, "CLIENT_DISCONNECT"))
                .verifyComplete();

        assertThat(sessionManager.findSession(SESSION_ID)).isEmpty();
        assertThat(sessionManager.getActiveSessionCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("terminateSession() marks actor offline when no remaining sessions")
    void terminateSessionMarksOfflineWhenNoSessions() {
        when(presenceDomainService.markOnline(any(), any(), any())).thenReturn(Mono.empty());
        when(sessionRepository.save(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        service.registerSession(SESSION_ID, USER_ID, TENANT_ID, DeviceType.IOS, DEVICE_INFO, "127.0.0.1").block();

        when(presenceDomainService.markOffline(any(), any())).thenReturn(Mono.empty());
        when(sessionRepository.deleteById(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        service.terminateSession(SESSION_ID, "TIMEOUT").block();

        verify(presenceDomainService).markOffline(USER_ID, TENANT_ID);
    }

    @Test
    @DisplayName("subscribeToTopic() registers topic in session manager")
    void subscribeToTopicRegistersCorrectly() {
        when(presenceDomainService.markOnline(any(), any(), any())).thenReturn(Mono.empty());
        when(sessionRepository.save(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        service.registerSession(SESSION_ID, USER_ID, TENANT_ID, DeviceType.IOS, DEVICE_INFO, "127.0.0.1").block();

        StepVerifier.create(service.subscribeToTopic(SESSION_ID, "/topic/delivery/M-001"))
                .verifyComplete();

        assertThat(sessionManager.getSubscriberCount("/topic/delivery/M-001")).isEqualTo(1);
    }

    @Test
    @DisplayName("unsubscribeFromTopic() removes subscription from manager")
    void unsubscribeFromTopicRemovesSubscription() {
        when(presenceDomainService.markOnline(any(), any(), any())).thenReturn(Mono.empty());
        when(sessionRepository.save(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        service.registerSession(SESSION_ID, USER_ID, TENANT_ID, DeviceType.IOS, DEVICE_INFO, "127.0.0.1").block();
        service.subscribeToTopic(SESSION_ID, "/topic/delivery/M-001").block();

        StepVerifier.create(service.unsubscribeFromTopic(SESSION_ID, "/topic/delivery/M-001"))
                .verifyComplete();

        assertThat(sessionManager.getSubscriberCount("/topic/delivery/M-001")).isEqualTo(0);
    }
}
