package com.yowyob.tiibntick.core.actor.domain.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RelayOperatorProfile extends TntActorProfile {

    private final UUID hubId;
    private final List<AvailabilitySlot> openingHours;
    private final int declaredCapacityParcels;

    private RelayOperatorProfile(
            UUID id,
            UUID tenantId,
            UUID actorId,
            ActorStatus actorStatus,
            KycStatus kycStatus,
            ActorLocation currentLocation,
            ActorRating rating,
            Set<Badge> badges,
            Instant createdAt,
            Instant updatedAt,
            UUID hubId,
            List<AvailabilitySlot> openingHours,
            int declaredCapacityParcels) {
        super(id, tenantId, actorId, ActorType.RELAY_OPERATOR, actorStatus, kycStatus,
                currentLocation, rating, badges, createdAt, updatedAt);
        this.hubId = Objects.requireNonNull(hubId, "hubId must not be null");
        this.openingHours = openingHours != null ? List.copyOf(openingHours) : List.of();
        if (declaredCapacityParcels < 0) {
            throw new IllegalArgumentException("declaredCapacityParcels must not be negative");
        }
        this.declaredCapacityParcels = declaredCapacityParcels;
    }

    public static RelayOperatorProfile create(
            UUID tenantId,
            UUID actorId,
            UUID hubId,
            List<AvailabilitySlot> openingHours,
            int declaredCapacityParcels) {
        return new RelayOperatorProfile(
                UUID.randomUUID(), tenantId, actorId,
                ActorStatus.INACTIVE, KycStatus.PENDING,
                null, ActorRating.zero(), Set.of(),
                Instant.now(), Instant.now(),
                hubId, openingHours, declaredCapacityParcels);
    }

    public static RelayOperatorProfile rehydrate(
            UUID id, UUID tenantId, UUID actorId,
            String actorStatus, String kycStatus,
            Double locationLat, Double locationLng, Double locationAccuracy,
            Instant locationTimestamp, String locationSource,
            double ratingScore, int ratingTotal, Instant ratingUpdatedAt,
            Set<Badge> badges,
            Instant createdAt, Instant updatedAt,
            UUID hubId, List<AvailabilitySlot> openingHours, int declaredCapacityParcels) {
        ActorLocation location = (locationLat != null && locationLng != null)
                ? ActorLocation.of(locationLat, locationLng, locationAccuracy,
                        locationTimestamp != null ? locationTimestamp : Instant.now(),
                        LocationSource.from(locationSource))
                : null;
        ActorRating rating = ratingTotal > 0
                ? ActorRating.of(ratingScore, ratingTotal, ratingUpdatedAt)
                : ActorRating.zero();
        return new RelayOperatorProfile(
                id, tenantId, actorId,
                ActorStatus.from(actorStatus), KycStatus.from(kycStatus),
                location, rating, badges, createdAt, updatedAt,
                hubId, openingHours, declaredCapacityParcels);
    }

    public RelayOperatorProfile withLocation(ActorLocation location) {
        return new RelayOperatorProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                location, rating(), badges(), createdAt(), Instant.now(),
                hubId, openingHours, declaredCapacityParcels);
    }

    public RelayOperatorProfile withRating(ActorRating rating) {
        return new RelayOperatorProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating, badges(), createdAt(), Instant.now(),
                hubId, openingHours, declaredCapacityParcels);
    }

    public RelayOperatorProfile withKycStatus(KycStatus kycStatus) {
        return new RelayOperatorProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus,
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                hubId, openingHours, declaredCapacityParcels);
    }

    public RelayOperatorProfile activate() {
        return new RelayOperatorProfile(id(), tenantId(), actorId(), ActorStatus.ACTIVE, kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                hubId, openingHours, declaredCapacityParcels);
    }

    public RelayOperatorProfile withBadge(Badge badge) {
        Set<Badge> updatedBadges = new LinkedHashSet<>(badges());
        updatedBadges.add(badge);
        return new RelayOperatorProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), updatedBadges, createdAt(), Instant.now(),
                hubId, openingHours, declaredCapacityParcels);
    }

    public UUID hubId() {
        return hubId;
    }

    public List<AvailabilitySlot> openingHours() {
        return openingHours;
    }

    public int declaredCapacityParcels() {
        return declaredCapacityParcels;
    }
}
