package com.yowyob.tiibntick.core.roles.config;

import com.yowyob.tiibntick.core.roles.adapter.out.persistence.InMemoryRoleRepository;
import com.yowyob.tiibntick.core.roles.adapter.out.persistence.InMemoryUserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TntRolesAutoConfiguration#rbacPersistentAdapterProdGuard}
 * (Chantier D · Audit n°6 · S5): booting with the {@code prod} profile active must fail
 * fast if the in-memory RBAC fallback repositories are still the active beans — otherwise
 * production would silently run with role assignments that vanish on restart and diverge
 * across every instance in a multi-instance deployment.
 *
 * @author MANFOUO Braun
 */
@DisplayName("TntRolesAutoConfiguration — RBAC prod fail-fast guard (S5)")
class TntRolesAutoConfigurationTest {

    private final TntRolesAutoConfiguration config = new TntRolesAutoConfiguration();

    @Test
    @DisplayName("prod profile + in-memory fallback repositories still active → fails fast at startup")
    void prodProfileWithInMemoryFallbacksFailsFast() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        InitializingBean guard = config.rbacPersistentAdapterProdGuard(
                env, new InMemoryUserRoleAssignmentRepository(), new InMemoryRoleRepository());

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod")
                .hasMessageContaining("UserRoleAssignmentRepository")
                .hasMessageContaining("RoleRepository");
    }

    @Test
    @DisplayName("prod profile + a real persistent adapter supplied → starts normally")
    void prodProfileWithPersistentAdaptersStartsFine() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        UserRoleAssignmentRepository persistentAssignments =
                mockPersistentAssignmentRepository();
        RoleRepository persistentRoles = mockPersistentRoleRepository();

        InitializingBean guard = config.rbacPersistentAdapterProdGuard(
                env, persistentAssignments, persistentRoles);

        runsWithoutThrowing(guard);
    }

    @Test
    @DisplayName("non-prod profile (e.g. dev/test) + in-memory fallback → starts normally, no guard tripped")
    void nonProdProfileNeverTrips() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        InitializingBean guard = config.rbacPersistentAdapterProdGuard(
                env, new InMemoryUserRoleAssignmentRepository(), new InMemoryRoleRepository());

        // Must not throw.
        guard.afterPropertiesSet();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static void runsWithoutThrowing(InitializingBean guard) {
        try {
            guard.afterPropertiesSet();
        } catch (Exception e) {
            throw new AssertionError("Expected no exception, but guard threw: " + e, e);
        }
    }

    private static UserRoleAssignmentRepository mockPersistentAssignmentRepository() {
        return new UserRoleAssignmentRepository() {
            @Override
            public reactor.core.publisher.Mono<com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment> save(
                    com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment assignment) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public reactor.core.publisher.Flux<com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment> findByTenantIdAndUserId(
                    java.util.UUID tenantId, java.util.UUID userId) {
                return reactor.core.publisher.Flux.empty();
            }

            @Override
            public reactor.core.publisher.Mono<com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment> findById(
                    java.util.UUID tenantId, java.util.UUID assignmentId) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public reactor.core.publisher.Flux<com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment> findByTenantIdAndRoleId(
                    java.util.UUID tenantId, java.util.UUID roleId) {
                return reactor.core.publisher.Flux.empty();
            }

            @Override
            public reactor.core.publisher.Mono<Void> deleteById(java.util.UUID tenantId, java.util.UUID assignmentId) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public reactor.core.publisher.Mono<Void> markKernelAssignmentId(
                    java.util.UUID tenantId, java.util.UUID assignmentId, java.util.UUID kernelAssignmentId) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public reactor.core.publisher.Mono<java.util.UUID> findKernelAssignmentId(
                    java.util.UUID tenantId, java.util.UUID assignmentId) {
                return reactor.core.publisher.Mono.empty();
            }
        };
    }

    private static RoleRepository mockPersistentRoleRepository() {
        return new RoleRepository() {
            @Override
            public reactor.core.publisher.Mono<Boolean> existsByCode(java.util.UUID tenantId, String code) {
                return reactor.core.publisher.Mono.just(false);
            }

            @Override
            public reactor.core.publisher.Mono<com.yowyob.tiibntick.core.roles.domain.model.Role> findById(
                    java.util.UUID tenantId, java.util.UUID roleId) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public reactor.core.publisher.Flux<com.yowyob.tiibntick.core.roles.domain.model.Role> findByTenantId(
                    java.util.UUID tenantId) {
                return reactor.core.publisher.Flux.empty();
            }

            @Override
            public reactor.core.publisher.Mono<com.yowyob.tiibntick.core.roles.domain.model.Role> save(
                    com.yowyob.tiibntick.core.roles.domain.model.Role role) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public reactor.core.publisher.Mono<Void> deleteById(java.util.UUID tenantId, java.util.UUID roleId) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public reactor.core.publisher.Mono<Void> markKernelRoleId(
                    java.util.UUID tenantId, java.util.UUID roleId, java.util.UUID kernelRoleId) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public reactor.core.publisher.Mono<java.util.UUID> findKernelRoleId(
                    java.util.UUID tenantId, java.util.UUID roleId) {
                return reactor.core.publisher.Mono.empty();
            }
        };
    }
}
