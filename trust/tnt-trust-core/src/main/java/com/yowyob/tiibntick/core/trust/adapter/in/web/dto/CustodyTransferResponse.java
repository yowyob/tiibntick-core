package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;

/**
 * Response DTO — Custody transfer details.
 *
 * @author MANFOUO Braun
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustodyTransferResponse(
        String transferId,
        String packageId,
        String trackingCode,
        String tenantId,
        String fromActorId,
        String toActorId,
        String transferType,
        String hubId,
        String transferredAt,
        String blockchainTxHash,
        boolean onChain) {

    /** Converts a {@link CustodyTransferRecord} domain object to this DTO. */
    public static CustodyTransferResponse from(final CustodyTransferRecord transfer) {
        return new CustodyTransferResponse(
                transfer.getTransferId(),
                transfer.getPackageId(),
                transfer.getTrackingCode(),
                transfer.getTenantId(),
                transfer.getFromActorId(),
                transfer.getToActorId(),
                transfer.getTransferType().name(),
                transfer.getHubId(),
                transfer.getTransferredAt() != null ? transfer.getTransferredAt().toString() : null,
                transfer.getBlockchainTxHash(),
                transfer.getBlockchainTxHash() != null);
    }
}
