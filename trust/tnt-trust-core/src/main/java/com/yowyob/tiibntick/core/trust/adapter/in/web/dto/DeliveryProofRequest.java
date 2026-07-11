package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;

import java.time.LocalDateTime;
import java.util.List;

// ── Delivery Proof Request ──────────────────────────────────────────────────

/**
 * Request DTO — Record a delivery proof.
 * Posted by platform modules to {@code POST /tnt/trust/delivery/proof}.
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryProofRequest(
        @JsonProperty("proofId") String proofId,
        @JsonProperty("missionId") String missionId,
        @JsonProperty("packageId") String packageId,
        @JsonProperty("actorId") String actorId,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("photoHash") String photoHash,
        @JsonProperty("signatureHash") String signatureHash,
        @JsonProperty("gpsLat") double gpsLat,
        @JsonProperty("gpsLng") double gpsLng,
        @JsonProperty("confirmedAt") String confirmedAt) {

    /**
     * Converts this request to a {@link DeliveryProofRecord} domain value object.
     */
    public DeliveryProofRecord toDomain() {
        return new DeliveryProofRecord(
                proofId, missionId, packageId, actorId, tenantId,
                photoHash, signatureHash, gpsLat, gpsLng,
                confirmedAt != null
                        ? LocalDateTime.parse(confirmedAt)
                        : LocalDateTime.now());
    }
}
