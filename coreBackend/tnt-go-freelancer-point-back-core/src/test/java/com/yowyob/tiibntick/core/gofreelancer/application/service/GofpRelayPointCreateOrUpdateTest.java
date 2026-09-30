package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
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
 * Proves that a second {@code createOrUpdate} on the same {@code coreRelayPointId}
 * issues an UPDATE (isNew=false) instead of a duplicate INSERT (isNew=true → 409).
 */
@ExtendWith(MockitoExtension.class)
class GofpRelayPointCreateOrUpdateTest {

    @Mock private GofpRelayPointRepository relayPointRepository;
    @Mock private GofpFreelancerRepository freelancerRepository;

    private GofpRelayPointService service;

    @BeforeEach
    void setUp() {
        service = new GofpRelayPointService(relayPointRepository, freelancerRepository);
    }

    @Test
    void createOrUpdate_whenRecordExists_savesWithIsNewFalse() {
        UUID coreRelayPointId = UUID.randomUUID();
        UUID existingPk = UUID.randomUUID();

        GofpRelayPoint existing = new GofpRelayPoint();
        existing.setId(existingPk);
        existing.setCoreRelayPointId(coreRelayPointId);
        existing.markNotNew();

        GofpRelayPoint incoming = new GofpRelayPoint();
        incoming.setCoreRelayPointId(coreRelayPointId);
        assertThat(incoming.isNew()).as("incoming entity from @RequestBody is new").isTrue();

        when(relayPointRepository.findByCoreRelayPointId(coreRelayPointId))
                .thenReturn(Mono.just(existing));
        when(relayPointRepository.save(any(GofpRelayPoint.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        // coreFreelancerId not set → hydrate() short-circuits, no freelancerRepository call needed

        StepVerifier.create(service.createOrUpdate(incoming))
                .expectNextMatches(rp -> existingPk.equals(rp.getId()))
                .verifyComplete();

        ArgumentCaptor<GofpRelayPoint> captor = ArgumentCaptor.forClass(GofpRelayPoint.class);
        verify(relayPointRepository).save(captor.capture());
        assertThat(captor.getValue().isNew())
                .as("entity passed to save() must have isNew=false (UPDATE, not INSERT)")
                .isFalse();
        assertThat(captor.getValue().getId()).isEqualTo(existingPk);
    }
}
