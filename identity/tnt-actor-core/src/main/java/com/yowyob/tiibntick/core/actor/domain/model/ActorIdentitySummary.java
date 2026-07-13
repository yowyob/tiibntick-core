package com.yowyob.tiibntick.core.actor.domain.model;

import java.util.UUID;

/**
 * Public, stable read-model for an actor's human-facing identity (display
 * name, phone, email). A deliberately narrow projection of
 * {@link KernelActorDto} — that DTO's own javadoc marks it internal/never
 * persisted, used only by {@code IKernelActorPort} for existence checks; this
 * record is the one meant to be exposed to callers outside this module via
 * {@link com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase}.
 *
 * <p>No avatar/photo URL field — the Kernel does not track one today.
 */
public record ActorIdentitySummary(
        UUID actorId,
        String displayName,
        String phoneNumber,
        String email
) {
    public static ActorIdentitySummary from(KernelActorDto dto) {
        return new ActorIdentitySummary(dto.id(), dto.displayName(), dto.phoneNumber(), dto.email());
    }
}
