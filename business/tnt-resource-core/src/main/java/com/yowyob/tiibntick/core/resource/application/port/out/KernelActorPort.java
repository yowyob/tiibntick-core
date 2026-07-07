package com.yowyob.tiibntick.core.resource.application.port.out;

import com.yowyob.tiibntick.core.resource.domain.model.KernelActorDto;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — Kernel Actor Bridge.
 *
 * <p>Defines the contract for querying the Yowyob Kernel (RT-comops-actor-core)
 * about actors (deliverers, agents). TiiBnTick does NOT extend Kernel actor classes.
 * Instead, it stores {@code assignedDelivererId} as a UUID reference (actorId) and
 * queries the Kernel only for best-effort existence validation before vehicle assignment.</p>
 *
 * <p>Special case: {@code tnt-resource-core} is a TNT-exclusive module — Vehicle and
 * Equipment have no Kernel counterpart. The only Kernel integration is the
 * {@code assignedDelivererId → RT-comops-actor-core} reference.</p>
 *
 * <p>The coupling is <em>optional</em>: assignment proceeds even if the Kernel is
 * unreachable (resilient by design). A WARN is logged and the TNT assignment continues.</p>
 *
 * <p>Implementation: {@link com.yowyob.tiibntick.core.resource.adapter.out.kernel.KernelActorAdapter}
 * (reactive WebClient over the Kernel REST API).</p>
 *
 * @author MANFOUO Braun
 */
public interface KernelActorPort {

    /**
     * Fetches an actor from the Kernel by its UUID (actorId).
     *
     * <p>Returns {@code Mono.empty()} if the actor does not exist in the Kernel,
     * allowing callers to treat the validation as optional (best-effort).</p>
     *
     * @param actorId  the Kernel actor UUID (= deliverer's actorId from tnt-actor-core)
     * @param tenantId tenant context
     * @return the kernel actor data, or {@code Mono.empty()} if not found
     */
    Mono<KernelActorDto> findActorById(UUID actorId, UUID tenantId);

    /**
     * Verifies that an actor exists and is active in the Kernel.
     *
     * <p>Used before assigning a vehicle to a deliverer to ensure the actor is
     * registered and active. Returns {@code false} (not {@code Mono.empty()}) on
     * any error — enabling resilient assignment without hard failure.</p>
     *
     * @param actorId  the Kernel actor UUID
     * @param tenantId tenant context
     * @return {@code true} if the actor exists and is active; {@code false} otherwise
     */
    Mono<Boolean> isActiveActor(UUID actorId, UUID tenantId);
}
