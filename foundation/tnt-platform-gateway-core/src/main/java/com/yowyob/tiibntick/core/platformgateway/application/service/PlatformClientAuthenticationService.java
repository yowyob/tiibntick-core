package com.yowyob.tiibntick.core.platformgateway.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IApiKeyRepository;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IClientPermissionRepository;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IPlatformClientRepository;
import com.yowyob.tiibntick.core.platformgateway.config.TntPlatformGatewayProperties;
import com.yowyob.tiibntick.core.platformgateway.domain.exception.TntPlatformGatewayException;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKey;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKeyStatus;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientStatus;
import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClient;
import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClientApplication;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authenticates an incoming {@code X-Client-Id}/{@code X-Api-Key} pair against the
 * DB-backed platform client registry — resolving the client's granted scopes alongside
 * its identity (see {@code docs/auth/platform-client-management-design.md} §2.3).
 *
 * <p>DB-only: the earlier {@code .env}-driven fallback (for migrating pre-existing
 * platforms off static config) was removed 2026-07-09 at the user's explicit request —
 * this system is greenfield, no platform was consuming it via the old
 * {@code TNT_<PLATFORM>_CLIENT_ID}/{@code TNT_<PLATFORM>_API_KEY} env vars yet, so there
 * was nothing to migrate away from.
 *
 * <p><b>Caching (decided 2026-07-08 — short TTL only, no active invalidation
 * broadcast):</b> caches the resolved {@code (client, valid keys, scopes)} tuple per
 * {@code clientId} (never the raw secret) for a short TTL, so the BCrypt hash
 * comparison still happens on every request but the DB round-trip does not. A client's
 * key set is bounded (a rotation window rarely holds more than 2-3 rows), so fetching
 * all of a client's keys — indexed on {@code platform_client_id} — and filtering by
 * status in memory is not a full-table-scan concern.
 *
 * @author MANFOUO Braun
 */
public class PlatformClientAuthenticationService {

    private final IPlatformClientRepository clientRepository;
    private final IApiKeyRepository apiKeyRepository;
    private final IClientPermissionRepository permissionRepository;
    private final ApiKeyHashingService hashingService;
    private final Cache<String, CachedClient> cache;

    public PlatformClientAuthenticationService(
            IPlatformClientRepository clientRepository,
            IApiKeyRepository apiKeyRepository,
            IClientPermissionRepository permissionRepository,
            ApiKeyHashingService hashingService,
            TntPlatformGatewayProperties properties) {
        this.clientRepository = clientRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.permissionRepository = permissionRepository;
        this.hashingService = hashingService;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.getClientCacheTtl())
                .maximumSize(2_000)
                .build();
    }

    /**
     * Resolves the platform-client principal for the given credentials, or errors with
     * {@link TntPlatformGatewayException#unauthorized} — never reveals which half
     * (client-id vs api-key) was wrong.
     */
    public Mono<PlatformClientApplication> authenticate(String clientId, String rawApiKey) {
        if (clientId == null || clientId.isBlank() || rawApiKey == null || rawApiKey.isBlank()) {
            return Mono.error(TntPlatformGatewayException.unauthorized("Missing X-Client-Id / X-Api-Key"));
        }
        return resolveCached(clientId)
                .flatMap(cached -> matchAgainstCached(cached, rawApiKey))
                .switchIfEmpty(Mono.error(TntPlatformGatewayException.unauthorized("Invalid X-Client-Id / X-Api-Key")));
    }

    /** Invalidates the local cache entry for a client — called by the admin service after any mutation (key issue/rotate/revoke, scope change, status change) for same-instance immediacy; cross-instance consistency relies on the TTL (decided 2026-07-08). */
    public void invalidate(String clientId) {
        cache.invalidate(clientId);
    }

    private Mono<CachedClient> resolveCached(String clientId) {
        CachedClient hit = cache.getIfPresent(clientId);
        if (hit != null) {
            return Mono.just(hit);
        }
        return clientRepository.findByClientId(clientId)
                .flatMap(client -> apiKeyRepository.findAllByPlatformClientId(client.id())
                        .filter(key -> key.status() == ApiKeyStatus.ACTIVE || key.status() == ApiKeyStatus.ROTATING)
                        .collectList()
                        .zipWith(permissionRepository.findByPlatformClientId(client.id())
                                .map(com.yowyob.tiibntick.core.platformgateway.domain.model.ClientPermission::scope)
                                .collect(Collectors.toUnmodifiableSet()))
                        .map(tuple -> new CachedClient(client, tuple.getT1(), tuple.getT2())))
                .doOnNext(cached -> cache.put(clientId, cached));
    }

    private Mono<PlatformClientApplication> matchAgainstCached(CachedClient cached, String rawApiKey) {
        if (cached.client.status() != ClientStatus.ACTIVE) {
            return Mono.empty();
        }
        Instant now = Instant.now();
        for (ApiKey key : cached.validKeys) {
            boolean notExpired = key.expiresAt() == null || key.expiresAt().isAfter(now);
            if (notExpired && hashingService.matches(rawApiKey, key.keyHash())) {
                apiKeyRepository.markLastUsed(key.id(), now).subscribe();
                return Mono.just(new PlatformClientApplication(
                        cached.client.id(), cached.client.clientId(), cached.client.platformCode(),
                        cached.client.environment(), cached.scopes));
            }
        }
        return Mono.empty();
    }

    private record CachedClient(PlatformClient client, List<ApiKey> validKeys, Set<String> scopes) {
    }
}
