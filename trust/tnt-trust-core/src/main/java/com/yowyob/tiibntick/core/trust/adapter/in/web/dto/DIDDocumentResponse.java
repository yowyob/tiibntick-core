package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;

/**
 * Response DTO — DID document details.
 * Returned by {@code GET /tnt/trust/actors/{actorId}/did}.
 *
 * <p>The {@code publicKeyPem} field is included to allow clients to verify
 * cryptographic signatures from the actor (e.g., delivery proof signatures).
 *
 * @author MANFOUO Braun
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DIDDocumentResponse(
        String did,
        String actorId,
        String tenantId,
        String publicKeyPem,
        String serviceEndpoint,
        String issuedAt,
        String expiresAt,
        String blockchainTxHash,
        boolean revoked,
        boolean verifiable) {

    /** Converts a {@link DIDDocument} domain object to this DTO. */
    public static DIDDocumentResponse from(final DIDDocument doc) {
        return new DIDDocumentResponse(
                doc.getDid(),
                doc.getActorId(),
                doc.getTenantId(),
                doc.getPublicKeyPem(),
                doc.getServiceEndpoint(),
                doc.getIssuedAt() != null ? doc.getIssuedAt().toString() : null,
                doc.getExpiresAt() != null ? doc.getExpiresAt().toString() : null,
                doc.getBlockchainTxHash(),
                doc.isRevoked(),
                doc.isVerifiable());
    }
}
