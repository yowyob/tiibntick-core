package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yowyob.tiibntick.core.gofreelancer.application.service.GofpUserProvisioningService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves GofpUserProvisioningFilter: skips silently when unauthenticated,
 * delegates to the service for valid JWT sub, swallows service errors.
 */
@ExtendWith(MockitoExtension.class)
class GofpUserProvisioningFilterTest {

    @Mock private GofpUserProvisioningService provisioningService;
    @Mock private ServerWebExchange           exchange;
    @Mock private WebFilterChain              chain;
    @Mock private ServerHttpRequest           request;

    private GofpUserProvisioningFilter filter;
    private SimpleMeterRegistry registry;

    private final List<LogCapture> logCaptures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        filter = new GofpUserProvisioningFilter(provisioningService, registry);
        filter.init();
        when(chain.filter(exchange)).thenReturn(Mono.empty());
    }

    @AfterEach
    void tearDown() {
        for (LogCapture capture : logCaptures) {
            capture.logger().detachAppender(capture.appender());
        }
        logCaptures.clear();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 1 — no security context (public path / unauthenticated request)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void filter_noSecurityContext_skipsProvisioning_chainContinues() {
        // No contextWrite → ReactiveSecurityContextHolder.getContext() returns empty
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(provisioningService, never()).provisionIfAbsent(any());
        verify(chain).filter(exchange);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 2 — principal name is not a valid UUID (platform client or service account)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void filter_principalNameNotUuid_skipsProvisioning_chainContinues() {
        Authentication auth = mockAuth(true, "not-a-uuid");

        Mono<Void> result = filter.filter(exchange, chain)
                .contextWrite(securityCtx(auth));

        StepVerifier.create(result).verifyComplete();

        verify(provisioningService, never()).provisionIfAbsent(any());
        verify(chain).filter(exchange);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 3 — valid JWT sub → provisionIfAbsent called exactly once with that sub
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void filter_validSub_callsProvisionIfAbsentWithSub_chainContinues() {
        UUID sub = UUID.randomUUID();
        Authentication auth = mockAuth(true, sub.toString());

        GofpUser provisioned = mock(GofpUser.class);
        when(provisioningService.provisionIfAbsent(sub)).thenReturn(Mono.just(provisioned));

        Mono<Void> result = filter.filter(exchange, chain)
                .contextWrite(securityCtx(auth));

        StepVerifier.create(result).verifyComplete();

        verify(provisioningService).provisionIfAbsent(sub);
        verify(chain).filter(exchange);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 4 — provisioning service throws → WARN logged, chain continues anyway
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void filter_serviceError_logsWarnAndChainContinues() {
        UUID sub = UUID.randomUUID();
        Authentication auth = mockAuth(true, sub.toString());

        when(provisioningService.provisionIfAbsent(sub))
                .thenReturn(Mono.error(new RuntimeException("DB unavailable")));
        when(exchange.getRequest()).thenReturn(request);

        ListAppender<ILoggingEvent> appender = attachLogAppender(GofpUserProvisioningFilter.class);

        Mono<Void> result = filter.filter(exchange, chain)
                .contextWrite(securityCtx(auth));

        StepVerifier.create(result).verifyComplete();

        assertThat(appender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .anyMatch(e -> e.getFormattedMessage().contains("provisioning failed"));

        verify(chain).filter(exchange);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 5 — provisioning failure increments the Micrometer counter
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void filter_serviceError_incrementsProvisioningFailureCounter() {
        UUID sub = UUID.randomUUID();
        Authentication auth = mockAuth(true, sub.toString());

        when(provisioningService.provisionIfAbsent(sub))
                .thenReturn(Mono.error(new RuntimeException("DB unavailable")));
        when(exchange.getRequest()).thenReturn(request);

        StepVerifier.create(
                filter.filter(exchange, chain).contextWrite(securityCtx(auth))
        ).verifyComplete();

        assertThat(registry.find("gofp.provisioning.failures").counter().count())
                .isEqualTo(1.0);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Fixtures
    // ══════════════════════════════════════════════════════════════════════

    private Authentication mockAuth(boolean authenticated, String name) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(authenticated);
        when(auth.getName()).thenReturn(name);
        return auth;
    }

    private Context securityCtx(Authentication auth) {
        return ReactiveSecurityContextHolder
                .withSecurityContext(Mono.just(new SecurityContextImpl(auth)));
    }

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
