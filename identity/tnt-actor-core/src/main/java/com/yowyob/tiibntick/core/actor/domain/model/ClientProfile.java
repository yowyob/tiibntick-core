package com.yowyob.tiibntick.core.actor.domain.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ClientProfile extends TntActorProfile {

    private final List<UUID> favoriteAddressIds;
    private final int loyaltyScore;
    private final String preferredPaymentMethod;

    private ClientProfile(
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
            List<UUID> favoriteAddressIds,
            int loyaltyScore,
            String preferredPaymentMethod,
            String blockchainDid) {
        super(id, tenantId, actorId, ActorType.CLIENT, actorStatus, kycStatus,
                currentLocation, rating, badges, createdAt, updatedAt, blockchainDid);
        this.favoriteAddressIds = favoriteAddressIds != null ? List.copyOf(favoriteAddressIds) : List.of();
        this.loyaltyScore = Math.max(0, loyaltyScore);
        this.preferredPaymentMethod = preferredPaymentMethod;
    }

    public static ClientProfile create(UUID tenantId, UUID actorId) {
        return new ClientProfile(
                UUID.randomUUID(), tenantId, actorId,
                ActorStatus.ACTIVE, KycStatus.PENDING,
                null, ActorRating.zero(), Set.of(),
                Instant.now(), Instant.now(),
                List.of(), 0, null, null);
    }

    public static ClientProfile rehydrate(
            UUID id, UUID tenantId, UUID actorId,
            String actorStatus, String kycStatus,
            Double locationLat, Double locationLng, Double locationAccuracy,
            Instant locationTimestamp, String locationSource,
            double ratingScore, int ratingTotal, Instant ratingUpdatedAt,
            Set<Badge> badges,
            Instant createdAt, Instant updatedAt,
            List<UUID> favoriteAddressIds,
            int loyaltyScore,
            String preferredPaymentMethod,
            String blockchainDid) {
        ActorLocation location = (locationLat != null && locationLng != null)
                ? ActorLocation.of(locationLat, locationLng, locationAccuracy,
                        locationTimestamp != null ? locationTimestamp : Instant.now(),
                        LocationSource.from(locationSource))
                : null;
        ActorRating rating = ratingTotal > 0
                ? ActorRating.of(ratingScore, ratingTotal, ratingUpdatedAt)
                : ActorRating.zero();
        return new ClientProfile(
                id, tenantId, actorId,
                ActorStatus.from(actorStatus), KycStatus.from(kycStatus),
                location, rating, badges, createdAt, updatedAt,
                favoriteAddressIds, loyaltyScore, preferredPaymentMethod, blockchainDid);
    }

    public ClientProfile addFavoriteAddress(UUID addressId) {
        List<UUID> updated = new java.util.ArrayList<>(favoriteAddressIds);
        if (!updated.contains(addressId)) {
            updated.add(addressId);
        }
        return new ClientProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                updated, loyaltyScore, preferredPaymentMethod, blockchainDid());
    }

    public ClientProfile addLoyaltyPoints(int points) {
        return new ClientProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                favoriteAddressIds, loyaltyScore + points, preferredPaymentMethod, blockchainDid());
    }

    public ClientProfile withRating(ActorRating rating) {
        return new ClientProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating, badges(), createdAt(), Instant.now(),
                favoriteAddressIds, loyaltyScore, preferredPaymentMethod, blockchainDid());
    }

    public ClientProfile withKycStatus(KycStatus kycStatus) {
        return new ClientProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus,
                currentLocation(), rating(), badges(), createdAt(), Instant.now(),
                favoriteAddressIds, loyaltyScore, preferredPaymentMethod, blockchainDid());
    }

    public ClientProfile withBadge(Badge badge) {
        Set<Badge> updatedBadges = new LinkedHashSet<>(badges());
        updatedBadges.add(badge);
        return new ClientProfile(id(), tenantId(), actorId(), actorStatus(), kycStatus(),
                currentLocation(), rating(), updatedBadges, createdAt(), Instant.now(),
                favoriteAddressIds, loyaltyScore, preferredPaymentMethod, blockchainDid());
    }

    public List<UUID> favoriteAddressIds() {
        return favoriteAddressIds;
    }

    public int loyaltyScore() {
        return loyaltyScore;
    }

    public String preferredPaymentMethod() {
        return preferredPaymentMethod;
    }
}
