package com.yowyob.tiibntick.core.agency.org.adapter.out.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Direct HTTP adapter to tnt-trust. Soft-fails so Trust outage never rolls back ERP writes.
 */
@Component
public class TrustCoreClient implements TrustPort {

    private static final Logger log = LoggerFactory.getLogger(TrustCoreClient.class);
    private static final String BASE = "/api/v1/trust/delivery";

    private final WebClient webClient;

    public TrustCoreClient(@Qualifier("trustWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<String> recordPickup(PickupTransaction tx) {
        Map<String, Object> body = new HashMap<>();
        body.put("missionId", tx.missionId().toString());
        body.put("packageId", tx.packageId().toString());
        body.put("delivererId", tx.delivererId().toString());
        body.put("latitude", tx.latitude());
        body.put("longitude", tx.longitude());
        body.put("timestamp", tx.timestamp().toString());
        return post(BASE + "/pickup", body, "pickup", tx.missionId().toString());
    }

    @Override
    public Mono<String> recordDelivery(DeliveryTransaction tx) {
        Map<String, Object> body = new HashMap<>();
        body.put("missionId", tx.missionId().toString());
        body.put("delivererId", tx.delivererId().toString());
        body.put("recipientName", nullToEmpty(tx.recipientName()));
        body.put("signatureHash", nullToEmpty(tx.signatureHash()));
        body.put("photoHash", nullToEmpty(tx.photoHash()));
        body.put("latitude", tx.latitude());
        body.put("longitude", tx.longitude());
        body.put("timestamp", tx.timestamp().toString());
        return post(BASE + "/delivery", body, "delivery", tx.missionId().toString());
    }

    @Override
    public Mono<String> recordHubDeposit(HubDepositTransaction tx) {
        Map<String, Object> body = new HashMap<>();
        body.put("hubId", tx.hubId().toString());
        body.put("packageId", tx.packageId().toString());
        body.put("missionId", tx.missionId().toString());
        body.put("trackingCode", tx.trackingCode());
        body.put("depositedAt", tx.depositedAt().toString());
        return post(BASE + "/hub/deposit", body, "hub deposit", tx.packageId().toString());
    }

    @Override
    public Mono<String> recordHubWithdrawal(HubWithdrawalTransaction tx) {
        Map<String, Object> body = new HashMap<>();
        body.put("hubId", tx.hubId().toString());
        body.put("packageId", tx.packageId().toString());
        body.put("trackingCode", tx.trackingCode());
        body.put("withdrawnBy", tx.withdrawnBy());
        body.put("identityVerified", tx.identityVerified());
        body.put("withdrawnAt", tx.withdrawnAt().toString());
        return post(BASE + "/hub/withdrawal", body, "hub withdrawal", tx.packageId().toString());
    }

    private Mono<String> post(String path, Map<String, Object> body, String label, String fallbackId) {
        return webClient.post()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.warn("[Trust] {} anchor failed id={}: {}", label, fallbackId, e.getMessage());
                    return Mono.just("offline-" + fallbackId);
                });
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
