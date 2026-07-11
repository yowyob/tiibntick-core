package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response DTO — Audit trail (Fil d'Ariane) entry for a delivery event.
 * Returned in the list by {@code GET /tnt/trust/delivery/{missionId}/trail}.
 *
 * @author MANFOUO Braun
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditTrailResponse(
        String eventId,
        String eventType,
        String eventDescription,
        String entityId,
        String entityType,
        String actorId,
        String tenantId,
        String occurredAt,
        String blockchainTxHash,
        boolean onChain,
        Double gpsLat,
        Double gpsLng) {

    /**
     * Builds an {@link AuditTrailResponse} from a delivery proof.
     */
    public static AuditTrailResponse fromDeliveryProof(
            final com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord proof) {
        return new AuditTrailResponse(
                proof.getProofId(),
                "DELIVERY_PROOF_RECORDED",
                "Delivery proof recorded (photo + GPS coordinates)",
                proof.getProofId(),
                "DELIVERY_PROOF",
                proof.getActorId(),
                proof.getTenantId(),
                proof.getConfirmedAt() != null ? proof.getConfirmedAt().toString() : null,
                proof.getBlockchainTxHash(),
                proof.getBlockchainTxHash() != null,
                proof.getGpsLat(),
                proof.getGpsLng());
    }

    /**
     * Builds an {@link AuditTrailResponse} from a custody transfer.
     */
    public static AuditTrailResponse fromCustodyTransfer(
            final com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord transfer) {
        return new AuditTrailResponse(
                transfer.getTransferId(),
                "PACKAGE_CUSTODY_TRANSFERRED",
                "Package custody transferred: " + transfer.getTransferType().name(),
                transfer.getTransferId(),
                "CUSTODY_TRANSFER",
                transfer.getFromActorId(),
                transfer.getTenantId(),
                transfer.getTransferredAt() != null ? transfer.getTransferredAt().toString() : null,
                transfer.getBlockchainTxHash(),
                transfer.getBlockchainTxHash() != null,
                null,
                null);
    }
}
