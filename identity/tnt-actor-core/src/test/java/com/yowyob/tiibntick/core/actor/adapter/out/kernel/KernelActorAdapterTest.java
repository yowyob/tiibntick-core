package com.yowyob.tiibntick.core.actor.adapter.out.kernel;

import com.yowyob.tiibntick.core.actor.domain.model.KernelActorDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KernelActorAdapter}.
 *
 * <p>Uses a subclass overriding {@link KernelActorAdapter#findById(UUID)} to avoid a real
 * WebClient dependency, matching the pattern in {@code tnt-tp-core}'s
 * {@code KernelThirdPartyAdapterTest}.
 *
 * @author MANFOUO Braun
 */
@DisplayName("KernelActorAdapter — outbound Kernel adapter tests")
class KernelActorAdapterTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();

    private KernelActorAdapter adapterFor(KernelActorDto found) {
        return new KernelActorAdapter(null) {
            @Override
            public Mono<KernelActorDto> findById(UUID actorId) {
                return actorId.equals(ACTOR_ID) && found != null ? Mono.just(found) : Mono.empty();
            }
        };
    }

    @Test
    @DisplayName("exists() should return true when Kernel actor is found")
    void exists_shouldReturnTrue_whenActorFound() {
        KernelActorDto dto = new KernelActorDto(ACTOR_ID, UUID.randomUUID(), UUID.randomUUID(),
                "Jean", "Mballa", null, null, "699000000", "jean@example.cm", "DRIVER");
        KernelActorAdapter adapter = adapterFor(dto);

        StepVerifier.create(adapter.exists(ACTOR_ID))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("exists() should return false when Kernel actor is not found")
    void exists_shouldReturnFalse_whenActorNotFound() {
        KernelActorAdapter adapter = adapterFor(null);

        StepVerifier.create(adapter.exists(UUID.randomUUID()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("findById() should return the DTO when the Kernel actor is found")
    void findById_shouldReturnDto_whenActorFound() {
        KernelActorDto dto = new KernelActorDto(ACTOR_ID, UUID.randomUUID(), UUID.randomUUID(),
                "Jean", "Mballa", null, null, "699000000", "jean@example.cm", "DRIVER");
        KernelActorAdapter adapter = adapterFor(dto);

        StepVerifier.create(adapter.findById(ACTOR_ID))
                .assertNext(result -> {
                    assertThat(result.id()).isEqualTo(ACTOR_ID);
                    assertThat(result.firstName()).isEqualTo("Jean");
                })
                .verifyComplete();
    }
}
