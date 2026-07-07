package com.yowyob.kernel.event.domain;

/**
 * Thrown when a new Avro schema version is incompatible with previously
 * registered versions for the same event type, according to the configured
 * {@link yowyob.kernel.event.domain.enums.SchemaCompatibility} level.
 *
 * <p>This is a domain exception (not a Spring or infrastructure exception)
 * because schema compatibility is a business invariant of the event bus.
 */
public class SchemaCompatibilityViolationException extends RuntimeException {

    private final String eventType;
    private final String solutionCode;
    private final int    newVersion;
    private final String compatibilityLevel;

    /**
     * Constructs a new exception describing a compatibility violation.
     *
     * @param eventType          the event type for which registration was attempted
     * @param solutionCode       the Yowyob solution that owns the schema
     * @param newVersion         the version number that was rejected
     * @param compatibilityLevel the enforced compatibility level (e.g. {@code FULL})
     * @param cause              the underlying Avro parser exception (may be {@code null})
     */
    public SchemaCompatibilityViolationException(
            final String eventType,
            final String solutionCode,
            final int newVersion,
            final String compatibilityLevel,
            final Throwable cause) {
        super(String.format(
            "Schema v%d for event type '%s' (solution: %s) violates %s compatibility rule",
            newVersion, eventType, solutionCode, compatibilityLevel
        ), cause);
        this.eventType          = eventType;
        this.solutionCode       = solutionCode;
        this.newVersion         = newVersion;
        this.compatibilityLevel = compatibilityLevel;
    }

    public String getEventType()          { return eventType; }
    public String getSolutionCode()       { return solutionCode; }
    public int    getNewVersion()         { return newVersion; }
    public String getCompatibilityLevel() { return compatibilityLevel; }
}
