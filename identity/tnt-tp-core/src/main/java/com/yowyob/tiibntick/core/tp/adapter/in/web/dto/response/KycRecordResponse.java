package com.yowyob.tiibntick.core.tp.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.tp.domain.model.enums.DocumentType;
import com.yowyob.tiibntick.core.tp.domain.model.enums.KycStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for KycRecord.
 *
 * @author MANFOUO Braun
 */
public record KycRecordResponse(
        UUID id,
        UUID thirdPartyId,
        DocumentType documentType,
        String documentNumber,
        LocalDate documentExpiryDate,
        KycStatus status,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt
) {}
