package com.yowyob.tiibntick.core.actor.domain.model;

import java.util.UUID;

/**
 * Public, stable read-model for an actor's human-facing identity (display
 * name, phone, email). A deliberately narrow projection of the Kernel actor —
 * {@link KernelActorDto} is internal/never persisted; this record is the one
 * meant to be exposed to callers outside this module via
 * {@link com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase}.
 *
 * <p>{@code firstName}/{@code lastName} are only filled when the Kernel returned them
 * (e.g. right after {@code PUT /api/actors/me/identity}); otherwise {@code null}.
 * No avatar/photo URL field — the Kernel does not track one today.
 */
public record ActorIdentitySummary(
        UUID actorId,
        String displayName,
        String phoneNumber,
        String email,
        String firstName,
        String lastName
) {
    /** Backward-compatible constructor (no first/last name). */
    public ActorIdentitySummary(UUID actorId, String displayName, String phoneNumber, String email) {
        this(actorId, displayName, phoneNumber, email, null, null);
    }

    public static ActorIdentitySummary from(KernelActorDto dto) {
        String display = dto.displayName() != null ? dto.displayName() : dto.name();
        return new ActorIdentitySummary(dto.id(), display, dto.phoneNumber(), dto.email(),
                dto.firstName(), dto.lastName());
    }

    public static ActorIdentitySummary from(KernelBusinessActorDto dto) {
        UUID id = dto.actorId() != null ? dto.actorId() : dto.id();
        return new ActorIdentitySummary(id, dto.name(), dto.contactPhone(), null);
    }
}
