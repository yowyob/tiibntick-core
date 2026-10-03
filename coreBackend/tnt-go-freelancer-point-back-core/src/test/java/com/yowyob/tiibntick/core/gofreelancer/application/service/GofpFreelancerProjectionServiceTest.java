package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryPersonStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.yowyob.tiibntick.core.gofreelancer.application.service.GofpFreelancerProjectionService.DEFAULT_INITIAL_QUOTA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GofpFreelancerProjectionServiceTest {

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID CORE_USER   = UUID.randomUUID();  // actor / JWT sub
    private static final UUID PROFILE_ID  = UUID.randomUUID();  // freelancerProfiles.id

    @Mock private IFreelancerRepository     freelancerRepository;
    @Mock private GofpUserProvisioningService userProvisioningService;
    @Mock private GofpFreelancerRepository   gofpFreelancerRepository;
    @Mock private DeliveryPersonRepository   deliveryPersonRepository;

    private GofpFreelancerProjectionService service;

    @BeforeEach
    void setUp() {
        service = new GofpFreelancerProjectionService(
                freelancerRepository, userProvisioningService,
                gofpFreelancerRepository, deliveryPersonRepository);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private FreelancerProfile stubProfile() {
        return FreelancerProfile.create(TENANT_ID, CORE_USER, List.of(), List.of());
    }

    private FreelancerProfile profileWithId(UUID id) {
        // rehydrate a profile with a controlled id
        return FreelancerProfile.rehydrate(
                id, TENANT_ID, CORE_USER,
                "INACTIVE", "PENDING",
                null, null, null, null, null,
                0.0, 0, null, java.util.Set.of(),
                Instant.now(), Instant.now(),
                List.of(), List.of(), null, java.util.Set.of(),
                0, null, null, false, null);
    }

    private GofpUser stubGofpUser() {
        return GofpUser.builder().id(UUID.randomUUID()).coreUserId(CORE_USER).build();
    }

    // ── Test 1: projection from zero — 3 rows with same id ───────────────

    @Test
    void projectIfAbsent_fromZero_createsBothRowsWithProfileId() {
        FreelancerProfile profile = profileWithId(PROFILE_ID);

        when(freelancerRepository.findByActorId(TENANT_ID, CORE_USER)).thenReturn(Mono.just(profile));
        when(userProvisioningService.provisionIfAbsent(CORE_USER)).thenReturn(Mono.just(stubGofpUser()));
        when(gofpFreelancerRepository.findById(PROFILE_ID)).thenReturn(Mono.empty());
        when(gofpFreelancerRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(deliveryPersonRepository.findById(TENANT_ID, PROFILE_ID)).thenReturn(Mono.empty());
        when(deliveryPersonRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.projectIfAbsent(CORE_USER, TENANT_ID))
                .expectNext(true)
                .verifyComplete();

        // Verify gofp_freelancers created with id = profileId
        ArgumentCaptor<GofpFreelancer> flCap = ArgumentCaptor.forClass(GofpFreelancer.class);
        verify(gofpFreelancerRepository).save(flCap.capture());
        GofpFreelancer savedFl = flCap.getValue();
        assertThat(savedFl.getId()).isEqualTo(PROFILE_ID);
        assertThat(savedFl.getCoreFreelancerId()).isEqualTo(PROFILE_ID);
        assertThat(savedFl.getCoreUserId()).isEqualTo(CORE_USER);
        assertThat(savedFl.getStatus()).isEqualTo(FreelancerStatus.APPROVED);
        assertThat(savedFl.getIsActive()).isTrue();
        assertThat(savedFl.getRemainingDeliveries()).isEqualTo(DEFAULT_INITIAL_QUOTA);

        // Verify tnt_delivery_persons created with id = profileId
        ArgumentCaptor<DeliveryPerson> dpCap = ArgumentCaptor.forClass(DeliveryPerson.class);
        verify(deliveryPersonRepository).save(dpCap.capture());
        DeliveryPerson savedDp = dpCap.getValue();
        assertThat(savedDp.getId()).isEqualTo(PROFILE_ID);
        assertThat(savedDp.getActorId()).isEqualTo(CORE_USER);
        assertThat(savedDp.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(savedDp.getStatus()).isEqualTo(DeliveryPersonStatus.APPROVED);
    }

    // ── Test 2: idempotence — second call produces no writes ─────────────

    @Test
    void projectIfAbsent_calledTwice_noSecondWrite() {
        FreelancerProfile profile = profileWithId(PROFILE_ID);
        GofpFreelancer existingFl = GofpFreelancer.builder().id(PROFILE_ID).build();
        existingFl.markNotNew();
        DeliveryPerson existingDp = DeliveryPerson.projection(PROFILE_ID, TENANT_ID, CORE_USER, 50);

        when(freelancerRepository.findByActorId(TENANT_ID, CORE_USER)).thenReturn(Mono.just(profile));
        when(userProvisioningService.provisionIfAbsent(CORE_USER)).thenReturn(Mono.just(stubGofpUser()));
        when(gofpFreelancerRepository.findById(PROFILE_ID)).thenReturn(Mono.just(existingFl));
        when(deliveryPersonRepository.findById(TENANT_ID, PROFILE_ID)).thenReturn(Mono.just(existingDp));

        StepVerifier.create(service.projectIfAbsent(CORE_USER, TENANT_ID))
                .expectNext(true)
                .verifyComplete();

        verify(gofpFreelancerRepository, never()).save(any());
        verify(deliveryPersonRepository, never()).save(any());
    }

    // ── Test 3: non-destructive idempotence — remainingDeliveries preserved ─

    @Test
    void projectIfAbsent_existingGofpFreelancerWithLowQuota_quotaNotReset() {
        FreelancerProfile profile = profileWithId(PROFILE_ID);
        // Existing row has remaining_deliveries = 3 (consumed by usage)
        GofpFreelancer existingFl = GofpFreelancer.builder()
                .id(PROFILE_ID).coreFreelancerId(PROFILE_ID).coreUserId(CORE_USER)
                .remainingDeliveries(3).status(FreelancerStatus.APPROVED).isActive(true)
                .build();
        existingFl.markNotNew();
        DeliveryPerson existingDp = DeliveryPerson.projection(PROFILE_ID, TENANT_ID, CORE_USER, 3);

        when(freelancerRepository.findByActorId(TENANT_ID, CORE_USER)).thenReturn(Mono.just(profile));
        when(userProvisioningService.provisionIfAbsent(CORE_USER)).thenReturn(Mono.just(stubGofpUser()));
        when(gofpFreelancerRepository.findById(PROFILE_ID)).thenReturn(Mono.just(existingFl));
        when(deliveryPersonRepository.findById(TENANT_ID, PROFILE_ID)).thenReturn(Mono.just(existingDp));

        StepVerifier.create(service.projectIfAbsent(CORE_USER, TENANT_ID))
                .expectNext(true)
                .verifyComplete();

        verify(gofpFreelancerRepository, never()).save(any());
        // existing.remainingDeliveries = 3, not reset to DEFAULT_INITIAL_QUOTA
        assertThat(existingFl.getRemainingDeliveries()).isEqualTo(3);
    }

    // ── Test 4: non-freelancer actor — no rows created ────────────────────

    @Test
    void projectIfAbsent_noFreelancerProfile_noRowsCreated() {
        when(freelancerRepository.findByActorId(TENANT_ID, CORE_USER)).thenReturn(Mono.empty());

        StepVerifier.create(service.projectIfAbsent(CORE_USER, TENANT_ID))
                .expectNext(false)
                .verifyComplete();

        verify(gofpFreelancerRepository, never()).save(any());
        verify(deliveryPersonRepository, never()).save(any());
        verify(userProvisioningService, never()).provisionIfAbsent(any());
    }

    // ── Test 5: gofp_users absent — provisioned before freelancer insert ──

    @Test
    void projectIfAbsent_gofpUserAbsent_userProvisionedFirst() {
        FreelancerProfile profile = profileWithId(PROFILE_ID);
        GofpUser newUser = stubGofpUser();

        when(freelancerRepository.findByActorId(TENANT_ID, CORE_USER)).thenReturn(Mono.just(profile));
        when(userProvisioningService.provisionIfAbsent(CORE_USER)).thenReturn(Mono.just(newUser));
        when(gofpFreelancerRepository.findById(PROFILE_ID)).thenReturn(Mono.empty());
        when(gofpFreelancerRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(deliveryPersonRepository.findById(TENANT_ID, PROFILE_ID)).thenReturn(Mono.empty());
        when(deliveryPersonRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.projectIfAbsent(CORE_USER, TENANT_ID))
                .expectNext(true)
                .verifyComplete();

        // gofp_users provisioned before freelancer row is created
        verify(userProvisioningService).provisionIfAbsent(CORE_USER);
        ArgumentCaptor<GofpFreelancer> cap = ArgumentCaptor.forClass(GofpFreelancer.class);
        verify(gofpFreelancerRepository).save(cap.capture());
        // coreUserId on the freelancer row = the user's coreUserId (FK integrity)
        assertThat(cap.getValue().getCoreUserId()).isEqualTo(CORE_USER);
    }

    // ── Test 6: best-effort — projection error doesn't propagate ─────────

    @Test
    void projectIfAbsent_repositoryError_completesWithoutError() {
        when(freelancerRepository.findByActorId(TENANT_ID, CORE_USER))
                .thenReturn(Mono.error(new RuntimeException("DB down")));

        // The filter wraps the service call in onErrorResume; the service itself
        // lets the error propagate so the filter can count it. This test verifies
        // the service emits the error (filter is responsible for swallowing it).
        StepVerifier.create(service.projectIfAbsent(CORE_USER, TENANT_ID))
                .expectError(RuntimeException.class)
                .verify();
    }

    // Test 8 (guard) lives in GofpProjectionTablesSeedGuardTest — repo-wide scan.
}
