package com.yowyob.tiibntick.core.sync.domain.service;

import com.yowyob.tiibntick.core.sync.application.port.out.IEntityVersionRepository;
import com.yowyob.tiibntick.core.sync.domain.exception.SyncTokenExpiredException;
import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import com.yowyob.tiibntick.core.sync.domain.model.SyncDelta;
import com.yowyob.tiibntick.core.sync.domain.model.SyncToken;
import com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeltaSyncDomainServiceTest {

    @Mock
    private IEntityVersionRepository entityVersionRepository;

    private DeltaSyncDomainService service;

    private static final String TENANT_ID = "tenant-A";
    private static final String USER_ID = "user-001";
    private static final String DEVICE_ID = "device-001";

    @BeforeEach
    void setUp() {
        service = new DeltaSyncDomainService(entityVersionRepository, 500);
    }

    private EntityVersionRecord buildRecord(String aggregateType, String aggregateId) {
        return new EntityVersionRecord(
                TENANT_ID, aggregateType, aggregateId,
                System.currentTimeMillis(), DeltaOperation.UPDATED,
                "{\"id\":\"" + aggregateId + "\"}", LocalDateTime.now(), "system");
    }

    @Test
    @DisplayName("computeDelta() returns records changed since the token")
    void computeDeltaReturnsChangedRecords() {
        SyncToken token = SyncToken.next(USER_ID, TENANT_ID, DEVICE_ID, LocalDateTime.now().minusHours(1));

        when(entityVersionRepository.findChangedSince(anyString(), any(), isNull(), anyInt()))
                .thenReturn(Flux.just(
                        buildRecord("MISSION", "M-001"),
                        buildRecord("PACKAGE", "PKG-042"),
                        buildRecord("MISSION", "M-002")
                ));

        StepVerifier.create(service.computeDelta(token, TENANT_ID, null))
                .assertNext(delta -> {
                    assertThat(delta.tenantId()).isEqualTo(TENANT_ID);
                    assertThat(delta.recordCount()).isEqualTo(3);
                    assertThat(delta.isEmpty()).isFalse();
                    assertThat(delta.hasConflicts()).isFalse();
                    assertThat(delta.newToken()).isNotNull();
                    assertThat(delta.newToken().isInitial()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("computeDelta() returns empty delta when no changes")
    void computeDeltaReturnsEmptyWhenNoChanges() {
        SyncToken token = SyncToken.next(USER_ID, TENANT_ID, DEVICE_ID, LocalDateTime.now().minusMinutes(10));

        when(entityVersionRepository.findChangedSince(anyString(), any(), isNull(), anyInt()))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.computeDelta(token, TENANT_ID, null))
                .assertNext(delta -> {
                    assertThat(delta.isEmpty()).isTrue();
                    assertThat(delta.recordCount()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("computeDelta() rejects expired token")
    void computeDeltaRejectsExpiredToken() {
        // Token from 8 days ago — exceeds 7-day max age
        SyncToken expiredToken = new SyncToken(
                "expired-token", USER_ID, TENANT_ID, DEVICE_ID,
                LocalDateTime.now().minusDays(8));

        StepVerifier.create(service.computeDelta(expiredToken, TENANT_ID, null))
                .expectError(SyncTokenExpiredException.class)
                .verify();
    }

    @Test
    @DisplayName("computeBootstrapDelta() uses initial token (epoch 1970)")
    void computeBootstrapDeltaUsesInitialToken() {
        when(entityVersionRepository.findChangedSince(anyString(), any(), isNull(), anyInt()))
                .thenReturn(Flux.just(buildRecord("MISSION", "M-001")));

        StepVerifier.create(service.computeBootstrapDelta(TENANT_ID, USER_ID, DEVICE_ID, null))
                .assertNext(delta -> {
                    assertThat(delta.sinceToken().isInitial()).isTrue();
                    assertThat(delta.recordCount()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("countPendingChanges() returns count from repository")
    void countPendingChangesReturnsFromRepo() {
        SyncToken token = SyncToken.next(USER_ID, TENANT_ID, DEVICE_ID, LocalDateTime.now().minusHours(2));
        when(entityVersionRepository.countChangedSince(anyString(), any())).thenReturn(Mono.just(42L));

        StepVerifier.create(service.countPendingChanges(token, TENANT_ID))
                .expectNext(42L)
                .verifyComplete();
    }

    @Test
    @DisplayName("SyncDelta.isEmpty() and hasConflicts() behave correctly")
    void syncDeltaPredicates() {
        SyncToken token = SyncToken.next(USER_ID, TENANT_ID, DEVICE_ID, LocalDateTime.now());
        SyncDelta empty = SyncDelta.empty(TENANT_ID, USER_ID, token, token);

        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.hasConflicts()).isFalse();
        assertThat(empty.recordCount()).isEqualTo(0);
        assertThat(empty.conflictCount()).isEqualTo(0);
    }
}
