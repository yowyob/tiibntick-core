package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpClientRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpClient;
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
 * Proves that a second {@code createOrUpdate} on the same {@code coreClientId}
 * issues an UPDATE (isNew=false) instead of a duplicate INSERT (isNew=true → 409).
 */
@ExtendWith(MockitoExtension.class)
class GofpClientCreateOrUpdateTest {

    @Mock private GofpClientRepository clientRepository;
    @Mock private GofpUserRepository userRepository;

    private GofpClientService service;

    @BeforeEach
    void setUp() {
        service = new GofpClientService(clientRepository, userRepository);
    }

    @Test
    void createOrUpdate_whenRecordExists_savesWithIsNewFalse() {
        UUID coreClientId = UUID.randomUUID();
        UUID existingPk = UUID.randomUUID();

        GofpClient existing = new GofpClient();
        existing.setId(existingPk);
        existing.setCoreClientId(coreClientId);
        existing.markNotNew();

        GofpClient incoming = new GofpClient();
        incoming.setCoreClientId(coreClientId);
        assertThat(incoming.isNew()).as("incoming entity from @RequestBody is new").isTrue();

        when(clientRepository.findByCoreClientId(coreClientId))
                .thenReturn(Mono.just(existing));
        when(clientRepository.save(any(GofpClient.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        // coreUserId not set → hydrate() short-circuits, no userRepository call needed

        StepVerifier.create(service.createOrUpdate(incoming))
                .expectNextMatches(c -> existingPk.equals(c.getId()))
                .verifyComplete();

        ArgumentCaptor<GofpClient> captor = ArgumentCaptor.forClass(GofpClient.class);
        verify(clientRepository).save(captor.capture());
        assertThat(captor.getValue().isNew())
                .as("entity passed to save() must have isNew=false (UPDATE, not INSERT)")
                .isFalse();
        assertThat(captor.getValue().getId()).isEqualTo(existingPk);
    }
}
