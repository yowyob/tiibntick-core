package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto;

import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientStatus;
import com.yowyob.tiibntick.core.platformgateway.domain.model.Environment;
import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClient;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for platform-client admin endpoints — never carries any key material.
 *
 * @author MANFOUO Braun
 */
public record PlatformClientResponse(
        UUID id,
        String clientId,
        String name,
        String platformCode,
        Environment environment,
        ClientStatus status,
        String description,
        String contactEmail,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {
    public static PlatformClientResponse from(PlatformClient client) {
        return new PlatformClientResponse(
                client.id(), client.clientId(), client.name(), client.platformCode(), client.environment(),
                client.status(), client.description(), client.contactEmail(),
                client.createdAt(), client.updatedAt(), client.createdBy(), client.updatedBy());
    }
}
