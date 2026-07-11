package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;

import java.time.LocalDateTime;

/**
 * Request DTO — Record a custody transfer.
 * Posted to {@code POST /tnt/trust/custody/transfer}.
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustodyTransferRequest(
        String transferId,
        String packageId,
        String trackingCode,
        String tenantId,
        String fromActorId,
        String toActorId,
        String transferType,
        String hubId,
        String transferredAt) {

    /**
     * Converts this request to a {@link CustodyTransferRecord} domain value object.
     */
    public CustodyTransferRecord toDomain() {
        return new CustodyTransferRecord(
                transferId, packageId, trackingCode, tenantId,
                fromActorId, toActorId,
                CustodyTransferType.valueOf(transferType),
                hubId,
                transferredAt != null
                        ? LocalDateTime.parse(transferredAt)
                        : LocalDateTime.now());
    }
}
