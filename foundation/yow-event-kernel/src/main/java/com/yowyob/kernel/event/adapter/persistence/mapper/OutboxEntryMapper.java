package com.yowyob.kernel.event.adapter.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import com.yowyob.kernel.event.adapter.persistence.entity.OutboxEntryEntity;
import com.yowyob.kernel.event.domain.enums.OutboxStatus;
import com.yowyob.kernel.event.domain.model.OutboxEntry;
import com.yowyob.kernel.event.domain.vo.EnvelopeId;
import com.yowyob.kernel.event.domain.vo.OutboxEntryId;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Manual mapper between {@link OutboxEntry} (domain), {@link OutboxEntryEntity}
 * (R2DBC entity), and raw {@link Row} objects returned by the SKIP LOCKED query.
 *
 * <p>Uses {@link OutboxEntry#restore} for all entity→domain conversions so that
 * persisted lifecycle state (status, processingAttempt, etc.) is set directly
 * without going through state-machine transitions.
 *
 * <p>Kafka message headers are stored as JSON in the {@code headers} column and
 * are serialized/deserialized via Jackson.
 */
@Component
public class OutboxEntryMapper {

    private static final Logger log = LoggerFactory.getLogger(OutboxEntryMapper.class);
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public OutboxEntryMapper(@Qualifier("tntObjectMapper") final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Entity → Domain ──────────────────────────────────────────────────────

    /**
     * Converts a R2DBC entity (read from the database) to the domain model.
     *
     * @param entity the persisted entity
     * @return the fully-restored domain outbox entry
     */
    public OutboxEntry toDomain(final OutboxEntryEntity entity) {
        if (entity == null) return null;
        return OutboxEntry.restore(
            OutboxEntryId.of(entity.getId()),
            EnvelopeId.of(entity.getEnvelopeId()),
            entity.getTenantId(),
            entity.getKafkaTopic(),
            entity.getKafkaPartitionKey(),
            OutboxStatus.valueOf(entity.getStatus()),
            entity.getScheduledAt(),
            entity.getProcessedAt(),
            entity.getProcessingAttempt(),
            deserializeHeaders(entity.getHeadersJson())
        );
    }

    /**
     * Maps a raw R2DBC {@link Row} (from the {@code SKIP LOCKED} SQL query)
     * to the domain model. Required because Spring Data R2DBC does not support
     * the {@code FOR UPDATE SKIP LOCKED} hint via its template API.
     *
     * @param row the R2DBC row from the custom SQL query
     * @return the fully-restored domain outbox entry
     */
    public OutboxEntry fromRow(final Row row) {
        String statusStr = row.get("status", String.class);
        return OutboxEntry.restore(
            OutboxEntryId.of(row.get("id",                  String.class)),
            EnvelopeId.of(row.get("envelope_id",            String.class)),
            row.get("tenant_id",                             String.class),
            row.get("kafka_topic",                           String.class),
            row.get("kafka_partition_key",                   String.class),
            statusStr != null ? OutboxStatus.valueOf(statusStr) : OutboxStatus.PENDING,
            row.get("scheduled_at",                          LocalDateTime.class),
            row.get("processed_at",                          LocalDateTime.class),
            resolveInt(row.get("processing_attempt",         Integer.class)),
            deserializeHeaders(row.get("headers",            String.class))
        );
    }

    // ── Domain → Entity ──────────────────────────────────────────────────────

    /**
     * Converts a domain outbox entry to a R2DBC entity for INSERT operations.
     *
     * @param entry the outbox entry to persist
     * @return the R2DBC entity
     */
    public OutboxEntryEntity toEntity(final OutboxEntry entry) {
        if (entry == null) return null;
        OutboxEntryEntity entity = new OutboxEntryEntity();
        entity.setId(entry.getId().value());
        entity.setEnvelopeId(entry.getEnvelopeId().value());
        entity.setTenantId(entry.getTenantId());
        entity.setKafkaTopic(entry.getKafkaTopic());
        entity.setKafkaPartitionKey(entry.getKafkaPartitionKey());
        entity.setStatus(entry.getStatus().name());
        entity.setScheduledAt(entry.getScheduledAt());
        entity.setProcessedAt(entry.getProcessedAt());
        entity.setProcessingAttempt(entry.getProcessingAttempt());
        entity.setHeadersJson(serializeHeaders(entry.getHeaders()));
        return entity;
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private Map<String, String> deserializeHeaders(final String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Failed to deserialize outbox entry headers: {}. Using empty map.", json);
            return Map.of();
        }
    }

    private String serializeHeaders(final Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            log.warn("Failed to serialize outbox entry headers. Using empty JSON.", e);
            return "{}";
        }
    }

    private static int resolveInt(final Integer value) {
        return value != null ? value : 0;
    }
}
