package com.yowyob.tiibntick.core.actor.domain.model;

import java.util.UUID;

/**
 * Pointer to one TiiBnTick profile of an account. {@code kind} is one of {@code CLIENT}, {@code FREELANCER},
 * {@code DELIVERER}, {@code RELAY_OPERATOR}; {@code tenantId} is the space the profile was created in.
 */
public record ProfileRef(String kind, UUID profileId, UUID tenantId, String actorStatus) {
}
