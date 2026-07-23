package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * Request DTO — Record a custody transfer.
 * Posted to {@code POST /tnt/trust/custody/transfer}.
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustodyTransferRequest(
        @NotBlank(message = "transferId is required") String transferId,
        @NotBlank(message = "packageId is required") String packageId,
        @NotBlank(message = "trackingCode is required") String trackingCode,
        String tenantId,
        String fromActorId,
        @NotBlank(message = "toActorId is required") String toActorId,
        @NotBlank(message = "transferType is required") String transferType,
        String hubId,
        String transferredAt) {

    /**
     * Converts this request to a {@link CustodyTransferRecord} domain value object.
     *
     * @param authenticatedTenantId the tenant ID resolved from the caller's JWT —
     *                              always wins over any {@code tenantId} present in the request body.
     */
    public CustodyTransferRecord toDomain(final String authenticatedTenantId) {
        return new CustodyTransferRecord(
                transferId, packageId, trackingCode, authenticatedTenantId,
                fromActorId, toActorId,
                CustodyTransferType.valueOf(transferType),
                hubId,
                transferredAt != null
                        ? LocalDateTime.parse(transferredAt)
                        : LocalDateTime.now());
    }
}
