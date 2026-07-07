package com.yowyob.tiibntick.core.sync.application.service;

import com.yowyob.tiibntick.core.sync.domain.exception.SyncTokenExpiredException;
import com.yowyob.tiibntick.core.sync.domain.model.DeltaRecord;
import com.yowyob.tiibntick.core.sync.domain.model.SyncDelta;
import com.yowyob.tiibntick.core.sync.domain.model.SyncToken;
import com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation;
import com.yowyob.tiibntick.core.sync.domain.service.DeltaSyncDomainService;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeltaComputeApplicationServiceTest {

    @Mock
    private DeltaSyncDomainService deltaSyncService;

    private DeltaComputeApplicationService service;

    private static final String USER_ID = "user-001";
    private static final String TENANT_ID = "tenant-A";
    private static final String DEVICE_ID = "device-001";

    @BeforeEach
    void setUp() {
        service = new DeltaComputeApplicationService(deltaSyncService, new SimpleMeterRegistry());
    }

    private SyncDelta buildDeltaWithRecords(int recordCount) {
        SyncToken token = SyncToken.next(USER_ID, TENANT_ID, DEVICE_ID, LocalDateTime.now());
        List<DeltaRecord> records = java.util.stream.IntStream.range(0, recordCount)
                .mapToObj(i -> new DeltaRecord("MISSION", "M-00" + i, DeltaOperation.UPDATED,
                        "{}", LocalDateTime.now(), 1000L + i))
                .toList();
        return new SyncDelta(TENANT_ID, USER_ID, token, records, Collections.emptyList(), token, LocalDateTime.now());
    }

    @Test
    @DisplayName("computeDelta() with null token triggers bootstrap (initial token)")
    void computeDeltaWithNullTokenTriggers() {
        when(deltaSyncService.computeBootstrapDelta(any(), any(), any(), isNull()))
                .thenReturn(Mono.just(buildDeltaWithRecords(5)));

        StepVerifier.create(service.computeDelta(USER_ID, TENANT_ID, DEVICE_ID, null, null))
                .assertNext(response -> {
                    assertThat(response.recordCount()).isEqualTo(5);
                    assertThat(response.newSyncToken()).isNotBlank();
                    assertThat(response.generatedAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("computeDelta() with valid token fetches incremental delta")
    void computeDeltaWithValidToken() {
        when(deltaSyncService.computeDelta(any(), any(), isNull()))
                .thenReturn(Mono.just(buildDeltaWithRecords(3)));

        String token = "some-base64-token";
        StepVerifier.create(service.computeDelta(USER_ID, TENANT_ID, DEVICE_ID, token, null))
                .assertNext(response -> assertThat(response.recordCount()).isEqualTo(3))
                .verifyComplete();
    }

    @Test
    @DisplayName("computeDelta() with empty result sets nextSyncRecommendedInSeconds=300")
    void emptyDeltaRecommendsSlowerSync() {
        when(deltaSyncService.computeBootstrapDelta(any(), any(), any(), isNull()))
                .thenReturn(Mono.just(buildDeltaWithRecords(0)));

        StepVerifier.create(service.computeDelta(USER_ID, TENANT_ID, DEVICE_ID, null, null))
                .assertNext(response -> {
                    assertThat(response.recordCount()).isEqualTo(0);
                    assertThat(response.nextSyncRecommendedInSeconds()).isEqualTo(300);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("computeDelta() with non-empty result sets nextSyncRecommendedInSeconds=60")
    void nonEmptyDeltaRecommendsFasterSync() {
        when(deltaSyncService.computeBootstrapDelta(any(), any(), any(), isNull()))
                .thenReturn(Mono.just(buildDeltaWithRecords(10)));

        StepVerifier.create(service.computeDelta(USER_ID, TENANT_ID, DEVICE_ID, null, null))
                .assertNext(response -> assertThat(response.nextSyncRecommendedInSeconds()).isEqualTo(60))
                .verifyComplete();
    }

    @Test
    @DisplayName("computeDelta() propagates SyncTokenExpiredException from domain")
    void computeDeltaPropagatesTokExpired() {
        when(deltaSyncService.computeDelta(any(), any(), isNull()))
                .thenReturn(Mono.error(new SyncTokenExpiredException("old-token")));

        StepVerifier.create(service.computeDelta(USER_ID, TENANT_ID, DEVICE_ID, "old-token", null))
                .expectError(SyncTokenExpiredException.class)
                .verify();
    }

    @Test
    @DisplayName("computeDelta() passes filter aggregates to domain service")
    void computeDeltaPassesFilter() {
        Set<String> filter = Set.of("MISSION", "PACKAGE");

        when(deltaSyncService.computeDelta(any(), any(), any()))
                .thenReturn(Mono.just(buildDeltaWithRecords(2)));

        StepVerifier.create(service.computeDelta(USER_ID, TENANT_ID, DEVICE_ID, "some-token", filter))
                .assertNext(response -> assertThat(response.recordCount()).isEqualTo(2))
                .verifyComplete();
    }
}
