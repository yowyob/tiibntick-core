package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerLocationUpdateRequest;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerLocationUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that FreelancerLocationController enforces position-write ownership:
 * a caller can only write their own GPS position, not another freelancer's.
 */
@ExtendWith(MockitoExtension.class)
class FreelancerLocationControllerTest {

    @Mock private FreelancerLocationUseCase locationUseCase;

    private FreelancerLocationController controller;

    private final UUID tenantId   = UUID.randomUUID();
    private final UUID ownerId    = UUID.randomUUID(); // the freelancer who owns the position
    private final UUID callerId   = UUID.randomUUID(); // a different authenticated user

    private final List<LogCapture> logCaptures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        controller = new FreelancerLocationController(locationUseCase);
    }

    @AfterEach
    void tearDown() {
        for (LogCapture capture : logCaptures) {
            capture.logger().detachAppender(capture.appender());
        }
        logCaptures.clear();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 1 — spoofing attempt returns 403, nothing is forwarded to processGpsPing
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void updateLocation_callerIsNotOwner_returns403AndDoesNotCallUseCase() {
        // callerId ≠ ownerId: caller tries to write position for a different freelancer
        TntSecurityContext context = TntSecurityContext.builder()
                .userId(callerId)
                .tenantId(tenantId)
                .authenticated(true)
                .build();

        FreelancerLocationUpdateRequest request = new FreelancerLocationUpdateRequest(
                3.866, 11.517, 0.0, 0.0, 0.0, null, null);

        StepVerifier.create(controller.updateLocation(ownerId, request, context))
                .expectNextMatches(r -> r.getStatusCode() == HttpStatus.FORBIDDEN)
                .verifyComplete();

        verify(locationUseCase, never()).updateLocation(
                any(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(), any());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 2 — caller writes their own position: use case is called
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void updateLocation_callerIsOwner_delegatesToUseCase() {
        // ownerId == callerId: the caller writes their own position
        TntSecurityContext context = TntSecurityContext.builder()
                .userId(ownerId)
                .tenantId(tenantId)
                .authenticated(true)
                .build();

        FreelancerLocationUpdateRequest request = new FreelancerLocationUpdateRequest(
                3.866, 11.517, 30.0, 90.0, 5.0, null, null);

        when(locationUseCase.updateLocation(
                eq(ownerId), eq(tenantId.toString()),
                eq(3.866), eq(11.517),
                eq(30.0), eq(90.0), eq(5.0),
                eq(null), eq(null)))
                .thenReturn(Mono.empty());

        StepVerifier.create(controller.updateLocation(ownerId, request, context))
                .expectNextMatches(r -> r.getStatusCode() == HttpStatus.OK)
                .verifyComplete();

        verify(locationUseCase).updateLocation(
                ownerId, tenantId.toString(),
                3.866, 11.517, 30.0, 90.0, 5.0, null, null);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 3 — spoofing attempt is logged as WARN with both identities
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void updateLocation_callerIsNotOwner_logsWarnWithBothIdentities() {
        TntSecurityContext context = TntSecurityContext.builder()
                .userId(callerId)
                .tenantId(tenantId)
                .authenticated(true)
                .build();

        FreelancerLocationUpdateRequest request = new FreelancerLocationUpdateRequest(
                3.866, 11.517, 0.0, 0.0, 0.0, null, null);

        ListAppender<ILoggingEvent> appender = attachLogAppender(FreelancerLocationController.class);

        StepVerifier.create(controller.updateLocation(ownerId, request, context))
                .expectNextMatches(r -> r.getStatusCode() == HttpStatus.FORBIDDEN)
                .verifyComplete();

        assertThat(appender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .anyMatch(e -> {
                    String msg = e.getFormattedMessage();
                    return msg.contains(callerId.toString())   // caller identity
                            && msg.contains(ownerId.toString()); // target identity
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    // Fixtures
    // ══════════════════════════════════════════════════════════════════════

    private ListAppender<ILoggingEvent> attachLogAppender(Class<?> loggerClass) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logCaptures.add(new LogCapture(logger, appender));
        return appender;
    }

    private record LogCapture(Logger logger, ListAppender<ILoggingEvent> appender) {}
}
