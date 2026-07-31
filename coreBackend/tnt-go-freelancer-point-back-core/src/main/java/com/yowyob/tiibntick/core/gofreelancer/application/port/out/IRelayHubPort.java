package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — identity / occupancy of a relay hub from {@code tnt-geo-core}.
 *
 * <p>{@code GofpRelayPoint.coreRelayPointId} MUST map to {@link RelayHub#id()}.</p>
 *
 * @author MANFOUO BRAUN
 */
public interface IRelayHubPort {

    Mono<RelayHub> findHub(UUID hubId, UUID tenantId);

    /**
     * Increments hub occupancy by one after a successful deposit.
     * Fails if the hub is missing, closed, or full.
     */
    Mono<RelayHub> incrementOccupancy(UUID hubId, UUID tenantId);

    /**
     * Decrements hub occupancy by one after a successful retrieval.
     *
     * <p>Symmetric with {@link #incrementOccupancy}: fails if the hub is missing.
     * No-ops (returns the hub unchanged) only if occupancy is already 0. Callers that
     * consider a missing hub non-fatal (e.g. hub not yet provisioned during migration)
     * must catch that explicitly — this port never hides the failure itself.
     */
    Mono<RelayHub> decrementOccupancy(UUID hubId, UUID tenantId);

    /**
     * Provisions a brand-new relay hub in {@code tnt-geo-core} at the given coordinates,
     * with no agency branch (freelance/independent hub — see {@code RelayHub.branchId}).
     *
     * <p>Also creates the underlying {@code RoadNode} the hub sits on, since hub creation
     * requires an existing road-network node. Callers must persist the returned hub's
     * {@link RelayHub#id()} as {@code GofpRelayPoint.coreRelayPointId}.
     *
     * @param coordinates  the relay point's physical location; required — a hub cannot be
     *                     provisioned without a real GPS position (no fabricated coordinates)
     * @param name         relay point name, used as the road node's display name
     * @param cityCode     city code for the road node
     * @param capacitySlots parcel storage capacity to provision
     */
    Mono<RelayHub> provisionHub(UUID tenantId, GeoPoint coordinates, String name, String cityCode,
                                int capacitySlots, String operatorActorId);
}
