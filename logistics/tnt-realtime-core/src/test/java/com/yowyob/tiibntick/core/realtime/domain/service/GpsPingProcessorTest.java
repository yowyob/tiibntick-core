package com.yowyob.tiibntick.core.realtime.domain.service;

import com.yowyob.tiibntick.core.realtime.application.port.out.IActorLocationUpdater;
import com.yowyob.tiibntick.core.realtime.application.port.out.IKalmanEtaUpdater;
import com.yowyob.tiibntick.core.realtime.application.port.out.IRealtimeEventPublisher;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.model.ETAInterval;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GpsPingProcessor}.
 * Verifies the GPS ingestion pipeline: validation, outlier detection,
 * Kalman invocation, broadcast, and geofence checking.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class GpsPingProcessorTest {

    @Mock private IActorLocationUpdater locationUpdater;
    @Mock private IKalmanEtaUpdater kalmanEtaUpdater;
    @Mock private IWebSocketBroadcaster broadcaster;
    @Mock private IRealtimeEventPublisher eventPublisher;
    @Mock private GeofenceMonitorService geofenceMonitorService;

    private GpsPingProcessor processor;

    private static final GeoCoordinates YAOUNDE = GeoCoordinates.of(3.8480, 11.5021);

    @BeforeEach
    void setUp() {
        processor = new GpsPingProcessor(
                locationUpdater, kalmanEtaUpdater, broadcaster, eventPublisher, geofenceMonitorService);
    }

    private GPSStreamEntry validPing(String delivererId, String missionId, GeoCoordinates coords) {
        return GPSStreamEntry.of(
                delivererId, missionId, "tenant-A",
                coords, 30.0, 90.0, 15.0, 85,
                LocalDateTime.now()
        );
    }

    private LiveETAUpdate stubEtaUpdate(String missionId) {
        return LiveETAUpdate.of(
                missionId, "d1", "tenant-A", "TNT-DEL-001",
                YAOUNDE,
                ETAInterval.of(LocalDateTime.now().plusMinutes(25), LocalDateTime.now().plusMinutes(35)),
                10.0, 30, 0.92
        );
    }

    @Test
    @DisplayName("process() with valid ping → calls locationUpdater, Kalman, geofence, eventPublisher")
    void processValidPingCallsAllPipelineSteps() {
        when(kalmanEtaUpdater.update(anyString(), anyString(), anyString(), any(), any(double.class), any(double.class)))
                .thenReturn(Mono.just(stubEtaUpdate("M1")));
        when(locationUpdater.updateLocation(anyString(), anyString(), any())).thenReturn(Mono.empty());
        when(geofenceMonitorService.checkGeofences(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());

        GPSStreamEntry ping = validPing("d1", "M1", YAOUNDE);

        StepVerifier.create(processor.process(ping))
                .verifyComplete();

        verify(locationUpdater).updateLocation("d1", "tenant-A", YAOUNDE);
        verify(kalmanEtaUpdater).update("d1", "M1", "tenant-A", YAOUNDE, 30.0, 90.0);
        verify(geofenceMonitorService).checkGeofences(ping);
        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("process() with invalid coordinates → discards without calling downstream")
    void processInvalidCoordinatesDiscards() {
        GeoCoordinates invalid = GeoCoordinates.of(200.0, 500.0);
        GPSStreamEntry ping = GPSStreamEntry.of(
                "d1", "M1", "tenant-A",
                invalid, 30.0, 90.0, 15.0, null,
                LocalDateTime.now()
        );

        StepVerifier.create(processor.process(ping))
                .verifyComplete();

        verify(locationUpdater, never()).updateLocation(any(), any(), any());
        verify(kalmanEtaUpdater, never()).update(any(), any(), any(), any(), any(double.class), any(double.class));
    }

    @Test
    @DisplayName("process() with outlier GPS jump → discards the ping")
    void processOutlierPingDiscards() {
        when(kalmanEtaUpdater.update(any(), any(), any(), any(), any(double.class), any(double.class)))
                .thenReturn(Mono.just(stubEtaUpdate("M1")));
        when(locationUpdater.updateLocation(any(), any(), any())).thenReturn(Mono.empty());
        when(geofenceMonitorService.checkGeofences(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());

        GPSStreamEntry firstPing = validPing("d1", "M1", YAOUNDE);
        processor.process(firstPing).block();

        GeoCoordinates farAway = GeoCoordinates.of(5.0, 15.0);
        GPSStreamEntry outlier = GPSStreamEntry.of(
                "d1", "M1", "tenant-A",
                farAway, 30.0, 90.0, 15.0, null,
                LocalDateTime.now()
        );

        StepVerifier.create(processor.process(outlier))
                .verifyComplete();

        verify(kalmanEtaUpdater, times(1)).update(any(), any(), any(), any(), any(double.class), any(double.class));
    }

    @Test
    @DisplayName("process() without missionId → skips Kalman but still processes location and geofence")
    void processWithoutMissionSkipsKalman() {
        when(locationUpdater.updateLocation(anyString(), anyString(), any())).thenReturn(Mono.empty());
        when(geofenceMonitorService.checkGeofences(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        GPSStreamEntry ping = validPing("d1", null, YAOUNDE);

        StepVerifier.create(processor.process(ping))
                .verifyComplete();

        verify(locationUpdater).updateLocation("d1", "tenant-A", YAOUNDE);
        verify(kalmanEtaUpdater, never()).update(any(), any(), any(), any(), any(double.class), any(double.class));
        verify(geofenceMonitorService).checkGeofences(ping);
    }

    @Test
    @DisplayName("process() 10 consecutive valid pings — all processed independently")
    void processConsecutivePings() {
        when(kalmanEtaUpdater.update(any(), any(), any(), any(), any(double.class), any(double.class)))
                .thenReturn(Mono.just(stubEtaUpdate("M1")));
        when(locationUpdater.updateLocation(any(), any(), any())).thenReturn(Mono.empty());
        when(geofenceMonitorService.checkGeofences(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());

        // Small incremental moves (realistic GPS updates)
        double baseLat = 3.848;
        for (int i = 0; i < 10; i++) {
            GeoCoordinates coords = GeoCoordinates.of(baseLat + i * 0.001, 11.5021);
            LocalDateTime ts = LocalDateTime.now().plusSeconds(i * 10);
            GPSStreamEntry ping = GPSStreamEntry.of(
                    "d1", "M1", "tenant-A",
                    coords, 30.0, 90.0, 15.0, 85, ts);
            processor.process(ping).block();
        }

        verify(locationUpdater, times(10)).updateLocation(any(), any(), any());
        verify(kalmanEtaUpdater, times(10)).update(any(), any(), any(), any(), any(double.class), any(double.class));
        verify(geofenceMonitorService, times(10)).checkGeofences(any());
    }

    @Test
    @DisplayName("clearLastPosition() resets outlier detection state for a deliverer")
    void clearLastPositionResetsState() {
        when(kalmanEtaUpdater.update(any(), any(), any(), any(), any(double.class), any(double.class)))
                .thenReturn(Mono.just(stubEtaUpdate("M1")));
        when(locationUpdater.updateLocation(any(), any(), any())).thenReturn(Mono.empty());
        when(geofenceMonitorService.checkGeofences(any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());

        processor.process(validPing("d1", "M1", YAOUNDE)).block();
        processor.clearLastPosition("d1");

        GeoCoordinates farAway = GeoCoordinates.of(5.0, 15.0);
        GPSStreamEntry pingAfterClear = validPing("d1", "M1", farAway);
        processor.process(pingAfterClear).block();

        verify(locationUpdater, times(2)).updateLocation(any(), any(), any());
    }
}
