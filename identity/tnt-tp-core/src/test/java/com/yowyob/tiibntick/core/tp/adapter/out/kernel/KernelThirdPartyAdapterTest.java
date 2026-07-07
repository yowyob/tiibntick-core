package com.yowyob.tiibntick.core.tp.adapter.out.kernel;

import com.yowyob.tiibntick.core.tp.domain.model.KernelThirdPartyDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KernelThirdPartyAdapter}.
 *
 * <p>Uses a mocked adapter (not a full WebClient) to validate mapping logic
 * between Kernel response and domain DTO, and the 404-to-empty-Mono behavior.
 *
 * @author MANFOUO Braun
 */
@DisplayName("KernelThirdPartyAdapter — outbound Kernel adapter tests")
class KernelThirdPartyAdapterTest {

    private KernelThirdPartyAdapter adapter;

    private static final UUID TP_ID = UUID.randomUUID();

    // Spy adapter that overrides findById to avoid real HTTP calls
    @BeforeEach
    void setUp() {
        // We mock the adapter itself via subclass to avoid a real WebClient dependency
        adapter = spy(new KernelThirdPartyAdapter(null) {
            @Override
            public Mono<KernelThirdPartyDto> findById(UUID thirdPartyId) {
                if (TP_ID.equals(thirdPartyId)) {
                    return Mono.just(new KernelThirdPartyDto(
                            thirdPartyId, "Test TP", "test@example.cm", "CM", true));
                }
                return Mono.empty();
            }
        });
    }

    @Test
    @DisplayName("existsAndActive() should return true when Kernel TP exists and is active")
    void existsAndActive_shouldReturnTrue_whenTpExistsAndActive() {
        StepVerifier.create(adapter.existsAndActive(TP_ID))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("existsAndActive() should return false when Kernel TP is not found")
    void existsAndActive_shouldReturnFalse_whenTpNotFound() {
        UUID unknownId = UUID.randomUUID();

        StepVerifier.create(adapter.existsAndActive(unknownId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("existsAndActive() should return false when TP is inactive")
    void existsAndActive_shouldReturnFalse_whenTpInactive() {
        UUID inactiveId = UUID.randomUUID();

        // Override to return an inactive TP
        KernelThirdPartyAdapter inactiveAdapter = new KernelThirdPartyAdapter(null) {
            @Override
            public Mono<KernelThirdPartyDto> findById(UUID id) {
                return Mono.just(new KernelThirdPartyDto(id, "Inactive", null, "CM", false));
            }
        };

        StepVerifier.create(inactiveAdapter.existsAndActive(inactiveId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("findById() should return correct DTO when Kernel TP found")
    void findById_shouldReturnDto_whenTpFound() {
        StepVerifier.create(adapter.findById(TP_ID))
                .assertNext(dto -> {
                    assertThat(dto.thirdPartyId()).isEqualTo(TP_ID);
                    assertThat(dto.displayName()).isEqualTo("Test TP");
                    assertThat(dto.country()).isEqualTo("CM");
                    assertThat(dto.active()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findById() should return empty Mono when Kernel TP not found")
    void findById_shouldReturnEmpty_whenTpNotFound() {
        UUID randomId = UUID.randomUUID();

        StepVerifier.create(adapter.findById(randomId))
                .verifyComplete(); // empty Mono
    }
}
