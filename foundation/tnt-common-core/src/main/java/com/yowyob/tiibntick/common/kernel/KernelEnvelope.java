package com.yowyob.tiibntick.common.kernel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Wire-format mirror of the Kernel's universal {@code ApiResponse} envelope.
 *
 * <p>Every Kernel HTTP endpoint wraps its payload as
 * {@code {success, data, message, errorCode, timestamp}} — verified live against
 * {@code kernel-core.yowyob.com} (2026-07-08) and consistent with every
 * {@code ApiResponseXxx} schema in {@code docs/kernel-api/schemas.md}. A Kernel adapter
 * that deserializes its response body directly into a flat DTO, skipping this envelope,
 * silently gets an empty/default-valued object instead of the real payload — see
 * {@code docs/architecture/decisions.md} ADR-012 and {@code docs/knowledge/known-issues.md} #12.
 *
 * @param <T> shape of the {@code data} payload for a given endpoint
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelEnvelope<T>(
        boolean success,
        T data,
        String message,
        String errorCode,
        String timestamp
) {}
