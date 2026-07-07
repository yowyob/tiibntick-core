package com.yowyob.kernel.event.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.yowyob.kernel.event.adapter.persistence.entity.DomainEventEnvelopeEntity;
import com.yowyob.kernel.event.domain.enums.EnvelopeStatus;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;
import com.yowyob.kernel.event.domain.vo.RetryPolicy;

/**
 * MapStruct mapper between {@link DomainEventEnvelope} (domain) and
 * {@link DomainEventEnvelopeEntity} (persistence).
 *
 * <p>The entity → domain direction uses {@link DomainEventEnvelope#restore}
 * to reconstruct the aggregate without replaying state-machine transitions,
 * avoiding IllegalStateException guards and retry-counter overflow.
 */
@Mapper(componentModel = "spring")
public interface EventEnvelopeMapper {

    @Mapping(target = "id",               expression = "java(envelope.getId().value())")
    @Mapping(target = "status",           expression = "java(envelope.getStatus().name())")
    @Mapping(target = "correlationId",    source = "correlationId")
    @Mapping(target = "causationId",      source = "causationId")
    @Mapping(target = "eventType",        source = "eventType")
    @Mapping(target = "aggregateId",      source = "aggregateId")
    @Mapping(target = "aggregateType",    source = "aggregateType")
    @Mapping(target = "tenantId",         source = "tenantId")
    @Mapping(target = "solutionCode",     source = "solutionCode")
    @Mapping(target = "payload",          source = "payload")
    @Mapping(target = "schemaVersion",    source = "schemaVersion")
    @Mapping(target = "payloadHash",      source = "payloadHash")
    @Mapping(target = "kafkaTopic",       source = "kafkaTopic")
    @Mapping(target = "kafkaPartitionKey",source = "kafkaPartitionKey")
    @Mapping(target = "retryCount",       source = "retryCount")
    @Mapping(target = "lastError",        source = "lastError")
    @Mapping(target = "occurredAt",       source = "occurredAt")
    @Mapping(target = "publishedAt",      source = "publishedAt")
    @Mapping(target = "version",          source = "version")
    DomainEventEnvelopeEntity toEntity(DomainEventEnvelope envelope);

    /**
     * Reconstructs a domain aggregate from persistence using {@link DomainEventEnvelope#restore},
     * which sets lifecycle fields directly without going through state-machine guards.
     */
    default DomainEventEnvelope toDomain(final DomainEventEnvelopeEntity entity) {
        if (entity == null) return null;
        return DomainEventEnvelope.restore(
            EnvelopeId.of(entity.getId()),
            entity.getCorrelationId(),
            entity.getCausationId(),
            entity.getEventType(),
            entity.getAggregateId(),
            entity.getAggregateType(),
            entity.getTenantId(),
            entity.getSolutionCode(),
            entity.getPayload(),
            entity.getSchemaVersion(),
            entity.getKafkaTopic(),
            entity.getKafkaPartitionKey(),
            EnvelopeStatus.valueOf(entity.getStatus()),
            entity.getRetryCount(),
            entity.getLastError(),
            entity.getOccurredAt(),
            entity.getPublishedAt(),
            entity.getVersion(),
            RetryPolicy.defaultOutboxPolicy()
        );
    }
}
