package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;

/**
 * Response DTO — Delivery proof details.
 *
 * @author MANFOUO Braun
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryProofResponse(
        String proofId,
        String missionId,
        String packageId,
        String actorId,
        String tenantId,
        String photoHash,
        String signatureHash,
        double gpsLat,
        double gpsLng,
        String confirmedAt,
        String blockchainTxHash,
        boolean verifiable) {

    /** Converts a {@link DeliveryProofRecord} domain object to this DTO. */
    public static DeliveryProofResponse from(final DeliveryProofRecord proof) {
        return new DeliveryProofResponse(
                proof.getProofId(),
                proof.getMissionId(),
                proof.getPackageId(),
                proof.getActorId(),
                proof.getTenantId(),
                proof.getPhotoHash(),
                proof.getSignatureHash(),
                proof.getGpsLat(),
                proof.getGpsLng(),
                proof.getConfirmedAt() != null ? proof.getConfirmedAt().toString() : null,
                proof.getBlockchainTxHash(),
                proof.getBlockchainTxHash() != null);
    }
}
