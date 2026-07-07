package com.yowyob.tiibntick.core.incident.domain.model;

import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import com.yowyob.tiibntick.core.incident.domain.enums.EvidenceType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Digital evidence (photo, video, GPS trace, OTP proof) attached to an incident and anchored on-chain.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IncidentEvidence {

    private UUID id;
    private UUID incidentId;
    private EvidenceType evidenceType;
    private String fileUrl;
    private String mimeType;
    private Instant capturedAt;
    private UUID capturedByActorId;
    private ActorRole capturedByRole;
    private boolean validated;
    private Instant validatedAt;
    private UUID validatedByActorId;
    private String blockchainTxHash;
    private String sha256Checksum;
    private Double latitude;
    private Double longitude;

    /**
     * Creates a new unvalidated evidence record.
     *
     * @param incidentId   the related incident
     * @param type         evidence type
     * @param fileUrl      URL of the stored file (MinIO)
     * @param mimeType     MIME type of the file
     * @param capturedBy   actor who captured the evidence
     * @param role         role of the capturing actor
     * @param sha256       SHA-256 checksum of the file for integrity check
     * @param lat          capture latitude (nullable)
     * @param lng          capture longitude (nullable)
     * @return a new unvalidated evidence record
     */
    public static IncidentEvidence attach(UUID incidentId, EvidenceType type,
                                          String fileUrl, String mimeType,
                                          UUID capturedBy, ActorRole role,
                                          String sha256, Double lat, Double lng) {
        return IncidentEvidence.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .evidenceType(type)
                .fileUrl(fileUrl)
                .mimeType(mimeType)
                .capturedAt(Instant.now())
                .capturedByActorId(capturedBy)
                .capturedByRole(role)
                .sha256Checksum(sha256)
                .latitude(lat)
                .longitude(lng)
                .validated(false)
                .build();
    }

    /**
     * Marks the evidence as validated and anchors it on the blockchain.
     *
     * @param validatorId      the actor who validated the evidence
     * @param blockchainTxHash the blockchain transaction hash proving validation
     * @return validated evidence record
     */
    public IncidentEvidence validate(UUID validatorId, String blockchainTxHash) {
        return toBuilder()
                .validated(true)
                .validatedAt(Instant.now())
                .validatedByActorId(validatorId)
                .blockchainTxHash(blockchainTxHash)
                .build();
    }

    public IncidentEvidenceBuilder toBuilder() {
        return IncidentEvidence.builder().id(id).incidentId(incidentId)
                .evidenceType(evidenceType).fileUrl(fileUrl).mimeType(mimeType)
                .capturedAt(capturedAt).capturedByActorId(capturedByActorId)
                .capturedByRole(capturedByRole).validated(validated).validatedAt(validatedAt)
                .validatedByActorId(validatedByActorId).blockchainTxHash(blockchainTxHash)
                .sha256Checksum(sha256Checksum).latitude(latitude).longitude(longitude);
    }
}
