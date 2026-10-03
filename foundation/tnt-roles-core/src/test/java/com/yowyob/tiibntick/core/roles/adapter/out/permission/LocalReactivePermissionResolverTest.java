package com.yowyob.tiibntick.core.roles.adapter.out.permission;

import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot C-18 regression: canonical roles are provisioned under the system tenant and assigned
 * to users of any tenant (TntRoleAssignmentService). The local resolver must find them there,
 * otherwise every FREELANCER assignment resolves to zero permissions (403 on subscribe).
 * Lot C-19.5: that fallback is limited to the canonical roles of {@link TntRoleDefinitionRegistry}.
 */
@ExtendWith(MockitoExtension.class)
class LocalReactivePermissionResolverTest {

    private static final UUID SYSTEM_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_TENANT   = UUID.fromString("dbae6615-8f7e-4ef5-9e58-23a6179acf22");
    private static final UUID USER          = UUID.randomUUID();

    @Mock private UserRoleAssignmentRepository assignmentRepository;
    @Mock private RoleRepository roleRepository;

    private LocalReactivePermissionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new LocalReactivePermissionResolver(
                assignmentRepository, roleRepository, new TntRoleDefinitionRegistry(), SYSTEM_TENANT);
    }

    @Test
    void systemTenantRoleAssignedInUserTenant_resolvesItsPermissions() {
        Role freelancer = new Role(UUID.randomUUID(), SYSTEM_TENANT, "FREELANCER", "Freelancer",
                RoleScopeType.TENANT, Set.of("announcement:respond", "announcement:elect"), false);
        UserRoleAssignment assignment = UserRoleAssignment.assign(
                USER_TENANT, USER, freelancer.id(), RoleScopeType.TENANT, USER_TENANT);

        when(assignmentRepository.findByTenantIdAndUserId(USER_TENANT, USER)).thenReturn(Flux.just(assignment));
        when(roleRepository.findById(USER_TENANT, freelancer.id())).thenReturn(Mono.empty());
        when(roleRepository.findById(SYSTEM_TENANT, freelancer.id())).thenReturn(Mono.just(freelancer));

        StepVerifier.create(resolver.resolvePermissions(USER_TENANT, USER))
                .assertNext(perms -> assertThat(perms).contains("announcement:respond", "announcement:elect"))
                .verifyComplete();
    }

    @Test
    void tenantLocalRole_takesPrecedence_noSystemLookup() {
        Role custom = new Role(UUID.randomUUID(), USER_TENANT, "CUSTOM_X", "Custom",
                RoleScopeType.TENANT, Set.of("parcel:read"), true);
        UserRoleAssignment assignment = UserRoleAssignment.assign(
                USER_TENANT, USER, custom.id(), RoleScopeType.TENANT, USER_TENANT);

        when(assignmentRepository.findByTenantIdAndUserId(USER_TENANT, USER)).thenReturn(Flux.just(assignment));
        when(roleRepository.findById(USER_TENANT, custom.id())).thenReturn(Mono.just(custom));

        StepVerifier.create(resolver.resolvePermissions(USER_TENANT, USER))
                .assertNext(perms -> assertThat(perms).containsExactly("parcel:read"))
                .verifyComplete();
        verify(roleRepository, never()).findById(SYSTEM_TENANT, custom.id());
    }

    @Test
    void nonCanonicalSystemTenantRole_notLentToOtherTenant_denyByDefault() {
        // C-19.5 : un rôle non canonique qui vit sous le tenant système ne doit pas être
        // résolu pour une assignation d'un autre tenant, même si l'assignation pointe dessus.
        Role systemCustom = new Role(UUID.randomUUID(), SYSTEM_TENANT, "SYSTEM_SUPPORT_X", "Support",
                RoleScopeType.TENANT, Set.of("*"), true);
        UserRoleAssignment assignment = UserRoleAssignment.assign(
                USER_TENANT, USER, systemCustom.id(), RoleScopeType.TENANT, USER_TENANT);

        when(assignmentRepository.findByTenantIdAndUserId(USER_TENANT, USER)).thenReturn(Flux.just(assignment));
        when(roleRepository.findById(USER_TENANT, systemCustom.id())).thenReturn(Mono.empty());
        when(roleRepository.findById(SYSTEM_TENANT, systemCustom.id())).thenReturn(Mono.just(systemCustom));

        StepVerifier.create(resolver.resolvePermissions(USER_TENANT, USER))
                .assertNext(perms -> assertThat(perms).isEmpty())
                .verifyComplete();
    }

    @Test
    void nonCanonicalRole_assignedInSystemTenantItself_stillResolves() {
        // Le filtre ne vise que le repli inter-tenant : dans le tenant système, un rôle
        // local reste résolu normalement (priorité au rôle local).
        Role systemCustom = new Role(UUID.randomUUID(), SYSTEM_TENANT, "SYSTEM_SUPPORT_X", "Support",
                RoleScopeType.TENANT, Set.of("parcel:read"), true);
        UserRoleAssignment assignment = UserRoleAssignment.assign(
                SYSTEM_TENANT, USER, systemCustom.id(), RoleScopeType.TENANT, SYSTEM_TENANT);

        when(assignmentRepository.findByTenantIdAndUserId(SYSTEM_TENANT, USER)).thenReturn(Flux.just(assignment));
        when(roleRepository.findById(SYSTEM_TENANT, systemCustom.id())).thenReturn(Mono.just(systemCustom));

        StepVerifier.create(resolver.resolvePermissions(SYSTEM_TENANT, USER))
                .assertNext(perms -> assertThat(perms).containsExactly("parcel:read"))
                .verifyComplete();
    }

    @Test
    void roleFoundNowhere_denyByDefault() {
        UUID ghost = UUID.randomUUID();
        UserRoleAssignment assignment = UserRoleAssignment.assign(
                USER_TENANT, USER, ghost, RoleScopeType.TENANT, USER_TENANT);

        when(assignmentRepository.findByTenantIdAndUserId(USER_TENANT, USER)).thenReturn(Flux.just(assignment));
        when(roleRepository.findById(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(resolver.resolvePermissions(USER_TENANT, USER))
                .assertNext(perms -> assertThat(perms).isEmpty())
                .verifyComplete();
    }
}
