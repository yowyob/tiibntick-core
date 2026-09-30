package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
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
 * Proves that a second {@code createOrUpdate} on the same {@code coreUserId}
 * issues an UPDATE (isNew=false) instead of a duplicate INSERT (isNew=true → 409).
 */
@ExtendWith(MockitoExtension.class)
class GofpUserCreateOrUpdateTest {

    @Mock private GofpUserRepository userRepository;

    private GofpUserService service;

    @BeforeEach
    void setUp() {
        service = new GofpUserService(userRepository);
    }

    @Test
    void createOrUpdate_whenRecordExists_savesWithIsNewFalse() {
        UUID coreUserId = UUID.randomUUID();
        UUID existingPk = UUID.randomUUID();

        GofpUser existing = new GofpUser();
        existing.setId(existingPk);
        existing.setCoreUserId(coreUserId);
        existing.markNotNew();

        GofpUser incoming = new GofpUser();
        incoming.setCoreUserId(coreUserId);
        assertThat(incoming.isNew()).as("incoming entity from @RequestBody is new").isTrue();

        when(userRepository.findByCoreUserId(coreUserId))
                .thenReturn(Mono.just(existing));
        when(userRepository.save(any(GofpUser.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.createOrUpdate(incoming))
                .expectNextMatches(u -> existingPk.equals(u.getId()))
                .verifyComplete();

        ArgumentCaptor<GofpUser> captor = ArgumentCaptor.forClass(GofpUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isNew())
                .as("entity passed to save() must have isNew=false (UPDATE, not INSERT)")
                .isFalse();
        assertThat(captor.getValue().getId()).isEqualTo(existingPk);
    }
}
