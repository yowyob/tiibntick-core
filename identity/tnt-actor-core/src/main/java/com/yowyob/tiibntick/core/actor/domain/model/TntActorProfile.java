package com.yowyob.tiibntick.core.actor.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public abstract sealed class TntActorProfile
        permits DelivererProfile, FreelancerProfile, RelayOperatorProfile, ClientProfile {

    private final UUID id;
    private final UUID tenantId;
    private final UUID actorId;
    private final ActorType actorType;
    private final ActorStatus actorStatus;
    private final KycStatus kycStatus;
    private final ActorLocation currentLocation;
    private final ActorRating rating;
    private final Set<Badge> badges;
    private final Instant createdAt;
    private final Instant updatedAt;

    protected TntActorProfile(
            UUID id,
            UUID tenantId,
            UUID actorId,
            ActorType actorType,
            ActorStatus actorStatus,
            KycStatus kycStatus,
            ActorLocation currentLocation,
            ActorRating rating,
            Set<Badge> badges,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.actorId = Objects.requireNonNull(actorId, "actorId must not be null");
        this.actorType = Objects.requireNonNull(actorType, "actorType must not be null");
        this.actorStatus = actorStatus != null ? actorStatus : ActorStatus.INACTIVE;
        this.kycStatus = kycStatus != null ? kycStatus : KycStatus.PENDING;
        this.currentLocation = currentLocation;
        this.rating = rating != null ? rating : ActorRating.zero();
        this.badges = badges != null ? Set.copyOf(badges) : Set.of();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID actorId() {
        return actorId;
    }

    public ActorType actorType() {
        return actorType;
    }

    public ActorStatus actorStatus() {
        return actorStatus;
    }

    public KycStatus kycStatus() {
        return kycStatus;
    }

    public ActorLocation currentLocation() {
        return currentLocation;
    }

    public ActorRating rating() {
        return rating;
    }

    public Set<Badge> badges() {
        return badges;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return actorStatus == ActorStatus.ACTIVE;
    }

    public boolean isKycVerified() {
        return kycStatus == KycStatus.VERIFIED;
    }

    public boolean hasLocation() {
        return currentLocation != null;
    }

    protected static Set<Badge> normalizeBadges(Set<Badge> badges) {
        if (badges == null || badges.isEmpty()) {
            return Set.of();
        }
        Set<Badge> normalized = new LinkedHashSet<>(badges);
        return Collections.unmodifiableSet(normalized);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TntActorProfile other)) return false;
        return id.equals(other.id) && tenantId.equals(other.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId);
    }
}
