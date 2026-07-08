package com.yowyob.tiibntick.core.actor.application.port.out;

import com.yowyob.tiibntick.core.actor.domain.model.KernelActorDto;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — Kernel Actor Bridge.
 *
 * <p>Every {@link com.yowyob.tiibntick.core.actor.domain.model.TntActorProfile} extends
 * a Kernel actor by {@code actorId}. This port lets the application layer validate that
 * the referenced actor actually exists in the Kernel before creating the local profile
 * extension — fail-open by design (see
 * {@link com.yowyob.tiibntick.core.actor.adapter.out.kernel.KernelActorAdapter}):
 * Kernel unavailability must never block actor-profile creation.</p>
 *
 * <p>Implementation: {@link com.yowyob.tiibntick.core.actor.adapter.out.kernel.KernelActorAdapter}
 * (reactive WebClient over the Kernel REST API).</p>
 *
 * @author MANFOUO Braun
 */
public interface IKernelActorPort {

    /**
     * Fetches an actor from the Kernel by its UUID.
     *
     * @param actorId the Kernel actor UUID
     * @return the actor data, or {@link Mono#empty()} if not found or the Kernel is unreachable
     */
    Mono<KernelActorDto> findById(UUID actorId);

    /**
     * Checks whether an actor exists in the Kernel. Fail-open — returns {@code false} on
     * any error rather than propagating it.
     *
     * @param actorId the Kernel actor UUID
     * @return {@code true} if the actor was found
     */
    Mono<Boolean> exists(UUID actorId);
}
