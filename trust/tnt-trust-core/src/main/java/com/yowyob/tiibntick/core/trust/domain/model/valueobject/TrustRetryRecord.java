package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Value Object — {@code TrustRetryRecord}.
 *
 * <p>A single pending row of {@code tnt_trust.trust_retry_queue} — a Kafka
 * envelope that failed (or was skipped, because the gateway was already known
 * unavailable) to publish to {@code yow.trust.events}, awaiting redelivery by
 * {@code TrustRetryQueueDrainer}.
 *
 * <p>Stores the pre-serialized wire envelope (see {@code TrustEventEnvelopeMapper})
 * rather than the original {@link LogisticTrustEvent}, since the latter has no
 * public constructor and is not meant to be Jackson-deserializable.
 *
 * <p><strong>No Spring annotations.</strong> Pure domain code.
 *
 * @param retryId        primary key
 * @param messageKey     the Kafka partition key (the original {@code entityId})
 * @param messagePayload the pre-serialized JSON envelope, ready to republish verbatim
 * @param eventType      the logistic event type name, for observability only
 * @param attemptCount   number of prior failed drain attempts
 * @param createdAt      when the row was first enqueued
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public record TrustRetryRecord(
        UUID retryId,
        String messageKey,
        String messagePayload,
        String eventType,
        int attemptCount,
        LocalDateTime createdAt) {
}
