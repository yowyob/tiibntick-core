package com.yowyob.tiibntick.core.delivery.adapter.out.thresholds;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yowyob.tiibntick.core.administration.application.port.in.ResolveDeliveryThresholdsUseCase;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.EffectiveDeliveryThresholds;
import com.yowyob.tiibntick.core.delivery.application.port.out.AgencyOrganizationPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.RelayOwnershipPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.RelayOwnershipPort.RelayOwnership;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the delivery thresholds from the administration module ({@link ResolveDeliveryThresholdsUseCase}, lot L1),
 * converting the agency registry id into the Kernel organisation id ({@link AgencyOrganizationPort}) and the relay hub
 * into its owner ({@link RelayOwnershipPort}). Values are cached 60 s; {@code DeliveryThresholdsChanged} clears the
 * cache (spec §5.1).
 */
@Slf4j
@Component
public class AdministrationDeliveryThresholdsAdapter implements DeliveryThresholdsPort {

    static final Duration TTL = Duration.ofSeconds(60);

    private final ResolveDeliveryThresholdsUseCase thresholds;
    private final ObjectProvider<AgencyOrganizationPort> agencyOrganizations;
    private final ObjectProvider<RelayOwnershipPort> relayOwnerships;
    /** Key: Kernel organisation id, empty = platform. */
    private final Cache<Optional<UUID>, EffectiveDeliveryThresholds> cache =
            Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(10_000).build();

    public AdministrationDeliveryThresholdsAdapter(ResolveDeliveryThresholdsUseCase thresholds,
                                                   ObjectProvider<AgencyOrganizationPort> agencyOrganizations,
                                                   ObjectProvider<RelayOwnershipPort> relayOwnerships) {
        this.thresholds = thresholds;
        this.agencyOrganizations = agencyOrganizations;
        this.relayOwnerships = relayOwnerships;
    }

    @Override
    public Mono<Thresholds> forAgency(UUID agencyId) {
        return kernelOrganization(agencyId)
                .flatMap(this::effective)
                .map(e -> new Thresholds(e.maxDeliveryAttempts(), e.maxFailedReturns(),
                        e.relayStorageReminderDaysBefore(), alerts(e)));
    }

    @Override
    public Mono<StorageDeadline> relayStorageDeadline(UUID hubId, Instant depositedAt, Integer requestedStorageDays) {
        return ownership(hubId)
                .flatMap(owner -> thresholds.relayStorageDeadline(owner.ownerKernelOrganizationId(),
                        owner.independentStorageDays(), depositedAt, requestedStorageDays))
                .map(d -> new StorageDeadline(d.relayStorageDays(), d.effectiveStorageDays(), d.expiresAt(),
                        d.reminderAt()));
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
        log.debug("Delivery thresholds cache cleared");
    }

    private Mono<EffectiveDeliveryThresholds> effective(Optional<UUID> organizationId) {
        EffectiveDeliveryThresholds cached = cache.getIfPresent(organizationId);
        if (cached != null) {
            return Mono.just(cached);
        }
        return thresholds.resolve(organizationId.orElse(null))
                .doOnNext(value -> cache.put(organizationId, value));
    }

    private Mono<Optional<UUID>> kernelOrganization(UUID agencyId) {
        AgencyOrganizationPort port = agencyOrganizations.getIfAvailable();
        if (agencyId == null || port == null) {
            return Mono.just(Optional.empty());
        }
        return port.kernelOrganizationId(agencyId).map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    /** Alert thresholds (lot L3, QO-18): the same platform values and organisation overrides as the others. */
    static Alerts alerts(EffectiveDeliveryThresholds e) {
        return new Alerts(
                Duration.ofMinutes(e.value(DeliveryThreshold.PICKUP_LATE_GRACE_MINUTES)),
                Duration.ofMinutes(e.value(DeliveryThreshold.DELIVERY_LATE_GRACE_MINUTES)),
                Duration.ofHours(e.value(DeliveryThreshold.DELIVERY_LATE_FALLBACK_HOURS_STANDARD)),
                Duration.ofHours(e.value(DeliveryThreshold.DELIVERY_LATE_FALLBACK_HOURS_EXPRESS)),
                Duration.ofHours(e.value(DeliveryThreshold.DELIVERY_LATE_FALLBACK_HOURS_SAME_DAY)),
                Duration.ofMinutes(e.value(DeliveryThreshold.ETA_EXCEEDED_GRACE_MINUTES)),
                Duration.ofMinutes(e.value(DeliveryThreshold.GPS_SIGNAL_LOST_MINUTES)),
                Duration.ofMinutes(e.value(DeliveryThreshold.PROLONGED_STOP_MINUTES)));
    }

    /** First implementation that knows the hub, else an independent relay without own value (platform). */
    private Mono<RelayOwnership> ownership(UUID hubId) {
        List<RelayOwnershipPort> ports = relayOwnerships.orderedStream().toList();
        if (hubId == null || ports.isEmpty()) {
            return Mono.just(new RelayOwnership(null, null));
        }
        return Flux.fromIterable(ports)
                .concatMap(p -> p.findByHubId(hubId).onErrorResume(e -> {
                    log.warn("Relay ownership lookup failed for hub {}: {}", hubId, e.getMessage());
                    return Mono.empty();
                }))
                .next()
                .defaultIfEmpty(new RelayOwnership(null, null));
    }
}
