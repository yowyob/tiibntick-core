package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.IPermissionChangeNotifier;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleAssignmentPort;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncStatus;
import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The immediate Kernel push of a self-service role grant: it must happen with the caller's
 * identity (the scheduled outbox worker has none), must never fail the grant, and must heal a
 * grant that exists locally but was never synced.
 */
@ExtendWith(MockitoExtension.class)
class TntRoleAssignmentServiceKernelPushTest {

    private static final UUID SYSTEM_TENANT = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleAssignmentRepository assignmentRepository;
    @Mock private RoleSyncOutboxRepository outboxRepository;
    @Mock private TransactionalOperator transactionalOperator;
    @Mock private IPermissionChangeNotifier notifier;
    @Mock private ITntRoleAssignmentPort kernelPort;

    private TntRoleAssignmentService service;
    private Role clientRole;

    private static UsernamePasswordAuthenticationToken caller() {
        return new UsernamePasswordAuthenticationToken(USER.toString(), "n/a", List.of());
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new TntRoleAssignmentService(roleRepository, assignmentRepository, outboxRepository,
                transactionalOperator, new ObjectMapper(), SYSTEM_TENANT, notifier, kernelPort,
                new TntRoleDefinitionRegistry());
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(assignmentRepository.findByTenantIdAndUserId(any(), any())).thenReturn(Flux.empty());
        clientRole = new Role(UUID.randomUUID(), SYSTEM_TENANT, "CLIENT", "Client", RoleScopeType.TENANT,
                Set.of("wallet:read"), false);
        lenient().when(roleRepository.findByTenantId(SYSTEM_TENANT)).thenReturn(Flux.just(clientRole));
        lenient().when(assignmentRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(outboxRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    @DisplayName("with a caller identity the grant is pushed to the Kernel and the outbox entry closed")
    void pushesImmediatelyAndClosesTheOutboxEntry() {
        UUID kernelAssignment = UUID.randomUUID();
        when(kernelPort.assignRoleInCallerTenant(eq(USER), any(TntRoleDefinition.class), eq("TENANT"), eq(TENANT)))
                .thenReturn(Mono.just(kernelAssignment));
        when(assignmentRepository.markKernelAssignmentId(eq(TENANT), any(), eq(kernelAssignment)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.assignRole(TENANT, USER, "CLIENT", TENANT)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(caller())))
                .assertNext(r -> assertThat(r.roleCode()).isEqualTo("CLIENT"))
                .verifyComplete();

        ArgumentCaptor<RoleSyncOutboxEntry> saved = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(saved.getAllValues().size() - 1).status())
                .isEqualTo(RoleSyncStatus.PROVISIONED);
        verify(assignmentRepository).markKernelAssignmentId(eq(TENANT), any(), eq(kernelAssignment));
    }

    @Test
    @DisplayName("a Kernel refusal never fails the grant: the entry stays PENDING for the worker")
    void kernelRefusalKeepsEntryPendingAndGrantSucceeds() {
        when(kernelPort.assignRoleInCallerTenant(any(), any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("403 from Kernel")));

        StepVerifier.create(service.assignRole(TENANT, USER, "CLIENT", TENANT)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(caller())))
                .assertNext(r -> assertThat(r.roleCode()).isEqualTo("CLIENT"))
                .verifyComplete();

        ArgumentCaptor<RoleSyncOutboxEntry> saved = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(RoleSyncStatus.PENDING);
        verify(assignmentRepository, never()).markKernelAssignmentId(any(), any(), any());
    }

    @Test
    @DisplayName("without any caller identity the Kernel is not called at all")
    void noCallerIdentityMeansNoKernelCall() {
        StepVerifier.create(service.assignRole(TENANT, USER, "CLIENT", TENANT))
                .assertNext(r -> assertThat(r.roleCode()).isEqualTo("CLIENT"))
                .verifyComplete();

        verify(kernelPort, never()).assignRoleInCallerTenant(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an assignment that exists locally but was never synced is pushed on the next grant")
    void healsALocalGrantThatNeverReachedTheKernel() {
        UserRoleAssignment existing = UserRoleAssignment.assign(TENANT, USER, clientRole.id(),
                RoleScopeType.TENANT, TENANT);
        when(assignmentRepository.findByTenantIdAndUserId(TENANT, USER)).thenReturn(Flux.just(existing));
        when(assignmentRepository.findKernelAssignmentId(TENANT, existing.id())).thenReturn(Mono.empty());
        UUID kernelAssignment = UUID.randomUUID();
        when(kernelPort.assignRoleInCallerTenant(eq(USER), any(TntRoleDefinition.class), eq("TENANT"), eq(TENANT)))
                .thenReturn(Mono.just(kernelAssignment));
        when(assignmentRepository.markKernelAssignmentId(TENANT, existing.id(), kernelAssignment))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.assignRole(TENANT, USER, "CLIENT", TENANT)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(caller())))
                .assertNext(r -> assertThat(r.assignmentId()).isEqualTo(existing.id()))
                .verifyComplete();

        verify(assignmentRepository, never()).save(any());
        verify(assignmentRepository).markKernelAssignmentId(TENANT, existing.id(), kernelAssignment);
    }
}
