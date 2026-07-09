package com.yowyob.tiibntick.core.platformgateway.domain.model;

/**
 * Pairs the Kernel's real HTTP status code with its {@link KernelApiEnvelope} body —
 * {@link KernelApiEnvelope} alone doesn't carry the status, and platform backends need
 * the real code (401/403/409/...), not a flattened 200 with {@code success:false}.
 *
 * @author MANFOUO Braun
 */
public record KernelAuthResult(
        int httpStatus,
        KernelApiEnvelope envelope
) {
}
