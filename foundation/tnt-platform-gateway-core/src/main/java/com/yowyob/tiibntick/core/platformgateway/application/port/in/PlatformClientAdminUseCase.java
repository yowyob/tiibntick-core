package com.yowyob.tiibntick.core.platformgateway.application.port.in;

import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKey;
import com.yowyob.tiibntick.core.platformgateway.domain.model.AuditOutcome;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientAuditLog;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientPermission;
import com.yowyob.tiibntick.core.platformgateway.domain.model.ClientStatus;
import com.yowyob.tiibntick.core.platformgateway.domain.model.Environment;
import com.yowyob.tiibntick.core.platformgateway.domain.model.IssuedApiKey;
import com.yowyob.tiibntick.core.platformgateway.domain.model.PlatformClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Primary (inbound) use-case for the admin API — the entire lifecycle of a platform
 * client and its API keys/scopes/audit trail (see
 * {@code docs/auth/platform-client-management-design.md} §4). Every operation is
 * restricted to {@code TNT_ADMIN} by the calling controller.
 *
 * <p>One cohesive port rather than one interface per operation — mirrors this
 * codebase's existing convention of grouping closely-related operations behind a
 * single use-case (e.g. {@code ProxyKernelAuthUseCase}), rather than proliferating
 * near-empty single-method interfaces for what is really one bounded-context aggregate.
 *
 * <p>Implemented by {@code PlatformClientAdminService}, exposed by
 * {@code PlatformClientAdminController} / {@code ApiKeyAdminController} /
 * {@code ScopeRegistryController}.
 *
 * @author MANFOUO Braun
 */
public interface PlatformClientAdminUseCase {

    // ── Platform clients ─────────────────────────────────────────────────────

    Mono<PlatformClient> createClient(String name, String platformCode, Environment environment,
            String description, String contactEmail, String createdBy);

    Flux<PlatformClient> listClients(String platformCode, Environment environment, ClientStatus status, int page, int size);

    Mono<Long> countClients(String platformCode, Environment environment, ClientStatus status);

    Mono<PlatformClient> getClient(UUID clientId);

    Mono<PlatformClient> updateClient(UUID clientId, String name, String description, String contactEmail,
            ClientStatus status, String updatedBy);

    /** Soft-decommission — sets {@code DECOMMISSIONED} and revokes every active/rotating key. Never a hard delete. */
    Mono<Void> decommissionClient(UUID clientId, String updatedBy);

    // ── API keys ──────────────────────────────────────────────────────────────

    /** Issues a brand-new key. The plaintext secret in the result is shown exactly once. */
    Mono<IssuedApiKey> issueApiKey(UUID clientId, Instant expiresAt, String createdBy);

    Flux<ApiKey> listApiKeys(UUID clientId);

    /** Issues a replacement key (status ACTIVE) and marks the old one ROTATING with a grace-period expiry; logs to rotation history. */
    Mono<IssuedApiKey> rotateApiKey(UUID apiKeyId, java.time.Duration graceWindow, String reason, String rotatedBy);

    /** Immediate revocation (incident response) — sets REVOKED now, no grace window. */
    Mono<Void> revokeApiKey(UUID apiKeyId, String reason, String revokedBy);

    // ── Scopes ────────────────────────────────────────────────────────────────

    /** Replaces the client's entire granted scope set. Every scope must be well-formed per {@code PlatformScopeRegistry}. */
    Mono<Void> replaceScopes(UUID clientId, Set<String> scopes, String grantedBy);

    Flux<ClientPermission> getScopes(UUID clientId);

    // ── Audit ─────────────────────────────────────────────────────────────────

    Flux<ClientAuditLog> listAuditLogs(UUID clientId, AuditOutcome outcome, Instant from, Instant to, int page, int size);

    Mono<Long> countAuditLogs(UUID clientId, AuditOutcome outcome, Instant from, Instant to);
}
