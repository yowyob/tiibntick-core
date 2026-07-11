package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import java.time.LocalDateTime;

/**
 * Domain projection of a blockchain proof record from the Trust Event API.
 *
 * <p>Replaces a kernel-level import for cross-module independence.
 * Built by {@link com.yowyob.tiibntick.core.trust.adapter.out.rest.TrustEventRestClientAdapter}
 * from the rich Trust Event REST response payload.
 *
 * @author MANFOUO Braun
 */
public record BlockchainProof(
        String id,
        String entityType,
        String entityId,
        String eventType,
        String status,
        String txHash,
        String proofHash,
        String previousProofHash,
        String payload,
        LocalDateTime timestamp
) {

    /**
     * Extracts a named field from the JSON {@code payload}.
     * Handles simple string values encoded as {@code "key":"value"}.
     *
     * @param fieldName the JSON field name
     * @return the string value, or {@code null} if absent
     */
    public String getPayloadField(final String fieldName) {
        if (payload == null || fieldName == null) return null;
        final String search = "\"" + fieldName + "\":\"";
        final int start = payload.indexOf(search);
        if (start < 0) return null;
        final int valueStart = start + search.length();
        final int end = payload.indexOf("\"", valueStart);
        return end < 0 ? null : payload.substring(valueStart, end);
    }
}
