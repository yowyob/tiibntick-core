package com.yowyob.tiibntick.common.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable value object capturing audit metadata for any mutation event.
 *
 * <p>Embedded in entities and event payloads to record the actor, timestamp,
 * and optional IP address of the operation. Mapped to {@code @Audited} AOP
 * interception in {@code tnt-kernel-core}.
 *
 * Author: MANFOUO Braun
 */
public final class AuditMetadata {

    private final UUID actorId;
    private final String actorType;
    private final Instant occurredAt;
    private final String ipAddress;
    private final String correlationId;
    private final String actionDescription;

    private AuditMetadata(Builder builder) {
        this.actorId           = Objects.requireNonNull(builder.actorId, "actorId is required");
        this.actorType         = Objects.requireNonNull(builder.actorType, "actorType is required");
        this.occurredAt        = Objects.requireNonNull(builder.occurredAt, "occurredAt is required");
        this.ipAddress         = builder.ipAddress;
        this.correlationId     = builder.correlationId;
        this.actionDescription = builder.actionDescription;
    }

    /**
     * Creates metadata for a system-generated operation (no human actor).
     *
     * @param actionDescription description of the automated action
     */
    public static AuditMetadata system(String actionDescription) {
        return new Builder()
                .actorId(new UUID(0, 0))
                .actorType("SYSTEM")
                .occurredAt(Instant.now())
                .actionDescription(actionDescription)
                .build();
    }

    /**
     * Creates metadata for a user-initiated operation.
     *
     * @param actorId    user identifier
     * @param actorType  role type (e.g., "CLIENT", "AGENCY_MANAGER", "DELIVERER")
     * @param ipAddress  originating IP (nullable)
     */
    public static AuditMetadata ofUser(UUID actorId, String actorType, String ipAddress) {
        return new Builder()
                .actorId(actorId)
                .actorType(actorType)
                .occurredAt(Instant.now())
                .ipAddress(ipAddress)
                .build();
    }

    public UUID getActorId()                      { return actorId; }
    public String getActorType()                  { return actorType; }
    public Instant getOccurredAt()                { return occurredAt; }
    public Optional<String> getIpAddress()        { return Optional.ofNullable(ipAddress); }
    public Optional<String> getCorrelationId()    { return Optional.ofNullable(correlationId); }
    public Optional<String> getActionDescription(){ return Optional.ofNullable(actionDescription); }

    public boolean isSystemGenerated() {
        return "SYSTEM".equals(actorType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditMetadata other)) return false;
        return Objects.equals(actorId, other.actorId)
                && Objects.equals(occurredAt, other.occurredAt)
                && Objects.equals(correlationId, other.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actorId, occurredAt, correlationId);
    }

    @Override
    public String toString() {
        return "AuditMetadata{actor=" + actorId + ", type=" + actorType + ", at=" + occurredAt + "}";
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID actorId;
        private String actorType;
        private Instant occurredAt;
        private String ipAddress;
        private String correlationId;
        private String actionDescription;

        public Builder actorId(UUID actorId)                   { this.actorId = actorId; return this; }
        public Builder actorType(String actorType)             { this.actorType = actorType; return this; }
        public Builder occurredAt(Instant occurredAt)          { this.occurredAt = occurredAt; return this; }
        public Builder ipAddress(String ipAddress)             { this.ipAddress = ipAddress; return this; }
        public Builder correlationId(String correlationId)     { this.correlationId = correlationId; return this; }
        public Builder actionDescription(String description)   { this.actionDescription = description; return this; }
        public AuditMetadata build()                           { return new AuditMetadata(this); }
    }
}
