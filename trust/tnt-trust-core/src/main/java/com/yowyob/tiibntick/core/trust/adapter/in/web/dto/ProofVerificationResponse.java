package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO — On-chain proof verification result.
 * Returned by {@code GET /tnt/trust/verify}.
 *
 * @author MANFOUO Braun
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProofVerificationResponse(
        String txHash,
        String expectedHash,
        boolean valid,
        String verifiedAt,
        String message) {

    /** Creates a valid verification response. */
    public static ProofVerificationResponse valid(final String txHash, final String expectedHash) {
        return new ProofVerificationResponse(txHash, expectedHash, true,
                java.time.LocalDateTime.now().toString(),
                "Proof is valid and confirmed on the Hyperledger Fabric ledger.");
    }

    /** Creates an invalid verification response. */
    public static ProofVerificationResponse invalid(final String txHash, final String expectedHash) {
        return new ProofVerificationResponse(txHash, expectedHash, false,
                java.time.LocalDateTime.now().toString(),
                "Proof could not be verified on-chain. Hash mismatch or record not found.");
    }
}
