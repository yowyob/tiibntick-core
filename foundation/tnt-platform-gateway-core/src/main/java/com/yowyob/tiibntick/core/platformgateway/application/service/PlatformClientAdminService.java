package com.yowyob.tiibntick.core.platformgateway.application.service;

import com.yowyob.tiibntick.core.platformgateway.application.port.in.PlatformClientAdminUseCase;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IApiKeyRepository;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IApiKeyRotationRepository;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IClientAuditLogRepository;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IClientPermissionRepository;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IPlatformClientRepository;
import com.yowyob.tiibntick.core.platformgateway.domain.exception.TntPlatformGatewayException;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKey;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKeyRotationRecord;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKeyStatus;
import com.yowyob.tiibntick.core.platformgateway.domain.model.AuditOutcome;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientAuditLog;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientPermission;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientStatus;
import com.yowyob.tiibntick.core.platformgateway.domain.model.Environment;
import com.yowyob.tiibntick.core.platformgateway.domain.model.IssuedApiKey;
import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Implements {@link PlatformClientAdminUseCase} — the entire platform-client/API-key
 * lifecycle. Every mutation invalidates {@link PlatformClientAuthenticationService}'s
 * local cache entry for the affected client (same-instance immediacy; cross-instance
 * consistency relies on the cache's short TTL, decided 2026-07-08).
 *
 * @author MANFOUO Braun
 */
public class PlatformClientAdminService implements PlatformClientAdminUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IPlatformClientRepository clientRepository;
    private final IApiKeyRepository apiKeyRepository;
    private final IClientPermissionRepository permissionRepository;
    private final IApiKeyRotationRepository rotationRepository;
    private final IClientAuditLogRepository auditLogRepository;
    private final ApiKeyHashingService hashingService;
    private final PlatformScopeRegistry scopeRegistry;
    private final PlatformClientAuthenticationService authenticationService;

    public PlatformClientAdminService(
            IPlatformClientRepository clientRepository,
            IApiKeyRepository apiKeyRepository,
            IClientPermissionRepository permissionRepository,
            IApiKeyRotationRepository rotationRepository,
            IClientAuditLogRepository auditLogRepository,
            ApiKeyHashingService hashingService,
            PlatformScopeRegistry scopeRegistry,
            PlatformClientAuthenticationService authenticationService) {
        this.clientRepository = clientRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.permissionRepository = permissionRepository;
        this.rotationRepository = rotationRepository;
        this.auditLogRepository = auditLogRepository;
        this.hashingService = hashingService;
        this.scopeRegistry = scopeRegistry;
        this.authenticationService = authenticationService;
    }

    // ── Platform clients ─────────────────────────────────────────────────────

    /**
     * A newly-created client is granted the global wildcard scope ({@code *}) by
     * default — full, unconditional access to every gateway block and (once built) every
     * curated business-module proxy, no separate {@code replaceScopes} call required
     * (decided 2026-07-09, at the user's explicit request: creating a client should be
     * immediately usable end-to-end). Restrict a specific client afterward via
     * {@code PUT /api/v1/admin/platform-clients/{id}/permissions} if narrower access is
     * ever needed for it.
     */
    @Override
    public Mono<PlatformClient> createClient(String name, String platformCode, Environment environment,
            String description, String contactEmail, String createdBy) {
        Instant now = Instant.now();
        PlatformClient client = new PlatformClient(
                UUID.randomUUID(), generateClientId(platformCode, environment), name, platformCode, environment,
                ClientStatus.ACTIVE, description, contactEmail, now, now, createdBy, createdBy);
        return clientRepository.save(client)
                .flatMap(saved -> permissionRepository.save(new ClientPermission(
                                UUID.randomUUID(), saved.id(), "*", now, createdBy))
                        .thenReturn(saved));
    }

    @Override
    public Flux<PlatformClient> listClients(String platformCode, Environment environment, ClientStatus status, int page, int size) {
        return clientRepository.findPage(platformCode, environment, status, page, size);
    }

    @Override
    public Mono<Long> countClients(String platformCode, Environment environment, ClientStatus status) {
        return clientRepository.count(platformCode, environment, status);
    }

    @Override
    public Mono<PlatformClient> getClient(UUID clientId) {
        return clientRepository.findById(clientId)
                .switchIfEmpty(Mono.error(() -> TntPlatformGatewayException.clientNotFound(clientId.toString())));
    }

    @Override
    public Mono<PlatformClient> updateClient(UUID clientId, String name, String description, String contactEmail,
            ClientStatus status, String updatedBy) {
        return getClient(clientId)
                .map(existing -> new PlatformClient(
                        existing.id(), existing.clientId(),
                        name != null ? name : existing.name(),
                        existing.platformCode(), existing.environment(),
                        status != null ? status : existing.status(),
                        description != null ? description : existing.description(),
                        contactEmail != null ? contactEmail : existing.contactEmail(),
                        existing.createdAt(), Instant.now(), existing.createdBy(), updatedBy))
                .flatMap(clientRepository::save)
                .doOnNext(updated -> authenticationService.invalidate(updated.clientId()));
    }

    @Override
    public Mono<Void> decommissionClient(UUID clientId, String updatedBy) {
        return getClient(clientId)
                .flatMap(client -> revokeAllActiveKeys(client.id(), "Client decommissioned", updatedBy)
                        .then(updateClient(clientId, null, null, null, ClientStatus.DECOMMISSIONED, updatedBy)))
                .then();
    }

    // ── API keys ──────────────────────────────────────────────────────────────

    @Override
    public Mono<IssuedApiKey> issueApiKey(UUID clientId, Instant expiresAt, String createdBy) {
        return getClient(clientId)
                .flatMap(client -> {
                    String rawKey = hashingService.generateRawKey();
                    ApiKey newKey = new ApiKey(
                            UUID.randomUUID(), client.id(), hashingService.prefixOf(rawKey), hashingService.hash(rawKey),
                            ApiKeyStatus.ACTIVE, expiresAt, null, Instant.now(), null, null, null);
                    return apiKeyRepository.save(newKey)
                            .doOnNext(saved -> authenticationService.invalidate(client.clientId()))
                            .map(saved -> new IssuedApiKey(saved.id(), saved.keyPrefix(), rawKey, saved.expiresAt()));
                });
    }

    @Override
    public Flux<ApiKey> listApiKeys(UUID clientId) {
        return apiKeyRepository.findAllByPlatformClientId(clientId);
    }

    @Override
    public Mono<IssuedApiKey> rotateApiKey(UUID apiKeyId, Duration graceWindow, String reason, String rotatedBy) {
        return apiKeyRepository.findById(apiKeyId)
                .switchIfEmpty(Mono.error(() -> TntPlatformGatewayException.apiKeyNotFound(apiKeyId.toString())))
                .flatMap(oldKey -> getClient(oldKey.platformClientId())
                        .flatMap(client -> {
                            String rawKey = hashingService.generateRawKey();
                            ApiKey newKey = new ApiKey(
                                    UUID.randomUUID(), client.id(), hashingService.prefixOf(rawKey), hashingService.hash(rawKey),
                                    ApiKeyStatus.ACTIVE, null, null, Instant.now(), null, null, null);
                            return apiKeyRepository.save(newKey)
                                    .flatMap(savedNewKey -> {
                                        Instant graceExpiry = Instant.now().plus(graceWindow != null ? graceWindow : Duration.ofHours(24));
                                        ApiKey rotatingOld = new ApiKey(
                                                oldKey.id(), oldKey.platformClientId(), oldKey.keyPrefix(), oldKey.keyHash(),
                                                ApiKeyStatus.ROTATING, graceExpiry, oldKey.lastUsedAt(), oldKey.createdAt(),
                                                oldKey.revokedAt(), oldKey.revokedBy(), oldKey.revokedReason());
                                        ApiKeyRotationRecord rotationRecord = new ApiKeyRotationRecord(
                                                UUID.randomUUID(), client.id(), oldKey.id(), savedNewKey.id(),
                                                Instant.now(), rotatedBy, reason);
                                        return apiKeyRepository.save(rotatingOld)
                                                .then(rotationRepository.save(rotationRecord))
                                                .doOnNext(x -> authenticationService.invalidate(client.clientId()))
                                                .thenReturn(new IssuedApiKey(savedNewKey.id(), savedNewKey.keyPrefix(), rawKey, savedNewKey.expiresAt()));
                                    });
                        }));
    }

    @Override
    public Mono<Void> revokeApiKey(UUID apiKeyId, String reason, String revokedBy) {
        return apiKeyRepository.findById(apiKeyId)
                .switchIfEmpty(Mono.error(() -> TntPlatformGatewayException.apiKeyNotFound(apiKeyId.toString())))
                .flatMap(key -> {
                    ApiKey revoked = new ApiKey(
                            key.id(), key.platformClientId(), key.keyPrefix(), key.keyHash(),
                            ApiKeyStatus.REVOKED, key.expiresAt(), key.lastUsedAt(), key.createdAt(),
                            Instant.now(), revokedBy, reason);
                    return apiKeyRepository.save(revoked)
                            .flatMap(saved -> clientRepository.findById(saved.platformClientId()))
                            .doOnNext(client -> authenticationService.invalidate(client.clientId()));
                })
                .then();
    }

    private Mono<Void> revokeAllActiveKeys(UUID platformClientId, String reason, String revokedBy) {
        return apiKeyRepository.findAllByPlatformClientId(platformClientId)
                .filter(key -> key.status() == ApiKeyStatus.ACTIVE || key.status() == ApiKeyStatus.ROTATING)
                .flatMap(key -> revokeApiKey(key.id(), reason, revokedBy))
                .then();
    }

    // ── Scopes ────────────────────────────────────────────────────────────────

    @Override
    public Mono<Void> replaceScopes(UUID clientId, Set<String> scopes, String grantedBy) {
        for (String scope : scopes) {
            if (!scopeRegistry.isValidScope(scope)) {
                return Mono.error(TntPlatformGatewayException.invalidScope(scope));
            }
        }
        return getClient(clientId)
                .flatMap(client -> permissionRepository.deleteByPlatformClientId(client.id())
                        .thenMany(Flux.fromIterable(scopes)
                                .flatMap(scope -> permissionRepository.save(new ClientPermission(
                                        UUID.randomUUID(), client.id(), scope, Instant.now(), grantedBy))))
                        .then()
                        .doOnSuccess(v -> authenticationService.invalidate(client.clientId())));
    }

    @Override
    public Flux<ClientPermission> getScopes(UUID clientId) {
        return permissionRepository.findByPlatformClientId(clientId);
    }

    // ── Audit ─────────────────────────────────────────────────────────────────

    @Override
    public Flux<ClientAuditLog> listAuditLogs(UUID clientId, AuditOutcome outcome, Instant from, Instant to, int page, int size) {
        return auditLogRepository.findPage(clientId, outcome, from, to, page, size);
    }

    @Override
    public Mono<Long> countAuditLogs(UUID clientId, AuditOutcome outcome, Instant from, Instant to) {
        return auditLogRepository.count(clientId, outcome, from, to);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String generateClientId(String platformCode, Environment environment) {
        byte[] suffix = new byte[4];
        RANDOM.nextBytes(suffix);
        StringBuilder hex = new StringBuilder();
        for (byte b : suffix) {
            hex.append(String.format("%02x", b));
        }
        return (platformCode + "-" + environment.name() + "-" + hex).toLowerCase();
    }
}
