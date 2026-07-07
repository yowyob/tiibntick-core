package com.yowyob.kernel.event.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.domain.model.EventSchema;
import com.yowyob.kernel.event.domain.vo.SchemaId;

/**
 * <b>Outbound port</b> — Persistent storage and lookup for Avro schemas.
 *
 * <p>Schemas are stored once per {@code (eventType, solutionCode, version)}
 * triple and are looked up by producers before serialising a payload and by
 * consumers when deserialising.
 */
public interface EventSchemaRepository {

    /**
     * Persists a new schema version.
     *
     * @param schema the schema to persist
     * @return a {@link Mono} emitting the saved schema
     */
    Mono<EventSchema> save(EventSchema schema);

    /**
     * Retrieves a schema by its unique database identifier.
     *
     * @param id the schema identifier
     * @return a {@link Mono} emitting the schema, or empty if not found
     */
    Mono<EventSchema> findById(SchemaId id);

    /**
     * Retrieves the latest (highest version number) schema for an event type.
     *
     * @param eventType    the event type name
     * @param solutionCode the owning solution
     * @return a {@link Mono} emitting the latest schema, or empty if none registered
     */
    Mono<EventSchema> findLatest(String eventType, String solutionCode);

    /**
     * Retrieves a specific schema version.
     *
     * @param eventType    the event type name
     * @param solutionCode the owning solution
     * @param version      the version number (1-based)
     * @return a {@link Mono} emitting the schema, or empty if not found
     */
    Mono<EventSchema> findByVersion(String eventType, String solutionCode, int version);

    /**
     * Lists all versions of a schema for a given event type.
     *
     * @param eventType    the event type name
     * @param solutionCode the owning solution
     * @return a {@link Flux} of schemas ordered by version ascending
     */
    Flux<EventSchema> findAllVersions(String eventType, String solutionCode);

    /**
     * Marks a schema version as deprecated in storage.
     *
     * @param id the schema to deprecate
     * @return a {@link Mono} emitting the updated row count
     */
    Mono<Long> markDeprecated(SchemaId id);

    /**
     * Checks whether at least one schema is registered for the given event type.
     *
     * @param eventType    the event type
     * @param solutionCode the owning solution
     * @return a {@link Mono} emitting {@code true} if at least one schema exists
     */
    Mono<Boolean> existsForEventType(String eventType, String solutionCode);
}
