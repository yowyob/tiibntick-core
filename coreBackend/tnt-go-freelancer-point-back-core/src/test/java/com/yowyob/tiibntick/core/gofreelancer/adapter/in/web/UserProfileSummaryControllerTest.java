package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpClientUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpFreelancerUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpUserUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that UserProfileSummaryController enforces self-access:
 * only the authenticated user can query their own profile summary.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileSummaryControllerTest {

    @Mock private GofpUserUseCase       userUseCase;
    @Mock private GofpClientUseCase     clientUseCase;
    @Mock private GofpFreelancerUseCase freelancerUseCase;
    @Mock private GofpRelayPointUseCase relayPointUseCase;

    private UserProfileSummaryController controller;

    private final UUID tenantId  = UUID.randomUUID();
    private final UUID ownerId   = UUID.randomUUID(); // the user who owns the profile
    private final UUID callerId  = UUID.randomUUID(); // a different authenticated user

    private final List<LogCapture> logCaptures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        controller = new UserProfileSummaryController(
                userUseCase, clientUseCase, freelancerUseCase, relayPointUseCase);
    }

    @AfterEach
    void tearDown() {
        for (LogCapture capture : logCaptures) {
            capture.logger().detachAppender(capture.appender());
        }
        logCaptures.clear();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 1 — cross-user access attempt returns 403, use case never called
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void getProfilesSummary_callerIsNotOwner_returns403AndDoesNotCallUseCase() {
        TntSecurityContext context = TntSecurityContext.builder()
                .userId(callerId)
                .tenantId(tenantId)
                .authenticated(true)
                .build();

        StepVerifier.create(controller.getProfilesSummary(ownerId, context))
                .expectNextMatches(r -> r.getStatusCode() == HttpStatus.FORBIDDEN)
                .verifyComplete();

        verify(userUseCase, never()).findByCoreUserId(any());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 2 — caller matches path variable: 200, use case is called
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void getProfilesSummary_callerIsOwner_returns200AndDelegatesToUseCase() {
        TntSecurityContext context = TntSecurityContext.builder()
                .userId(ownerId)
                .tenantId(tenantId)
                .authenticated(true)
                .build();

        GofpUser user = GofpUser.builder()
                .id(UUID.randomUUID())
                .coreUserId(ownerId)
                .firstName("Alice")
                .lastName("Martin")
                .email("alice@example.com")
                .build();

        when(userUseCase.findByCoreUserId(ownerId)).thenReturn(Mono.just(user));
        when(clientUseCase.findByCoreUserId(ownerId)).thenReturn(Mono.empty());
        when(freelancerUseCase.findByCoreUserId(ownerId)).thenReturn(Mono.empty());

        StepVerifier.create(controller.getProfilesSummary(ownerId, context))
                .expectNextMatches(r -> r.getStatusCode() == HttpStatus.OK)
                .verifyComplete();

        verify(userUseCase).findByCoreUserId(ownerId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 3 — cross-user access is WARN-logged with both identities
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void getProfilesSummary_callerIsNotOwner_logsWarnWithBothIdentities() {
        TntSecurityContext context = TntSecurityContext.builder()
                .userId(callerId)
                .tenantId(tenantId)
                .authenticated(true)
                .build();

        ListAppender<ILoggingEvent> appender = attachLogAppender(UserProfileSummaryController.class);

        StepVerifier.create(controller.getProfilesSummary(ownerId, context))
                .expectNextMatches(r -> r.getStatusCode() == HttpStatus.FORBIDDEN)
                .verifyComplete();

        assertThat(appender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .anyMatch(e -> {
                    String msg = e.getFormattedMessage();
                    return msg.contains(callerId.toString())
                            && msg.contains(ownerId.toString());
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
