package com.yowyob.tiibntick.core.roles.application.port.out;

import com.yowyob.tiibntick.core.roles.domain.model.Role;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Secondary (outbound) port for {@link Role} persistence.
 *
 * <p>Local replacement for the Kernel's {@code RoleRepository} port — TiiBnTick no
 * longer shares Kernel Spring beans/types (see root {@code CLAUDE.md}: Kernel is
 * HTTP-only). Default implementation: {@link
 * com.yowyob.tiibntick.core.roles.adapter.out.persistence.InMemoryRoleRepository}.
 *
 * @author MANFOUO Braun
 */
public interface RoleRepository {

    Mono<Boolean> existsByCode(UUID tenantId, String code);

    Mono<Role> findById(UUID tenantId, UUID roleId);

    Flux<Role> findByTenantId(UUID tenantId);

    Mono<Role> save(Role role);

    Mono<Void> deleteById(UUID tenantId, UUID roleId);
}
