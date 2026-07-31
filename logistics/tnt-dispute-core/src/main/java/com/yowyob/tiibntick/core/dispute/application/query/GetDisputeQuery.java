package com.yowyob.tiibntick.core.dispute.application.query;

import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.util.Objects;

/**
 * Query to retrieve a single dispute by its identifier.
 *
 * <p>{@code requesterId} may be {@code null} — e.g. a service-to-service platform-client
 * call with no individually-linked actor profile ({@code TntUserIdentity.actorId() ==
 * null}). A non-privileged, actor-less requester is simply visible to nothing (see
 * {@code DisputeQueryService#isVisible}) rather than rejected outright here.
 *
 * @author MANFOUO Braun
 */
public record GetDisputeQuery(
        DisputeId disputeId,
        String tenantId,
        String requesterId,
        boolean privileged
) {
    public GetDisputeQuery {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
    }
}
