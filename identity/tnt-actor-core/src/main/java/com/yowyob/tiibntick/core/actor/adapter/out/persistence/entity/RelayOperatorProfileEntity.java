package com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table(schema = "tnt_actor", name = "relay_operator_profiles")
public record RelayOperatorProfileEntity(
        @Id UUID id,
        UUID tenantId,
        UUID actorId,
        String actorStatus,
        String kycStatus,
        Double locationLat,
        Double locationLng,
        Double locationAccuracy,
        Instant locationTimestamp,
        String locationSource,
        double ratingScore,
        int ratingTotal,
        Instant ratingUpdatedAt,
        String badgesJson,
        Instant createdAt,
        Instant updatedAt,
        UUID hubId,
        String openingHoursJson,
        int declaredCapacityParcels,
        /** Blockchain DID anchored via tnt-trust after KYC verification. Nullable. */
        String blockchainDid) {
}
