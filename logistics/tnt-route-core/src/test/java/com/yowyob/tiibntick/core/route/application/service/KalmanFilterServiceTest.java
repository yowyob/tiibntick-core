package com.yowyob.tiibntick.core.route.application.service;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.route.application.port.out.IKalmanStateRepository;
import com.yowyob.tiibntick.core.route.application.port.out.IRouteEventPublisher;
import com.yowyob.tiibntick.core.route.domain.model.GPSMeasurement;
import com.yowyob.tiibntick.core.route.domain.model.KalmanState;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KalmanFilterServiceTest {

    @Mock private IKalmanStateRepository stateRepo;
    @Mock private IRouteEventPublisher eventPublisher;

    private KalmanFilterService service;

    @BeforeEach
    void setUp() {
        service = new KalmanFilterService(stateRepo, eventPublisher);
    }

    @Test
    void kalmanState_initialize_zeroDistanceAndSpeed() {
        KalmanState s = KalmanState.initialize("M1", 20.0);
        assertThat(s.distanceCoveredKm()).isEqualTo(0.0);
        assertThat(s.speedKmh()).isEqualTo(0.0);
        assertThat(s.remainingDistanceKm()).isEqualTo(20.0);
    }

    @Test
    void kalmanState_predictAndUpdate_convergesToMeasuredSpeed() {
        KalmanState s = KalmanState.initialize("M1", 100.0);  // Long distance
        RealMatrix Q = MatrixUtils.createRealDiagonalMatrix(new double[]{0.1, 1.0, 0.01});

        // Simulate 50 minutes of journey at ~50 km/h → ~42 km travelled
        for (int i = 0; i < 300; i++) {  // 300 iterations of 10s = 50 min
            s.predict(10.0, Q);
            double measuredDist = (i + 1) * 0.14;  // ~42 km total
            s.update(measuredDist, 50.0, 0.01, 4.0);
        }

        assertThat(s.speedKmh()).isBetween(40.0, 60.0);
        assertThat(s.distanceCoveredKm()).isGreaterThan(30.0);
    }

    @Test
    void computeInitialEta_setsKalmanStateAndReturnsEta() {
        when(stateRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.computeInitialEta("M1", 15.0, 40.0))
                .assertNext(eta -> {
                    assertThat(eta.expected()).isAfter(Instant.now());
                    assertThat(eta.lowerBound()).isBefore(eta.upperBound());
                    assertThat(eta.confidenceLevel()).isGreaterThan(0);
                })
                .verifyComplete();
    }

    @Test
    void updateEta_withGPSMeasurement_returnsUpdatedEta() {
        KalmanState initialState = KalmanState.initialize("M1", 10.0);
        when(stateRepo.findByMissionId("M1")).thenReturn(Mono.just(initialState));
        when(stateRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishEtaUpdated(any())).thenReturn(Mono.empty());

        GPSMeasurement gps = new GPSMeasurement(
                GeoPoint.of(3.85, 11.51), 45.0, 90.0, 10.0,
                Instant.now().plusSeconds(10));

        StepVerifier.create(service.updateEta("M1", gps))
                .assertNext(eta -> {
                    assertThat(eta.expected()).isNotNull();
                    assertThat(eta.confidenceLevel()).isEqualTo(0.80);
                })
                .verifyComplete();
    }
}
