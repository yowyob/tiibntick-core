package com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table(schema = "tnt_actor", name = "client_profiles")
public record ClientProfileEntity(
        @Id UUID id,
        UUID tenantId,
        UUID actorId,
        String actorStatus,
        String kycStatus,
        Double locationLat,
        Double locationLng,
        double ratingScore,
        int ratingTotal,
        Instant ratingUpdatedAt,
        String badgesJson,
        Instant createdAt,
        Instant updatedAt,
        String favoriteAddressIdsJson,
        int loyaltyScore,
        String preferredPaymentMethod,
        /** Blockchain DID anchored via tnt-trust. Nullable — not currently issued for clients. */
        String blockchainDid) {
}
