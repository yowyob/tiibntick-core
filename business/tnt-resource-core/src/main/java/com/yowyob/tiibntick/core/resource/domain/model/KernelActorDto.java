package com.yowyob.tiibntick.core.resource.domain.model;

import java.util.UUID;

/**
 * Read-only DTO representing an actor (deliverer) fetched from the Yowyob Kernel
 * (RT-comops-actor-core) via {@link com.yowyob.tiibntick.core.resource.application.port.out.KernelActorPort}.
 *
 * <p>This record is <b>never persisted</b> in tnt_core_db. It is used exclusively
 * for best-effort validation of {@code assignedDelivererId} before assigning a vehicle
 * or equipment to a deliverer in the TiiBnTick resource domain.</p>
 *
 * <p>Integration pattern: {@code Vehicle.assignedDelivererId} is a logical reference
 * to a Kernel actor UUID. No Java inheritance from Kernel actor classes.</p>
 *
 * @param actorId         UUID of the actor in the Kernel database
 * @param tenantId        tenant owning this actor
 * @param displayName     actor's display/business name
 * @param actorType       type of actor (e.g. "DELIVERER", "AGENT")
 * @param isActive        whether the actor is currently active in the Kernel
 *
 * @author MANFOUO Braun
 */
public record KernelActorDto(
        UUID actorId,
        UUID tenantId,
        String displayName,
        String actorType,
        boolean isActive
) {}
