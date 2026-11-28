package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.in.ManagePlatformAdminsUseCase;
import com.yowyob.tiibntick.core.roles.application.port.out.IPermissionChangeNotifier;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformAdminAuthorities;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformGrantKind;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Decision D-171: the former roles endpoint keeps its contract and sends TNT_ADMIN to the registry. */
class TntAdminThroughRolesApiTest {

    static final UUID SYSTEM = com.yowyob.tiibntick.common.tenant.SystemTenant.id();
    static final UUID CALLER = UUID.randomUUID();
    static final UUID CALLER_TENANT = UUID.randomUUID();
    static final UUID TARGET = UUID.randomUUID();

    final ManagePlatformAdminsUseCase admins = mock(ManagePlatformAdminsUseCase.class);
    final UserRoleAssignmentRepository assignments = mock(UserRoleAssignmentRepository.class);
    final RoleSyncOutboxRepository outbox = mock(RoleSyncOutboxRepository.class);

    static UsernamePasswordAuthenticationToken caller(boolean owner) {
        var list = new java.util.ArrayList<SimpleGrantedAuthority>(List.of(
                new SimpleGrantedAuthority("ROLE_TNT_ADMIN"), new SimpleGrantedAuthority("TENANT_" + CALLER_TENANT)));
        if (owner) list.add(new SimpleGrantedAuthority(PlatformAdminAuthorities.SYSTEM_TENANT_OWNER_MARKER));
        return new UsernamePasswordAuthenticationToken(CALLER.toString(), "n/a", list);
    }

    @Test
    void assigningTntAdminGrantsInTheRegistryWithTheCallersIdentityAndTenant() {
        PlatformRoleGrant grant = PlatformRoleGrant.newGrant(TARGET, PlatformGrantKind.ASSIGNED, CALLER,
                CALLER_TENANT, SYSTEM, Instant.now());
        when(admins.grant(any(), eq(TARGET))).thenReturn(Mono.just(grant));
        TntRoleAssignmentService service = new TntRoleAssignmentService(mock(RoleRepository.class), assignments,
                outbox, mock(TransactionalOperator.class), new com.fasterxml.jackson.databind.ObjectMapper(), SYSTEM,
                mock(IPermissionChangeNotifier.class));
        service.setPlatformAdmins(admins);

        StepVerifier.create(service.assignRole(UUID.randomUUID(), TARGET, "TNT_ADMIN", null)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(caller(false))))
                .assertNext(r -> {
                    assertThat(r.assignmentId()).isEqualTo(grant.id());
                    assertThat(r.roleCode()).isEqualTo("TNT_ADMIN");
                })
                .verifyComplete();
        verify(admins).grant(eq(new ManagePlatformAdminsUseCase.Caller(CALLER, CALLER_TENANT, false)), eq(TARGET));
        verifyNoInteractions(assignments, outbox);
    }

    @Test
    void revokingAnIdOfTheRegistryGoesThroughItsRules() {
        UUID grantId = UUID.randomUUID();
        PlatformRoleGrant grant = PlatformRoleGrant.newGrant(TARGET, PlatformGrantKind.ASSIGNED, CALLER,
                CALLER_TENANT, SYSTEM, Instant.now());
        when(admins.find(grantId)).thenReturn(Mono.just(grant));
        when(admins.revoke(any(), eq(grantId), any())).thenReturn(Mono.just(grant));
        TntRoleRevocationService service = new TntRoleRevocationService(assignments, outbox,
                mock(TransactionalOperator.class), new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(IPermissionChangeNotifier.class));
        service.setPlatformAdmins(admins);

        StepVerifier.create(service.revokeAssignment(UUID.randomUUID(), grantId)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(caller(true))))
                .verifyComplete();
        verify(admins).revoke(eq(new ManagePlatformAdminsUseCase.Caller(CALLER, CALLER_TENANT, true)), eq(grantId),
                eq(TntRoleRevocationService.REVOKED_THROUGH_ROLES_API));
        verifyNoInteractions(assignments, outbox);
    }
}
