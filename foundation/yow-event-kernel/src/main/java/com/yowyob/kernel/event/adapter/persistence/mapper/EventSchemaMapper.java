package com.yowyob.kernel.event.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.yowyob.kernel.event.adapter.persistence.entity.EventSchemaEntity;
import com.yowyob.kernel.event.domain.enums.SchemaCompatibility;
import com.yowyob.kernel.event.domain.model.EventSchema;
import com.yowyob.kernel.event.domain.vo.SchemaId;

/**
 * MapStruct mapper between {@link EventSchema} (domain) and
 * {@link EventSchemaEntity} (persistence).
 *
 * <p>Schema versions are immutable once registered; the only mutable field is
 * {@code deprecatedAt}, updated when an operator deprecates a schema version.
 * This makes the mapper straightforward: no state-machine reconstruction is needed.
 */
@Mapper(componentModel = "spring")
public interface EventSchemaMapper {

    /**
     * Maps a {@link EventSchema} domain object to a persistence entity.
     */
    @Mapping(target = "id",                  expression = "java(schema.getId().value())")
    @Mapping(target = "eventType",           source = "eventType")
    @Mapping(target = "version",             source = "version")
    @Mapping(target = "solutionCode",        source = "solutionCode")
    @Mapping(target = "avroSchemaJson",      source = "avroSchemaJson")
    @Mapping(target = "compatibility",       expression = "java(schema.getCompatibility().name())")
    @Mapping(target = "backwardCompatible",  source = "backwardCompatible")
    @Mapping(target = "registeredAt",        source = "registeredAt")
    @Mapping(target = "deprecatedAt",        source = "deprecatedAt")
    EventSchemaEntity toEntity(EventSchema schema);

    /**
     * Reconstructs an {@link EventSchema} from a persistence entity.
     *
     * <p>Calls the canonical constructor directly, which is safe here because
     * {@link EventSchema} has no state machine — all fields are set at construction
     * time except for {@code deprecatedAt}.
     */
    default EventSchema toDomain(final EventSchemaEntity entity) {
        if (entity == null) return null;

        EventSchema schema = new EventSchema(
            SchemaId.of(entity.getId()),
            entity.getEventType(),
            entity.getVersion(),
            entity.getSolutionCode(),
            entity.getAvroSchemaJson(),
            SchemaCompatibility.valueOf(entity.getCompatibility())
        );

        if (entity.getDeprecatedAt() != null) {
            schema.deprecate(); // restores deprecatedAt by calling the domain method
        }
        return schema;
    }
}
