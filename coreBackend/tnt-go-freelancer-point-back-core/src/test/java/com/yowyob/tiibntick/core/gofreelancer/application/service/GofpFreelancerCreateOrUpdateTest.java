package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that a second {@code createOrUpdate} on the same {@code coreFreelancerId}
 * issues an UPDATE (isNew=false) instead of a duplicate INSERT (isNew=true → 409).
 */
@ExtendWith(MockitoExtension.class)
class GofpFreelancerCreateOrUpdateTest {

    @Mock private GofpFreelancerRepository freelancerRepository;
    @Mock private GofpUserRepository userRepository;
    @Mock private com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository deliveryPersons;
    @Mock private TenantContextHolder tenantContextHolder;

    private GofpFreelancerService service;

    @BeforeEach
    void setUp() {
        service = new GofpFreelancerService(freelancerRepository, userRepository, deliveryPersons, tenantContextHolder);
    }

    @Test
    void createOrUpdate_whenRecordExists_savesWithIsNewFalse() {
        UUID coreFreelancerId = UUID.randomUUID();
        UUID existingPk = UUID.randomUUID();

        GofpFreelancer existing = new GofpFreelancer();
        existing.setId(existingPk);
        existing.setCoreFreelancerId(coreFreelancerId);
        existing.markNotNew();

        GofpFreelancer incoming = new GofpFreelancer();
        incoming.setCoreFreelancerId(coreFreelancerId);
        assertThat(incoming.isNew()).as("incoming entity from @RequestBody is new").isTrue();

        when(freelancerRepository.findByCoreFreelancerId(coreFreelancerId))
                .thenReturn(Mono.just(existing));
        when(freelancerRepository.save(any(GofpFreelancer.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        // coreUserId not set → hydrate() short-circuits, no userRepository call needed

        StepVerifier.create(service.createOrUpdate(incoming))
                .expectNextMatches(f -> existingPk.equals(f.getId()))
                .verifyComplete();

        ArgumentCaptor<GofpFreelancer> captor = ArgumentCaptor.forClass(GofpFreelancer.class);
        verify(freelancerRepository).save(captor.capture());
        assertThat(captor.getValue().isNew())
                .as("entity passed to save() must have isNew=false (UPDATE, not INSERT)")
                .isFalse();
        assertThat(captor.getValue().getId()).isEqualTo(existingPk);
    }

    private static final UUID TENANT = UUID.randomUUID();

    /**
     * New freelancer (lot L3, C1): the row id is coreFreelancerId and a delivery person exists at the same id,
     * PENDING, so that its status is read back and the vetting transitions find it.
     */
    @Test
    void createOrUpdate_newRecord_takesCoreFreelancerIdAndCreatesTheDeliveryPerson() {
        service.setAutoApprove(false); // manual vetting: PENDING
        UUID profileId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        GofpFreelancer incoming = new GofpFreelancer();
        incoming.setId(UUID.randomUUID()); // ignored
        incoming.setCoreFreelancerId(profileId);
        incoming.setCoreUserId(actor);
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(freelancerRepository.findByCoreFreelancerId(profileId)).thenReturn(Mono.empty());
        when(deliveryPersons.findById(TENANT, profileId)).thenReturn(Mono.empty());
        when(deliveryPersons.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(freelancerRepository.save(any(GofpFreelancer.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.findByCoreUserId(actor)).thenReturn(Mono.empty());

        GofpFreelancer saved = service.createOrUpdate(incoming).block();

        assertThat(saved.getId()).isEqualTo(profileId);
        var dp = ArgumentCaptor.forClass(com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson.class);
        verify(deliveryPersons).save(dp.capture());
        assertThat(dp.getValue().getId()).isEqualTo(profileId);
        assertThat(dp.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(dp.getValue().getActorId()).isEqualTo(actor);
        assertThat(dp.getValue().getStatus())
                .isEqualTo(com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryPersonStatus.PENDING);
    }

    @Test
    void createOrUpdate_newRecord_keepsAnExistingDeliveryPerson() {
        UUID profileId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        GofpFreelancer incoming = new GofpFreelancer();
        incoming.setCoreFreelancerId(profileId);
        incoming.setCoreUserId(actor);
        var existing = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson
                .projection(profileId, TENANT, actor, 100);
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(freelancerRepository.findByCoreFreelancerId(profileId)).thenReturn(Mono.empty());
        when(deliveryPersons.findById(TENANT, profileId)).thenReturn(Mono.just(existing));
        when(freelancerRepository.save(any(GofpFreelancer.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.findByCoreUserId(actor)).thenReturn(Mono.empty());

        StepVerifier.create(service.createOrUpdate(incoming))
                .expectNextMatches(f -> profileId.equals(f.getId()))
                .verifyComplete();
        verify(deliveryPersons, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createOrUpdate_newRecord_withoutActor_isRejected() {
        UUID profileId = UUID.randomUUID();
        GofpFreelancer incoming = new GofpFreelancer();
        incoming.setCoreFreelancerId(profileId);
        when(freelancerRepository.findByCoreFreelancerId(profileId)).thenReturn(Mono.empty());

        StepVerifier.create(service.createOrUpdate(incoming)).expectError(IllegalArgumentException.class).verify();
        verify(freelancerRepository, org.mockito.Mockito.never()).save(any());
    }

    /** D-181: auto-approval on (default) — the new courier's delivery person is created APPROVED, never by the body. */
    @Test
    void createOrUpdate_newRecord_autoApproved_createsTheDeliveryPersonApproved() {
        UUID profileId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        GofpFreelancer incoming = new GofpFreelancer();
        incoming.setCoreFreelancerId(profileId);
        incoming.setCoreUserId(actor);
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(freelancerRepository.findByCoreFreelancerId(profileId)).thenReturn(Mono.empty());
        when(deliveryPersons.findById(TENANT, profileId)).thenReturn(Mono.empty());
        when(deliveryPersons.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(freelancerRepository.save(any(GofpFreelancer.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.findByCoreUserId(actor)).thenReturn(Mono.empty());

        service.createOrUpdate(incoming).block();

        var dp = ArgumentCaptor.forClass(com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson.class);
        verify(deliveryPersons).save(dp.capture());
        assertThat(dp.getValue().getStatus())
                .isEqualTo(com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryPersonStatus.APPROVED);
        assertThat(dp.getValue().getTenantId()).isEqualTo(TENANT);
    }

    /** D-181: an existing delivery person is never re-approved by a profile update or creation retry. */
    @Test
    void createOrUpdate_existingDeliveryPerson_keepsItsStatus() {
        UUID profileId = UUID.randomUUID();
        GofpFreelancer incoming = new GofpFreelancer();
        incoming.setCoreFreelancerId(profileId);
        incoming.setCoreUserId(UUID.randomUUID());
        var suspended = com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson.builder()
                .id(profileId).tenantId(TENANT)
                .status(com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryPersonStatus.SUSPENDED).build();
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(freelancerRepository.findByCoreFreelancerId(profileId)).thenReturn(Mono.empty());
        when(deliveryPersons.findById(TENANT, profileId)).thenReturn(Mono.just(suspended));
        when(freelancerRepository.save(any(GofpFreelancer.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.findByCoreUserId(any())).thenReturn(Mono.empty());

        service.createOrUpdate(incoming).block();

        verify(deliveryPersons, org.mockito.Mockito.never()).save(any());
    }
}
