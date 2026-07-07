package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import com.yowyob.tiibntick.core.actor.domain.model.KycStatus;
import jakarta.validation.constraints.NotNull;

public record KycValidateRequest(
        @NotNull KycStatus newKycStatus,
        String rejectionReason) {
}
