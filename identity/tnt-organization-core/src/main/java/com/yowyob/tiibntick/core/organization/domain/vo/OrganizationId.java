package com.yowyob.tiibntick.core.organization.domain.vo;

import java.util.UUID;

/**
 * Value Object representing the internal TiiBnTick organization identifier.
 *
 * <p>This VO wraps the UUID primary key of TiiBnTick organizational aggregates
 * (Agency, Branch, HubRelais). It is <strong>distinct</strong> from the
 * {@code organizationId} field, which references the Kernel (RT-comops) entity.
 *
 * <p>Usage:
 * <pre>{@code
 *     OrganizationId id = OrganizationId.generate();   // creates a new random UUID
 *     OrganizationId id = OrganizationId.of(existingUUID);
 * }</pre>
 *
 * @author MANFOUO Braun
 */
public record OrganizationId(UUID value) {

    /**
     * Compact constructor — validates that the wrapped value is not null.
     *
     * @param value the UUID value; must not be null
     * @throws IllegalArgumentException if {@code value} is null
     */
    public OrganizationId {
        if (value == null) {
            throw new IllegalArgumentException("OrganizationId value must not be null");
        }
    }

    /**
     * Generates a new random {@link OrganizationId}.
     *
     * @return a new {@code OrganizationId} backed by a random UUID v4
     */
    public static OrganizationId generate() {
        return new OrganizationId(UUID.randomUUID());
    }

    /**
     * Wraps an existing UUID.
     *
     * @param uuid the existing UUID
     * @return the corresponding {@link OrganizationId}
     */
    public static OrganizationId of(UUID uuid) {
        return new OrganizationId(uuid);
    }

    /**
     * Parses a UUID string into an {@link OrganizationId}.
     *
     * @param uuidString a valid UUID string representation
     * @return the corresponding {@link OrganizationId}
     * @throws IllegalArgumentException if the string is not a valid UUID
     */
    public static OrganizationId parse(String uuidString) {
        return new OrganizationId(UUID.fromString(uuidString));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
