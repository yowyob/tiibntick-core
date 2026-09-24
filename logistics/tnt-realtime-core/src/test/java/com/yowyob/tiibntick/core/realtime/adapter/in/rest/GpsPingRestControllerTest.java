package com.yowyob.tiibntick.core.realtime.adapter.in.rest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.realtime.application.port.in.IProcessGpsPingUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Proves that GpsPingRestController anchors the GPS entry's delivererId on the JWT sub:
 * the presence key written to Redis is always the JWT userId, regardless of the request body.
 *
 * <p>Mirrors the same invariant enforced by FreelancerLocationController (lot 3 fix)
 * on the other GPS write path.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class GpsPingRestControllerTest {

    @Mock private IProcessGpsPingUseCase processGpsPing;

    private GpsPingRestController controller;

    private final UUID sub      = UUID.randomUUID(); // JWT sub = presence key
    private final UUID tenantId = UUID.randomUUID();

    private final List<LogCapture> logCaptures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        controller = new GpsPingRestController(processGpsPing);
        // lenient: unused in test 3 (403 path never reaches processGpsPing)
        lenient().when(processGpsPing.processGpsPing(any())).thenReturn(Mono.empty());
    }

    @AfterEach
    void tearDown() {
        for (LogCapture capture : logCaptures) {
            capture.logger().detachAppender(capture.appender());
        }
        logCaptures.clear();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 1 — delivererId absent in body → entry carries JWT sub
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void ping_noDelivererId_entryCarriesJwtSub() {
        GpsPingRestController.GpsPingRestRequest body =
                new GpsPingRestController.GpsPingRestRequest(null, null, 3.8, 11.5, null, null, null);

        StepVerifier.create(controller.ping(user(sub, tenantId), body))
                .expectNextMatches(r -> r.getStatusCode() == HttpStatus.NO_CONTENT)
                .verifyComplete();

        ArgumentCaptor<GPSStreamEntry> captor = ArgumentCaptor.forClass(GPSStreamEntry.class);
        verify(processGpsPing).processGpsPing(captor.capture());
        assertThat(captor.getValue().delivererId()).isEqualTo(sub.toString());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 2 — delivererId == sub → accepted, entry carries sub
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void ping_delivererIdEqualsJwtSub_acceptedAndEntryCarriesSub() {
        GpsPingRestController.GpsPingRestRequest body =
                new GpsPingRestController.GpsPingRestRequest(sub.toString(), "mission-1", 3.8, 11.5, 5.0, 30.0, 90.0);

        StepVerifier.create(controller.ping(user(sub, tenantId), body))
                .expectNextMatches(r -> r.getStatusCode() == HttpStatus.NO_CONTENT)
                .verifyComplete();

        ArgumentCaptor<GPSStreamEntry> captor = ArgumentCaptor.forClass(GPSStreamEntry.class);
        verify(processGpsPing).processGpsPing(captor.capture());
        assertThat(captor.getValue().delivererId()).isEqualTo(sub.toString());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 3 — delivererId != sub → 403 + WARN log, processGpsPing never called
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void ping_delivererIdDiffersFromJwtSub_returns403AndLogsWarn() {
        UUID wrongId = UUID.randomUUID();
        GpsPingRestController.GpsPingRestRequest body =
                new GpsPingRestController.GpsPingRestRequest(wrongId.toString(), null, 3.8, 11.5, null, null, null);

        ListAppender<ILoggingEvent> appender = attachLogAppender(GpsPingRestController.class);

        StepVerifier.create(controller.ping(user(sub, tenantId), body))
                .expectNextMatches(r -> r.getStatusCode() == HttpStatus.FORBIDDEN)
                .verifyComplete();

        verify(processGpsPing, never()).processGpsPing(any());

        assertThat(appender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .anyMatch(e -> {
                    String msg = e.getFormattedMessage();
                    return msg.contains(sub.toString()) && msg.contains(wrongId.toString());
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 4 — tenantId in the entry comes from JWT, never from the body
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void ping_tenantIdInEntryIsFromJwt() {
        GpsPingRestController.GpsPingRestRequest body =
                new GpsPingRestController.GpsPingRestRequest(null, null, 3.8, 11.5, null, null, null);

        StepVerifier.create(controller.ping(user(sub, tenantId), body))
                .expectNextMatches(r -> r.getStatusCode() == HttpStatus.NO_CONTENT)
                .verifyComplete();

        ArgumentCaptor<GPSStreamEntry> captor = ArgumentCaptor.forClass(GPSStreamEntry.class);
        verify(processGpsPing).processGpsPing(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(tenantId.toString());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Fixtures
    // ══════════════════════════════════════════════════════════════════════

    private TntUserIdentity user(UUID userId, UUID tenant) {
        return new TntUserIdentity(userId, tenant, null, null, null, Set.of(), false);
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
