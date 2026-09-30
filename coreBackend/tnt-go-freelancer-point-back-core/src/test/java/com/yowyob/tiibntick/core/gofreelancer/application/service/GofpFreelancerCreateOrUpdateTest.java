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

    private GofpFreelancerService service;

    @BeforeEach
    void setUp() {
        service = new GofpFreelancerService(freelancerRepository, userRepository);
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
}
