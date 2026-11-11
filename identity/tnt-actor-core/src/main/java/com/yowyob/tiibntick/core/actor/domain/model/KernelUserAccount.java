package com.yowyob.tiibntick.core.actor.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * The part of the Kernel's {@code UserAccountResponse} ({@code GET /api/users/me}) that TiiBnTick reads:
 * who the person is, where to write to them and which file is their profile photo. Never persisted — the
 * Kernel stays the single owner of all of it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelUserAccount(
        UUID id,
        UUID actorId,
        String username,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        UUID avatarId) {

    /** "First Last", falling back to the username; {@code null} when the Kernel gave nothing. */
    public String displayName() {
        String full = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
        if (!full.isEmpty()) {
            return full;
        }
        return username != null && !username.isBlank() ? username : null;
    }
}
