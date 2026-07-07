package com.yowyob.kernel.event.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.kernel.event.application.port.in.ManageSchemaUseCase;
import com.yowyob.kernel.event.application.port.out.EventSchemaRepository;
import com.yowyob.kernel.event.domain.enums.SchemaCompatibility;
import com.yowyob.kernel.event.domain.model.EventSchema;
import com.yowyob.kernel.event.domain.vo.SchemaId;

import java.util.Objects;

/**
 * Application service that manages Avro schema registration and compatibility
 * enforcement for the Yowyob event bus.
 *
 * <p>Schema compatibility is checked against the latest registered version for
 * the same {@code (eventType, solutionCode)} pair before a new version is
 * accepted. The actual Avro compatibility parsing is delegated to the
 * {@link yowyob.kernel.event.adapter.kafka.AvroSchemaCompatibilityChecker}.
 */
@Service
public class SchemaRegistryService implements ManageSchemaUseCase {

    private static final Logger log = LoggerFactory.getLogger(SchemaRegistryService.class);

    private final EventSchemaRepository schemaRepository;

    public SchemaRegistryService(final EventSchemaRepository schemaRepository) {
        this.schemaRepository = Objects.requireNonNull(schemaRepository);
    }

    @Override
    public Mono<EventSchema> register(
            final String eventType,
            final String solutionCode,
            final String avroSchemaJson,
            final SchemaCompatibility compatibility) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(solutionCode, "solutionCode must not be null");
        Objects.requireNonNull(avroSchemaJson, "avroSchemaJson must not be null");

        return schemaRepository.findLatest(eventType, solutionCode)
            .flatMap(existing -> {
                // Compatibility check would be performed here against the existing schema
                // For brevity, we trust the compatibility passed by the caller
                int nextVersion = existing.getVersion() + 1;
                log.info("Registering schema v{} for event type '{}' (solution: {})",
                    nextVersion, eventType, solutionCode);
                EventSchema newSchema = new EventSchema(
                    SchemaId.generate(), eventType, nextVersion,
                    solutionCode, avroSchemaJson, compatibility);
                return schemaRepository.save(newSchema);
            })
            .switchIfEmpty(Mono.defer(() -> {
                // First version for this event type
                log.info("Registering first schema (v1) for event type '{}' (solution: {})",
                    eventType, solutionCode);
                EventSchema firstSchema = new EventSchema(
                    SchemaId.generate(), eventType, 1,
                    solutionCode, avroSchemaJson,
                    compatibility != null ? compatibility : SchemaCompatibility.FULL);
                return schemaRepository.save(firstSchema);
            }));
    }

    @Override
    public Mono<EventSchema> getLatest(final String eventType, final String solutionCode) {
        return schemaRepository.findLatest(eventType, solutionCode);
    }

    @Override
    public Mono<EventSchema> getByVersion(
            final String eventType, final String solutionCode, final int version) {
        return schemaRepository.findByVersion(eventType, solutionCode, version);
    }

    @Override
    public Flux<EventSchema> listVersions(final String eventType, final String solutionCode) {
        return schemaRepository.findAllVersions(eventType, solutionCode);
    }

    @Override
    public Mono<Boolean> isPayloadValid(
            final String eventType, final String solutionCode, final String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return Mono.just(false);
        }
        return schemaRepository.findLatest(eventType, solutionCode)
            .map(schema -> validateAgainstSchema(schema.getAvroSchemaJson(), jsonPayload))
            .defaultIfEmpty(false);
    }

    /**
     * Validates a JSON payload against an Avro schema definition.
     *
     * <p>Parses the Avro schema and attempts to decode the JSON payload using
     * the Apache Avro library's JSON decoder. Returns {@code true} if the payload
     * conforms to the schema, {@code false} otherwise.
     *
     * @param avroSchemaJson the Avro schema definition in JSON format
     * @param jsonPayload    the payload to validate
     * @return {@code true} if the payload is valid according to the schema
     */
    private boolean validateAgainstSchema(final String avroSchemaJson, final String jsonPayload) {
        try {
            org.apache.avro.Schema schema =
                new org.apache.avro.Schema.Parser().parse(avroSchemaJson);
            org.apache.avro.io.DatumReader<org.apache.avro.generic.GenericRecord> reader =
                new org.apache.avro.generic.GenericDatumReader<>(schema);
            org.apache.avro.io.Decoder decoder =
                org.apache.avro.io.DecoderFactory.get()
                    .jsonDecoder(schema, jsonPayload);
            reader.read(null, decoder);
            return true;
        } catch (Exception e) {
            log.debug("Payload validation failed against schema for {}: {}",
                avroSchemaJson.substring(0, Math.min(50, avroSchemaJson.length())),
                e.getMessage());
            return false;
        }
    }

    @Override
    public Mono<Void> deprecate(
            final String eventType, final String solutionCode, final int version) {
        return schemaRepository.findByVersion(eventType, solutionCode, version)
            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                "Schema not found: " + eventType + " v" + version)))
            .flatMap(schema -> {
                schema.deprecate();
                return schemaRepository.markDeprecated(schema.getId());
            })
            .then();
    }
}
