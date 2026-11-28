package com.yowyob.tiibntick.core.administration.application.port.in;

import com.yowyob.tiibntick.core.administration.domain.model.thresholds.EffectiveDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.RelayStorageDeadline;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.ResolvedThreshold;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Effective delivery thresholds (spec §5.1) — read by delivery-core (lot L2: attempts, relay storage, returns) and by
 * the late-delivery watch (lot L3: QO-18 alerts). administration-core is L2, delivery-core L3: the dependency points
 * down, no cycle.
 *
 * <p>The organization is identified by its Kernel organization id (the agency's unique id). A delivery made by an
 * agency must be resolved with the Kernel organization of that agency.
 */
public interface ResolveDeliveryThresholdsUseCase {

    /**
     * Thresholds of an organization: its override when it is a TiiBnTick Agency, else the platform values; each
     * threshold carries its source.
     *
     * @param organizationId Kernel organization id, or {@code null} for the platform values
     */
    Mono<EffectiveDeliveryThresholds> resolve(UUID organizationId);

    /**
     * Storage duration at a destination relay (QO-29): agency relay → the agency's override, else the platform
     * value; independent relay with its own duration → that duration within the admin's bounds; else platform.
     *
     * @param relayOwnerOrganizationId    Kernel organization owning the relay, or {@code null}
     * @param independentRelayStorageDays duration an independent relay set for itself, or {@code null}
     */
    Mono<ResolvedThreshold> resolveRelayStorageDays(UUID relayOwnerOrganizationId, Integer independentRelayStorageDays);

    /**
     * End of storage and reminder of a parcel deposited at its destination relay (QO-30: shortest of the client's
     * request and the relay's duration).
     *
     * @param relayOwnerOrganizationId    Kernel organization owning the relay, or {@code null}
     * @param independentRelayStorageDays duration an independent relay set for itself, or {@code null}
     * @param depositedAt                 deposit time
     * @param requestedStorageDays        the client's request ({@code requestedStorageDays}), or {@code null}
     */
    Mono<RelayStorageDeadline> relayStorageDeadline(UUID relayOwnerOrganizationId, Integer independentRelayStorageDays,
                                                    Instant depositedAt, Integer requestedStorageDays);
}
