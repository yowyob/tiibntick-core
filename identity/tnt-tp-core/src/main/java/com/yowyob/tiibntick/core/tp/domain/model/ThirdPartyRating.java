package com.yowyob.tiibntick.core.tp.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity: ThirdPartyRating.
 *
 * <p>Represents a single rating given to a third party after a delivery.
 * Ratings (1–5 stars) come from senders/recipients after mission completion.
 * The aggregate average is maintained in {@link TntClientProfile}.
 *
 * <p>The {@code ratedThirdPartyId} is the Kernel integration key referencing the
 * ThirdParty entity in RT-comops-tp-core.
 *
 * <p>Immutable. The {@link #reconstitute} factory is used by persistence adapters.
 *
 * @author MANFOUO Braun
 */
public final class ThirdPartyRating {

    private final UUID id;
    private final UUID tenantId;

    /**
     * The rated third party — Kernel integration key (RT-comops-tp-core).
     * Must not be null.
     */
    private final UUID ratedThirdPartyId;

    /** The rater (actor ID from tnt-actor-core or another ThirdParty UUID). */
    private final UUID raterActorId;

    /** Associated delivery mission reference for this rating. */
    private final String missionId;

    /** Score: 1.0 to 5.0 inclusive. */
    private final double score;

    /** Optional textual comment. Nullable. */
    private final String comment;

    private final Instant createdAt;

    private ThirdPartyRating(
            UUID id, UUID tenantId,
            UUID ratedThirdPartyId, UUID raterActorId,
            String missionId, double score, String comment,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.ratedThirdPartyId = Objects.requireNonNull(ratedThirdPartyId,
                "ratedThirdPartyId (Kernel integration key) is required");
        this.raterActorId = Objects.requireNonNull(raterActorId, "raterActorId is required");
        this.missionId = missionId;
        if (score < 1.0 || score > 5.0) {
            throw new IllegalArgumentException("Score must be between 1.0 and 5.0, got: " + score);
        }
        this.score = score;
        this.comment = comment;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    // ─── Factory methods ─────────────────────────────────────────────────────

    /**
     * Creates a new rating after a delivery mission.
     * Generates a new UUID and sets {@code createdAt} to now.
     *
     * @param tenantId           the owning tenant
     * @param ratedThirdPartyId  the Kernel ThirdParty reference UUID
     * @param raterActorId       the rater's actor UUID
     * @param missionId          the delivery mission reference
     * @param score              the rating score [1.0, 5.0]
     * @param comment            optional comment (nullable)
     * @return new {@link ThirdPartyRating}
     */
    public static ThirdPartyRating create(
            UUID tenantId, UUID ratedThirdPartyId, UUID raterActorId,
            String missionId, double score, String comment) {
        return new ThirdPartyRating(
                UUID.randomUUID(), tenantId,
                ratedThirdPartyId, raterActorId,
                missionId, score, comment, Instant.now());
    }

    /**
     * Reconstitutes a {@link ThirdPartyRating} from persistence data.
     *
     * <p>Preserves the original UUID and timestamp. Does not validate score
     * range beyond the constructor guard (data assumed consistent if already persisted).
     * Used exclusively by repository adapters.
     *
     * @param id                the persisted rating UUID
     * @param tenantId          the tenant UUID
     * @param ratedThirdPartyId the rated Kernel ThirdParty UUID
     * @param raterActorId      the rater's actor UUID
     * @param missionId         the delivery mission reference
     * @param score             the rating score [1.0, 5.0]
     * @param comment           optional comment (nullable)
     * @param createdAt         the persisted creation timestamp
     * @return the reconstituted {@link ThirdPartyRating}
     */
    public static ThirdPartyRating reconstitute(
            UUID id, UUID tenantId,
            UUID ratedThirdPartyId, UUID raterActorId,
            String missionId, double score, String comment,
            Instant createdAt) {
        return new ThirdPartyRating(id, tenantId, ratedThirdPartyId, raterActorId,
                missionId, score, comment, createdAt);
    }

    // ─── Accessors ───────────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRatedThirdPartyId() { return ratedThirdPartyId; }
    public UUID getRaterActorId() { return raterActorId; }
    public String getMissionId() { return missionId; }
    public double getScore() { return score; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ThirdPartyRating that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
