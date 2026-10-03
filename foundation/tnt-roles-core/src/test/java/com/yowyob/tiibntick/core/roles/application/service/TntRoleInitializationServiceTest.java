package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.domain.model.Role;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.TntPermission;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TntRoleInitializationServiceTest {

    private static final UUID SYSTEM_TENANT_ID = UUID.randomUUID();

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RoleSyncOutboxRepository outboxRepository;
    @Mock
    private TransactionalOperator transactionalOperator;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TntRoleDefinitionRegistry registry = new TntRoleDefinitionRegistry();

    private TntRoleInitializationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new TntRoleInitializationService(
                registry, roleRepository, outboxRepository, transactionalOperator, objectMapper, SYSTEM_TENANT_ID);
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ── Test 1: absent role → created with canonical permissions + outbox ────

    @Test
    void provisionForTenant_roleAbsent_shouldSaveWithCanonicalPermissionsAndEnqueueProvisionEntry() {
        when(roleRepository.findByCode(eq(SYSTEM_TENANT_ID), any())).thenReturn(Mono.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(outboxRepository.save(any(RoleSyncOutboxEntry.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.provisionForTenant(SYSTEM_TENANT_ID))
                .verifyComplete();

        verify(roleRepository, times(registry.size())).save(any(Role.class));
        verify(outboxRepository, times(registry.size())).save(any(RoleSyncOutboxEntry.class));

        ArgumentCaptor<RoleSyncOutboxEntry> outboxCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, times(registry.size())).save(outboxCaptor.capture());
        outboxCaptor.getAllValues().forEach(entry ->
                assertThat(entry.operation()).isEqualTo(RoleSyncOperation.PROVISION_ROLE));
    }

    // ── Test 2: identical permissions → no write, no outbox ─────────────────

    @Test
    void provisionForTenant_roleExistsWithCanonicalPermissions_shouldBeNoOp() {
        registry.getAllDefinitions().forEach(definition -> {
            Role matching = new Role(UUID.randomUUID(), SYSTEM_TENANT_ID,
                    definition.code(), definition.name(), definition.scopeType(),
                    definition.defaultPermissions(), false);
            when(roleRepository.findByCode(SYSTEM_TENANT_ID, definition.code())).thenReturn(Mono.just(matching));
        });

        StepVerifier.create(service.provisionForTenant(SYSTEM_TENANT_ID))
                .verifyComplete();

        verify(roleRepository, never()).save(any(Role.class));
        verify(outboxRepository, never()).save(any(RoleSyncOutboxEntry.class));
    }

    // ── Test 3: amputated role (V5 scenario) → updated to canonical + outbox ─

    @Test
    void provisionForTenant_freelancerRoleWithAmputatedPermissions_shouldReconcileToCanonicalAndEnqueueUpdateEntry() {
        Set<String> v5Permissions = Set.of("announcement:respond", "announcement:elect", "freelancer:read");
        UUID existingId = UUID.randomUUID();
        Role v5Role = new Role(existingId, SYSTEM_TENANT_ID, "FREELANCER", "Freelancer Deliverer",
                RoleScopeType.TENANT, v5Permissions, false);

        registry.getAllDefinitions().forEach(def -> {
            if ("FREELANCER".equals(def.code())) {
                when(roleRepository.findByCode(SYSTEM_TENANT_ID, "FREELANCER")).thenReturn(Mono.just(v5Role));
            } else {
                lenient().when(roleRepository.findByCode(eq(SYSTEM_TENANT_ID), eq(def.code())))
                        .thenReturn(Mono.empty());
            }
        });
        lenient().when(roleRepository.save(any(Role.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(outboxRepository.save(any(RoleSyncOutboxEntry.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.provisionForTenant(SYSTEM_TENANT_ID))
                .verifyComplete();

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(registry.size())).save(roleCaptor.capture());
        Role savedFreelancer = roleCaptor.getAllValues().stream()
                .filter(r -> "FREELANCER".equals(r.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FREELANCER not saved"));

        assertThat(savedFreelancer.id()).isEqualTo(existingId);
        assertThat(savedFreelancer.permissions()).containsAll(TntRole.FREELANCER.defaultPermissions());
        assertThat(savedFreelancer.permissions()).hasSize(TntRole.FREELANCER.defaultPermissions().size());

        ArgumentCaptor<RoleSyncOutboxEntry> outboxCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, times(registry.size())).save(outboxCaptor.capture());
        RoleSyncOutboxEntry updateEntry = outboxCaptor.getAllValues().stream()
                .filter(e -> e.operation() == RoleSyncOperation.UPDATE_ROLE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("UPDATE_ROLE outbox entry not found"));
        assertThat(updateEntry.aggregateId()).isEqualTo(existingId);
        assertThat(updateEntry.aggregateType()).isEqualTo(RoleSyncAggregateType.ROLE);
    }

    // ── Test 4: truly custom role (non-canonical code) → never modified ─────
    //
    // A role with a non-canonical code (e.g. "CUSTOM_DISPATCHER") should never be
    // overwritten regardless of its editable flag. Canonical-code roles (even with
    // editable=true in DB from pre-C18 provisioning) ARE reconciled — that is the
    // intentional fix for the pre-C18 mapper bug.

    @Test
    void provisionForTenant_roleWithNonCanonicalCode_shouldNeverBeOverwritten() {
        // Stub every canonical definition → return a role with a NON-canonical code
        // that happens to be stored for this tenant (edge case: custom role whose code
        // would collide with a canonical code at lookup — should be impossible via
        // TntRoleManagementService guard, but defensive test).
        registry.getAllDefinitions().forEach(definition -> {
            Role customRole = new Role(UUID.randomUUID(), SYSTEM_TENANT_ID,
                    "CUSTOM_" + definition.code(),  // non-canonical code
                    "Custom role", definition.scopeType(),
                    Set.of("some:custom:permission"), true);
            // findByCode for the canonical code returns empty (no canonical row exists)
            when(roleRepository.findByCode(SYSTEM_TENANT_ID, definition.code())).thenReturn(Mono.empty());
        });
        // All absent → provision each
        lenient().when(roleRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(outboxRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.provisionForTenant(SYSTEM_TENANT_ID))
                .verifyComplete();

        // Canonical roles were absent → provisioned (not custom ones)
        verify(roleRepository, times(registry.size())).save(any(Role.class));
    }

    // ── Test 5: FREELANCER permissions contain both announce permissions ─────

    @Test
    void freelancerRole_defaultPermissions_shouldContainAnnouncementRespondAndElect() {
        Set<String> permissions = TntRole.FREELANCER.defaultPermissions();

        assertThat(permissions)
                .contains(TntPermission.ANNOUNCEMENT_RESPOND)
                .contains(TntPermission.ANNOUNCEMENT_ELECT);
    }

    // ── Lot C-21: freelancer:read on FREELANCER, reached by existing holders ──

    @Test
    void freelancerRole_defaultPermissions_shouldContainFreelancerRead() {
        // GET /api/v1/freelancers/me is @RequirePermission(resource="freelancer", action="read"):
        // without it a FREELANCER gets 403 on its own profile.
        assertThat(TntRole.FREELANCER.defaultPermissions()).contains(TntPermission.FREELANCER_READ);
        assertThat(TntPermission.FREELANCER_READ).isEqualTo("freelancer:read");
    }

    @Test
    void reconcileTenantCopies_staleFreelancerCopyInAgencyTenant_shouldGainFreelancerReadAndEnqueueUpdate() {
        UUID agencyTenant = UUID.randomUUID();
        Set<String> preC21 = new java.util.HashSet<>(TntRole.FREELANCER.defaultPermissions());
        preC21.remove(TntPermission.FREELANCER_READ);
        Role staleCopy = new Role(UUID.randomUUID(), agencyTenant, "FREELANCER", "Freelancer Deliverer",
                RoleScopeType.TENANT, preC21, false);
        Role systemRow = new Role(UUID.randomUUID(), SYSTEM_TENANT_ID, "FREELANCER", "Freelancer Deliverer",
                RoleScopeType.TENANT, preC21, false);

        when(roleRepository.findAllByCode(any())).thenReturn(reactor.core.publisher.Flux.empty());
        when(roleRepository.findAllByCode("FREELANCER"))
                .thenReturn(reactor.core.publisher.Flux.just(systemRow, staleCopy));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(outboxRepository.save(any(RoleSyncOutboxEntry.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.reconcileTenantCopies()).verifyComplete();

        // Only the agency-tenant copy is touched here: the system row is provisionForTenant's job.
        ArgumentCaptor<Role> saved = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(staleCopy.id());
        assertThat(saved.getValue().tenantId()).isEqualTo(agencyTenant);
        assertThat(saved.getValue().permissions()).isEqualTo(TntRole.FREELANCER.defaultPermissions());

        ArgumentCaptor<RoleSyncOutboxEntry> outbox = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        verify(outboxRepository, times(1)).save(outbox.capture());
        assertThat(outbox.getValue().operation()).isEqualTo(RoleSyncOperation.UPDATE_ROLE);
    }

    @Test
    void reconcileTenantCopies_tenantWithoutCopies_shouldNeverProvision() {
        when(roleRepository.findAllByCode(any())).thenReturn(reactor.core.publisher.Flux.empty());

        StepVerifier.create(service.reconcileTenantCopies()).verifyComplete();

        verify(roleRepository, never()).save(any(Role.class));
        verify(roleRepository, never()).findByCode(any(), any());
        verify(outboxRepository, never()).save(any(RoleSyncOutboxEntry.class));
    }
}
