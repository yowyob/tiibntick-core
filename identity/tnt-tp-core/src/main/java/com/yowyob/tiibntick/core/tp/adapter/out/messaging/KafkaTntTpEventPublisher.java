package com.yowyob.tiibntick.core.tp.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.tp.application.port.out.TntTpEventPublisher;
import com.yowyob.tiibntick.core.tp.domain.event.TntTpDomainEvents.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox-backed adapter for publishing tnt-tp-core domain events.
 *
 * <p>Chantier C · Audit n°3 · P5 (identity domain): delegates to
 * {@link PublishEventUseCase}/{@link PublishEventBatchUseCase} (yow-event-kernel's
 * transactional outbox) instead of a direct {@code KafkaTemplate.send()}. The previous
 * implementation never awaited the broker acknowledgement (fire-and-forget inside
 * {@code Mono.fromCallable}) and {@code publishAll} subscribed each event ad hoc — both
 * silent-loss paths are gone: envelopes commit with the business transaction and
 * {@code OutboxPollerService} handles delivery with retry/DLQ. The Kafka message body
 * remains the raw event JSON, so existing consumers are unaffected.
 *
 * @author MANFOUO Braun
 */
@Component
public class KafkaTntTpEventPublisher implements TntTpEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaTntTpEventPublisher.class);

    private static final String TOPIC_CLIENT_PROFILE  = "tnt.tp.client.profile.events";
    private static final String TOPIC_KYC             = "tnt.tp.kyc.events";
    private static final String TOPIC_LOYALTY         = "tnt.tp.loyalty.events";
    private static final String TOPIC_RATING          = "tnt.tp.rating.events";
    private static final String TOPIC_PHONE_ALIAS     = "tnt.tp.phone.alias.events";

    private static final String AGGREGATE_TYPE = "ThirdParty";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final PublishEventBatchUseCase publishEventBatchUseCase;
    private final ObjectMapper objectMapper;

    public KafkaTntTpEventPublisher(
            PublishEventUseCase publishEventUseCase,
            PublishEventBatchUseCase publishEventBatchUseCase,
            @Qualifier("tntTpObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.publishEventBatchUseCase = publishEventBatchUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(Object event) {
        return Mono.fromCallable(() -> toEnvelope(event))
                .flatMap(publishEventUseCase::publish)
                .doOnSuccess(v -> log.debug("Enqueued event type={} to outbox",
                        event.getClass().getSimpleName()))
                .doOnError(e -> log.error("Failed to enqueue event type={} to outbox: {}",
                        event.getClass().getSimpleName(), e.getMessage()));
    }

    @Override
    public Mono<Void> publishAll(List<Object> events) {
        if (events == null || events.isEmpty()) return Mono.empty();
        return Flux.fromIterable(events)
                .map(this::toEnvelope)
                .collectList()
                .flatMap(publishEventBatchUseCase::publishAll)
                .then();
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private DomainEventEnvelope toEnvelope(Object event) {
        return DomainEventEnvelope.wrap()
                .correlationId(resolveKey(event))
                .eventType(event.getClass().getSimpleName())
                .aggregateId(resolveKey(event))
                .aggregateType(AGGREGATE_TYPE)
                .tenantId(resolveTenantId(event))
                .solutionCode(SOLUTION_CODE)
                .payload(serialize(event))
                .kafkaTopic(resolveTopic(event))
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private String resolveTopic(Object event) {
        return switch (event) {
            case ClientProfileRegisteredEvent ignored   -> TOPIC_CLIENT_PROFILE;
            case ClientProfileDeactivatedEvent ignored  -> TOPIC_CLIENT_PROFILE;
            case KycStatusChangedEvent ignored          -> TOPIC_KYC;
            case LoyaltyPointsEarnedEvent ignored       -> TOPIC_LOYALTY;
            case LoyaltyPointsRedeemedEvent ignored     -> TOPIC_LOYALTY;
            case ThirdPartyRatedEvent ignored           -> TOPIC_RATING;
            case PhoneAliasAssignedEvent ignored        -> TOPIC_PHONE_ALIAS;
            default -> "tnt.tp.unknown.events";
        };
    }

    private String resolveKey(Object event) {
        return switch (event) {
            case ClientProfileRegisteredEvent e  -> e.thirdPartyId().toString();
            case ClientProfileDeactivatedEvent e -> e.thirdPartyId().toString();
            case KycStatusChangedEvent e         -> e.thirdPartyId().toString();
            case LoyaltyPointsEarnedEvent e      -> e.thirdPartyId().toString();
            case LoyaltyPointsRedeemedEvent e    -> e.thirdPartyId().toString();
            case ThirdPartyRatedEvent e          -> e.ratedThirdPartyId().toString();
            case PhoneAliasAssignedEvent e       -> e.thirdPartyId().toString();
            default -> "unknown";
        };
    }

    private String resolveTenantId(Object event) {
        return switch (event) {
            case ClientProfileRegisteredEvent e  -> e.tenantId().toString();
            case ClientProfileDeactivatedEvent e -> e.tenantId().toString();
            case KycStatusChangedEvent e         -> e.tenantId().toString();
            case LoyaltyPointsEarnedEvent e      -> e.tenantId().toString();
            case LoyaltyPointsRedeemedEvent e    -> e.tenantId().toString();
            case ThirdPartyRatedEvent e          -> e.tenantId().toString();
            case PhoneAliasAssignedEvent e       -> e.tenantId().toString();
            default -> "unknown";
        };
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event: " + event.getClass().getSimpleName(), e);
        }
    }
}
