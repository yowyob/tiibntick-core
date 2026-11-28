package com.yowyob.tiibntick.core.administration.application.service;

import com.yowyob.tiibntick.common.exception.TntConflictException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.common.exception.TntValidationException.FieldViolation;
import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase;
import com.yowyob.tiibntick.core.administration.application.port.in.ResolveDeliveryThresholdsUseCase;
import com.yowyob.tiibntick.core.administration.application.port.out.AgencyOrganizationPort;
import com.yowyob.tiibntick.core.administration.application.port.out.DeliveryThresholdsRepository;
import com.yowyob.tiibntick.core.administration.application.port.out.TntAdministrationEventPublisher;
import com.yowyob.tiibntick.core.administration.domain.event.DeliveryThresholdsChanged;
import com.yowyob.tiibntick.core.administration.domain.event.DeliveryThresholdsChanged.Change;
import com.yowyob.tiibntick.core.administration.domain.event.DeliveryThresholdsChanged.Scope;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.EffectiveDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.OrganizationDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.PlatformDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.RelayStorageDeadline;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.ResolvedThreshold;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Delivery thresholds: platform values of the TiiBnTick administrator, overrides of TiiBnTick Agency organizations,
 * resolution of the effective values (spec §5.1, decisions [B.5], QO-8, QO-18, QO-29, QO-30).
 *
 * <p>Each write and its {@code DeliveryThresholdsChanged} event run in one transaction: the event is enqueued
 * through the outbox ({@link TntAdministrationEventPublisher}) only after the row is stored, and a failure of either
 * rolls both back.
 */
