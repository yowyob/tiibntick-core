package com.yowyob.tiibntick.core.actor.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Read-only projection of the Kernel's {@code BusinessActorResponse} returned by
 * {@code GET /api/actors/me}. Never persisted.
 *
 * <p>Note: this is the caller's <i>business</i> actor — it carries a single {@code name} and a
 * {@code contactPhone}, not the personal {@code firstName}/{@code lastName} (those are only
 * returned by {@code PUT /api/actors/me/identity}, see {@link KernelActorDto}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelBusinessActorDto(
        UUID id,
        UUID actorId,
        String name,
        String contactPhone,
        String type,
        Boolean isIndividual
) {}
