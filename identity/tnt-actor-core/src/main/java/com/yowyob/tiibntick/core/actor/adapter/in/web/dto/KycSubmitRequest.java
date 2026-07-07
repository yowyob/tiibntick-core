package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record KycSubmitRequest(
        @NotBlank String documentUrl,
        @NotBlank String documentType) {
}
