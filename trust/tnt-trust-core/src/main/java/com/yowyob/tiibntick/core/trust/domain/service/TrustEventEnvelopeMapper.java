package com.yowyob.tiibntick.core.trust.domain.service;

import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Domain Service — {@code TrustEventEnvelopeMapper}.
 *
 * <p>Translates a {@link LogisticTrustEvent} into the wire-format map conforming
 * to the {@code TrustEventKafkaMessage} contract of {@code yow-trust-event}.
 *
 * <p>Extracted as a pure, stateless function so both the live publish path
 * ({@code KafkaTrustEventPublisherAdapter}) and the retry-queue enqueue path
 * ({@code LogisticEventPublisherService}'s fallback) build the exact same
 * envelope — the retry queue stores the serialized envelope rather than the
 * {@link LogisticTrustEvent} itself, since the latter has no public constructor
 * and is not meant to be Jackson-deserializable (pure domain code).
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public final class TrustEventEnvelopeMapper {

    private TrustEventEnvelopeMapper() {
    }

    /**
     * Builds the Kafka message map conforming to the yow-trust-event contract.
     *
     * @param event the logistic trust event to translate
     * @return an ordered map ready for JSON serialization
     */
    public static Map<String, Object> toEnvelope(final LogisticTrustEvent event) {
        final Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("correlationId", event.getCorrelationId());
        msg.put("tenantId", event.getTenantId());
        msg.put("solutionCode", LogisticTrustEvent.SOLUTION_CODE);
        msg.put("eventType", event.toKernelEventType());
        msg.put("entityType", event.getEntityType());
        msg.put("entityId", event.getEntityId());
        msg.put("payload", event.toKafkaPayload());
        msg.put("sourceService", LogisticTrustEvent.SOURCE_SERVICE);
        msg.put("occurredAt", event.getOccurredAt().toString());
        if (event.getMissionId() != null) msg.put("missionId", event.getMissionId());
        if (event.getActorId() != null) msg.put("actorId", event.getActorId());
        return msg;
    }
}
