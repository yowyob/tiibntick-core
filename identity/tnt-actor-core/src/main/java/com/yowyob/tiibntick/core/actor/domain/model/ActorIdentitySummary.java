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
        String lastName,
        UUID photoId
) {
    /** Backward-compatible constructor (no first/last name). */
    public ActorIdentitySummary(UUID actorId, String displayName, String phoneNumber, String email) {
        this(actorId, displayName, phoneNumber, email, null, null, null);
    }

    /** Backward-compatible constructor (no photo). */
    public ActorIdentitySummary(UUID actorId, String displayName, String phoneNumber, String email,
                                String firstName, String lastName) {
        this(actorId, displayName, phoneNumber, email, firstName, lastName, null);
    }

    /**
     * Completes this identity with the Kernel user account: the actor read from the business-actor endpoint
     * carries neither the e-mail nor the names nor the photo, the account does.
     */
    public ActorIdentitySummary withAccount(KernelUserAccount account) {
        if (account == null) {
            return this;
        }
        return new ActorIdentitySummary(actorId,
                displayName != null ? displayName : account.displayName(),
                phoneNumber != null ? phoneNumber : account.phoneNumber(),
                email != null ? email : account.email(),
                firstName != null ? firstName : account.firstName(),
                lastName != null ? lastName : account.lastName(),
                account.avatarId() != null ? account.avatarId() : photoId);
    }

    public static ActorIdentitySummary from(KernelActorDto dto) {
        String display = dto.displayName() != null ? dto.displayName() : dto.name();
        return new ActorIdentitySummary(dto.id(), display, dto.phoneNumber(), dto.email(),
                dto.firstName(), dto.lastName(), dto.photoId());
    }

    public static ActorIdentitySummary from(KernelBusinessActorDto dto) {
        UUID id = dto.actorId() != null ? dto.actorId() : dto.id();
        return new ActorIdentitySummary(id, dto.name(), dto.contactPhone(), null);
    }
}
