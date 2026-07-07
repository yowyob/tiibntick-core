package com.yowyob.tiibntick.core.actor.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Value object representing an actor's reputation rating in TiiBnTick.
 *
 * <p>Immutable — all mutation methods return new instances.
 *
 * <p> — Added {@link #decreaseScore(double, String)} method to support
 * {@code IActorReputationPort.decreaseReputation()} called by {@code tnt-incident-core}
 * when an incident is confirmed to be caused by the actor's fault.
 *
 * @author MANFOUO Braun
 */
public final class ActorRating {

    private static final double MIN_SCORE = 0.0;
    private static final double MAX_SCORE = 5.0;
    private static final ActorRating ZERO = new ActorRating(0.0, 0, null);

    private final double score;
    private final int totalRatings;
    private final Instant lastUpdatedAt;

    private ActorRating(double score, int totalRatings, Instant lastUpdatedAt) {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "Rating score must be between 0 and 5, got: " + score);
        }
        if (totalRatings < 0) {
            throw new IllegalArgumentException("totalRatings must not be negative");
        }
        this.score = score;
        this.totalRatings = totalRatings;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public static ActorRating zero() {
        return ZERO;
    }

    public static ActorRating of(double score, int totalRatings, Instant lastUpdatedAt) {
        return new ActorRating(score, totalRatings, lastUpdatedAt);
    }

    /**
     * Computes the new weighted average after adding a new rating score.
     *
     * @param newScore the rating score to add (must be between 0 and 5)
     * @return new {@link ActorRating} with updated average
     */
    public ActorRating addRating(double newScore) {
        if (newScore < MIN_SCORE || newScore > MAX_SCORE) {
            throw new IllegalArgumentException("Rating must be between 0 and 5, got: " + newScore);
        }
        int updatedTotal = totalRatings + 1;
        double updatedScore = ((score * totalRatings) + newScore) / updatedTotal;
        double roundedScore = Math.round(updatedScore * 100.0) / 100.0;
        return new ActorRating(roundedScore, updatedTotal, Instant.now());
    }

    /**
     * Decreases the score by the given penalty points (clamped to 0.0 minimum).
     *
     * <p>Called by {@code IActorReputationPort.decreaseReputation()} when
     * {@code tnt-incident-core} confirms that an incident was caused by the actor.
     * The deduction is applied directly to the current score (not a weighted average)
     * to provide a meaningful, immediate penalty.
     *
     * @param points  penalty points to deduct (must be positive)
     * @param reason  human-readable reason for the deduction (for audit trail)
     * @return new {@link ActorRating} with reduced score
     */
    public ActorRating decreaseScore(double points, String reason) {
        if (points <= 0) {
            throw new IllegalArgumentException("Penalty points must be positive, got: " + points);
        }
        double newScore = Math.max(MIN_SCORE, score - points);
        double roundedScore = Math.round(newScore * 100.0) / 100.0;
        return new ActorRating(roundedScore, totalRatings, Instant.now());
    }

    public double score() {
        return score;
    }

    public int totalRatings() {
        return totalRatings;
    }

    public Instant lastUpdatedAt() {
        return lastUpdatedAt;
    }

    public boolean hasRatings() {
        return totalRatings > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActorRating other)) return false;
        return Double.compare(score, other.score) == 0
                && totalRatings == other.totalRatings
                && Objects.equals(lastUpdatedAt, other.lastUpdatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(score, totalRatings, lastUpdatedAt);
    }

    @Override
    public String toString() {
        return "ActorRating{score=" + score + ", total=" + totalRatings + "}";
    }
}
