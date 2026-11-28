package com.yowyob.tiibntick.core.administration.application;

import com.yowyob.tiibntick.common.exception.TntConflictException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase.PutOrganizationCommand;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase.UpdatePlatformCommand;
import com.yowyob.tiibntick.core.administration.application.port.out.AgencyOrganizationPort;
import com.yowyob.tiibntick.core.administration.application.port.out.DeliveryThresholdsRepository;
import com.yowyob.tiibntick.core.administration.application.port.out.TntAdministrationEventPublisher;
import com.yowyob.tiibntick.core.administration.application.service.DeliveryThresholdsService;
import com.yowyob.tiibntick.core.administration.domain.event.DeliveryThresholdsChanged;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThresholdValues;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.OrganizationDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.PlatformDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.RelayStorageBounds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.ResolvedThreshold;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.ThresholdSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DeliveryThresholdsService — use cases, guards and DeliveryThresholdsChanged events")
class DeliveryThresholdsServiceTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    private DeliveryThresholdsRepository repository;
    private AgencyOrganizationPort agencies;
    private TntAdministrationEventPublisher events;
    private DeliveryThresholdsService service;
    private final PlatformDeliveryThresholds platform = PlatformDeliveryThresholds.defaults();

    @BeforeEach
    void setUp() {
        repository = mock(DeliveryThresholdsRepository.class);
        agencies = mock(AgencyOrganizationPort.class);
        events = mock(TntAdministrationEventPublisher.class);
        service = new DeliveryThresholdsService(repository, agencies, events);
        when(repository.findPlatform()).thenReturn(Mono.just(platform));
        when(repository.findOrganization(any())).thenReturn(Mono.empty());
        when(events.publish(any(UUID.class), anyString(), anyString(), any(UUID.class), any())).thenReturn(Mono.empty());
    }

    private static DeliveryThresholdValues defaultsWith(DeliveryThreshold t, int v) {
        Map<DeliveryThreshold, Integer> map = new EnumMap<>(DeliveryThresholdValues.defaults().asMap());
        map.put(t, v);
        return DeliveryThresholdValues.of(map);
    }

    // ── platform ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update platform: stored with version+1, then DeliveryThresholdsChanged(PLATFORM) with previous values")
    @SuppressWarnings("unchecked")
    void updatePlatform() {
        when(repository.updatePlatform(any(), eq(0L))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        DeliveryThresholdValues next = defaultsWith(MAX_DELIVERY_ATTEMPTS, 4);

        StepVerifier.create(service.updatePlatform(new UpdatePlatformCommand(next, new RelayStorageBounds(2, 14),
                        0L, ADMIN, TENANT)))
                .assertNext(saved -> {
                    assertThat(saved.get(MAX_DELIVERY_ATTEMPTS)).isEqualTo(4);
                    assertThat(saved.version()).isEqualTo(1L);
                    assertThat(saved.updatedBy()).isEqualTo(ADMIN);
                })
                .verifyComplete();

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(TENANT), eq("DeliveryThresholdsChanged"), eq("administration"),
                eq(DeliveryThresholdsChanged.PLATFORM_AGGREGATE_ID), payload.capture());
        assertThat(payload.getValue()).containsEntry("scope", "PLATFORM").containsEntry("change", "UPDATED")
                .containsEntry("version", 1L).containsEntry("independentRelayStorageMinDays", 2)
                .containsEntry("independentRelayStorageMaxDays", 14).containsEntry("organizationId", null);
        assertThat((Map<String, Object>) payload.getValue().get("thresholds")).containsEntry("maxDeliveryAttempts", 4);
        assertThat((Map<String, Object>) payload.getValue().get("previousThresholds"))
                .containsEntry("maxDeliveryAttempts", 3);
    }

    @Test
    @DisplayName("update platform: stale version → 409, no event")
    void updatePlatformStale() {
        when(repository.updatePlatform(any(), anyLong())).thenReturn(Mono.empty());
        StepVerifier.create(service.updatePlatform(new UpdatePlatformCommand(DeliveryThresholdValues.defaults(),
                        RelayStorageBounds.DEFAULT, 7L, ADMIN, TENANT)))
                .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(TntConflictException.class)
                        .hasMessageContaining("expected version 7"))
                .verify();
        verify(events, never()).publish(any(UUID.class), anyString(), anyString(), any(UUID.class), any());
    }

    @Test
    @DisplayName("update platform: invalid value → 422, nothing stored")
    void updatePlatformInvalid() {
        StepVerifier.create(service.updatePlatform(new UpdatePlatformCommand(defaultsWith(MAX_DELIVERY_ATTEMPTS, 0),
                        RelayStorageBounds.DEFAULT, 0L, ADMIN, TENANT)))
                .expectError(TntValidationException.class).verify();
        verify(repository, never()).updatePlatform(any(), anyLong());
    }

    @Test
    @DisplayName("platform row missing: the decided defaults are served")
    void platformDefaultsWhenMissing() {
        when(repository.findPlatform()).thenReturn(Mono.empty());
        StepVerifier.create(service.getPlatform())
                .assertNext(p -> assertThat(p.get(RELAY_STORAGE_DAYS)).isEqualTo(7)).verifyComplete();
    }

    // ── organizations ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("put override: agency, new → inserted (version 0) and event without previous values")
    @SuppressWarnings("unchecked")
    void putOverrideCreate() {
        when(agencies.isAgency(ORG)).thenReturn(Mono.just(true));
        when(repository.insertOrganization(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.putOrganizationOverride(new PutOrganizationCommand(ORG,
                        DeliveryThresholdValues.of(Map.of(MAX_DELIVERY_ATTEMPTS, 5)), null, ADMIN, TENANT)))
                .assertNext(o -> assertThat(o.version()).isZero())
                .verifyComplete();
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(TENANT), eq("DeliveryThresholdsChanged"), eq("administration"), eq(ORG),
                payload.capture());
        assertThat(payload.getValue()).containsEntry("scope", "ORGANIZATION")
                .containsEntry("organizationId", ORG.toString()).containsEntry("previousThresholds", null)
                .doesNotContainKey("independentRelayStorageMinDays");
        assertThat((Map<String, Object>) payload.getValue().get("thresholds"))
                .containsEntry("maxDeliveryAttempts", 5).containsEntry("relayStorageDays", null);
    }

    @Test
    @DisplayName("put override: existing, right version → updated (version+1)")
    void putOverrideUpdate() {
        when(agencies.isAgency(ORG)).thenReturn(Mono.just(true));
        OrganizationDeliveryThresholds existing = new OrganizationDeliveryThresholds(ORG,
                DeliveryThresholdValues.of(Map.of(MAX_DELIVERY_ATTEMPTS, 5)), ADMIN, Instant.now(), 2);
        when(repository.findOrganization(ORG)).thenReturn(Mono.just(existing));
        when(repository.updateOrganization(any(), eq(2L))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.putOrganizationOverride(new PutOrganizationCommand(ORG,
                        DeliveryThresholdValues.of(Map.of(MAX_DELIVERY_ATTEMPTS, 6)), 2L, ADMIN, TENANT)))
                .assertNext(o -> assertThat(o.version()).isEqualTo(3L))
                .verifyComplete();
    }

    @Test
    @DisplayName("put override: version rules — create with a version, update without / with a stale one → 409")
    void putOverrideVersionRules() {
        when(agencies.isAgency(ORG)).thenReturn(Mono.just(true));
        DeliveryThresholdValues v = DeliveryThresholdValues.of(Map.of(MAX_DELIVERY_ATTEMPTS, 6));
        StepVerifier.create(service.putOrganizationOverride(new PutOrganizationCommand(ORG, v, 0L, ADMIN, TENANT)))
                .expectError(TntConflictException.class).verify();

        when(repository.findOrganization(ORG)).thenReturn(Mono.just(new OrganizationDeliveryThresholds(ORG,
                DeliveryThresholdValues.of(Map.of(MAX_DELIVERY_ATTEMPTS, 5)), ADMIN, Instant.now(), 2)));
        StepVerifier.create(service.putOrganizationOverride(new PutOrganizationCommand(ORG, v, null, ADMIN, TENANT)))
                .expectError(TntConflictException.class).verify();
        StepVerifier.create(service.putOrganizationOverride(new PutOrganizationCommand(ORG, v, 1L, ADMIN, TENANT)))
                .expectError(TntConflictException.class).verify();
        // concurrent change between the read and the write
        when(repository.updateOrganization(any(), eq(2L))).thenReturn(Mono.empty());
        StepVerifier.create(service.putOrganizationOverride(new PutOrganizationCommand(ORG, v, 2L, ADMIN, TENANT)))
                .expectError(TntConflictException.class).verify();
        verify(events, never()).publish(any(UUID.class), anyString(), anyString(), any(UUID.class), any());
    }

    @Test
    @DisplayName("put override: not an agency → 422 ORGANIZATION_NOT_AGENCY, nothing stored")
    void putOverrideNotAgency() {
        when(agencies.isAgency(ORG)).thenReturn(Mono.just(false));
        StepVerifier.create(service.putOrganizationOverride(new PutOrganizationCommand(ORG,
                        DeliveryThresholdValues.of(Map.of(MAX_DELIVERY_ATTEMPTS, 5)), null, ADMIN, TENANT)))
                .expectErrorSatisfies(e -> assertThat(((TntValidationException) e).getErrorCode())
                        .isEqualTo("ORGANIZATION_NOT_AGENCY"))
                .verify();
        verify(repository, never()).insertOrganization(any());
    }

    @Test
    @DisplayName("put override: empty → 422; out of range → 422; nothing stored")
    void putOverrideInvalid() {
        when(agencies.isAgency(ORG)).thenReturn(Mono.just(true));
        StepVerifier.create(service.putOrganizationOverride(new PutOrganizationCommand(ORG,
                        DeliveryThresholdValues.empty(), null, ADMIN, TENANT)))
                .expectErrorSatisfies(e -> assertThat(((TntValidationException) e).getErrorCode())
                        .isEqualTo("DELIVERY_THRESHOLDS_EMPTY_OVERRIDE"))
                .verify();
        StepVerifier.create(service.putOrganizationOverride(new PutOrganizationCommand(ORG,
                        DeliveryThresholdValues.of(Map.of(MAX_DELIVERY_ATTEMPTS, 11)), null, ADMIN, TENANT)))
                .expectErrorSatisfies(e -> assertThat(((TntValidationException) e).getErrorCode())
                        .isEqualTo("DELIVERY_THRESHOLDS_INVALID"))
                .verify();
        verify(repository, never()).insertOrganization(any());
    }

    @Test
    @DisplayName("delete override: removed → event DELETED with previous values; none → false, no event")
    @SuppressWarnings("unchecked")
    void deleteOverride() {
        StepVerifier.create(service.deleteOrganizationOverride(ORG, ADMIN, TENANT)).expectNext(false).verifyComplete();
        verify(events, never()).publish(any(UUID.class), anyString(), anyString(), any(UUID.class), any());

        when(repository.findOrganization(ORG)).thenReturn(Mono.just(new OrganizationDeliveryThresholds(ORG,
                DeliveryThresholdValues.of(Map.of(MAX_DELIVERY_ATTEMPTS, 5)), ADMIN, Instant.now(), 1)));
        when(repository.deleteOrganization(ORG)).thenReturn(Mono.just(true));
        StepVerifier.create(service.deleteOrganizationOverride(ORG, ADMIN, TENANT)).expectNext(true).verifyComplete();
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(TENANT), anyString(), anyString(), eq(ORG), payload.capture());
        assertThat(payload.getValue()).containsEntry("change", "DELETED").containsEntry("thresholds", null);
        assertThat((Map<String, Object>) payload.getValue().get("previousThresholds"))
                .containsEntry("maxDeliveryAttempts", 5);
    }

    // ── resolution ───────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolve: agency override applied with source; non-agency ignored; null organization = platform")
    void resolve() {
        when(repository.findOrganization(ORG)).thenReturn(Mono.just(new OrganizationDeliveryThresholds(ORG,
                DeliveryThresholdValues.of(Map.of(MAX_DELIVERY_ATTEMPTS, 5)), ADMIN, Instant.now(), 1)));
        when(agencies.isAgency(ORG)).thenReturn(Mono.just(true));
        StepVerifier.create(service.resolve(ORG))
                .assertNext(e -> {
                    assertThat(e.get(MAX_DELIVERY_ATTEMPTS)).isEqualTo(new ResolvedThreshold(5, ThresholdSource.ORGANIZATION));
                    assertThat(e.get(MAX_FAILED_RETURNS)).isEqualTo(new ResolvedThreshold(3, ThresholdSource.PLATFORM));
                }).verifyComplete();

        when(agencies.isAgency(ORG)).thenReturn(Mono.just(false));
        StepVerifier.create(service.resolve(ORG))
                .assertNext(e -> assertThat(e.get(MAX_DELIVERY_ATTEMPTS).source()).isEqualTo(ThresholdSource.PLATFORM))
                .verifyComplete();

        StepVerifier.create(service.resolve(null))
                .assertNext(e -> assertThat(e.organizationId()).isNull()).verifyComplete();
        verify(agencies, never()).isAgency(null);
    }

    @Test
    @DisplayName("relay storage deadline: independent relay value within bounds, then min(client request, relay)")
    void relayDeadline() {
        when(repository.findPlatform()).thenReturn(Mono.just(new PlatformDeliveryThresholds(
                DeliveryThresholdValues.defaults(), new RelayStorageBounds(3, 10), null, Instant.now(), 1)));
        Instant deposit = Instant.parse("2026-11-26T08:00:00Z");
        StepVerifier.create(service.relayStorageDeadline(null, 20, deposit, null))
                .assertNext(d -> {
                    assertThat(d.relayStorageDays()).isEqualTo(10);
                    assertThat(d.expiresAt()).isEqualTo(deposit.plus(Duration.ofDays(10)));
                    assertThat(d.reminderAt()).isEqualTo(deposit.plus(Duration.ofDays(9)));
                }).verifyComplete();
        StepVerifier.create(service.relayStorageDeadline(null, 20, deposit, 4))
                .assertNext(d -> assertThat(d.effectiveStorageDays()).isEqualTo(4)).verifyComplete();
        StepVerifier.create(service.resolveRelayStorageDays(null, null))
                .expectNext(new ResolvedThreshold(7, ThresholdSource.PLATFORM)).verifyComplete();
    }

    @Test
    @DisplayName("independent relay storage check (QO-29): within bounds ok, outside → 422")
    void independentRelayCheck() {
        when(repository.findPlatform()).thenReturn(Mono.just(new PlatformDeliveryThresholds(
                DeliveryThresholdValues.defaults(), new RelayStorageBounds(3, 10), null, Instant.now(), 1)));
        StepVerifier.create(service.checkIndependentRelayStorageDays(3)).verifyComplete();
        StepVerifier.create(service.checkIndependentRelayStorageDays(10)).verifyComplete();
        StepVerifier.create(service.checkIndependentRelayStorageDays(2))
                .expectErrorSatisfies(e -> assertThat(((TntValidationException) e).getErrorCode())
                        .isEqualTo("RELAY_STORAGE_DAYS_OUT_OF_BOUNDS")).verify();
        StepVerifier.create(service.checkIndependentRelayStorageDays(11)).expectError(TntValidationException.class).verify();

        // D-130 defaults (3..30 days) when the platform row is missing: no 1..60 fallback
        when(repository.findPlatform()).thenReturn(Mono.empty());
        StepVerifier.create(service.checkIndependentRelayStorageDays(30)).verifyComplete();
        StepVerifier.create(service.checkIndependentRelayStorageDays(31)).expectError(TntValidationException.class).verify();
        StepVerifier.create(service.checkIndependentRelayStorageDays(2)).expectError(TntValidationException.class).verify();
    }
}
