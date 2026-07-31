package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpClient;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.client.ClientStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.user.LoyaltyStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpClientUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpClientRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service for GofpClient lifecycle.
 *
 * <p><strong>Composition:</strong> after loading a {@link GofpClient}, the
 * service hydrates the transient {@code user} field from {@link GofpUserRepository}
 * so callers always receive a fully enriched object.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GofpClientService implements GofpClientUseCase {

    private final GofpClientRepository clientRepository;
    private final GofpUserRepository   userRepository;

    // ── Composition helper ────────────────────────────────────────────────

    private Mono<GofpClient> hydrate(GofpClient client) {
        if (client.getCoreUserId() == null) return Mono.just(client);
        return userRepository.findByCoreUserId(client.getCoreUserId())
                .doOnNext(client::setUser)
                .thenReturn(client)
                .onErrorResume(e -> {
                    log.warn("Could not hydrate GofpUser for client {}: {}", client.getId(), e.getMessage());
                    return Mono.just(client);
                });
    }

    // ── Write operations ──────────────────────────────────────────────────

    @Override
    public Mono<GofpClient> createOrUpdate(GofpClient client) {
        if (client.getCoreClientId() == null) {
            return Mono.error(new IllegalArgumentException("coreClientId is required"));
        }
        return clientRepository.findByCoreClientId(client.getCoreClientId())
                .flatMap(existing -> {
                    client.setId(existing.getId());
                    client.setCreatedAt(existing.getCreatedAt());
                    client.setUpdatedAt(Instant.now());
                    log.info("Updating GofpClient coreClientId={}", client.getCoreClientId());
                    return clientRepository.save(client);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    if (client.getId() == null) client.setId(UUID.randomUUID());
                    client.setCreatedAt(Instant.now());
                    client.setUpdatedAt(Instant.now());
                    log.info("Creating GofpClient coreClientId={}", client.getCoreClientId());
                    return clientRepository.save(client);
                }))
                .flatMap(this::hydrate);
    }

    @Override
    public Mono<GofpClient> updateStatus(UUID id, ClientStatus status) {
        return clientRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpClient not found: " + id)))
                .flatMap(c -> {
                    c.setStatus(status);
                    c.setUpdatedAt(Instant.now());
                    return clientRepository.save(c);
                })
                .flatMap(this::hydrate);
    }

    @Override
    public Mono<GofpClient> updateLoyaltyStatus(UUID id, LoyaltyStatus loyaltyStatus) {
        return clientRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpClient not found: " + id)))
                .flatMap(c -> {
                    c.setLoyaltyStatus(loyaltyStatus);
                    c.setUpdatedAt(Instant.now());
                    return clientRepository.save(c);
                })
                .flatMap(this::hydrate);
    }

    /**
     * Increments {@code totalOrders} and recomputes the loyalty tier:
     * BRONZE → SILVER (≥10) → GOLD (≥25) → PLATINUM (≥50).
     */
    @Override
    public Mono<GofpClient> recordOrder(UUID coreClientId) {
        return clientRepository.findByCoreClientId(coreClientId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpClient not found for coreClientId: " + coreClientId)))
                .flatMap(c -> {
                    int orders = (c.getTotalOrders() == null ? 0 : c.getTotalOrders()) + 1;
                    c.setTotalOrders(orders);
                    c.setLoyaltyStatus(computeLoyalty(orders));
                    c.setUpdatedAt(Instant.now());
                    return clientRepository.save(c);
                })
                .flatMap(this::hydrate);
    }

    // ── Read operations ───────────────────────────────────────────────────

    @Override
    public Mono<GofpClient> findById(UUID id) {
        return clientRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpClient not found: " + id)))
                .flatMap(this::hydrate);
    }

    @Override
    public Mono<GofpClient> findByCoreClientId(UUID coreClientId) {
        return clientRepository.findByCoreClientId(coreClientId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpClient not found for coreClientId: " + coreClientId)))
                .flatMap(this::hydrate);
    }

    @Override
    public Mono<GofpClient> findByCoreUserId(UUID coreUserId) {
        return clientRepository.findByCoreUserId(coreUserId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpClient not found for coreUserId: " + coreUserId)))
                .flatMap(this::hydrate);
    }

    @Override
    public Flux<GofpClient> findAll() {
        return clientRepository.findAll().flatMap(this::hydrate);
    }

    @Override
    public Flux<GofpClient> findByStatus(ClientStatus status) {
        return clientRepository.findAllByStatus(status).flatMap(this::hydrate);
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return clientRepository.deleteById(id);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private LoyaltyStatus computeLoyalty(int totalOrders) {
        if (totalOrders >= 50) return LoyaltyStatus.PLATINUM;
        if (totalOrders >= 25) return LoyaltyStatus.GOLD;
        if (totalOrders >= 10) return LoyaltyStatus.SILVER;
        return LoyaltyStatus.BRONZE;
    }
}
