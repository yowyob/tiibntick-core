package com.yowyob.tiibntick.core.dispute.domain.event;

import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeCause;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputePriority;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain event emitted when a new dispute is opened.
 * Consumed by tnt-delivery-core to set package status to DISPUTED,
 * and tnt-notify-core to alert both parties.
 *
 * @author MANFOUO Braun
 */
public record DisputeOpened(
        DisputeId disputeId,
        String tenantId,
        String reference,
        DisputeCause cause,
        DisputePriority priority,
        String claimantId,
        String missionId,
        String packageId,
        String trackingCode,
        LocalDateTime occurredAt
) {
    public DisputeOpened {
        Objects.requireNonNull(disputeId, "disputeId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(cause, "cause must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(claimantId, "claimantId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
