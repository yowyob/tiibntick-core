package com.yowyob.tiibntick.core.agency.intake.adapter.in.web.dto;

import com.yowyob.tiibntick.core.agency.intake.domain.ClientIntakeRequest;

import java.time.Instant;
import java.util.UUID;

public record IntakeResponse(
        UUID id, String referenceCode, String status, String source, Instant createdAt) {

    public static IntakeResponse from(ClientIntakeRequest r) {
        return new IntakeResponse(
                r.getId(), r.getReferenceCode(), r.getStatus().name(),
                r.getSource().name(), r.getCreatedAt());
    }
}
