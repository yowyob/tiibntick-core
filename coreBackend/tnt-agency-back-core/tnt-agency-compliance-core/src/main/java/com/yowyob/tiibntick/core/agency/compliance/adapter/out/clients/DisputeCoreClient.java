package com.yowyob.tiibntick.core.agency.compliance.adapter.out.clients;

import com.yowyob.tiibntick.core.agency.compliance.application.port.out.DisputeCorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Adapter to platform tnt-dispute-core. Ported from the BFF (now lives in the ERP).
 */
@Component
public class DisputeCoreClient implements DisputeCorePort {

    private static final Logger log = LoggerFactory.getLogger(DisputeCoreClient.class);

    private final WebClient webClient;

    public DisputeCoreClient(@Qualifier("agencyPlatformWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<DisputeView> openDispute(OpenDisputeRequest request) {
        var body = new CoreOpenDisputeRequest(
                request.description(),
                mapCategory(request.claimType()),
                "NORMAL",
                request.contactEmail(),
                "CLIENT",
                request.agencyId().toString(),
                "AGENCY",
                request.missionId() != null ? request.missionId().toString() : null,
                null,
                null,
                request.description(),
                request.agencyId().toString(),
                null,
                false
        );
        return webClient.post()
                .uri("/api/v1/disputes")
                .header("X-Tenant-ID", request.tenantId().toString())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(CoreDisputeOpenedResponse.class)
                .map(r -> new DisputeView(r.disputeId(), r.reference(), r.status()))
                .doOnError(e -> log.warn("[DisputeCore] open failed mission={}: {}",
                        request.missionId(), e.getMessage()));
    }

    @Override
    public Mono<DisputePage> listDisputes(ListDisputesRequest request) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/api/v1/disputes")
                            .queryParam("respondentId", request.agencyId().toString())
                            .queryParam("page", request.page())
                            .queryParam("size", request.size());
                    if (request.status() != null && !request.status().isBlank()) {
                        builder.queryParam("status", request.status());
                    }
                    return builder.build();
                })
                .header("X-Tenant-ID", request.tenantId().toString())
                .retrieve()
                .bodyToMono(CoreDisputePageResponse.class)
                .map(page -> new DisputePage(
                        page.content().stream()
                                .map(d -> new DisputeSummary(
                                        d.id(),
                                        d.reference(),
                                        d.status(),
                                        d.category(),
                                        d.priority(),
                                        d.missionId(),
                                        d.trackingCode(),
                                        d.cause(),
                                        d.filedAt()))
                                .toList(),
                        page.page(),
                        page.size(),
                        page.totalElements()))
                .doOnError(e -> log.warn("[DisputeCore] list failed agency={}: {}",
                        request.agencyId(), e.getMessage()));
    }

    @Override
    public Mono<DisputeDetail> getDispute(UUID tenantId, String disputeId) {
        return webClient.get()
                .uri("/api/v1/disputes/{id}", disputeId)
                .header("X-Tenant-ID", tenantId.toString())
                .retrieve()
                .bodyToMono(CoreDisputeDetailResponse.class)
                .map(d -> new DisputeDetail(
                        d.id(),
                        d.reference(),
                        d.status(),
                        d.category(),
                        d.priority(),
                        d.missionId(),
                        d.trackingCode(),
                        d.description(),
                        d.claimantId(),
                        d.assignedMediatorId(),
                        d.filedAt(),
                        d.deadline(),
                        d.evidences() != null ? d.evidences().size() : 0))
                .doOnError(e -> log.warn("[DisputeCore] get failed id={}: {}", disputeId, e.getMessage()));
    }

    private static String mapCategory(String claimType) {
        if (claimType == null) {
            return "DELIVERY_ISSUE";
        }
        return switch (claimType.toUpperCase()) {
            case "DAMAGE", "DAMAGED" -> "PARCEL_DAMAGE";
            case "LOSS", "LOST" -> "PARCEL_LOSS";
            case "DELAY" -> "SLA_BREACH";
            case "BILLING" -> "BILLING_DISPUTE";
            default -> "DELIVERY_ISSUE";
        };
    }

    private record CoreOpenDisputeRequest(
            String cause,
            String category,
            String priority,
            String claimantId,
            String claimantType,
            String respondentId,
            String respondentType,
            String missionId,
            String packageId,
            String trackingCode,
            String description,
            String respondentOrgId,
            String impliedSubDelivererId,
            Boolean subDelivererInvolved
    ) {}

    private record CoreDisputeOpenedResponse(String disputeId, String reference, String status) {}

    private record CoreDisputeSummary(
            String id,
            String reference,
            String status,
            String cause,
            String category,
            String priority,
            String missionId,
            String trackingCode,
            LocalDateTime filedAt) {}

    private record CoreDisputePageResponse(
            List<CoreDisputeSummary> content,
            int page,
            int size,
            long totalElements,
            boolean hasNextPage,
            boolean hasPreviousPage) {}

    private record CoreDisputeDetailResponse(
            String id,
            String reference,
            String status,
            String category,
            String priority,
            String claimantId,
            String missionId,
            String trackingCode,
            String description,
            LocalDateTime filedAt,
            LocalDateTime deadline,
            String assignedMediatorId,
            List<Object> evidences) {}
}
