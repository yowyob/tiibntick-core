package com.yowyob.kernel.event.application.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.model.EventSchema;
import com.yowyob.kernel.event.domain.enums.SchemaCompatibility;

/**
 * <b>Inbound port</b> — Manage Avro schemas in the event registry.
 *
 * <p>Every event type published through the Yowyob event bus must have at least
 * one registered schema. The registry enforces compatibility rules between
 * successive versions and provides schema lookup for both producers and consumers.
 *
 * <p>Schemas are registered once per solution during the bootstrap sequence
 * (via {@link yowyob.kernel.event.config.YowEventKernelAutoConfiguration}) and
 * should not be modified in production without a formal schema evolution review.
 */
public interface ManageSchemaUseCase {

    /**
     * Registers a new Avro schema for the given event type.
     *
     * <p>If a schema already exists for the {@code eventType} with the same
     * {@code solutionCode}, the version is automatically incremented and a
     * compatibility check is performed against all prior versions.
     *
     * @param eventType      the fully qualified event type name
     * @param solutionCode   the Yowyob solution that owns this schema
     * @param avroSchemaJson the Avro schema definition in JSON format
     * @param compatibility  the compatibility level to enforce
     * @return a {@link Mono} emitting the registered {@link EventSchema}
     * @throws SchemaCompatibilityException if the new schema violates the
     *                                      configured compatibility level
     */
    Mono<EventSchema> register(
            String eventType,
            String solutionCode,
            String avroSchemaJson,
            SchemaCompatibility compatibility);

    /**
     * Retrieves the latest registered schema for the given event type.
     *
     * @param eventType    the event type to look up
     * @param solutionCode the owning solution
     * @return a {@link Mono} emitting the latest schema, or empty if none found
     */
    Mono<EventSchema> getLatest(String eventType, String solutionCode);

    /**
     * Retrieves a specific version of a schema.
     *
     * @param eventType    the event type
     * @param solutionCode the owning solution
     * @param version      the schema version number (1-based)
     * @return a {@link Mono} emitting the requested schema, or empty if not found
     */
    Mono<EventSchema> getByVersion(String eventType, String solutionCode, int version);

    /**
     * Lists all registered schemas for a given event type across all versions.
     *
     * @param eventType    the event type
     * @param solutionCode the owning solution
     * @return a {@link Flux} of schemas ordered by version ascending
     */
    Flux<EventSchema> listVersions(String eventType, String solutionCode);

    /**
     * Validates a raw JSON payload against the latest schema for the given
     * event type without registering anything.
     *
     * @param eventType    the event type
     * @param solutionCode the owning solution
     * @param jsonPayload  the payload to validate
     * @return a {@link Mono} emitting {@code true} if the payload is valid
     */
    Mono<Boolean> isPayloadValid(String eventType, String solutionCode, String jsonPayload);

    /**
     * Deprecates a specific schema version. Producers referencing this version
     * will receive a deprecation warning; the version remains readable.
     *
     * @param eventType    the event type
     * @param solutionCode the owning solution
     * @param version      the version to deprecate
     * @return a {@link Mono} completing empty on success
     */
    Mono<Void> deprecate(String eventType, String solutionCode, int version);
}
