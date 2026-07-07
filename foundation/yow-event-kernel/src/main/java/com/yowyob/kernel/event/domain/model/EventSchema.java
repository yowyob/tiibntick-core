package com.yowyob.kernel.event.domain.model;

import com.yowyob.kernel.event.domain.enums.SchemaCompatibility;
import com.yowyob.kernel.event.domain.vo.SchemaId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Avro schema registered for a specific event type and version.
 *
 * <p>The schema registry ensures that producers and consumers agree on the
 * structure of event payloads. When a new schema version is registered, the
 * compatibility check is enforced against all previously registered versions
 * for the same {@code eventType}.
 *
 * <p>Schema IDs are assigned sequentially within each {@code eventType} and
 * {@code solutionCode} combination, starting at {@code 1}.
 */
public class EventSchema {

    private final SchemaId           id;
    private final String             eventType;
    private final int                version;
    private final String             solutionCode;
    private final String             avroSchemaJson;
    private final SchemaCompatibility compatibility;
    private final boolean            isBackwardCompatible;
    private final LocalDateTime      registeredAt;
    private LocalDateTime            deprecatedAt;

    // ── Constructor ──────────────────────────────────────────────────────────

    public EventSchema(
            final SchemaId id,
            final String eventType,
            final int version,
            final String solutionCode,
            final String avroSchemaJson,
            final SchemaCompatibility compatibility) {
        this.id                   = Objects.requireNonNull(id);
        this.eventType            = Objects.requireNonNull(eventType);
        this.version              = version;
        this.solutionCode         = Objects.requireNonNull(solutionCode);
        this.avroSchemaJson       = Objects.requireNonNull(avroSchemaJson);
        this.compatibility        = compatibility != null ? compatibility : SchemaCompatibility.FULL;
        this.isBackwardCompatible = this.compatibility == SchemaCompatibility.BACKWARD
                                 || this.compatibility == SchemaCompatibility.FULL
                                 || this.compatibility == SchemaCompatibility.BACKWARD_TRANSITIVE
                                 || this.compatibility == SchemaCompatibility.FULL_TRANSITIVE;
        this.registeredAt         = LocalDateTime.now();
    }

    // ── Behaviour ────────────────────────────────────────────────────────────

    /**
     * Marks this schema version as deprecated.
     * Deprecated schemas are still accepted by the registry but producers are
     * encouraged to migrate to the latest version.
     */
    public void deprecate() {
        this.deprecatedAt = LocalDateTime.now();
    }

    /** Returns {@code true} if this schema has been deprecated. */
    public boolean isDeprecated() {
        return deprecatedAt != null;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public SchemaId           getId()                 { return id; }
    public String             getEventType()          { return eventType; }
    public int                getVersion()            { return version; }
    public String             getSolutionCode()       { return solutionCode; }
    public String             getAvroSchemaJson()     { return avroSchemaJson; }
    public SchemaCompatibility getCompatibility()     { return compatibility; }
    public boolean            isBackwardCompatible()  { return isBackwardCompatible; }
    public LocalDateTime      getRegisteredAt()       { return registeredAt; }
    public LocalDateTime      getDeprecatedAt()       { return deprecatedAt; }
}
