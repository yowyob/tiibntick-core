package com.yowyob.tiibntick.core.dispute.domain.model;

import com.yowyob.tiibntick.core.dispute.domain.enums.EvidenceSubmitterType;
import com.yowyob.tiibntick.core.dispute.domain.enums.EvidenceType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing a piece of evidence submitted to support or contest a dispute.
 *
 * <p>Evidence can be physical (photo, video, document), digital (GPS trace, QR scan log),
 * or blockchain-backed (delivery proof, blockchain hash from tnt-trust).
 *
 * <p>Evidence is part of the {@link Dispute} aggregate but maintains its own identity
 * via {@link EvidenceId}. It is verified by the mediator before being used in the ruling.
 *
 * @author MANFOUO Braun
 */
public final class DisputeEvidence {

    private final EvidenceId id;
    private final DisputeId disputeId;
    private final String submittedBy;
    private final EvidenceSubmitterType submitterType;
    private final EvidenceType type;
    private final String fileKey;
    private final String description;
    private final LocalDateTime submittedAt;
    private boolean isVerified;
    private LocalDateTime verifiedAt;
    private String verifiedByMediatorId;
    private String blockchainRef;
    private final String evidenceHash;

    private DisputeEvidence(
            final EvidenceId id,
            final DisputeId disputeId,
            final String submittedBy,
            final EvidenceSubmitterType submitterType,
            final EvidenceType type,
            final String fileKey,
            final String description,
            final LocalDateTime submittedAt,
            final boolean isVerified,
            final LocalDateTime verifiedAt,
            final String verifiedByMediatorId,
            final String blockchainRef,
            final String evidenceHash) {
        this.id = Objects.requireNonNull(id, "EvidenceId must not be null");
        this.disputeId = Objects.requireNonNull(disputeId, "DisputeId must not be null");
        this.submittedBy = Objects.requireNonNull(submittedBy, "submittedBy must not be null");
        this.submitterType = Objects.requireNonNull(submitterType, "submitterType must not be null");
        this.type = Objects.requireNonNull(type, "EvidenceType must not be null");
        this.fileKey = fileKey;
        this.description = description;
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        this.isVerified = isVerified;
        this.verifiedAt = verifiedAt;
        this.verifiedByMediatorId = verifiedByMediatorId;
        this.blockchainRef = blockchainRef;
        this.evidenceHash = evidenceHash;
    }

    /**
     * Creates a new, unverified piece of evidence.
     *
     * @param disputeId     the dispute this evidence belongs to
     * @param submittedBy   the actor ID of the submitter
     * @param submitterType role of the submitter
     * @param type          nature of the evidence
     * @param fileKey       MinIO object key for the file (nullable for non-file evidence)
     * @param description   human-readable description of the evidence
     * @param evidenceHash  SHA-256 hash of the evidence content, client-supplied (nullable —
     *                      typically computed by whatever service drove the MinIO upload,
     *                      e.g. tnt-media-core), enabling real cryptographic verification
     *                      of the anchored proof later via {@code IBlockchainProofPort.verifyProof}
     * @return a new {@code DisputeEvidence}
     */
    public static DisputeEvidence create(
            final DisputeId disputeId,
            final String submittedBy,
            final EvidenceSubmitterType submitterType,
            final EvidenceType type,
            final String fileKey,
            final String description,
            final String evidenceHash) {
        return new DisputeEvidence(
                EvidenceId.generate(), disputeId, submittedBy, submitterType,
                type, fileKey, description, LocalDateTime.now(), false, null, null, null, evidenceHash);
    }

    /**
     * Reconstructs a {@code DisputeEvidence} from persistence.
     */
    public static DisputeEvidence reconstitute(
            final EvidenceId id,
            final DisputeId disputeId,
            final String submittedBy,
            final EvidenceSubmitterType submitterType,
            final EvidenceType type,
            final String fileKey,
            final String description,
            final LocalDateTime submittedAt,
            final boolean isVerified,
            final LocalDateTime verifiedAt,
            final String verifiedByMediatorId,
            final String blockchainRef,
            final String evidenceHash) {
        return new DisputeEvidence(id, disputeId, submittedBy, submitterType, type, fileKey,
                description, submittedAt, isVerified, verifiedAt, verifiedByMediatorId, blockchainRef,
                evidenceHash);
    }

    /**
     * Marks this evidence as verified by a mediator.
     *
     * @param mediatorId the ID of the mediator who verified the evidence
     */
    public void verify(final String mediatorId) {
        Objects.requireNonNull(mediatorId, "mediatorId must not be null");
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
        this.verifiedByMediatorId = mediatorId;
    }

    /**
     * Associates a blockchain transaction hash with this evidence,
     * confirming its integrity via tnt-trust.
     *
     * @param blockchainRef the transaction hash or DID reference
     */
    public void attachBlockchainRef(final String blockchainRef) {
        this.blockchainRef = Objects.requireNonNull(blockchainRef, "blockchainRef must not be null");
    }

    /** Returns {@code true} if this evidence has an associated file stored in MinIO. */
    public boolean hasFile() {
        return fileKey != null && !fileKey.isBlank();
    }

    /** Returns {@code true} if this evidence is backed by a blockchain proof. */
    public boolean hasBlockchainProof() {
        return blockchainRef != null && !blockchainRef.isBlank();
    }

    public EvidenceId getId() { return id; }
    public DisputeId getDisputeId() { return disputeId; }
    public String getSubmittedBy() { return submittedBy; }
    public EvidenceSubmitterType getSubmitterType() { return submitterType; }
    public EvidenceType getType() { return type; }
    public String getFileKey() { return fileKey; }
    public String getDescription() { return description; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public boolean isVerified() { return isVerified; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public String getVerifiedByMediatorId() { return verifiedByMediatorId; }
    public String getBlockchainRef() { return blockchainRef; }
    public String getEvidenceHash() { return evidenceHash; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DisputeEvidence that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DisputeEvidence{id=%s, type=%s, verified=%b}".formatted(id, type, isVerified);
    }
}
