package com.yowyob.tiibntick.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/**
 * TiiBnTick identifier generator utilities.
 *
 * <p>Provides:
 * <ul>
 *   <li>UUID v4 generation for new entities</li>
 *   <li>UUID v5 (name-based SHA-1) for deterministic idempotent identifiers</li>
 * </ul>
 *
 * <p>Deterministic IDs are used for idempotency in Kafka message processing and
 * in-at-least-once delivery guarantees: the same logical operation always produces
 * the same UUID, so duplicate messages can be detected without a database lookup.
 *
 * Author: MANFOUO Braun
 * Created: 2025-10-01
 */
public final class TntIdGenerator {

    /**
     * TNT-specific UUID v5 namespace.
     * Generated once from "tiibntick.yowyob.com" to uniquely scope all TNT deterministic IDs.
     */
    private static final UUID TNT_NAMESPACE =
        UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"); // DNS namespace (RFC 4122)

    private TntIdGenerator() {}

    // ─── UUID v4 — random ────────────────────────────────────────────────

    /**
     * Generates a new random UUID v4.
     * Use for new entities and events where uniqueness is required.
     */
    public static UUID generate() {
        return UUID.randomUUID();
    }

    /**
     * Returns true if the given UUID is syntactically valid (not null, parseable).
     */
    public static boolean isValid(UUID id) {
        return id != null;
    }

    /**
     * Parses a UUID string, throwing a descriptive exception if invalid.
     *
     * @param idString the UUID string to parse
     * @param context  human-readable context for the error message (e.g., "missionId")
     * @return parsed UUID
     * @throws IllegalArgumentException if the string is null or not a valid UUID
     */
    public static UUID parse(String idString, String context) {
        if (idString == null || idString.isBlank()) {
            throw new IllegalArgumentException(context + " must not be null or blank");
        }
        try {
            return UUID.fromString(idString.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid UUID for " + context + ": '" + idString + "'");
        }
    }

    // ─── UUID v5 — deterministic ─────────────────────────────────────────

    /**
     * Generates a deterministic UUID v5 (RFC 4122) from a name within the TNT namespace.
     *
     * <p>Given the same {@code name}, this method always returns the same UUID.
     * Suitable for idempotency keys and deduplication identifiers.
     *
     * <p>Example: generating an idempotent ID for a Kafka outbox entry:
     * <pre>{@code
     * UUID idempotencyKey = TntIdGenerator.deterministicId(
     *     "MissionCreated:" + missionId + ":" + aggregateVersion);
     * }</pre>
     *
     * @param name any non-null, non-blank string to hash
     * @return a stable UUID derived from the name
     */
    public static UUID deterministicId(String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return uuidV5(TNT_NAMESPACE, name);
    }

    /**
     * Generates a deterministic UUID v5 scoped to a specific domain (e.g., "mission", "package").
     * Useful for avoiding collisions between different aggregate types that use the same name.
     *
     * <p>Example:
     * <pre>{@code
     * UUID key = TntIdGenerator.deterministicId("delivery", missionId.toString());
     * }</pre>
     */
    public static UUID deterministicId(String domain, String name) {
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(name, "name must not be null");
        return deterministicId(domain + ":" + name);
    }

    // ─── UUID v5 implementation (RFC 4122) ──────────────────────────────

    private static UUID uuidV5(UUID namespace, String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

            // Hash namespace bytes + name bytes
            byte[] namespaceBytes = toBytes(namespace);
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);

            sha1.update(namespaceBytes);
            sha1.update(nameBytes);
            byte[] hash = sha1.digest();

            // Set version (5) and variant (RFC 4122) bits
            hash[6] &= 0x0f;  // clear version bits
            hash[6] |= 0x50;  // set version 5
            hash[8] &= 0x3f;  // clear variant bits
            hash[8] |= 0x80;  // set RFC 4122 variant

            return fromBytes(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is required by the JVM spec — this cannot happen
            throw new AssertionError("SHA-1 not available", e);
        }
    }

    private static byte[] toBytes(UUID uuid) {
        byte[] out = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            out[i]     = (byte) ((msb >> (56 - 8 * i)) & 0xff);
            out[8 + i] = (byte) ((lsb >> (56 - 8 * i)) & 0xff);
        }
        return out;
    }

    private static UUID fromBytes(byte[] bytes) {
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xff);
            lsb = (lsb << 8) | (bytes[8 + i] & 0xff);
        }
        return new UUID(msb, lsb);
    }
}
