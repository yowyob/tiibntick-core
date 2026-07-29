package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import com.yowyob.tiibntick.core.actor.domain.model.ActorIdentitySummary;
import com.yowyob.tiibntick.core.actor.domain.model.ClientProfile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code identity} is resolved live from the Kernel via
 * {@code IResolveActorIdentityUseCase} — never persisted on the profile
 * itself, so it can be {@code null} if the Kernel is unreachable or does not
 * (yet) expose a by-id actor lookup.
 */
public record ClientProfileResponse(
        UUID id,
        UUID tenantId,
        UUID actorId,
        String actorStatus,
        String kycStatus,
        double ratingScore,
        int ratingTotal,
        List<UUID> favoriteAddressIds,
        int loyaltyScore,
        String preferredPaymentMethod,
        Instant createdAt,
        Instant updatedAt,
        ActorIdentitySummary identity) {

    public static ClientProfileResponse from(ClientProfile p) {
        return from(p, null);
    }

    public static ClientProfileResponse from(ClientProfile p, ActorIdentitySummary identity) {
        return new ClientProfileResponse(
                p.id(), p.tenantId(), p.actorId(),
                p.actorStatus().name(), p.kycStatus().name(),
                p.rating().score(), p.rating().totalRatings(),
                p.favoriteAddressIds(), p.loyaltyScore(), p.preferredPaymentMethod(),
                p.createdAt(), p.updatedAt(), identity);
    }
}
