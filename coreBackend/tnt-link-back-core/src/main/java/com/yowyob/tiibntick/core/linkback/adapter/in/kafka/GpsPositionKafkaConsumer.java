package com.yowyob.tiibntick.core.linkback.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.linkback.application.port.out.ILinkPositionCache;
import com.yowyob.tiibntick.core.linkback.domain.model.LinkPosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Wires {@code tnt-link-back-core} onto {@code tnt.realtime.gps.position.updated} (Chantier G,
 * Audit n5 P-17): materializes the latest deliverer position in Redis (see
 * {@code LinkPositionRedisRepository}) and republishes a compact per-mission snapshot on
 * {@link TntTopics#LINK_TRACKING_POSITION_LATEST} — a compacted log that any future consumer
 * can replay to rebuild the Redis cache from scratch.
 *
 * <p><b>Ordering:</b> the upstream producer ({@code KafkaRealtimeEventPublisher}) still keys
 * records by a random {@code eventId} rather than {@code missionId}/{@code delivererId} (Audit
 * n5 P-07, not yet fixed — see phase-1-hardening.md Chantier C), so two positions for the same
 * mission can arrive out of order. Rather than depend on a partition-ordering guarantee that
 * doesn't exist yet, {@link ILinkPositionCache#saveIfNewer} compares each event's
 * {@code occurredAt} against whatever is already cached and only overwrites when the incoming
 * event is actually newer.
 *
 * <p>Unlike {@code tnt-incident-core}'s {@code IncidentEventConsumer} (which consumes the same
 * topic but silently drops malformed messages), this listener uses MANUAL ack + a real DLQ
 * (see {@code LinkKafkaConsumerConfig}): only successfully processed records are acknowledged,
 * everything else is retried then dead-lettered.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GpsPositionKafkaConsumer {

    private final ILinkPositionCache positionCache;
    private final ObjectMapper objectMapper;
    @Qualifier("linkKafkaTemplate")
    private final KafkaTemplate<String, String> linkKafkaTemplate;

    @KafkaListener(
            topics = TntTopics.REALTIME_GPS_POSITION_UPDATED,
            groupId = "tnt-link-back-core",
            containerFactory = "linkKafkaListenerContainerFactory")
    public void onGpsPositionUpdated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        JsonNode payload = parseOrThrow(record.value());

        String missionId = payload.path("missionId").asText(null);
        if (missionId == null || missionId.isBlank()) {
            // Idle deliverer ping (no active mission) — nothing for Link's tracking cache to do.
            ack.acknowledge();
            return;
        }

        UUID tenantId = UUID.fromString(payload.path("tenantId").asText());
        JsonNode coordinates = payload.path("coordinates");
        double latitude = coordinates.path("latitude").asDouble();
        double longitude = coordinates.path("longitude").asDouble();
        Instant occurredAt = parseOccurredAt(payload.path("occurredAt"));

        LinkPosition position = new LinkPosition(missionId, latitude, longitude, occurredAt);

        positionCache.saveIfNewer(tenantId, position)
                .doOnSuccess(v -> republishLatestSnapshot(tenantId, position))
                .doOnSuccess(v -> ack.acknowledge())
                .doOnError(ex -> log.error("Failed to materialize position for mission {}: {}",
                        missionId, ex.getMessage()))
                .block();
    }

    private void republishLatestSnapshot(UUID tenantId, LinkPosition position) {
        try {
            String json = objectMapper.writeValueAsString(position);
            linkKafkaTemplate.send(TntTopics.LINK_TRACKING_POSITION_LATEST, position.missionId(), json);
        } catch (Exception e) {
            // Best-effort companion log, not the source of truth (Redis already has the value) —
            // never fail the consume/ack over a republish problem.
            log.warn("Failed to republish latest-position snapshot for mission {}: {}",
                    position.missionId(), e.getMessage());
        }
    }

    private JsonNode parseOrThrow(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed GpsPositionUpdatedEvent payload", e);
        }
    }

    /** {@code RealtimeDomainEvent.occurredAt} serializes as a Jackson-JSR310 array or ISO string
     *  depending on {@code WRITE_DATES_AS_TIMESTAMPS} — handle both defensively. */
    private Instant parseOccurredAt(JsonNode node) {
        if (node.isArray()) {
            int[] p = new int[7];
            for (int i = 0; i < node.size() && i < 7; i++) {
                p[i] = node.get(i).asInt();
            }
            LocalDateTime ldt = LocalDateTime.of(p[0], Math.max(p[1], 1), Math.max(p[2], 1), p[3], p[4], p[5], p[6]);
            return ldt.toInstant(ZoneOffset.UTC);
        }
        if (node.isTextual()) {
            return LocalDateTime.parse(node.asText()).toInstant(ZoneOffset.UTC);
        }
        return Instant.now();
    }
}
