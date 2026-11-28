package com.yowyob.tiibntick.core.geo.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Link between an owner (a person, an actor, a relay point... any id the caller chooses) and an {@link Address},
 * with a free usage label ("PRIMARY", "HOME", "WORK"...). The label is the caller's vocabulary, not the core's.
 */
public record AddressLink(UUID id, UUID ownerId, UUID addressId, String label, Instant createdAt) {

    public AddressLink {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(addressId, "addressId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        label = label == null || label.isBlank() ? null : label.trim();
    }

    public static AddressLink create(UUID ownerId, UUID addressId, String label) {
        return new AddressLink(UUID.randomUUID(), ownerId, addressId, label, Instant.now());
    }
}
