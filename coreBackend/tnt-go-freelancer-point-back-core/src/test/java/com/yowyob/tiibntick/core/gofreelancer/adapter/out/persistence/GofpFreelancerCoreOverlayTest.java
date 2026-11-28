package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryPersonStatus;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.GofpFreelancerR2dbcRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Lot L3: status and rating of a freelancer are read from delivery-core and actor-core, never from the Go row. */
@ExtendWith(MockitoExtension.class)
class GofpFreelancerCoreOverlayTest {

    @Mock private GofpFreelancerR2dbcRepository r2dbc;
    @Mock private DeliveryPersonRepository deliveryPersons;
    @Mock private IFreelancerRepository profiles;
    @Mock private TenantContextHolder tenantContextHolder;

    private GofpFreelancerRepositoryAdapter adapter;
    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new GofpFreelancerRepositoryAdapter(r2dbc, deliveryPersons, profiles, tenantContextHolder);
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(TENANT));
    }

    private GofpFreelancer row(UUID id) {
        GofpFreelancer f = new GofpFreelancer();
        f.setId(id);
        f.setIsActive(true);
        return f;
    }

    @Test
    void statusAndRatingComeFromTheCore() {
        UUID id = UUID.randomUUID();
        when(r2dbc.findById(id)).thenReturn(Mono.just(row(id)));
        when(deliveryPersons.findById(TENANT, id)).thenReturn(Mono.just(DeliveryPerson.builder()
                .id(id).tenantId(TENANT).status(DeliveryPersonStatus.SUSPENDED).build()));
        FreelancerProfile profile = FreelancerProfile.create(TENANT, UUID.randomUUID(), List.of(), List.of());
        profile = profile.withRating(profile.rating().addRating(3.0));
        when(profiles.findById(TENANT, id)).thenReturn(Mono.just(profile));

        StepVerifier.create(adapter.findById(id))
                .assertNext(f -> {
                    assertThat(f.getStatus()).isEqualTo(FreelancerStatus.SUSPENDED);
                    assertThat(f.getRating()).isEqualTo(3.0);
                })
                .verifyComplete();
    }

    @Test
    void withoutDeliveryPerson_theFreelancerIsPendingAndUnrated() {
        UUID id = UUID.randomUUID();
        when(r2dbc.findById(id)).thenReturn(Mono.just(row(id)));
        when(deliveryPersons.findById(TENANT, id)).thenReturn(Mono.empty());
        when(profiles.findById(TENANT, id)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.findById(id))
                .assertNext(f -> {
                    assertThat(f.getStatus()).isEqualTo(FreelancerStatus.PENDING);
                    assertThat(f.getRating()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void activeApprovedFilter_usesTheCoreStatus() {
        UUID approved = UUID.randomUUID();
        UUID revoked = UUID.randomUUID();
        when(r2dbc.findAllByIsActive(true)).thenReturn(Flux.just(row(approved), row(revoked)));
        when(deliveryPersons.findById(TENANT, approved)).thenReturn(Mono.just(DeliveryPerson.builder()
                .id(approved).tenantId(TENANT).status(DeliveryPersonStatus.APPROVED).build()));
        when(deliveryPersons.findById(TENANT, revoked)).thenReturn(Mono.just(DeliveryPerson.builder()
                .id(revoked).tenantId(TENANT).status(DeliveryPersonStatus.REVOKED).build()));
        when(profiles.findById(eq(TENANT), any())).thenReturn(Mono.empty());

        StepVerifier.create(adapter.findAllByStatusAndIsActive(FreelancerStatus.APPROVED, true))
                .assertNext(f -> assertThat(f.getId()).isEqualTo(approved))
                .verifyComplete();
    }
}
