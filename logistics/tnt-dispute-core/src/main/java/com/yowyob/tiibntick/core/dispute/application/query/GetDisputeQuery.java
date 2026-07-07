package com.yowyob.tiibntick.core.dispute.application.query;

import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.util.Objects;

/**
 * Query to retrieve a single dispute by its identifier.
 *
 * @author MANFOUO Braun
 */
public record GetDisputeQuery(
        DisputeId disputeId,
        String tenantId,
        String requesterId
) {
    public GetDisputeQuery {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(requesterId, "requesterId is required");
    }
}
