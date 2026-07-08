package com.yowyob.tiibntick.core.actor.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Read-only DTO representing an actor fetched from the Yowyob Kernel
 * (RT-comops-actor-core) via {@link com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort}.
 *
 * <p>This record is <b>never persisted</b> — it exists only to validate that the
 * {@code actorId} a {@link TntActorProfile} extends actually exists in the Kernel
 * before (or after, depending on the caller) creating the local profile.</p>
 *
 * <p>Field names match the Kernel's real {@code ActorResponse} JSON keys exactly (see
 * {@code docs/kernel-api/schemas.md}) — no aliasing needed. The Kernel's {@code Actor}
 * has no active/status field at all (unlike {@code Product}/{@code SalesOrder}, which at
 * least have a {@code status} string) — existence is the only signal available, so this
 * port only exposes an existence check, not an "active" one.</p>
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelActorDto(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        String firstName,
        String lastName,
        String name,
        String displayName,
        String phoneNumber,
        String email,
        String type
) {}
