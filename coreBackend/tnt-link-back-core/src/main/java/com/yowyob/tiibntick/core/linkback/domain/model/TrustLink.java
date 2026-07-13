package com.yowyob.tiibntick.core.linkback.domain.model;

import com.yowyob.tiibntick.core.linkback.domain.exception.NetworkNodeDomainException;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * A directed peer endorsement between two {@link NetworkNode}s — genuinely
 * new Link domain, no equivalent in L2-L5.
 *
 * @author Dilane PAFE
 */
@Getter
@Builder
public class TrustLink {

    private final UUID id;
    private final UUID tenantId;
    private final UUID fromNodeId;
    private final UUID toNodeId;
    private final Instant createdAt;

    public static TrustLink endorse(UUID tenantId, UUID fromNodeId, UUID toNodeId) {
        if (fromNodeId == null || toNodeId == null) {
            throw new NetworkNodeDomainException("An endorsement requires both a fromNodeId and toNodeId");
        }
        if (fromNodeId.equals(toNodeId)) {
            throw new NetworkNodeDomainException("A network node cannot endorse itself: " + fromNodeId);
        }
        return TrustLink.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .fromNodeId(fromNodeId)
                .toNodeId(toNodeId)
                .createdAt(Instant.now())
                .build();
    }
}
