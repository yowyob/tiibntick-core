package com.yowyob.tiibntick.core.agency.org.adapter.out.clients;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port — tnt-trust delivery anchors ({@code /api/v1/trust/delivery/**}).
 */
public interface TrustPort {

    Mono<String> recordPickup(PickupTransaction tx);

    Mono<String> recordDelivery(DeliveryTransaction tx);

    Mono<String> recordHubDeposit(HubDepositTransaction tx);

    Mono<String> recordHubWithdrawal(HubWithdrawalTransaction tx);

    record PickupTransaction(
            UUID missionId,
            UUID packageId,
            UUID delivererId,
            double latitude,
            double longitude,
            Instant timestamp
    ) {}

    record DeliveryTransaction(
            UUID missionId,
            UUID delivererId,
            String recipientName,
            String signatureHash,
            String photoHash,
            double latitude,
            double longitude,
            Instant timestamp
    ) {}

    record HubDepositTransaction(
            UUID hubId,
            UUID packageId,
            UUID missionId,
            String trackingCode,
            Instant depositedAt
    ) {}

    record HubWithdrawalTransaction(
            UUID hubId,
            UUID packageId,
            String trackingCode,
            String withdrawnBy,
            boolean identityVerified,
            Instant withdrawnAt
    ) {}
}
