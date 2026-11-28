package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.in.ManagePlatformAdminsUseCase;
import com.yowyob.tiibntick.core.roles.application.port.out.IPermissionChangeNotifier;
import com.yowyob.tiibntick.core.roles.application.port.out.IPlatformAdminAlertPort;
import com.yowyob.tiibntick.core.roles.application.port.out.PlatformRoleGrantRepository;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformGrantKind;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant.TNT_ADMIN;

/**
 * The registry of TiiBnTick platform administrators (decisions D-159 to D-175). The role is held by the account,
 * whatever tenant it logs into: no Kernel membership of the system tenant is needed, and the Kernel is not written
 * (D-170).
 *
 * <p>Rules: any active TNT_ADMIN grants and revokes ASSIGNED grants; the Kernel owner of the system tenant holds
 * a protected SYSTEM_OWNER grant, which nobody revokes while it was obtained from the system tenant in force; only
 * that owner runs the mass revocation of the grants obtained from another tenant, after a preview.
 *
 * @author MANFOUO Braun
 */
public class PlatformAdminService implements ManagePlatformAdminsUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminService.class);

    static final Duration CONFIRMATION_TTL = Duration.ofMinutes(5);
    private static final String TOKEN_VERSION = "v1";
    static final String SUPERSEDED_BY_OWNER = "superseded by the SYSTEM_OWNER grant";
    static final String OWNERSHIP_TRANSFERRED = "system-tenant ownership transferred (former owner kept as ASSIGNED)";

    private final PlatformRoleGrantRepository repository;
    private final PlatformAdminCache cache;
    private final IPermissionChangeNotifier changeNotifier;
    private final TransactionalOperator transactions;
    private final List<IPlatformAdminAlertPort> alerts;
    private final UUID systemTenantId;
    private final Clock clock;

    public PlatformAdminService(PlatformRoleGrantRepository repository, PlatformAdminCache cache,
                                IPermissionChangeNotifier changeNotifier, TransactionalOperator transactions,
                                List<IPlatformAdminAlertPort> alerts, UUID systemTenantId, Clock clock) {
        this.repository = repository;
        this.cache = cache;
        this.changeNotifier = changeNotifier;
        this.transactions = transactions;
        this.alerts = alerts == null ? List.of() : List.copyOf(alerts);
        this.systemTenantId = systemTenantId;
        this.clock = clock;
    }

    // ── resolution ───────────────────────────────────────────────────────

    @Override
    public Mono<Boolean> isPlatformAdmin(UUID userId) {
        if (userId == null) {
            return Mono.just(false);
        }
        Boolean cached = cache.getIfPresent(userId);
        if (cached != null) {
            return Mono.just(cached);
        }
        return repository.findActive(TNT_ADMIN, userId).hasElement()
                .doOnNext(admin -> cache.put(userId, admin));
    }

    @Override
    public Mono<PlatformRoleGrant> ensureSystemOwner(UUID ownerUserId) {
        if (ownerUserId == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "No subject in the token"));
        }
        return repository.findActive(TNT_ADMIN, ownerUserId)
                .flatMap(active -> active.protectedFor(systemTenantId) ? Mono.just(active)
                        // an ASSIGNED grant, or the owner grant of a former system tenant: superseded
                        : recordOwner(ownerUserId, active))
                .switchIfEmpty(Mono.defer(() -> recordOwner(ownerUserId, null)))
                .doOnNext(g -> cache.confirmOwner(ownerUserId));
    }

    private Mono<PlatformRoleGrant> recordOwner(UUID ownerUserId, PlatformRoleGrant superseded) {
        Instant now = clock.instant();
        PlatformRoleGrant owner = PlatformRoleGrant.newGrant(ownerUserId, PlatformGrantKind.SYSTEM_OWNER,
                ownerUserId, systemTenantId, systemTenantId, now);
        Mono<Boolean> retire = superseded == null ? Mono.just(true)
                : repository.revoke(superseded.id(), ownerUserId, SUPERSEDED_BY_OWNER, null, now);
        List<PlatformRoleGrant> transferred = new java.util.ArrayList<>();
        return retire
                .then(transferOwnership(ownerUserId, now, transferred))
                .then(repository.insertIfNoActive(owner))
                .as(transactions::transactional)
                .flatMap(saved -> Flux.fromIterable(transferred)
                        .concatMap(g -> changed(g).then(alert(g, g.active(), ownerUserId)))
                        .then(changed(saved)).then(alert(saved, true, ownerUserId)).thenReturn(saved))
                // a concurrent first login recorded it already
                .switchIfEmpty(Mono.defer(() -> repository.findActive(TNT_ADMIN, ownerUserId)));
    }

    /**
     * Decision D-177: the Kernel ownership of the system tenant changed hands (same system tenant). The former owner's
     * protected grant is closed ({@link #OWNERSHIP_TRANSFERRED}) and replaced by an ordinary ASSIGNED grant, granted by
     * the new owner from the system tenant: the former owner stays TNT_ADMIN but becomes revocable. Both changes are
     * collected in {@code out} (the revoked row, then the new one) for the alerts sent after the commit.
     */
    private Mono<Void> transferOwnership(UUID newOwner, Instant now, List<PlatformRoleGrant> out) {
        return repository.findAll(TNT_ADMIN, false)
                .filter(g -> g.protectedFor(systemTenantId) && !g.userId().equals(newOwner))
                .concatMap(former -> repository.revoke(former.id(), newOwner, OWNERSHIP_TRANSFERRED, null, now)
                        .filter(Boolean::booleanValue)
                        .flatMap(done -> repository.findById(former.id()))
                        .doOnNext(out::add)
                        .then(repository.insertIfNoActive(PlatformRoleGrant.newGrant(former.userId(),
                                PlatformGrantKind.ASSIGNED, newOwner, systemTenantId, systemTenantId, now)))
                        .doOnNext(out::add))
                .then();
    }

    // ── administration ───────────────────────────────────────────────────

    @Override
    public Mono<PlatformRoleGrant> claimOwnership(Caller caller) {
        if (caller == null || !caller.systemTenantOwner()) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Kernel owner of the system tenant, logged in that tenant, can claim the ownership"));
        }
        return ensureSystemOwner(caller.userId());
    }

    @Override
    public Mono<PlatformRoleGrant> grant(Caller caller, UUID targetUserId) {
        if (targetUserId == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetUserId is required"));
        }
        return requireAdmin(caller).then(Mono.defer(() -> {
            if (caller.tenantId() == null) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The tenant of the granter's token is unknown: the source tenant cannot be recorded"));
            }
            return repository.findActive(TNT_ADMIN, targetUserId)
                    .switchIfEmpty(Mono.defer(() -> {
                        PlatformRoleGrant grant = PlatformRoleGrant.newGrant(targetUserId, PlatformGrantKind.ASSIGNED,
                                caller.userId(), caller.tenantId(), systemTenantId, clock.instant());
                        return repository.insertIfNoActive(grant)
                                .flatMap(saved -> changed(saved).then(alert(saved, true, null)).thenReturn(saved))
                                .switchIfEmpty(Mono.defer(() -> repository.findActive(TNT_ADMIN, targetUserId)));
                    }));
        }));
    }

    @Override
    public Mono<PlatformRoleGrant> revoke(Caller caller, UUID grantId, String reason) {
        return requireAdmin(caller).then(find(grantId)).flatMap(grant -> {
            if (!grant.active()) {
                return Mono.just(grant);
            }
            if (grant.protectedFor(systemTenantId)) {
                return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "The TNT_ADMIN of the system-tenant owner cannot be revoked"));
            }
            if (grant.kind() == PlatformGrantKind.SYSTEM_OWNER) {
                return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "The owner grant of a former system tenant is revoked by the foreign-tenant mass revocation"));
            }
            Instant now = clock.instant();
            return repository.revoke(grant.id(), caller.userId(), blankToNull(reason), null, now)
                    .flatMap(done -> repository.findById(grant.id()))
                    .flatMap(revoked -> changed(revoked).then(alert(revoked, false, null)).thenReturn(revoked));
        });
    }

    @Override
    public Mono<PlatformRoleGrant> find(UUID grantId) {
        return repository.findById(grantId).filter(g -> TNT_ADMIN.equals(g.roleCode()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Platform admin grant not found: " + grantId)));
    }

    @Override
    public Flux<PlatformRoleGrant> list(boolean includeRevoked) {
        return repository.findAll(TNT_ADMIN, includeRevoked);
    }

    @Override
    public Mono<ForeignTenantPreview> previewForeignTenantRevocation(Caller caller) {
        return requireSystemOwner(caller).then(foreignGrants().collectList()).map(grants -> {
            Instant expiresAt = clock.instant().plus(CONFIRMATION_TTL);
            return new ForeignTenantPreview(grants, token(grants, expiresAt), expiresAt);
        });
    }

    @Override
    public Mono<ForeignTenantRevocation> revokeForeignTenant(Caller caller, String confirmationToken, String reason) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required"));
        }
        return requireSystemOwner(caller).then(Mono.defer(() -> {
            Instant expiresAt = expiryOf(confirmationToken);
            if (!clock.instant().isBefore(expiresAt)) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The confirmation token expired: preview again"));
            }
            return foreignGrants().collectList().flatMap(grants -> {
                if (!token(grants, expiresAt).equals(confirmationToken)) {
                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                            "The list changed since the preview: preview again"));
                }
                UUID batchId = UUID.randomUUID();
                Instant now = clock.instant();
                return Flux.fromIterable(grants)
                        .concatMap(g -> repository.revoke(g.id(), caller.userId(), reason.trim(), batchId, now)
                                .filter(Boolean::booleanValue)
                                .flatMap(done -> repository.findById(g.id())))
                        .collectList()
                        .as(transactions::transactional)
                        .flatMap(revoked -> Flux.fromIterable(revoked)
                                .concatMap(r -> changed(r).then(alert(r, false, caller.userId())))
                                .then(Mono.just(new ForeignTenantRevocation(batchId, revoked))));
            });
        }));
    }

    // ── rules ────────────────────────────────────────────────────────────

    private Mono<Void> requireAdmin(Caller caller) {
        if (caller == null || caller.userId() == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        }
        if (caller.systemTenantOwner()) {
            return Mono.empty();
        }
        return isPlatformAdmin(caller.userId()).flatMap(admin -> admin ? Mono.<Void>empty()
                : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "TNT_ADMIN required")));
    }

    /** D-166: the token's owner rule, or the protected SYSTEM_OWNER grant held from any tenant. */
    private Mono<Void> requireSystemOwner(Caller caller) {
        if (caller == null || caller.userId() == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        }
        if (caller.systemTenantOwner()) {
            return ensureSystemOwner(caller.userId()).then();
        }
        return repository.findActive(TNT_ADMIN, caller.userId())
                .filter(g -> g.protectedFor(systemTenantId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Reserved to the owner of the system tenant")))
                .then();
    }

    private Flux<PlatformRoleGrant> foreignGrants() {
        return repository.findActiveFromOtherTenants(TNT_ADMIN, systemTenantId)
                .filter(g -> !g.protectedFor(systemTenantId))
                .sort(Comparator.comparing(PlatformRoleGrant::id));
    }

    private Mono<Void> changed(PlatformRoleGrant grant) {
        cache.invalidate(grant.userId());
        try {
            changeNotifier.notifyChanged(systemTenantId, grant.userId());
        } catch (RuntimeException e) {
            log.warn("Platform admin change of {} not broadcast: {}", grant.userId(), e.getMessage());
        }
        return Mono.empty();
    }

    private Mono<Void> alert(PlatformRoleGrant grant, boolean granted, UUID knownOwner) {
        if (alerts.isEmpty()) {
            return Mono.empty();
        }
        Mono<UUID> owner = knownOwner != null ? Mono.just(knownOwner)
                : repository.findAll(TNT_ADMIN, false).filter(g -> g.protectedFor(systemTenantId))
                        .next().map(PlatformRoleGrant::userId);
        return owner.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                .flatMap(o -> Flux.fromIterable(alerts)
                        .concatMap(a -> (granted ? a.granted(grant, o.orElse(null)) : a.revoked(grant, o.orElse(null)))
                                .onErrorResume(e -> {
                                    log.warn("Platform admin alert {} for grant {} failed: {}",
                                            a.getClass().getSimpleName(), grant.id(), e.getMessage());
                                    return Mono.empty();
                                }))
                        .then())
                .onErrorResume(e -> Mono.empty());
    }

    // ── confirmation token (a guard against mistakes, not an authentication) ─

    private String token(List<PlatformRoleGrant> grants, Instant expiresAt) {
        String ids = grants.stream().map(g -> g.id().toString()).sorted().collect(Collectors.joining(","));
        String material = systemTenantId + "|" + expiresAt.getEpochSecond() + "|" + ids;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return TOKEN_VERSION + "." + expiresAt.getEpochSecond() + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Instant expiryOf(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3 || !TOKEN_VERSION.equals(parts[0])) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid confirmation token");
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid confirmation token");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
