package com.yowyob.tiibntick.core.roles.adapter.out.persistence;

import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process, non-persistent {@link RoleRepository} — process-lifetime fallback used
 * when no other bean provides the port (see {@code TntRolesAutoConfiguration}).
 *
 * @author MANFOUO Braun
 */
public class InMemoryRoleRepository implements RoleRepository {

    private final Map<UUID, Role> roles = new ConcurrentHashMap<>();

    @Override
    public Mono<Boolean> existsByCode(UUID tenantId, String code) {
        return Mono.fromSupplier(() -> roles.values().stream()
                .anyMatch(r -> tenantId.equals(r.tenantId()) && r.code().equalsIgnoreCase(code)));
    }

    @Override
    public Mono<Role> findById(UUID tenantId, UUID roleId) {
        return Mono.justOrEmpty(roles.get(roleId))
                .filter(r -> tenantId.equals(r.tenantId()));
    }

    @Override
    public Flux<Role> findByTenantId(UUID tenantId) {
        return Flux.fromIterable(roles.values())
                .filter(r -> tenantId.equals(r.tenantId()));
    }

    @Override
    public Mono<Role> save(Role role) {
        return Mono.fromSupplier(() -> {
            roles.put(role.id(), role);
            return role;
        });
    }

    @Override
    public Mono<Void> deleteById(UUID tenantId, UUID roleId) {
        return Mono.fromRunnable(() -> roles.computeIfPresent(roleId,
                (id, existing) -> tenantId.equals(existing.tenantId()) ? null : existing));
    }
}
