package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/actors/me/identity}. Every field is optional; omitted fields are
 * left unchanged on the Kernel actor.
 */
public record UpdateIdentityRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 30) String phoneNumber) {
}
