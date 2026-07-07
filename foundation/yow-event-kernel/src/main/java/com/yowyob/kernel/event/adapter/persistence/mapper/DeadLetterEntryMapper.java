package com.yowyob.kernel.event.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.yowyob.kernel.event.adapter.persistence.entity.DeadLetterEntryEntity;
import com.yowyob.kernel.event.domain.enums.DLQStatus;
import com.yowyob.kernel.event.domain.model.DeadLetterEntry;
import com.yowyob.kernel.event.domain.vo.DeadLetterEntryId;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;
import com.yowyob.kernel.event.domain.vo.RetryPolicy;

/**
 * MapStruct mapper between {@link DeadLetterEntry} (domain) and
 * {@link DeadLetterEntryEntity} (persistence).
 *
 * <p>Because {@link DeadLetterEntry} uses a private constructor (via
 * {@link DeadLetterEntry#from}), the entity → domain conversion is handled
 * by a {@code default} method that calls a dedicated
 * {@link DeadLetterEntry#restore} factory (added to the domain model for
 * this purpose).
 */
@Mapper(componentModel = "spring")
public interface DeadLetterEntryMapper {

    /**
     * Maps a {@link DeadLetterEntry} domain object to a persistence entity.
     */
    @Mapping(target = "id",                expression = "java(entry.getId().value())")
    @Mapping(target = "originalEnvelopeId",expression = "java(entry.getOriginalEnvelopeId().value())")
    @Mapping(target = "kafkaTopic",        source = "kafkaTopic")
    @Mapping(target = "originalPayload",   source = "originalPayload")
    @Mapping(target = "failureReason",     source = "failureReason")
    @Mapping(target = "status",            expression = "java(entry.getStatus().name())")
    @Mapping(target = "failedAt",          source = "failedAt")
    @Mapping(target = "reprocessedAt",     source = "reprocessedAt")
    @Mapping(target = "discardReason",     source = "discardReason")
    DeadLetterEntryEntity toEntity(DeadLetterEntry entry);

    /**
     * Reconstructs a {@link DeadLetterEntry} from a persistence entity.
     *
     * <p>Uses {@link DeadLetterEntry#restore} to set all persisted lifecycle
     * fields directly without replaying state-machine transitions.
     */
    default DeadLetterEntry toDomain(final DeadLetterEntryEntity entity) {
        if (entity == null) return null;
        return DeadLetterEntry.restore(
            DeadLetterEntryId.of(entity.getId()),
            EnvelopeId.of(entity.getOriginalEnvelopeId()),
            entity.getKafkaTopic(),
            entity.getOriginalPayload(),
            entity.getFailureReason(),
            DLQStatus.valueOf(entity.getStatus()),
            entity.getFailedAt(),
            entity.getReprocessedAt(),
            entity.getDiscardReason(),
            RetryPolicy.defaultDlqPolicy()
        );
    }
}
