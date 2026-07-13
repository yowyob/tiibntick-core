package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.util.List;
import java.util.UUID;

/**
 * The rich, composed node profile — everything {@link NetworkNodeResponse}
 * has, plus identity/rating/delivery/zone/endorsement data resolved from
 * other modules at query time. Exposed at {@code GET /nodes/{id}/profile},
 * separate from the lightweight {@code GET /nodes/{id}} used for map
 * rendering, so map queries don't pay for this composition cost.
 */
public record NetworkNodeProfileResponse(
        NetworkNodeResponse node,
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