@Service
public class DeliveryThresholdsService implements ResolveDeliveryThresholdsUseCase, ManageDeliveryThresholdsUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeliveryThresholdsService.class);

    static final String VERSION_CONFLICT = "DELIVERY_THRESHOLDS_VERSION_CONFLICT";
    static final String NOT_AGENCY = "ORGANIZATION_NOT_AGENCY";
    static final String EMPTY_OVERRIDE = "DELIVERY_THRESHOLDS_EMPTY_OVERRIDE";
    static final String RELAY_STORAGE_OUT_OF_BOUNDS = "RELAY_STORAGE_DAYS_OUT_OF_BOUNDS";

    private final DeliveryThresholdsRepository repository;
    private final AgencyOrganizationPort agencies;
    private final TntAdministrationEventPublisher events;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DeliveryThresholdsService(DeliveryThresholdsRepository repository, AgencyOrganizationPort agencies,
                                     TntAdministrationEventPublisher events) {
        this(repository, agencies, events, Clock.systemUTC());
    }

    DeliveryThresholdsService(DeliveryThresholdsRepository repository, AgencyOrganizationPort agencies,
                              TntAdministrationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.agencies = agencies;
        this.events = events;
        this.clock = clock;
    }

    // ── resolution ────────────────────────────────────────────────────────────────────────────

    @Override
    public Mono<EffectiveDeliveryThresholds> resolve(UUID organizationId) {
        if (organizationId == null) {
            return platform().map(p -> EffectiveDeliveryThresholds.of(p, null, false, null));
        }
        return Mono.zip(platform(), agencies.isAgency(organizationId).defaultIfEmpty(false))
                .flatMap(t -> {
                    PlatformDeliveryThresholds p = t.getT1();
                    boolean agency = t.getT2();
                    if (!agency) {
                        return Mono.just(EffectiveDeliveryThresholds.of(p, organizationId, false, null));
                    }
                    return repository.findOrganization(organizationId)
                            .map(o -> EffectiveDeliveryThresholds.of(p, organizationId, true, o))
                            .defaultIfEmpty(EffectiveDeliveryThresholds.of(p, organizationId, true, null));
                });
    }

    @Override
    public Mono<ResolvedThreshold> resolveRelayStorageDays(UUID relayOwnerOrganizationId,
                                                           Integer independentRelayStorageDays) {
        return resolve(relayOwnerOrganizationId).map(e -> e.relayStorageDays(independentRelayStorageDays));
    }

    @Override
    public Mono<RelayStorageDeadline> relayStorageDeadline(UUID relayOwnerOrganizationId,
                                                           Integer independentRelayStorageDays,
                                                           Instant depositedAt, Integer requestedStorageDays) {
        return resolve(relayOwnerOrganizationId).map(e -> RelayStorageDeadline.compute(depositedAt,
                e.relayStorageDays(independentRelayStorageDays).value(), requestedStorageDays,
                e.relayStorageReminderDaysBefore()));
    }

    // ── platform ──────────────────────────────────────────────────────────────────────────────

    @Override
    public Mono<PlatformDeliveryThresholds> getPlatform() {
        return platform();
    }

    @Override
    @Transactional
    public Mono<PlatformDeliveryThresholds> updatePlatform(UpdatePlatformCommand command) {
        return Mono.fromCallable(() -> new PlatformDeliveryThresholds(command.values(), command.relayBounds(),
                        command.actorUserId(), clock.instant(), command.expectedVersion() + 1).validate())
                .flatMap(next -> platform().flatMap(previous -> repository.updatePlatform(next, command.expectedVersion())
                .switchIfEmpty(Mono.error(() -> new TntConflictException(VERSION_CONFLICT,
                        "The platform delivery thresholds were changed by someone else (expected version "
                                + command.expectedVersion() + ", current " + previous.version() + ")")))
                .flatMap(saved -> publish(command.tenantId(), new DeliveryThresholdsChanged(Scope.PLATFORM,
                        Change.UPDATED, null, saved.version(), saved.updatedBy(), saved.updatedAt(), saved.values(),
                        previous.values(), saved.independentRelayStorage())).thenReturn(saved))));
    }

    @Override
    public Mono<Void> checkIndependentRelayStorageDays(int storageDays) {
        return platform().flatMap(p -> {
            var bounds = p.independentRelayStorage();
            if (bounds.contains(storageDays)) {
                return Mono.empty();
            }
            return Mono.error(new TntValidationException(RELAY_STORAGE_OUT_OF_BOUNDS,
                    "Relay storage must be between " + bounds.minDays() + " and " + bounds.maxDays()
                            + " days", List.of(new FieldViolation("storageDays",
                    "must be between " + bounds.minDays() + " and " + bounds.maxDays(), storageDays))));
        });
    }

    // ── organizations ─────────────────────────────────────────────────────────────────────────

    @Override
    public Flux<OrganizationDeliveryThresholds> listOrganizationOverrides() {
        return repository.findAllOrganizations();
    }

    @Override
    public Mono<OrganizationDeliveryThresholds> getOrganizationOverride(UUID organizationId) {
        return repository.findOrganization(organizationId);
    }

    @Override
    @Transactional
    public Mono<OrganizationDeliveryThresholds> putOrganizationOverride(PutOrganizationCommand command) {
        UUID organizationId = command.organizationId();
        if (command.values() == null || command.values().isEmpty()) {
            return Mono.error(new TntValidationException(EMPTY_OVERRIDE,
                    "An override needs at least one value; DELETE removes the override", List.of()));
        }
        return agencies.isAgency(organizationId).defaultIfEmpty(false)
                .flatMap(agency -> agency ? platform() : Mono.error(new TntValidationException(NOT_AGENCY,
                        "Only a TiiBnTick Agency organization can override the delivery thresholds: "
                                + organizationId, List.of())))
                .flatMap(platform -> repository.findOrganization(organizationId)
                        .map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                        .flatMap(existing -> write(command, platform, existing.orElse(null))));
    }

    private Mono<OrganizationDeliveryThresholds> write(PutOrganizationCommand command,
                                                       PlatformDeliveryThresholds platform,
                                                       OrganizationDeliveryThresholds existing) {
        long currentVersion = existing == null ? -1 : existing.version();
        Long expected = command.expectedVersion();
        if ((existing == null && expected != null) || (existing != null && (expected == null || expected != currentVersion))) {
            return Mono.error(new TntConflictException(VERSION_CONFLICT, "The delivery thresholds of organization "
                    + command.organizationId() + " were changed by someone else (expected version " + expected
                    + ", current " + (existing == null ? "none" : currentVersion) + ")"));
        }
        long nextVersion = existing == null ? 0 : currentVersion + 1;
        OrganizationDeliveryThresholds next = new OrganizationDeliveryThresholds(command.organizationId(),
                command.values(), command.actorUserId(), clock.instant(), nextVersion).validateAgainst(platform);
        Mono<OrganizationDeliveryThresholds> stored = existing == null
                ? repository.insertOrganization(next)
                : repository.updateOrganization(next, currentVersion);
        return stored
                .switchIfEmpty(Mono.error(() -> new TntConflictException(VERSION_CONFLICT,
                        "The delivery thresholds of organization " + command.organizationId()
                                + " were changed concurrently")))
                .flatMap(saved -> publish(command.tenantId(), new DeliveryThresholdsChanged(Scope.ORGANIZATION,
                        Change.UPDATED, saved.organizationId(), saved.version(), saved.updatedBy(), saved.updatedAt(),
                        saved.values(), existing == null ? null : existing.values(), null)).thenReturn(saved));
    }

    @Override
    @Transactional
    public Mono<Boolean> deleteOrganizationOverride(UUID organizationId, UUID actorUserId, UUID tenantId) {
        return repository.findOrganization(organizationId)
                .flatMap(existing -> repository.deleteOrganization(organizationId)
                        .filter(Boolean::booleanValue)
                        .flatMap(deleted -> publish(tenantId, new DeliveryThresholdsChanged(Scope.ORGANIZATION,
                                Change.DELETED, organizationId, existing.version(), actorUserId, clock.instant(),
                                null, existing.values(), null)).thenReturn(true)))
                .defaultIfEmpty(false);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    private Mono<PlatformDeliveryThresholds> platform() {
        return repository.findPlatform().switchIfEmpty(Mono.fromSupplier(() -> {
            log.warn("Platform delivery thresholds row missing (changeset evt-l1-001 not applied?): "
                    + "using the decided defaults");
            return PlatformDeliveryThresholds.defaults();
        }));
    }

    private Mono<Void> publish(UUID tenantId, DeliveryThresholdsChanged event) {
        UUID tenant = tenantId != null ? tenantId : SystemTenant.id();
        return events.publish(tenant, DeliveryThresholdsChanged.EVENT_TYPE, DeliveryThresholdsChanged.MODULE,
                event.aggregateId(), event.toPayload());
    }
}
