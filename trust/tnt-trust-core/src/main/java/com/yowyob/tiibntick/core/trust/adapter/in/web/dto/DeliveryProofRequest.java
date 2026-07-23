package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

// ── Delivery Proof Request ──────────────────────────────────────────────────

/**
 * Request DTO — Record a delivery proof.
 * Posted by platform modules to {@code POST /tnt/trust/delivery/proof}.
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryProofRequest(
        @JsonProperty("proofId") @NotBlank(message = "proofId is required") String proofId,
        @JsonProperty("missionId") @NotBlank(message = "missionId is required") String missionId,
        @JsonProperty("packageId") @NotBlank(message = "packageId is required") String packageId,
        @JsonProperty("actorId") @NotBlank(message = "actorId is required") String actorId,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("photoHash") @NotBlank(message = "photoHash is required") String photoHash,
        @JsonProperty("signatureHash") String signatureHash,
        @JsonProperty("gpsLat") double gpsLat,
        @JsonProperty("gpsLng") double gpsLng,
        @JsonProperty("confirmedAt") String confirmedAt) {

    /**
     * Converts this request to a {@link DeliveryProofRecord} domain value object.
     *
     * @param authenticatedTenantId the tenant ID resolved from the caller's JWT —
     *                              always wins over any {@code tenantId} present in the request body.
     */
    public DeliveryProofRecord toDomain(final String authenticatedTenantId) {
        return new DeliveryProofRecord(
                proofId, missionId, packageId, actorId, authenticatedTenantId,
                photoHash, signatureHash, gpsLat, gpsLng,
                confirmedAt != null
                        ? LocalDateTime.parse(confirmedAt)
                        : LocalDateTime.now());
    }
}
