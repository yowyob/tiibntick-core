package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpClient;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.client.ClientStatus;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateClientProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.command.CreateClientProfileCommand;
import com.yowyob.tiibntick.core.actor.application.port.out.IClientProfileRepository;
import com.yowyob.tiibntick.core.tp.application.service.LoyaltyService;
import com.yowyob.tiibntick.core.tp.domain.model.LoyaltyAccount;
import com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTier;
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
    private final LoyaltyService       loyaltyService;
    private final IClientProfileRepository clientProfiles;
    private final TenantContextHolder  tenantContextHolder;
    private final ICreateClientProfileUseCase createClientProfile;

    // ── Composition helper ────────────────────────────────────────────────

    private Mono<GofpClient> hydrate(GofpClient client) {
        Mono<GofpClient> withUser = client.getCoreUserId() == null ? Mono.just(client)
                : userRepository.findByCoreUserId(client.getCoreUserId())
                .doOnNext(client::setUser)
                .thenReturn(client)
                .onErrorResume(e -> {
                    log.warn("Could not hydrate GofpUser for client {}: {}", client.getId(), e.getMessage());
                    return Mono.just(client);
                });
        return withUser.flatMap(this::fromCore);
    }

    /**
     * Loyalty tier, order count and rating come from the core (lot L3), keyed by the client's actor id
     * ({@code coreUserId}, H1). A core read that fails (e.g. a caller without {@code loyalty:read}) leaves the
     * corresponding fields empty instead of failing the whole read.
     */
    private Mono<GofpClient> fromCore(GofpClient client) {
        UUID actorId = client.getCoreUserId();
        if (actorId == null) return Mono.just(client);
        return tenantContextHolder.currentTenantId().flatMap(tenantId -> {
            Mono<Void> loyalty = loyaltyService.getByThirdPartyId(tenantId, actorId)
                    .doOnNext(account -> {
                        client.setLoyaltyStatus(account.getCurrentTier());
                        client.setTotalOrders(account.getLifetimePoints() / LoyaltyAccount.POINTS_PER_DELIVERY);
                    })
                    .onErrorResume(e -> {
                        log.warn("Could not read the loyalty account of client {}: {}", client.getId(), e.getMessage());
                        return Mono.empty();
                    })
                    .then();
            Mono<Void> rating = clientProfiles.findByActorId(tenantId, actorId)
                    .doOnNext(profile -> client.setRating(
                            profile.rating().hasRatings() ? profile.rating().score() : null))
                    .onErrorResume(e -> Mono.empty())
                    .then();
            return Mono.when(loyalty, rating).thenReturn(client);
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
                    client.markNotNew();
                    client.setCreatedAt(existing.getCreatedAt());
                    client.setUpdatedAt(Instant.now());
                    log.info("Updating GofpClient coreClientId={}", client.getCoreClientId());
                    return clientRepository.save(client);
                })
                .switchIfEmpty(Mono.defer(() -> clientProfileIdOf(client).flatMap(profileId -> {
                    client.setId(profileId);
                    client.setCreatedAt(Instant.now());
                    client.setUpdatedAt(Instant.now());
                    log.info("Creating GofpClient id={} coreClientId={}", profileId, client.getCoreClientId());
                    return clientRepository.save(client);
                })))
                .flatMap(this::hydrate);
    }

    /**
     * Id of a new Go client row (lot L3, C1): the actor-core client profile whose id is {@code coreClientId},
     * otherwise the client profile of the actor {@code coreUserId}, found or created by the core (idempotent).
     */
    private Mono<UUID> clientProfileIdOf(GofpClient client) {
        return tenantContextHolder.currentTenantId().flatMap(tenantId -> clientProfiles
                .findById(tenantId, client.getCoreClientId())
                .switchIfEmpty(Mono.defer(() -> client.getCoreUserId() == null
                        ? Mono.error(new IllegalArgumentException(
                                "coreUserId is required when coreClientId is not a client profile id"))
                        : createClientProfile.createClientProfile(
                                new CreateClientProfileCommand(tenantId, client.getCoreUserId()))))
                .map(profile -> profile.id()));
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

    /**
     * Not supported any more (lot L3, H3): the tier is derived from the points of the tp-core loyalty account.
     * Answers {@link IllegalStateException} (HTTP 409) for an existing client.
     */
    @Override
    public Mono<GofpClient> updateLoyaltyStatus(UUID id, LoyaltyTier loyaltyStatus) {
        return clientRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpClient not found: " + id)))
                .flatMap(c -> Mono.error(new IllegalStateException(
                        "The loyalty tier is computed from the loyalty points of the core and cannot be set")));
    }

    /**
     * Deprecated no-op (lot L3, C4): a completed delivery is credited once by the core itself
     * ({@code DeliveryDelivered} of {@code tnt.delivery.events} consumed by tp-core, idempotent per delivery). Crediting here too would
     * count the delivery twice. Returns the client unchanged, without any permission beyond reading it.
     */
    @Override
    @Deprecated
    public Mono<GofpClient> recordOrder(UUID coreClientId) {
        return clientRepository.findByCoreClientId(coreClientId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpClient not found for coreClientId: " + coreClientId)))
                .doOnNext(c -> log.info("record-order for coreClientId={} ignored (deprecated, the core credits"
                        + " completed deliveries)", coreClientId))
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
}
