package com.yowyob.tiibntick.core.agency.compliance.application.port.out;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Client dispute lifecycle via platform tnt-dispute-core.
 */
public interface DisputeCorePort {

    Mono<DisputeView> openDispute(OpenDisputeRequest request);

    Mono<DisputePage> listDisputes(ListDisputesRequest request);

    Mono<DisputeDetail> getDispute(UUID tenantId, String disputeId);

    record OpenDisputeRequest(
            UUID tenantId,
            UUID agencyId,
            UUID missionId,
            String claimType,
            String description,
            String contactEmail
    ) {}

    record ListDisputesRequest(
            UUID tenantId,
            UUID agencyId,
            String status,
            int page,
            int size
    ) {}

    record DisputeView(String id, String reference, String status) {}

    record DisputeSummary(
            String id,
            String reference,
            String status,
            String category,
            String priority,
            String missionId,
            String trackingCode,
            String description,
            LocalDateTime filedAt
    ) {}

    record DisputePage(List<DisputeSummary> items, int page, int size, long total) {}

    record DisputeDetail(
            String id,
            String reference,
            String status,
            String category,
            String priority,
            String missionId,
            String trackingCode,
            String description,
            String claimantId,
            String assignedMediatorId,
            LocalDateTime filedAt,
            LocalDateTime deadline,
            int evidenceCount
    ) {}
}
