package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.CreateFreelancerProfileCommand;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.actor.domain.model.ServiceZoneId;
import com.yowyob.tiibntick.core.roles.application.port.in.AssignTntRoleUseCase;
import com.yowyob.tiibntick.core.roles.application.port.in.TntRoleAssignmentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the FREELANCER role assignment behaviour added in Lot C-16.
 *
 * <p>Observable assertions: whether the profile is returned and whether
 * {@link AssignTntRoleUseCase#assignRole} is called with the expected arguments.
 * DB-level assertions (presence in {@code tnt_user_role_assignments}) require
 * integration tests with a real PostgreSQL container — deferred to the E2E run.
 */
@ExtendWith(MockitoExtension.class)
class FreelancerServiceRoleAssignmentTest {

    @Mock private IFreelancerRepository freelancerRepository;
    @Mock private IActorEventPublisher eventPublisher;
    @Mock private IKernelActorPort kernelActorPort;
    @Mock private AssignTntRoleUseCase assignRoleUseCase;

    private FreelancerService service;

    private UUID tenantId;
    private UUID actorId;
    private FreelancerProfile profile;
    private TntRoleAssignmentResult assignmentResult;

    @BeforeEach
    void setUp() {
        service = new FreelancerService(freelancerRepository, eventPublisher, kernelActorPort, assignRoleUseCase);
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        profile = FreelancerProfile.create(tenantId, actorId,
                List.of(ServiceZoneId.of(UUID.randomUUID())), List.of());
        assignmentResult = new TntRoleAssignmentResult(UUID.randomUUID(), actorId, "FREELANCER", "TENANT", tenantId);
    }

    // ── T1 : new profile → assignRole called with FREELANCER + profile returned ──

    @Test
    @DisplayName("T1 — creating a new profile triggers FREELANCER role assignment and returns the profile")
    void newProfile_triggersFreelancerRoleAssignment_andReturnsProfile() {
        when(freelancerRepository.existsByActorId(tenantId, actorId)).thenReturn(Mono.just(false));
        when(kernelActorPort.exists(actorId)).thenReturn(Mono.just(true));
        when(freelancerRepository.save(any())).thenReturn(Mono.just(profile));
        when(eventPublisher.publishActorStatusChanged(any())).thenReturn(Mono.empty());
        when(assignRoleUseCase.assignRole(tenantId, actorId, "FREELANCER", tenantId))
                .thenReturn(Mono.just(assignmentResult));

        StepVerifier.create(service.createFreelancerProfile(
                        new CreateFreelancerProfileCommand(tenantId, actorId, List.of(), List.of(), null)))
                .assertNext(returned -> assertThat(returned.actorId()).isEqualTo(actorId))
                .verifyComplete();

        ArgumentCaptor<UUID> tenantCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> userCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> scopeCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(assignRoleUseCase).assignRole(
                tenantCaptor.capture(), userCaptor.capture(), codeCaptor.capture(), scopeCaptor.capture());
        assertThat(codeCaptor.getValue()).isEqualTo("FREELANCER");
        assertThat(userCaptor.getValue()).isEqualTo(actorId);
        assertThat(tenantCaptor.getValue()).isEqualTo(tenantId);
        assertThat(scopeCaptor.getValue()).isEqualTo(tenantId);
    }

    // ── T2 : exists == true branch → assignRole also called (idempotence path) ──

    @Test
    @DisplayName("T2 — fetching an existing profile also triggers FREELANCER role assignment (idempotence path)")
    void existingProfile_triggersFreelancerRoleAssignment_andReturnsProfile() {
        when(freelancerRepository.existsByActorId(tenantId, actorId)).thenReturn(Mono.just(true));
        when(freelancerRepository.findByActorId(tenantId, actorId)).thenReturn(Mono.just(profile));
        when(assignRoleUseCase.assignRole(eq(tenantId), eq(actorId), eq("FREELANCER"), eq(tenantId)))
                .thenReturn(Mono.just(assignmentResult));

        StepVerifier.create(service.createFreelancerProfile(
                        new CreateFreelancerProfileCommand(tenantId, actorId, List.of(), List.of(), null)))
                .assertNext(returned -> assertThat(returned.actorId()).isEqualTo(actorId))
                .verifyComplete();

        verify(assignRoleUseCase, times(1)).assignRole(tenantId, actorId, "FREELANCER", tenantId);
    }

    // ── T3 : profile existed before lot (exists == true, simulate re-call) ──────

    @Test
    @DisplayName("T3 — profile inserted before Lot C-16 gets the role on the next createFreelancerProfile call")
    void profileInsertedBeforeLot_getsRoleOnNextCall() {
        // Same as T2: the exists==true branch covers pre-existing profiles.
        // Observable: assignRole is called and the profile is returned.
        when(freelancerRepository.existsByActorId(tenantId, actorId)).thenReturn(Mono.just(true));
        when(freelancerRepository.findByActorId(tenantId, actorId)).thenReturn(Mono.just(profile));
        when(assignRoleUseCase.assignRole(tenantId, actorId, "FREELANCER", tenantId))
                .thenReturn(Mono.just(assignmentResult));

        StepVerifier.create(service.createFreelancerProfile(
                        new CreateFreelancerProfileCommand(tenantId, actorId, List.of(), List.of(), null)))
                .assertNext(returned -> assertThat(returned).isNotNull())
                .verifyComplete();

        verify(assignRoleUseCase, atLeastOnce()).assignRole(tenantId, actorId, "FREELANCER", tenantId);
    }

    // ── T4 : assignRole fails → profile still returned (best-effort) ─────────

    @Test
    @DisplayName("T4 — assignRole failure does not fail createFreelancerProfile (best-effort)")
    void assignRoleFailure_doesNotFailProfileCreation() {
        when(freelancerRepository.existsByActorId(tenantId, actorId)).thenReturn(Mono.just(false));
        when(kernelActorPort.exists(actorId)).thenReturn(Mono.just(true));
        when(freelancerRepository.save(any())).thenReturn(Mono.just(profile));
        when(eventPublisher.publishActorStatusChanged(any())).thenReturn(Mono.empty());
        when(assignRoleUseCase.assignRole(any(), any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("role-service unavailable")));

        StepVerifier.create(service.createFreelancerProfile(
                        new CreateFreelancerProfileCommand(tenantId, actorId, List.of(), List.of(), null)))
                .assertNext(returned -> assertThat(returned.actorId()).isEqualTo(actorId))
                .verifyComplete();
    }

    // ── T4b : assignRole fails on exists==true path → profile still returned ──

    @Test
    @DisplayName("T4b — assignRole failure on existing-profile path does not fail createFreelancerProfile")
    void assignRoleFailure_existingProfile_doesNotFailProfileCreation() {
        when(freelancerRepository.existsByActorId(tenantId, actorId)).thenReturn(Mono.just(true));
        when(freelancerRepository.findByActorId(tenantId, actorId)).thenReturn(Mono.just(profile));
        when(assignRoleUseCase.assignRole(any(), any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("duplicate key — assignment already exists")));

        StepVerifier.create(service.createFreelancerProfile(
                        new CreateFreelancerProfileCommand(tenantId, actorId, List.of(), List.of(), null)))
                .assertNext(returned -> assertThat(returned.actorId()).isEqualTo(actorId))
                .verifyComplete();
    }
}
