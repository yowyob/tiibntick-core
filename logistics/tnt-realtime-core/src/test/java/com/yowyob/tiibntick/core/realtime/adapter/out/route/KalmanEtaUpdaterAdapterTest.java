package com.yowyob.tiibntick.core.realtime.adapter.out.route;

import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;
import com.yowyob.tiibntick.core.realtime.domain.service.MissionTrackingCodeCache;
import com.yowyob.tiibntick.core.route.application.port.in.IUpdateEtaUseCase;
import com.yowyob.tiibntick.core.route.domain.model.EtaResult;
import com.yowyob.tiibntick.core.route.domain.model.GPSMeasurement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KalmanEtaUpdaterAdapter}, the real (non-stub) bridge from
 * tnt-realtime-core's GPS pipeline into tnt-route-core's Kalman filter.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class KalmanEtaUpdaterAdapterTest {

    @Mock private IUpdateEtaUseCase updateEtaUseCase;

    private MissionTrackingCodeCache trackingCodeCache;
    private KalmanEtaUpdaterAdapter adapter;

    private static final GeoCoordinates YAOUNDE = GeoCoordinates.of(3.8480, 11.5021);

    @BeforeEach
    void setUp() {
        trackingCodeCache = new MissionTrackingCodeCache();
        adapter = new KalmanEtaUpdaterAdapter(updateEtaUseCase, trackingCodeCache);
    }

    @Test
    @DisplayName("update() resolves the cached trackingCode and maps the real EtaResult into a LiveETAUpdate")
    void updateMapsRealEtaResultIntoLiveETAUpdate() {
        trackingCodeCache.put("M1", "TNT-20260725-A1B2C3D4");

        Instant now = Instant.now();
        EtaResult eta = new EtaResult(now.plusSeconds(1800), now.plusSeconds(1500),
                now.plusSeconds(2100), 0.82, now, 12.5);
        when(updateEtaUseCase.updateEta(eq("M1"), eq("TNT-20260725-A1B2C3D4"), any(GPSMeasurement.class)))
                .thenReturn(Mono.just(eta));

        StepVerifier.create(adapter.update("d1", "M1", "tenant-A", YAOUNDE, 30.0, 90.0, 15.0))
                .assertNext(update -> {
                    assertThat(update.missionId()).isEqualTo("M1");
                    assertThat(update.delivererId()).isEqualTo("d1");
                    assertThat(update.tenantId()).isEqualTo("tenant-A");
                    assertThat(update.trackingCode()).isEqualTo("TNT-20260725-A1B2C3D4");
                    assertThat(update.currentCoordinates()).isEqualTo(YAOUNDE);
                    assertThat(update.remainingDistanceKm()).isEqualTo(12.5);
                    assertThat(update.kalmanConfidence()).isEqualTo(0.82);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("update() builds a GPSMeasurement from the GPS ping fields, including accuracy")
    void updateBuildsGpsMeasurementFromPingFields() {
        Instant now = Instant.now();
        EtaResult eta = new EtaResult(now.plusSeconds(600), now.plusSeconds(500),
                now.plusSeconds(700), 0.9, now, 5.0);
        when(updateEtaUseCase.updateEta(anyString(), any(), any(GPSMeasurement.class)))
                .thenReturn(Mono.just(eta));

        adapter.update("d1", "M1", "tenant-A", YAOUNDE, 42.0, 180.0, 20.0).block();

        ArgumentCaptor<GPSMeasurement> captor = ArgumentCaptor.forClass(GPSMeasurement.class);
        verify(updateEtaUseCase).updateEta(eq("M1"), any(), captor.capture());
        GPSMeasurement measurement = captor.getValue();
        assertThat(measurement.speedKmh()).isEqualTo(42.0);
        assertThat(measurement.bearing()).isEqualTo(180.0);
        assertThat(measurement.accuracyMetres()).isEqualTo(20.0);
        assertThat(measurement.coordinates().latitude()).isEqualTo(YAOUNDE.latitude());
        assertThat(measurement.coordinates().longitude()).isEqualTo(YAOUNDE.longitude());
    }

    @Test
    @DisplayName("update() passes null trackingCode when the mission is not yet in the cache")
    void updateWithUncachedMissionPassesNullTrackingCode() {
        Instant now = Instant.now();
        EtaResult eta = new EtaResult(now.plusSeconds(600), now.plusSeconds(500),
                now.plusSeconds(700), 0.9, now, 5.0);
        when(updateEtaUseCase.updateEta(anyString(), any(), any(GPSMeasurement.class)))
                .thenReturn(Mono.just(eta));

        StepVerifier.create(adapter.update("d1", "M-unknown", "tenant-A", YAOUNDE, 20.0, 45.0, 10.0))
                .assertNext(update -> assertThat(update.trackingCode()).isNull())
                .verifyComplete();

        verify(updateEtaUseCase).updateEta(eq("M-unknown"), eq(null), any());
    }

    @Test
    @DisplayName("update() propagates the error when no Kalman state exists for the mission")
    void updatePropagatesErrorWhenNoStateExists() {
        when(updateEtaUseCase.updateEta(anyString(), any(), any(GPSMeasurement.class)))
                .thenReturn(Mono.error(new IllegalStateException("No Kalman state for mission: M1")));

        StepVerifier.create(adapter.update("d1", "M1", "tenant-A", YAOUNDE, 20.0, 45.0, 10.0))
                .expectError(IllegalStateException.class)
                .verify();
    }
}
