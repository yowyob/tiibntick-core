package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto;

import com.yowyob.tiibntick.core.platformgateway.domain.model.IssuedApiKey;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body when a key is issued or rotated — the ONLY response that ever carries
 * the plaintext secret; never returned again after this ("show-once").
 *
 * @author MANFOUO Braun
 */
public record IssuedApiKeyResponse(
        UUID keyId,
        String prefix,
        String plaintextSecret,
        Instant expiresAt
) {
    public static IssuedApiKeyResponse from(IssuedApiKey issued) {
        return new IssuedApiKeyResponse(issued.keyId(), issued.prefix(), issued.plaintextSecret(), issued.expiresAt());
    }
}
