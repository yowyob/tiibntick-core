package com.yowyob.tiibntick.core.dispute.application.query;

import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeCategory;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputePriority;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus;

import java.time.LocalDateTime;

/**
 * Query to retrieve a paginated, filtered list of disputes.
 * All filter fields are optional — only non-null fields are applied.
 *
 * @author MANFOUO Braun
 */
public record ListDisputesQuery(
        String tenantId,
        String requesterId,
        DisputeStatus statusFilter,
        DisputePriority priorityFilter,
        DisputeCategory categoryFilter,
        String claimantIdFilter,
        String respondentIdFilter,
        String missionIdFilter,
        LocalDateTime from,
        LocalDateTime to,
        int page,
        int size
) {
    public ListDisputesQuery {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 20;
    }
}
