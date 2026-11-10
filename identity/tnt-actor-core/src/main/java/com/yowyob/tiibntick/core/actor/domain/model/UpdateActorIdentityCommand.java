package com.yowyob.tiibntick.core.actor.domain.model;

/**
 * Personal identity fields the caller may change on their own Kernel actor
 * ({@code PUT /api/actors/me/identity}). {@code null} fields are left untouched.
 */
public record UpdateActorIdentityCommand(
        String firstName,
        String lastName,
        String phoneNumber
) {}
