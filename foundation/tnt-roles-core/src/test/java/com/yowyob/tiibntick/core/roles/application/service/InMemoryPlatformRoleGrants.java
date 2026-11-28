package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.out.PlatformRoleGrantRepository;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Test double of the registry with the same rules as the table (one active grant per role and account). */
class InMemoryPlatformRoleGrants implements PlatformRoleGrantRepository {

    final Map<UUID, PlatformRoleGrant> rows = new LinkedHashMap<>();

    @Override
    public synchronized Mono<PlatformRoleGrant> findActive(String roleCode, UUID userId) {
        return Mono.justOrEmpty(rows.values().stream()
                .filter(g -> g.roleCode().equals(roleCode) && g.userId().equals(userId) && g.active()).findFirst());
    }

    @Override
    public synchronized Mono<PlatformRoleGrant> findById(UUID id) {
        return Mono.justOrEmpty(rows.get(id));
    }

    @Override
    public synchronized Flux<PlatformRoleGrant> findAll(String roleCode, boolean includeRevoked) {
        return Flux.fromIterable(rows.values().stream()
                .filter(g -> g.roleCode().equals(roleCode) && (includeRevoked || g.active()))
                .sorted(Comparator.comparing(PlatformRoleGrant::grantedAt).reversed()).toList());
    }

    @Override
    public synchronized Flux<PlatformRoleGrant> findActiveFromOtherTenants(String roleCode, UUID systemTenantId) {
        return Flux.fromIterable(rows.values().stream()
                .filter(g -> g.roleCode().equals(roleCode) && g.active() && !g.sourceTenantId().equals(systemTenantId))
                .sorted(Comparator.comparing(PlatformRoleGrant::id)).toList());
    }

    @Override
    public synchronized Mono<PlatformRoleGrant> insertIfNoActive(PlatformRoleGrant grant) {
        boolean exists = rows.values().stream().anyMatch(g -> g.roleCode().equals(grant.roleCode())
                && g.userId().equals(grant.userId()) && g.active());
        if (exists) return Mono.empty();
        rows.put(grant.id(), grant);
        return Mono.just(grant);
    }

    @Override
    public synchronized Mono<Boolean> revoke(UUID id, UUID revokedBy, String reason, UUID batchId, Instant at) {
        PlatformRoleGrant g = rows.get(id);
        if (g == null || !g.active()) return Mono.just(false);
        rows.put(id, new PlatformRoleGrant(g.id(), g.roleCode(), g.userId(), g.kind(), g.grantedBy(),
                g.sourceTenantId(), g.systemTenantAtGrant(), g.grantedAt(), at, revokedBy, reason, batchId));
        return Mono.just(true);
    }
}
