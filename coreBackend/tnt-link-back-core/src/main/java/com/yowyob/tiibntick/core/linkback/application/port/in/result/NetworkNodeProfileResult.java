package com.yowyob.tiibntick.core.linkback.application.port.in.result;

import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;

import java.util.List;
import java.util.UUID;

/**
 * Composed read-model for a network node's full profile — assembles the
 * node's own state with data resolved from tnt-actor-core (identity, rating),
 * tnt-delivery-core (delivery/flow counts) and this module's own DaoZone/
 * TrustLink aggregates. Nothing here is stored redundantly; it's recomputed
 * on every read.
 */
public record NetworkNodeProfileResult(
        NetworkNode node,
        String displayName,
        String phoneNumber,
        String email,
        Double rating,
        Integer reviewCount,
        Long deliveryCount,
        Long activeFlows,
        List<UUID> containingZoneIds,
        long endorsementCount
) {
}
