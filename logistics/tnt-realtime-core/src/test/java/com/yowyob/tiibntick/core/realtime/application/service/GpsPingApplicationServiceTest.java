package com.yowyob.tiibntick.core.realtime.application.service;

import com.yowyob.tiibntick.core.realtime.adapter.out.websocket.RedisBackedWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.service.GpsPingProcessor;
import com.yowyob.tiibntick.core.realtime.domain.service.PresenceDomainService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GpsPingApplicationService}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class GpsPingApplicationServiceTest {

    @Mock private GpsPingProcessor gpsPingProcessor;
    @Mock private PresenceDomainService presenceDomainService;

    private GpsPingApplicationService service;

    @BeforeEach
    void setUp() {
        RedisBackedWebSocketBroadcaster mockBroadcaster = mock(RedisBackedWebSocketBroadcaster.class);
        service = new GpsPingApplicationService(gpsPingProcessor, mockBroadcaster, presenceDomainService, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("processGpsPing() invokes presence update and GPS processor")
    void processGpsPingDelegatesCorrectly() {
        when(presenceDomainService.updateCoordinates(any(), any(), any())).thenReturn(Mono.empty());
        when(gpsPingProcessor.process(any())).thenReturn(Mono.empty());

        GPSStreamEntry entry = GPSStreamEntry.of(
                "d-001", "M-001", "tenant-A",
                GeoCoordinates.of(3.848, 11.502),
                25.0, 90.0, 10.0, 80, LocalDateTime.now()
        );

        StepVerifier.create(service.processGpsPing(entry))
                .verifyComplete();

        verify(presenceDomainService).updateCoordinates("d-001", "tenant-A", GeoCoordinates.of(3.848, 11.502));
        verify(gpsPingProcessor).process(entry);
    }

    @Test
    @DisplayName("processGpsPing() completes when processor returns empty")
    void processGpsPingCompletesOnEmpty() {
        when(presenceDomainService.updateCoordinates(any(), any(), any())).thenReturn(Mono.empty());
        when(gpsPingProcessor.process(any())).thenReturn(Mono.empty());

        GPSStreamEntry entry = GPSStreamEntry.of(
                "d-002", null, "tenant-A",
                GeoCoordinates.of(3.848, 11.502),
                0.0, 0.0, 5.0, null, LocalDateTime.now()
        );

        StepVerifier.create(service.processGpsPing(entry))
                .verifyComplete();
    }
}
